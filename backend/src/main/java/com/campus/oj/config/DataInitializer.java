package com.campus.oj.config;

import com.campus.oj.entity.*;
import com.campus.oj.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    @Bean CommandLineRunner initialData(UserRepository users, QuestionRepository questions) { return args -> {
        if (!users.findByUsername("admin").isPresent()) { User u=new User(); u.username="admin";u.password="admin123";u.realName="系统管理员";u.role="ADMIN";users.save(u); }
        if (!users.findByUsername("student").isPresent()) { User u=new User(); u.username="student";u.password="student123";u.realName="示例学生";users.save(u); }
        if (questions.count()==0) { Question q=new Question(); q.title="两数之和";q.category="基础算法";q.difficulty="简单";q.score=100;q.description="给定两个整数 a 和 b，编写 Java 程序输出它们的和。";q.standardAnswer="System.out.println(a + b)";questions.save(q); }
    }; }
}
