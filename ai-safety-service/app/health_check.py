"""
后端（Spring Boot）在线探测与鉴权管理。

真实后端没有 /health 端点，因此"可达"判定改为：对告警接收地址发起一次 GET，
只要收到任意 HTTP 响应（200/400/401/404 等）即视为服务在线，仅连接失败 /
超时 / 网络异常视为不可达。原因：
    - 鉴权关闭（auth.enabled=false）时，GET /api/alarms 返回 200（分页查询）
      或 400（缺少分页参数）；
    - 鉴权开启（auth.enabled=true）时，无 token 返回 401，有 token 返回 200/400。
三者都证明 Java 服务进程存活。

鉴权支持：auth.enabled=true 时，配置用户名/密码后，本模块自动调用
POST /api/auth/login 获取 Bearer Token 并缓存（默认 7 天，即后端 token
过期时长），推送时通过 auth_headers 附加 Authorization 头；收到 401 时调用
invalidate_token() 强制重新登录。

线程安全：缓存与 token 更新均在锁内完成，网络请求在锁外执行。
"""

from __future__ import annotations

import logging
import threading
import time
from urllib.parse import urlsplit, urlunsplit

import requests

logger = logging.getLogger("ai_monitor.health")


def build_login_url(java_endpoint: str) -> str:
    """由告警接收地址推导登录地址：http://host:port/api/auth/login。"""
    parts = urlsplit(java_endpoint or "")
    if not parts.scheme or not parts.netloc:
        return ""
    return urlunsplit((parts.scheme, parts.netloc, "/api/auth/login", "", ""))


class JavaHealthChecker:
    """后端连通性探测 + 可选登录鉴权（带缓存，线程安全）。"""

    def __init__(
        self,
        java_endpoint: str,
        login_url: str = "",
        username: str = "",
        password: str = "",
        auth_enabled: bool = False,
        timeout: float = 2.0,
        cache_seconds: float = 5.0,
        token_ttl_seconds: float = 7 * 24 * 3600,
    ) -> None:
        """
        Args:
            java_endpoint:   告警接收地址（探测目标），如 http://host:8080/api/alarms。
            login_url:       登录接口，缺省由 java_endpoint 推导。
            username:        登录用户名（鉴权开启时）。
            password:        登录密码（鉴权开启时）。
            auth_enabled:    后端是否开启鉴权（auth.enabled=true）。
            timeout:         探测/登录超时秒。
            cache_seconds:   可达性结果缓存秒数。
            token_ttl_seconds: 登录 token 有效时长（后端 Redis token 过期时间）。
        """
        self.java_endpoint = java_endpoint or ""
        self.login_url = login_url or build_login_url(self.java_endpoint)
        self.username = username or ""
        self.password = password or ""
        self.auth_enabled = bool(auth_enabled)
        self.timeout = float(timeout)
        self.cache_seconds = float(cache_seconds)
        self.token_ttl_seconds = float(token_ttl_seconds)

        self._session = requests.Session()
        self._lock = threading.Lock()
        self._cache: tuple[float, bool] | None = None  # (探测时间戳, 是否可达)
        self._token: str | None = None
        self._token_expires_at: float = 0.0

        if self.auth_enabled and (not self.login_url or not self.username or not self.password):
            logger.warning(
                "后端鉴权已开启但未配置登录地址/用户名/密码，推送将携带空 token"
            )

    # ------------------------------------------------------------------ #
    # 对外接口
    # ------------------------------------------------------------------ #
    @property
    def auth_headers(self) -> dict:
        """推送时附加的鉴权头；未持有 token 时返回空 dict。"""
        with self._lock:
            token = self._token
        if token:
            return {"Authorization": f"Bearer {token}"}
        return {}

    def is_healthy(self, force: bool = False) -> bool:
        """
        后端是否可达（带缓存）。鉴权开启时先确保持有有效 token。
        """
        with self._lock:
            if (
                not force
                and self._cache is not None
                and (time.monotonic() - self._cache[0]) < self.cache_seconds
            ):
                return self._cache[1]

        if self.auth_enabled and not self.ensure_token():
            self._set_cache(False)
            return False

        healthy = self._probe()
        self._set_cache(healthy)
        return healthy

    def ensure_token(self, force: bool = False) -> bool:
        """
        确保持有未过期的登录 token。

        Returns:
            是否持有可用 token；未开启鉴权时恒为 True。
        """
        if not self.auth_enabled:
            return True
        with self._lock:
            now = time.monotonic()
            if not force and self._token and now < self._token_expires_at:
                return True
        if not self.login_url or not self.username or not self.password:
            return False

        try:
            resp = self._session.post(
                self.login_url,
                json={"username": self.username, "password": self.password},
                timeout=self.timeout,
            )
            body = resp.json()
            data = body.get("data") or {}
            token = data.get("token")
            if resp.status_code == 200 and token:
                with self._lock:
                    self._token = token
                    self._token_expires_at = time.monotonic() + self.token_ttl_seconds
                logger.info("后端登录成功: %s (token 缓存 %ds)", self.login_url, int(self.token_ttl_seconds))
                return True
            logger.warning("后端登录失败: HTTP %s, msg=%s", resp.status_code, body.get("msg"))
        except requests.RequestException as exc:
            logger.warning("后端登录异常: %s", exc)
        except Exception as exc:  # 兜底，登录失败不影响主流程
            logger.warning("后端登录未预期异常: %s", exc)

        with self._lock:
            self._token = None
            self._token_expires_at = 0.0
        return False

    def invalidate_token(self) -> None:
        """强制丢弃当前 token（收到 401 时调用，下次推送前重新登录）。"""
        with self._lock:
            self._token = None
            self._token_expires_at = 0.0

    def close(self) -> None:
        try:
            self._session.close()
        except Exception:
            pass

    # ------------------------------------------------------------------ #
    # 内部
    # ------------------------------------------------------------------ #
    def _set_cache(self, healthy: bool) -> None:
        with self._lock:
            self._cache = (time.monotonic(), healthy)

    def _probe(self) -> bool:
        """
        探测后端可达性：对告警接收地址 GET，收到任意 HTTP 响应即视为在线。
        """
        if not self.java_endpoint:
            return False
        try:
            resp = self._session.get(
                self.java_endpoint, headers=self.auth_headers, timeout=self.timeout
            )
            logger.debug("后端探测: HTTP %s (%s)", resp.status_code, self.java_endpoint)
            return True
        except requests.RequestException as exc:
            logger.debug("后端探测失败: %s (%s)", exc, self.java_endpoint)
            return False
        except Exception:  # 兜底，任何异常都视为不可达
            logger.debug("后端探测未预期异常", exc_info=True)
            return False
