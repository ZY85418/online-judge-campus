package com.campus.oj.service;

import com.campus.oj.entity.Question;
import com.campus.oj.entity.Submission;
import com.campus.oj.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JudgeService {
    private final SubmissionRepository submissions;
    public JudgeService(SubmissionRepository submissions) { this.submissions = submissions; }

    public Map<String, Object> judge(Long userId, Long examId, Question question, String code, boolean persist) {
        long started = System.currentTimeMillis();
        String normalized = code == null ? "" : code.trim();
        String result;
        String message;
        if (normalized.isEmpty()) { result = "RE"; message = "未检测到可运行的代码。"; }
        else if (question.standardAnswer != null && !question.standardAnswer.trim().isEmpty() && normalized.contains(question.standardAnswer.trim())) { result = "AC"; message = "通过全部模拟测试用例。"; }
        else { result = "WA"; message = "输出与标准答案不匹配，请检查核心逻辑。"; }
        int score = "AC".equals(result) ? question.score : 0;
        long elapsed = Math.max(1, System.currentTimeMillis() - started);
        if (persist) {
            Submission submission = new Submission(); submission.userId = userId; submission.examId = examId; submission.questionId = question.id;
            submission.code = code; submission.result = result; submission.score = score; submission.judgeTime = elapsed; submissions.insert(submission);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("result", result); response.put("score", score); response.put("message", message); response.put("judgeTime", elapsed);
        return response;
    }
}
