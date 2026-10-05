package com.kellen.oa_system.config;

import com.kellen.oa_system.file.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置：把文件存储目录挂到 HTTP 路径上。
 *
 * <p>映射 {@code file.access-prefix}（默认 {@code /uploads}）到 {@code file.base-path}，
 * 使上传后返回的相对地址可以直接被浏览器加载。路径由配置推导，代码里不出现任何盘符。</p>
 *
 * <p>注意：该前缀要在 {@code SecurityConfig} 中放行，否则 {@code <img src>} 这类
 * 不带 Authorization 头的请求会被拦成 401。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** 静态资源缓存秒数，头像这类文件改名即换 URL，长缓存收益更高 */
    private static final int CACHE_SECONDS = 3600;

    private final FileStorageProperties fileStorageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = fileStorageProperties.getBasePath();
        if (!StringUtils.hasText(basePath)) {
            // 未配置目录时不注册映射，让问题在启动日志里可见，而不是产生一个永远 404 的路径
            return;
        }
        String location = Paths.get(basePath).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(fileStorageProperties.getAccessPrefix() + "/**")
                .addResourceLocations(location)
                .setCachePeriod(CACHE_SECONDS);
    }
}
