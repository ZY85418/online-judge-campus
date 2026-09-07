package com.campus.oj.controller;

import com.campus.oj.entity.Question;
import com.campus.oj.repository.QuestionRepository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/questions")
public class QuestionController {
    private final QuestionRepository questions;
    public QuestionController(QuestionRepository questions) { this.questions = questions; }
    @GetMapping public List<Question> list(@RequestParam(required=false) String category, @RequestParam(required=false) String difficulty) {
        QueryWrapper<Question> wrapper = new QueryWrapper<>();
        if (category != null) wrapper.eq("category", category); if (difficulty != null) wrapper.eq("difficulty", difficulty);
        return questions.selectList(wrapper);
    }
    @GetMapping("/{id}") public Question one(@PathVariable Long id) { return find(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Question create(@RequestBody Question body) { body.id = null; questions.insert(body); return body; }
    @PutMapping("/{id}") public Question update(@PathVariable Long id, @RequestBody Question body) { body.id = id; questions.updateById(body); return body; }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { questions.deleteById(id); }
    private Question find(Long id) { Question question=questions.selectById(id); if(question==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在"); return question; }
}
