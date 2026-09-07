package com.campus.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("submission")
public class Submission {
    @TableId(type = IdType.AUTO) public Long id;
    @TableField("user_id") public Long userId;
    @TableField("exam_id") public Long examId;
    @TableField("question_id") public Long questionId;
    public String code;
    public String language = "Java";
    public String result;
    public Integer score;
    @TableField("judge_time") public Long judgeTime;
    @TableField("submit_time") public LocalDateTime submitTime = LocalDateTime.now();
}
