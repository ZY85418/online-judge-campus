package com.campus.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.oj.entity.*;
import com.campus.oj.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamService {
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final ExamQuestionRepository links;
    private final ExamAttemptRepository attempts;
    private final ExamResultRepository results;
    private final SubmissionRepository submissions;
    private final JudgeService judge;
    public ExamService(ExamRepository exams, QuestionRepository questions, ExamQuestionRepository links,
            ExamAttemptRepository attempts, ExamResultRepository results, SubmissionRepository submissions, JudgeService judge) {
        this.exams=exams; this.questions=questions; this.links=links; this.attempts=attempts;
        this.results=results; this.submissions=submissions; this.judge=judge;
    }
    public List<Exam> list(boolean admin) {
        QueryWrapper<Exam> query = new QueryWrapper<>();
        if (!admin) query.eq("status", "PUBLISHED");
        return exams.selectList(query.orderByDesc("id"));
    }
    @Transactional public Exam save(Long id, Exam body) {
        validate(body);
        Exam previous = id == null ? null : lockedExam(id);
        if (previous != null && !"DRAFT".equals(previous.status))
            throw error(HttpStatus.CONFLICT, "发布后的考试不能编辑，请新建考试或关闭原考试");
        List<Long> ids = parseQuestionIds(body.questionIds);
        // Lock in ID order to serialize edits/deletes against exam selection.
        for (Long questionId : new TreeSet<>(ids)) {
            Question question = questions.lockById(questionId);
            if (question == null || !"PUBLISHED".equals(question.status))
                throw error(HttpStatus.BAD_REQUEST, "考试只能选择已发布且存在的题目");
            requirePublished(question);
        }
        if ("PUBLISHED".equals(body.status) && (ids.isEmpty() || !BusinessClock.now().isBefore(body.endTime)))
            throw error(HttpStatus.BAD_REQUEST, "发布考试需要至少一道题，且结束时间必须晚于当前时间");
        body.questionIds = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (previous == null) { body.id=null; body.createTime=BusinessClock.now(); exams.insert(body); }
        else { body.id=id; body.createTime=previous.createTime; exams.updateById(body); }
        links.delete(new QueryWrapper<ExamQuestion>().eq("exam_id", body.id));
        for (int i=0; i<ids.size(); i++) {
            ExamQuestion link=new ExamQuestion(); link.examId=body.id; link.questionId=ids.get(i); link.sortOrder=i;
            links.insert(link);
        }
        return body;
    }
    @Transactional public Exam close(Long id) {
        Exam exam=lockedExam(id);
        exam.status="CLOSED"; exams.updateById(exam);
        for (ExamAttempt attempt : attempts.selectList(new QueryWrapper<ExamAttempt>().eq("exam_id", id).eq("status", "IN_PROGRESS")))
            finalizeAttempt(attempt, "CLOSED", BusinessClock.now());
        return exam;
    }
    @Transactional public Map<String,Object> start(Long id, Long userId) {
        Exam exam=lockedExam(id);
        ExamAttempt attempt=attempt(id, userId);
        LocalDateTime now=BusinessClock.now();
        if (attempt != null) { expire(exam, attempt, now); return session(exam, attempt, now); }
        if (!"PUBLISHED".equals(exam.status) || now.isBefore(exam.startTime) || !now.isBefore(exam.endTime))
            throw error(HttpStatus.BAD_REQUEST, "考试尚未开始、已结束或未发布");
        if (questionLinks(id).isEmpty()) throw error(HttpStatus.CONFLICT, "考试没有有效题目，请联系管理员");
        attempt=new ExamAttempt(); attempt.examId=id; attempt.userId=userId; attempt.startTime=now;
        attempt.deadline=now.plusMinutes(exam.duration).isBefore(exam.endTime) ? now.plusMinutes(exam.duration) : exam.endTime;
        attempt.status="IN_PROGRESS"; attempts.insert(attempt);
        return session(exam, attempt, now);
    }
    @Transactional public Map<String,Object> current(Long id, Long userId) {
        Exam exam=lockedExam(id); ExamAttempt attempt=requireAttempt(id, userId);
        LocalDateTime now=BusinessClock.now(); expire(exam, attempt, now);
        return session(exam, attempt, now);
    }
    @Transactional public Map<String,Object> finish(Long id, Long userId) {
        Exam exam=lockedExam(id); ExamAttempt attempt=requireAttempt(id, userId);
        LocalDateTime now=BusinessClock.now();
        expire(exam, attempt, now);
        if ("IN_PROGRESS".equals(attempt.status)) finalizeAttempt(attempt, "SUBMITTED", now);
        return session(exam, attempt, now);
    }
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String,Object> judge(Long examId, Long userId, Long questionId, String code, boolean persist) {
        if (examId == null) {
            Question question=questions.lockById(questionId);
            requirePublished(question);
            return judge.judge(userId, null, question, code, persist);
        }
        Exam exam=lockedExam(examId); ExamAttempt attempt=requireAttempt(examId, userId);
        LocalDateTime submittedAt=BusinessClock.now();
        expire(exam, attempt, submittedAt);
        if (!"IN_PROGRESS".equals(attempt.status)) throw error(HttpStatus.CONFLICT, "考试已交卷、关闭或超时，不能继续提交");
        if (links.selectCount(new QueryWrapper<ExamQuestion>().eq("exam_id", examId).eq("question_id", questionId)) == 0)
            throw error(HttpStatus.BAD_REQUEST, "题目不属于当前考试");
        Question question=questions.lockById(questionId); requirePublished(question);
        return judge.judge(userId, examId, question, code, persist, submittedAt);
    }
    @Transactional public void finalizeExpired(Long id, Long userId) {
        Exam exam=lockedExam(id); ExamAttempt attempt=attempt(id,userId);
        if (attempt != null) expire(exam, attempt, BusinessClock.now());
    }
    private void expire(Exam exam, ExamAttempt attempt, LocalDateTime now) {
        if (!"IN_PROGRESS".equals(attempt.status)) return;
        if ("CLOSED".equals(exam.status)) finalizeAttempt(attempt, "CLOSED", now);
        else if (!now.isBefore(attempt.deadline)) finalizeAttempt(attempt, "TIMED_OUT", attempt.deadline);
    }
    private void finalizeAttempt(ExamAttempt attempt, String state, LocalDateTime time) {
        if (!"IN_PROGRESS".equals(attempt.status)) return;
        int total=scoreDetails(attempt.examId, attempt.userId).stream().mapToInt(row -> (Integer) row.get("score")).sum();
        ExamResult result=new ExamResult(); result.examId=attempt.examId; result.userId=attempt.userId;
        result.totalScore=total; result.submitTime=time; results.insert(result);
        attempt.status=state; attempt.submitTime=time; attempts.updateById(attempt);
    }
    public List<Map<String,Object>> scoreDetails(Long examId, Long userId) {
        ExamAttempt attempt=attempt(examId,userId);
        QueryWrapper<Submission> query=new QueryWrapper<Submission>().eq("exam_id",examId).eq("user_id",userId);
        if(attempt!=null) {
            query.ge("submit_time",attempt.startTime).lt("submit_time",attempt.deadline);
            if(attempt.submitTime!=null) query.le("submit_time",attempt.submitTime);
        }
        List<Submission> rows=submissions.selectList(query.orderByAsc("id"));
        Map<Long,Submission> best=new HashMap<>();
        for (Submission row: rows) {
            Submission old=best.get(row.questionId);
            if(row.score!=null && (old==null || row.score > old.score)) best.put(row.questionId,row);
        }
        List<Map<String,Object>> details=new ArrayList<>();
        for (ExamQuestion link:questionLinks(examId)) {
            Question q=questions.selectById(link.questionId); Submission selected=best.get(link.questionId);
            Map<String,Object> detail=new LinkedHashMap<>(); detail.put("questionId",link.questionId);
            detail.put("title",q==null ? "题目不可用" : q.title); detail.put("maxScore",q==null ? 0 : q.score);
            detail.put("score",selected==null ? 0 : selected.score); detail.put("result",selected==null ? "UNANSWERED" : selected.result);
            detail.put("submissionId",selected==null ? null : selected.id); details.add(detail);
        }
        return details;
    }
    public List<Map<String,Object>> results(Long userId, Long examId, boolean admin) {
        QueryWrapper<ExamResult> query=new QueryWrapper<>();
        if (userId != null) query.eq("user_id",userId);
        if (examId != null) query.eq("exam_id",examId);
        List<Map<String,Object>> response=new ArrayList<>();
        for (ExamResult result:results.selectList(query.orderByDesc("submit_time"))) {
            Map<String,Object> row=new LinkedHashMap<>(); row.put("result",result);
            Exam exam=exams.selectById(result.examId); row.put("examName",exam==null ? "考试不可用" : exam.name);
            row.put("details",scoreDetails(result.examId,result.userId)); response.add(row);
        }
        return response;
    }
    public Map<String,Object> resultDetail(Long examId, Long userId) {
        List<Map<String,Object>> rows=results(userId,examId,false);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"成绩尚未生成");
        return rows.get(0);
    }
    private Map<String,Object> session(Exam exam, ExamAttempt attempt, LocalDateTime now) {
        Map<String,Object> response=new LinkedHashMap<>(); response.put("exam",exam); response.put("attempt",attempt); response.put("serverTime",now);
        List<Question> selected=new ArrayList<>();
        for (ExamQuestion link:questionLinks(exam.id)) selected.add(questions.selectById(link.questionId));
        response.put("questions",selected);
        response.put("submissions",submissions.selectList(new QueryWrapper<Submission>().eq("exam_id",exam.id).eq("user_id",attempt.userId).orderByDesc("id")));
        return response;
    }
    private List<ExamQuestion> questionLinks(Long id) { return links.selectList(new QueryWrapper<ExamQuestion>().eq("exam_id",id).orderByAsc("sort_order")); }
    private ExamAttempt attempt(Long id, Long userId) { return attempts.selectOne(new QueryWrapper<ExamAttempt>().eq("exam_id",id).eq("user_id",userId)); }
    private ExamAttempt requireAttempt(Long id, Long userId) {
        ExamAttempt attempt=attempt(id,userId); if (attempt==null) throw error(HttpStatus.BAD_REQUEST,"请先开始考试"); return attempt;
    }
    private Exam lockedExam(Long id) { Exam exam=exams.lockById(id); if(exam==null) throw error(HttpStatus.NOT_FOUND,"考试不存在"); return exam; }
    private void requirePublished(Question q) {
        if(q==null || !"PUBLISHED".equals(q.status)) throw error(HttpStatus.NOT_FOUND,"题目不存在或未发布");
        if(q.score==null || q.score<=0 || q.standardAnswer==null || q.standardAnswer.trim().isEmpty())
            throw error(HttpStatus.CONFLICT,"题目配置不完整，请联系管理员");
    }
    private List<Long> parseQuestionIds(String text) {
        List<Long> ids=new ArrayList<>();
        if(text==null || text.trim().isEmpty()) return ids;
        try {
            for(String part:text.split(",",-1)) { long id=Long.parseLong(part.trim()); if(id<=0 || ids.contains(id)) throw new NumberFormatException(); ids.add(id); }
        } catch(NumberFormatException error) { throw error(HttpStatus.BAD_REQUEST,"题目编号必须是互不重复的正整数"); }
        if(ids.size()>50 || text.length()>500) throw error(HttpStatus.BAD_REQUEST,"考试最多选择50道题，编号总长度不能超过500");
        return ids;
    }
    private void validate(Exam exam) {
        if(exam.name==null || exam.name.trim().isEmpty() || exam.name.length()>200 || exam.startTime==null || exam.endTime==null
                || !exam.startTime.isBefore(exam.endTime) || exam.duration==null || exam.duration<=0 || exam.duration>1440
                || !Arrays.asList("DRAFT","PUBLISHED").contains(exam.status))
            throw error(HttpStatus.BAD_REQUEST,"考试名称、时间和状态必须有效，时长须为1至1440分钟");
        exam.name=exam.name.trim();
    }
    private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
