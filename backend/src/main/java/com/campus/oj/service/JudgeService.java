package com.campus.oj.service;

import com.campus.oj.entity.Question;
import com.campus.oj.entity.Submission;
import com.campus.oj.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JudgeService {
    private final SubmissionRepository submissions;
    public JudgeService(SubmissionRepository submissions) { this.submissions = submissions; }

    public Map<String, Object> judge(Long userId, Long examId, Question question, String code, boolean persist) {
        return judge(userId, examId, question, code, persist, BusinessClock.now());
    }

    public Map<String, Object> judge(Long userId, Long examId, Question question, String code, boolean persist, LocalDateTime submittedAt) {
        long started = System.currentTimeMillis();
        String normalized = code == null ? "" : code.trim();
        String result;
        String message;
        if (normalized.isEmpty()) { result = "RE"; message = "模拟判题：代码为空，未执行程序。"; }
        else if (question.standardAnswer != null && !question.standardAnswer.trim().isEmpty() && normalized.contains(question.standardAnswer.trim())) { result = "AC"; message = "模拟判题：已匹配答案关键片段，未编译或执行程序。"; }
        else { result = "WA"; message = "模拟判题：未匹配答案关键片段，未编译或执行程序。"; }
        int score = "AC".equals(result) ? question.score : 0;
        long elapsed = Math.max(1, System.currentTimeMillis() - started);
        Long submissionId = null;
        if (persist) {
            Submission submission = new Submission(); submission.userId = userId; submission.examId = examId; submission.questionId = question.id;
            submission.code = code; submission.result = result; submission.score = score; submission.judgeTime = elapsed; submission.submitTime = submittedAt; submissions.insert(submission);
            submissionId = submission.id;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("result", result); response.put("score", score); response.put("message", message); response.put("judgeTime", elapsed);
        response.put("judgeMode", "SIMULATED");
        if (persist) response.put("submissionId", submissionId);
        return response;
    }
}
