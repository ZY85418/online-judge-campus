package com.campus.oj.controller;

import com.campus.oj.entity.Submission;
import com.campus.oj.repository.SubmissionRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/results")
public class ResultController {
    private final SubmissionRepository submissions;
    public ResultController(SubmissionRepository submissions) { this.submissions=submissions; }
    @GetMapping("/my") public List<Submission> mine(@RequestHeader(value="X-User-Id", defaultValue="1") Long userId) { return submissions.findByUserIdOrderBySubmitTimeDesc(userId); }
    @GetMapping("/{examId}") public Map<String,Object> exam(@PathVariable Long examId) {
        List<Submission> rows=submissions.findByExamId(examId); int total=rows.stream().mapToInt(s -> s.score == null ? 0 : s.score).sum();
        Map<String,Object> response=new LinkedHashMap<>(); response.put("submissions", rows); response.put("totalScore", total); response.put("count", rows.size()); return response;
    }
}
