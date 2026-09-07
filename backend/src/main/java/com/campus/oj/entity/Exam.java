package com.campus.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("exam")
public class Exam {
    @TableId(type = IdType.AUTO) public Long id;
    public String name;
    @TableField("start_time")
    public LocalDateTime startTime;
    @TableField("end_time")
    public LocalDateTime endTime;
    public Integer duration = 90;
    public String status = "DRAFT";
    /** Comma-separated question identifiers, kept simple for this teaching project. */
    @TableField("question_ids") public String questionIds = "";
    @TableField("create_time") public LocalDateTime createTime = LocalDateTime.now();
}
