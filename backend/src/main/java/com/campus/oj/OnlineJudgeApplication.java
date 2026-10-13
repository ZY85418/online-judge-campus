package com.campus.oj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.campus.oj.repository")
public class OnlineJudgeApplication {
    public static void main(String[] args) { SpringApplication.run(OnlineJudgeApplication.class, args); }
}
