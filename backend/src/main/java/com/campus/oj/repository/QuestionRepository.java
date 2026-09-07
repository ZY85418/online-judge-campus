package com.campus.oj.repository;
import com.campus.oj.entity.Question; import org.springframework.data.jpa.repository.JpaRepository;
public interface QuestionRepository extends JpaRepository<Question, Long> { }
