package com.campus.oj.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Exam {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public String name;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public Integer duration = 90;
    public String status = "DRAFT";
    /** Comma-separated question identifiers, kept simple for this teaching project. */
    public String questionIds = "";
    public LocalDateTime createTime = LocalDateTime.now();
}
