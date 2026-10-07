package com.campus.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.oj.entity.ExamAttempt;
import com.campus.oj.repository.ExamAttemptRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

@Component
public class ExamExpiryScheduler {
    private static final Logger log=LoggerFactory.getLogger(ExamExpiryScheduler.class);
    private final ExamAttemptRepository attempts;
    private final ExamService exams;
    public ExamExpiryScheduler(ExamAttemptRepository attempts,ExamService exams) { this.attempts=attempts; this.exams=exams; }
    @Scheduled(fixedDelay=10000, initialDelay=10000) public void collectExpired() {
        for(ExamAttempt attempt:attempts.selectList(new QueryWrapper<ExamAttempt>().eq("status","IN_PROGRESS").le("deadline",BusinessClock.now()))) {
            try { exams.finalizeExpired(attempt.examId,attempt.userId); }
            catch(RuntimeException error) { log.error("Failed to finalize exam {} user {}",attempt.examId,attempt.userId,error); }
        }
    }
}
