package com.hify.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页转发：本地部署形态下前端 dist 由文件目录托管（见 {@link SpaWebConfig}），
 * Spring Boot 自带的 WelcomePageHandlerMapping 只扫 classpath 默认位置，会对 / 返回 404。
 * 这里显式把 / 转发到静态链路上的 /index.html；RequestMapping 优先级高于欢迎页映射。
 */
@Controller
public class WelcomePageController {

    @GetMapping("/")
    public String welcome() {
        return "forward:/index.html";
    }
}
