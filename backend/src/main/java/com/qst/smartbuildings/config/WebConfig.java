package com.qst.smartbuildings.config;

import com.qst.smartbuildings.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web配置类 - 静态资源映射 + 拦截器注册
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private ModelStorageProperties modelStorageProperties;

    /** AI 截图存储根目录（可配置 app.ai-image-dir，缺省为 {user.dir}/ai_image） */
    @Value("${app.ai-image-dir:}")
    private String aiImageDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")                      // 拦截所有请求
                .excludePathPatterns("/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/files/**",
                        "/device-models/**",
                        "/ai_image/**",
                        "/certificate/**",
                        "/images/**");                        // 公开静态资源不要求认证
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 固定映射规则：将/ai_image/** 映射到 AI 截图存储目录
        // 1. 获取项目根目录（与src同级）
        String projectRoot = System.getProperty("user.dir");

        // 2. 拼接ai_image目录的绝对路径：优先取配置 app.ai-image-dir（如 build-sec 根目录，便于前后端共享截图），
        //    缺省回退到项目根目录下的 ai_image
        String aiImageAbsolutePath = StringUtils.hasText(aiImageDir)
                ? aiImageDir
                : projectRoot + File.separator + "ai_image" + File.separator;
        if (!aiImageAbsolutePath.endsWith(File.separator)) {
            aiImageAbsolutePath += File.separator;
        }

        // 3. 注册资源处理器
        registry.addResourceHandler("/ai_image/**")  // 访问路径
                .addResourceLocations("file:" + aiImageAbsolutePath)  // 本地文件路径
                .setCachePeriod(0);  // 开发环境不缓存，生产环境可改为3600等

        // 3D 模型 / 上传文件映射：/files/** -> 项目根目录同级 uploads/ 文件夹
        String uploadAbsolutePath = projectRoot + File.separator + "uploads" + File.separator;
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadAbsolutePath)
                .setCachePeriod(0);

        // 设备合格证附件：/certificate/** 映射到项目根目录同级的 certificate 文件夹
        String certificateDir = projectRoot + File.separator + "certificate" + File.separator;
        registry.addResourceHandler("/certificate/**")
                .addResourceLocations("file:" + certificateDir)
                .setCachePeriod(0);

        // 通用图片映射：/images/** -> 项目根目录 images/ 文件夹。
        // 图片地址统一使用 /api/images/{filename}（应用 context-path 为 /api）。
        Path imageDirectory = Paths.get(projectRoot, "images").toAbsolutePath().normalize();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(imageDirectory.toUri().toString())
                .setCachePeriod(0);

        // 设备3D模型映射：/device-models/** -> 可外部化的模型存储目录（默认 src/main/resources/device-models，
        // 生产环境通过 app.model-storage-dir 指定外部绝对目录，避免 jar 内 resources 不可写导致 404）
        registry.addResourceHandler("/device-models/**")
                .addResourceLocations(modelStorageProperties.resourceLocation())
                .setCachePeriod(0);
    }
}


