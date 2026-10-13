package com.campus.oj.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.oj.entity.Exam;
public interface ExamRepository extends BaseMapper<Exam> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM exam WHERE id = #{id} FOR UPDATE")
    Exam lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
