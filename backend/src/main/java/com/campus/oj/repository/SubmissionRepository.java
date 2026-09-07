package com.campus.oj.repository;
import com.campus.oj.entity.Submission; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface SubmissionRepository extends JpaRepository<Submission, Long> { List<Submission> findByUserIdOrderBySubmitTimeDesc(Long userId); List<Submission> findByExamId(Long examId); }
