package com.campus.oj.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("exam_question")
public class ExamQuestion {
    @TableId(type = IdType.AUTO) public Long id;
    public Long examId;
    public Long questionId;
    public Integer sortOrder;
}
