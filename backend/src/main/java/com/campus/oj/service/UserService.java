package com.campus.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.oj.entity.ExamAttempt;
import com.campus.oj.entity.ExamResult;
import com.campus.oj.entity.Submission;
import com.campus.oj.entity.User;
import com.campus.oj.repository.ExamAttemptRepository;
import com.campus.oj.repository.ExamResultRepository;
import com.campus.oj.repository.SubmissionRepository;
import com.campus.oj.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository users;
    private final ExamAttemptRepository attempts;
    private final ExamResultRepository results;
    private final SubmissionRepository submissions;

    public UserService(UserRepository users, ExamAttemptRepository attempts, ExamResultRepository results, SubmissionRepository submissions) {
        this.users=users; this.attempts=attempts; this.results=results; this.submissions=submissions;
    }

    public Map<String,Object> students(String keyword, Long userId, String status, int page, int pageSize) {
        QueryWrapper<User> query=new QueryWrapper<User>().eq("role","STUDENT");
        if(userId!=null) query.eq("id",userId);
        if(status!=null) query.eq("status",status);
        if(keyword!=null && !keyword.trim().isEmpty()) {
            String term=keyword.trim(); query.and(w -> w.like("username",term).or().like("real_name",term));
        }
        long total=users.selectCount(query);
        query.orderByDesc("id").last("LIMIT " + pageSize + " OFFSET " + ((long)page-1)*pageSize);
        List<User> selected=users.selectList(query);
        List<Map<String,Object>> items=new ArrayList<>();
        for(User user:selected) items.add(summary(user));
        Map<String,Object> response=new LinkedHashMap<>(); response.put("items",items); response.put("page",page);
        response.put("pageSize",pageSize); response.put("total",total); return response;
    }

    public Map<String,Object> detail(Long id) {
        User user=users.selectById(id);
        if(user==null || !"STUDENT".equals(user.role)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"学生不存在");
        Map<String,Object> response=summary(user);
        response.put("attempts",attempts.selectList(new QueryWrapper<ExamAttempt>().eq("user_id",id).orderByDesc("start_time")));
        response.put("results",results.selectList(new QueryWrapper<ExamResult>().eq("user_id",id).orderByDesc("submit_time")));
        List<Submission> recent=submissions.selectList(new QueryWrapper<Submission>().eq("user_id",id).orderByDesc("submit_time").last("LIMIT 20"));
        response.put("recentSubmissions",recent.stream().map(row -> {
            Map<String,Object> item=new LinkedHashMap<>(); item.put("id",row.id); item.put("examId",row.examId);
            item.put("questionId",row.questionId); item.put("result",row.result); item.put("score",row.score); item.put("submitTime",row.submitTime); return item;
        }).collect(Collectors.toList()));
        return response;
    }

    private Map<String,Object> summary(User user) {
        Map<String,Object> row=new LinkedHashMap<>(); row.put("id",user.id); row.put("username",user.username);
        row.put("realName",user.realName); row.put("role",user.role); row.put("status",user.status); row.put("createTime",user.createTime);
        row.put("completedExamCount",results.selectCount(new QueryWrapper<ExamResult>().eq("user_id",user.id)));
        row.put("recentActivityTime",submissions.selectList(new QueryWrapper<Submission>().select("submit_time").eq("user_id",user.id).orderByDesc("submit_time").last("LIMIT 1")).stream().map(s->s.submitTime).findFirst().orElse(null));
        return row;
    }
}
