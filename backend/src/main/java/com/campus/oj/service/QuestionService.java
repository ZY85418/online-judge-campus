package com.campus.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.oj.entity.*;
import com.campus.oj.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class QuestionService {
    private final QuestionRepository questions;
    private final ExamQuestionRepository links;
    private final SubmissionRepository submissions;
    public QuestionService(QuestionRepository questions, ExamQuestionRepository links, SubmissionRepository submissions) {
        this.questions = questions; this.links = links; this.submissions = submissions;
    }
    public List<Question> list(String category, String difficulty, boolean admin) {
        QueryWrapper<Question> query = new QueryWrapper<>();
        if (!admin) query.eq("status", "PUBLISHED");
        if (category != null && !category.trim().isEmpty()) query.eq("category", category.trim());
        if (difficulty != null && !difficulty.trim().isEmpty()) query.eq("difficulty", difficulty);
        return questions.selectList(query.orderByAsc("id"));
    }
    public Question find(Long id, boolean admin) {
        Question question = questions.selectById(id);
        if (question == null || (!admin && !"PUBLISHED".equals(question.status)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在或未发布");
        return question;
    }
    @Transactional public Question save(Long id, Question body) {
        validate(body);
        if (id == null) { body.id = null; body.createTime = LocalDateTime.now(); questions.insert(body); }
        else {
            Question old = questions.lockById(id);
            if (old == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在");
            requireUnreferenced(id);
            body.id = id; body.createTime = old.createTime;
            if (questions.updateById(body) != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "更新失败，请刷新后重试");
        }
        return body;
    }
    @Transactional public void delete(Long id) {
        if (questions.lockById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在");
        requireUnreferenced(id);
        questions.deleteById(id);
    }
    private void requireUnreferenced(Long id) {
        if (links.selectCount(new QueryWrapper<ExamQuestion>().eq("question_id", id)) > 0
                || submissions.selectCount(new QueryWrapper<Submission>().eq("question_id", id)) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "题目已被考试或提交记录引用，不能修改或删除；请新建题目版本");
    }
    private void validate(Question q) {
        if (q.title == null || q.title.trim().isEmpty() || q.title.length() > 200
                || q.category == null || q.category.trim().isEmpty() || q.category.length() > 50
                || q.description == null || q.description.trim().isEmpty()
                || q.description.getBytes(StandardCharsets.UTF_8).length > 60000
                || tooLong(q.sampleInput) || tooLong(q.sampleOutput)
                || q.standardAnswer == null || q.standardAnswer.trim().isEmpty()
                || q.standardAnswer.getBytes(StandardCharsets.UTF_8).length > 60000
                || q.score == null || q.score <= 0 || q.score > 1000
                || !Arrays.asList("简单", "中等", "困难").contains(q.difficulty)
                || !Arrays.asList("DRAFT", "PUBLISHED", "CLOSED").contains(q.status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "题目标题、分类、描述、答案、难度和状态必须有效，分值须为1至1000");
        q.title = q.title.trim(); q.category = q.category.trim();
    }
    private boolean tooLong(String value) { return value != null && value.getBytes(StandardCharsets.UTF_8).length > 60000; }
}
