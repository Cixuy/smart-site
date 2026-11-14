package com.qst.smartbuildings.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端信息工具：从请求中提取客户端 IP、浏览器、操作系统。
 * 用于登录日志记录，不依赖第三方 User-Agent 解析库。
 */
public final class ClientInfoUtil {

    private static final String UNKNOWN = "未知";

    private ClientInfoUtil() {
    }

    /**
     * 获取客户端真实 IP（考虑反向代理转发头）
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                String candidate = value.split(",")[0].trim();
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
            }
        }
        String ip = request.getRemoteAddr();
        if (!StringUtils.hasText(ip)) {
            return UNKNOWN;
        }
        return ip;
    }

    /**
     * 获取客户端操作系统
     */
    public static String getOs(HttpServletRequest request) {
        String userAgent = safeUserAgent(request);
        if (userAgent == null) {
            return UNKNOWN;
        }
        if (userAgent.contains("Windows")) {
            return windowsVersion(userAgent);
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod")) {
            return "iOS";
        }
        if (userAgent.contains("Mac OS X")) {
            return "macOS";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        if (userAgent.contains("Unix")) {
            return "Unix";
        }
        return UNKNOWN;
    }

    /**
     * 获取客户端浏览器
     */
    public static String getBrowser(HttpServletRequest request) {
        String userAgent = safeUserAgent(request);
        if (userAgent == null) {
            return UNKNOWN;
        }
        if (userAgent.contains("Edg/")) {
            return "Edge";
        }
        if (userAgent.contains("Firefox/")) {
            return "Firefox";
        }
        if (userAgent.contains("Chrome/")) {
            return "Chrome";
        }
        if (userAgent.contains("Safari/")) {
            return "Safari";
        }
        if (userAgent.contains("Opera") || userAgent.contains("OPR/")) {
            return "Opera";
        }
        if (userAgent.contains("Trident") || userAgent.contains("MSIE")) {
            return "IE";
        }
        if (userAgent.contains("MicroMessenger")) {
            return "微信浏览器";
        }
        return UNKNOWN;
    }

    private static String windowsVersion(String userAgent) {
        if (userAgent.contains("Windows NT 10.0")) {
            return "Windows 10/11";
        }
        if (userAgent.contains("Windows NT 6.3")) {
            return "Windows 8.1";
        }
        if (userAgent.contains("Windows NT 6.2")) {
            return "Windows 8";
        }
        if (userAgent.contains("Windows NT 6.1")) {
            return "Windows 7";
        }
        if (userAgent.contains("Windows NT 6.0")) {
            return "Windows Vista";
        }
        if (userAgent.contains("Windows NT 5.1")) {
            return "Windows XP";
        }
        return "Windows";
    }

    private static String safeUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        return StringUtils.hasText(userAgent) ? userAgent : null;
    }
}