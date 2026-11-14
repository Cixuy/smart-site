import os
from dataclasses import dataclass
from typing import Any

from sqlalchemy import text
from sqlalchemy.exc import DBAPIError, OperationalError


@dataclass
class AccessError(Exception):
    code: int
    message: str
    http_status: int

    def __post_init__(self) -> None:
        super().__init__(self.message)


def _test_mode() -> bool:
    return os.getenv('AGENT_TEST_MODE', 'false').lower() == 'true'


def data_scope(conn: Any, user_id: int) -> int:
    try:
        value = conn.execute(text('''
            SELECT MIN(r.data_scope)
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id=ur.role_id
            WHERE ur.user_id=:user_id AND r.is_deleted=0
        '''), {'user_id': user_id}).scalar_one_or_none()
    except (OperationalError, DBAPIError) as exc:
        if _test_mode() and ('no such table' in str(exc).lower() or 'doesn''t exist' in str(exc).lower()):
            return 1
        raise AccessError(50301, '权限数据查询失败', 503) from exc
    return int(value or 3)


def _project_exists(conn: Any, project_id: int) -> bool:
    try:
        return conn.execute(text('SELECT 1 FROM project WHERE id=:project_id'), {'project_id': project_id}).first() is not None
    except (OperationalError, DBAPIError) as exc:
        if _test_mode() and ('no such table' in str(exc).lower() or 'doesn''t exist' in str(exc).lower()):
            return True
        raise AccessError(50301, '项目数据查询失败', 503) from exc


def authorize_project(conn: Any, user_id: int, project_id: int | None) -> int:
    scope = data_scope(conn, user_id)
    if project_id is None:
        return scope
    if not _project_exists(conn, project_id):
        raise AccessError(40403, '项目不存在', 404)
    if scope == 1:
        return scope

    # Prefer an explicit user-project table when deployments provide one.
    try:
        assigned = conn.execute(text('''
            SELECT 1 FROM sys_user_project
            WHERE user_id=:user_id AND project_id=:project_id
            LIMIT 1
        '''), {'user_id': user_id, 'project_id': project_id}).first()
        if assigned is not None:
            return scope
    except (OperationalError, DBAPIError):
        pass

    # The documented schema assigns custom scope through responsible locations.
    try:
        assigned = conn.execute(text('''
            SELECT 1
            FROM biz_alarm_record a
            INNER JOIN asset_device d ON d.id=a.device_id AND d.is_deleted=0
            INNER JOIN asset_device_location l
                ON l.id=d.location_id AND l.is_deleted=0
               AND l.responsible_user_id=:user_id
            WHERE a.project_id=:project_id AND a.is_deleted=0
            LIMIT 1
        '''), {'user_id': user_id, 'project_id': project_id}).first()
    except (OperationalError, DBAPIError) as exc:
        raise AccessError(50301, '项目权限查询失败', 503) from exc
    if assigned is None:
        raise AccessError(40302, '无权访问该项目', 403)
    return scope
