package com.campus.oj.repository;
import com.campus.oj.entity.Exam; import org.springframework.data.jpa.repository.JpaRepository;
public interface ExamRepository extends JpaRepository<Exam, Long> { }
