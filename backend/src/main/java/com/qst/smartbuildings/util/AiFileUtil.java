package com.qst.smartbuildings.util;

import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AI 截图本地文件定位与清理工具。
 *
 * 截图访问 URL 形如 http://host:8080/api/ai_image/CAM_MOCK/xxx.jpg
 * （context-path=/api，静态映射 /ai_image/** 指向 app.ai-image-dir）。
 * 本工具将 URL 解析为本地文件路径并删除，供 AI 截图删除接口复用。
 */
public final class AiFileUtil {

    private AiFileUtil() {
    }

    /**
     * 将截图 URL / 相对路径解析为本地文件 Path。
     *
     * @param url        截图地址（http(s)://… 或 /api/ai_image/… 或 /ai_image/…）
     * @param aiImageDir 截图存储根目录（缺省回退 {user.dir}/ai_image）
     * @return 本地文件 Path；无法解析或不在 ai_image 下时返回 null
     */
    public static Path resolve(String url, String aiImageDir) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            String path = url;
            if (url.startsWith("http://") || url.startsWith("https://")) {
                path = new URI(url).getPath();
            }
            if (path == null) {
                return null;
            }
            // 兼容 context-path=/api 前缀
            if (path.startsWith("/api/")) {
                path = path.substring("/api".length());
            }
            if (!path.startsWith("/ai_image/")) {
                return null;
            }
            String root = StringUtils.hasText(aiImageDir)
                    ? aiImageDir
                    : System.getProperty("user.dir") + File.separator + "ai_image";
            return Paths.get(root).resolve(path.substring("/ai_image/".length()));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 删除截图本地文件；删除失败静默忽略（不影响数据库操作）。
     */
    public static void deleteLocalFile(String url, String aiImageDir) {
        Path file = resolve(url, aiImageDir);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 文件删除失败仅遗留本地文件
        }
    }
}
