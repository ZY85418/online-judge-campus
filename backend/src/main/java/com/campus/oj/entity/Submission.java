package com.campus.oj.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Submission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long userId;
    public Long examId;
    public Long questionId;
    @Column(columnDefinition = "TEXT") public String code;
    public String language = "Java";
    public String result;
    public Integer score;
    public Long judgeTime;
    public LocalDateTime submitTime = LocalDateTime.now();
}
