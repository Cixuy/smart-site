package com.qst.smartbuildings.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 设备3D模型存储目录配置。
 *
 * <p>模型文件按 {@code device-models/{projectId}/{sceneId}/{iconType}/{deviceCode}.glb} 存放。
 * 未配置 {@code app.model-storage-dir} 时，默认指向开发目录
 * {@code src/main/resources/device-models}，便于本地直接读写；打包为 jar 后该路径不存在，
 * 生产环境须通过 {@code app.model-storage-dir} 指定外部绝对目录，否则静态访问 /device-models/** 返回 404。</p>
 */
@Component
public class ModelStorageProperties {

    /** 外部模型存储根目录；留空表示使用开发默认目录 */
    @Value("${app.model-storage-dir:}")
    private String modelStorageDir;

    /** 模型根目录绝对路径 */
    public Path baseDir() {
        if (modelStorageDir == null || modelStorageDir.isBlank()) {
            return Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "device-models");
        }
        return Paths.get(modelStorageDir);
    }

    /** 由相对路径（device-models/...）解析为绝对文件路径 */
    public Path resolve(String relative) {
        return baseDir().resolve(relative);
    }

    /** WebMvc 资源处理器用的 file: 前缀位置（末尾带路径分隔符） */
    public String resourceLocation() {
        return "file:" + baseDir().toString() + File.separator;
    }

    public String getModelStorageDir() {
        return modelStorageDir;
    }

    public void setModelStorageDir(String modelStorageDir) {
        this.modelStorageDir = modelStorageDir;
    }
}
