package com.campus.oj.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "oj_user")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(unique = true, nullable = false) public String username;
    @Column(nullable = false) public String password;
    public String realName;
    @Column(nullable = false) public String role = "STUDENT";
    public String status = "ACTIVE";
    public LocalDateTime createTime = LocalDateTime.now();
}
