package com.campus.oj.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public String title;
    @Column(columnDefinition = "TEXT") public String description;
    public String category;
    public String difficulty;
    @Column(columnDefinition = "TEXT") public String standardAnswer;
    public Integer score = 100;
    public String status = "PUBLISHED";
    public LocalDateTime createTime = LocalDateTime.now();
}
