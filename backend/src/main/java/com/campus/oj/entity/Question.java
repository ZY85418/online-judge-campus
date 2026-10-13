package com.campus.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO) public Long id;
    public String title;
    public String description;
    @TableField("sample_input") public String sampleInput;
    @TableField("sample_output") public String sampleOutput;
    public String category;
    public String difficulty;
    @TableField("standard_answer") public String standardAnswer;
    public Integer score = 100;
    public String status = "PUBLISHED";
    @TableField("create_time") public LocalDateTime createTime = LocalDateTime.now();
}
