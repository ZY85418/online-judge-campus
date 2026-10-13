package com.campus.oj.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("exam_attempt")
public class ExamAttempt {
    @TableId(type = IdType.AUTO) public Long id;
    public Long examId;
    public Long userId;
    public LocalDateTime startTime;
    public LocalDateTime deadline;
    public String status;
    public LocalDateTime submitTime;
}
