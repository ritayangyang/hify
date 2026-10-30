package com.hify.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.IOException;

/**
 * 本地部署形态下由后端直接托管前端 dist（无独立 Nginx 时使用）。
 * <ul>
 *   <li>静态目录由 {@code hify.web.dir} 指定（打包脚本默认指向包内 dist，不存在则不注册）；</li>
 *   <li>Vue 使用 history 路由：找不到对应静态文件时，非 /api 开头且无文件扩展名的路径
 *       回退到 index.html，由前端路由接管；</li>
 *   <li>/api/** 的 Controller 映射优先于静态资源映射，REST 接口不受影响。</li>
 * </ul>
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SpaWebConfig.class);

    @Value("${hify.web.dir:./dist}")
    private String webDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(webDir);
        if (!dir.isDirectory()) {
            log.info("前端静态目录不存在，跳过 SPA 资源托管: {}", dir.getAbsolutePath());
            return;
        }
        File index = new File(dir, "index.html");
        String location = "file:" + dir.getAbsolutePath() + File.separator;
        log.info("注册前端静态资源目录: {}", dir.getAbsolutePath());

        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 根路径显式回首页（super 对空路径可能返回目录资源，无法直接服务）
                        if (resourcePath.isEmpty()) {
                            Resource indexResource = new FileSystemResource(index);
                            return indexResource.isReadable() ? indexResource : null;
                        }
                        Resource requested = super.getResource(resourcePath, location);
                        if (requested != null && requested.isReadable() && requested.isFile()) {
                            return requested;
                        }
                        // 未知 API 路径维持 404；带扩展名的静态资源缺失也不回退（避免 .js/.css 返回 HTML）
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource fallback = new FileSystemResource(index);
                        return fallback.isReadable() ? fallback : null;
                    }
                });
    }
}
