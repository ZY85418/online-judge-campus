package com.campus.oj.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.oj.entity.Question;
public interface QuestionRepository extends BaseMapper<Question> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM question WHERE id = #{id} FOR UPDATE")
    Question lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
