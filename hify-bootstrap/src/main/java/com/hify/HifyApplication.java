package com.hify;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hify 引导入口。
 * 扫描根包 com.hify，覆盖 hify-common 与 modules 下全部模块；
 * Mapper 接口统一在 com.hify.**.mapper 包下被扫描。
 */
@SpringBootApplication(scanBasePackages = "com.hify")
@MapperScan("com.hify.**.mapper")
public class HifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(HifyApplication.class, args);
    }
}
