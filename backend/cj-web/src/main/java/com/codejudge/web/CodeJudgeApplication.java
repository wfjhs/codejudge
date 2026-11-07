package com.codejudge.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CodeJudge 启动类。
 */
@SpringBootApplication(scanBasePackages = "com.codejudge")
@MapperScan("com.codejudge.dao.mapper")
@ConfigurationPropertiesScan("com.codejudge.web.config")
public class CodeJudgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeJudgeApplication.class, args);
    }
}
