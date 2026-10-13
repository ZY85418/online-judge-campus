package com.campus.oj.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("exam_result")
public class ExamResult {
    @TableId(type = IdType.AUTO) public Long id;
    public Long examId;
    public Long userId;
    public Integer totalScore;
    public LocalDateTime submitTime;
}
