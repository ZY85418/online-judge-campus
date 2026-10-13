package com.campus.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.oj.config.DataInitializer;
import com.campus.oj.entity.*;
import com.campus.oj.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:examtest;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@Sql("/exam-test-schema.sql")
class ExamWorkflowTest {
    @MockBean DataInitializer initializer;
    @MockBean ExamExpiryScheduler scheduler;
    @Autowired ExamService exams;
    @Autowired QuestionService questions;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questionRows;
    @Autowired ExamRepository examRows;
    @Autowired ExamAttemptRepository attempts;
    @SpyBean ExamResultRepository results;
    @Autowired SubmissionRepository submissions;
    @Autowired JudgeService judge;
    @Autowired JdbcTemplate jdbc;
    private Long student1,student2,q1,q2;

    @BeforeEach void seed() {
        reset(results);
        User one=new User(); one.username="one"; one.password="test"; users.insert(one); student1=one.id;
        User two=new User(); two.username="two"; two.password="test"; users.insert(two); student2=two.id;
        q1=questions.save(null,question("first",100)).id;
        q2=questions.save(null,question("second",50)).id;
    }
    private Question question(String title,int score) {
        Question q=new Question(); q.title=title; q.score=score; q.category="test"; q.description="test";
        q.standardAnswer="answer"; q.difficulty="简单"; return q;
    }
    private Exam exam(String ids,String status) {
        Exam e=new Exam(); e.name="test exam"; e.startTime=LocalDateTime.now().minusMinutes(1);
        e.endTime=LocalDateTime.now().plusMinutes(5); e.duration=1; e.status=status; e.questionIds=ids;
        return exams.save(null,e);
    }
    private ExamAttempt attempt(Long id,Long user) { return attempts.selectOne(new QueryWrapper<ExamAttempt>().eq("exam_id",id).eq("user_id",user)); }
    private void expectError(int status,Runnable action) {
        assertEquals(status,assertThrows(ResponseStatusException.class,action::run).getStatus().value());
    }

    @Test void multiUserRepeatedSubmissionsProduceIndependentFrozenGrades() {
        Exam e=exam(q1+","+q2,"PUBLISHED");
        Map<String,Object> started=exams.start(e.id,student1);
        Long attemptId=((ExamAttempt)started.get("attempt")).id;
        assertEquals(attemptId,((ExamAttempt)exams.start(e.id,student1).get("attempt")).id);
        assertEquals(attemptId,((ExamAttempt)exams.current(e.id,student1).get("attempt")).id);
        exams.start(e.id,student2);
        exams.judge(e.id,student1,q1,"answer",true);
        exams.judge(e.id,student1,q1,"wrong",true);
        exams.judge(e.id,student1,q1,"answer",true);
        exams.judge(e.id,student2,q2,"answer",true);
        exams.finish(e.id,student1); exams.finish(e.id,student1); exams.finish(e.id,student2);
        assertEquals(100,((ExamResult)exams.resultDetail(e.id,student1).get("result")).totalScore);
        assertEquals(50,((ExamResult)exams.resultDetail(e.id,student2).get("result")).totalScore);
        assertEquals(2,results.selectCount(null));
        expectError(409,()->exams.judge(e.id,student1,q2,"answer",true));
        assertEquals(4,submissions.selectCount(null));
    }
    @Test void personalExamStatusReturnsOnlyTheRequestedStudentsAttemptsAndGrades() {
        Exam e=exam(q1.toString(),"PUBLISHED");
        exams.start(e.id,student1); exams.judge(e.id,student1,q1,"answer",true); exams.finish(e.id,student1);
        exams.start(e.id,student2);
        List<Map<String,Object>> one=exams.myStatuses(student1), two=exams.myStatuses(student2);
        assertEquals(1,one.size()); assertEquals("SUBMITTED",one.get(0).get("status")); assertEquals(100,one.get(0).get("totalScore"));
        assertEquals(1,two.size()); assertEquals("IN_PROGRESS",two.get(0).get("status")); assertNull(two.get(0).get("totalScore"));
    }
    @Test void adminResultsIdentifyEachStudentWithoutExposingCredentials() {
        Exam e=exam(q1.toString(),"PUBLISHED");
        User one=users.selectById(student1); one.realName="Student One"; users.updateById(one);
        exams.start(e.id,student1); exams.judge(e.id,student1,q1,"answer",true); exams.finish(e.id,student1);
        exams.start(e.id,student2); exams.finish(e.id,student2);
        List<Map<String,Object>> rows=exams.results(null,e.id,true);
        assertEquals(2,rows.size());
        for(Map<String,Object> row:rows) {
            ExamResult result=(ExamResult)row.get("result");
            Map<?,?> student=(Map<?,?>)row.get("student");
            assertEquals(result.userId,student.get("id"));
            assertEquals(users.selectById(result.userId).username,student.get("username"));
            assertEquals(3,student.size()); assertFalse(student.containsKey("password"));
        }
        assertFalse(exams.results(student1,e.id,false).get(0).containsKey("student"));
    }
    @Test void analyticsCountsEndedAttemptsOnceAndExcludesInProgressAttemptsFromQuestionRates() {
        Exam e=exam(q1+","+q2,"PUBLISHED");
        User three=new User(); three.username="three"; three.password="test"; three.realName="Student Three"; users.insert(three);
        User one=users.selectById(student1); one.realName="Student One"; users.updateById(one);
        exams.start(e.id,student1); exams.start(e.id,student2); exams.start(e.id,three.id);
        exams.judge(e.id,student1,q1,"answer",true);
        exams.judge(e.id,student1,q2,"wrong",true);
        exams.judge(e.id,student2,q1,"answer",true);
        exams.judge(e.id,student2,q2,"answer",true);
        exams.finish(e.id,student1); exams.finish(e.id,student2);

        Map<String,Object> report=exams.analytics(e.id);
        Map<String,Object> counts=(Map<String,Object>)report.get("attemptCounts");
        assertEquals(3,counts.get("started")); assertEquals(2L,counts.get("completed"));
        assertEquals(1L,counts.get("inProgress")); assertEquals(66.7,counts.get("completionRate"));
        List<Map<String,Object>> questionStats=(List<Map<String,Object>>)report.get("questionStats");
        assertEquals(2,questionStats.size());
        assertEquals(2,questionStats.get(0).get("passCount")); assertEquals(100.0,questionStats.get(0).get("passRate"));
        assertEquals(1,questionStats.get(1).get("passCount")); assertEquals(1,questionStats.get(1).get("notPassedCount"));
        assertEquals(50.0,questionStats.get(1).get("passRate"));
        List<Map<String,Object>> review=(List<Map<String,Object>>)report.get("studentsToReview");
        assertEquals(1,review.size()); assertEquals(student1,review.get(0).get("userId"));
        assertEquals("Student One",review.get(0).get("realName")); assertEquals(Collections.singletonList(q2),review.get(0).get("questionIds"));
    }
    @Test void invalidExamContextNeverSavesSubmission() {
        Exam e=exam(q1.toString(),"PUBLISHED");
        expectError(404,()->exams.judge(999L,student1,q1,"answer",true));
        expectError(400,()->exams.judge(e.id,student1,q1,"answer",true));
        exams.start(e.id,student1);
        expectError(400,()->exams.judge(e.id,student1,q2,"answer",true));
        assertEquals(0,submissions.selectCount(null));
    }
    @Test void exactDeadlineCollectsAndRejectsBothRunAndSubmit() {
        Exam e=exam(q1.toString(),"PUBLISHED"); exams.start(e.id,student1);
        exams.judge(e.id,student1,q1,"answer",true);
        jdbc.update("UPDATE exam_attempt SET start_time=?,deadline=? WHERE exam_id=?",LocalDateTime.now().minusMinutes(1),LocalDateTime.now().minusSeconds(1),e.id);
        jdbc.update("UPDATE submission SET submit_time=? WHERE exam_id=?",LocalDateTime.now().minusSeconds(5),e.id);
        expectError(409,()->exams.judge(e.id,student1,q1,"answer",true));
        expectError(409,()->exams.judge(e.id,student1,q1,"answer",false));
        assertEquals("TIMED_OUT",attempt(e.id,student1).status);
        assertEquals(1,results.selectCount(null));
        assertEquals(100,results.selectList(null).get(0).totalScore);
        assertEquals(1,submissions.selectCount(null));
    }

    @Test void persistedSubmissionKeepsTheProvidedBusinessTime() {
        LocalDateTime businessTime=LocalDateTime.of(2026, 10, 7, 10, 0, 0, 123000000);
        judge.judge(student1, null, questionRows.selectById(q1), "answer", true, businessTime);
        Submission saved=submissions.selectOne(new QueryWrapper<Submission>().eq("user_id",student1).eq("question_id",q1));
        assertNotNull(saved);
        assertEquals(businessTime, saved.submitTime);
    }
    @Test void automaticCollectionWorksWithoutBrowserAndIsIdempotent() {
        Exam e=exam(q1.toString(),"PUBLISHED"); exams.start(e.id,student1);
        jdbc.update("UPDATE exam_attempt SET deadline=? WHERE exam_id=?",LocalDateTime.now().minusSeconds(1),e.id);
        new ExamExpiryScheduler(attempts,exams).collectExpired();
        new ExamExpiryScheduler(attempts,exams).collectExpired();
        assertEquals("TIMED_OUT",attempt(e.id,student1).status);
        assertEquals(1,results.selectCount(null));
    }
    @Test void failedGradeWriteRollsBackAndCanBeRetried() {
        Exam e=exam(q1.toString(),"PUBLISHED"); exams.start(e.id,student1);
        doThrow(new IllegalStateException("simulated database failure")).when(results).insert(any(ExamResult.class));
        assertThrows(IllegalStateException.class,()->exams.finish(e.id,student1));
        assertEquals("IN_PROGRESS",attempt(e.id,student1).status);
        assertEquals(0,results.selectCount(null));
        reset(results); exams.finish(e.id,student1);
        assertEquals(1,results.selectCount(null));
    }
    @Test void unpublishedAndFutureExamsCannotBeStarted() {
        Exam draft=exam(q1.toString(),"DRAFT"); expectError(400,()->exams.start(draft.id,student1));
        Exam future=new Exam(); future.name="future"; future.startTime=LocalDateTime.now().plusMinutes(1);
        future.endTime=future.startTime.plusMinutes(5); future.status="PUBLISHED"; future.questionIds=q1.toString();
        Exam saved=exams.save(null,future); expectError(400,()->exams.start(saved.id,student1));
        jdbc.update("UPDATE exam SET end_time=? WHERE id=?",LocalDateTime.now().minusMinutes(1),saved.id);
        expectError(400,()->exams.start(saved.id,student1));
        assertEquals(0,attempts.selectCount(null));
    }
    @Test void closeCollectsAllParticipantsAndLocksPublishedExam() {
        Exam e=exam(q1.toString(),"PUBLISHED"); exams.start(e.id,student1); exams.start(e.id,student2);
        exams.judge(e.id,student1,q1,"answer",true);
        expectError(409,()->exams.save(e.id,e)); exams.close(e.id); exams.close(e.id);
        assertEquals(2,results.selectCount(null)); assertEquals("CLOSED",attempt(e.id,student1).status);
        expectError(409,()->exams.judge(e.id,student2,q1,"answer",true));
        assertTrue(exams.list(false).stream().anyMatch(row->row.id.equals(e.id)));
        assertEquals("CLOSED",exams.list(false).stream().filter(row->row.id.equals(e.id)).findFirst().get().status);
    }
    @Test void referencedQuestionsCannotChangeOrDisappearAndDraftSelectionCanReleaseThem() {
        Exam e=exam(q1.toString(),"DRAFT");
        expectError(409,()->questions.delete(q1)); expectError(409,()->questions.save(q1,question("changed",200)));
        e.questionIds=q2.toString(); exams.save(e.id,e); questions.delete(q1);
        expectError(404,()->questions.save(q1,question("missing",100)));
        Long fresh=questions.save(null,question("fresh",100)).id; exams.judge(null,student1,fresh,"answer",true);
        expectError(409,()->questions.delete(fresh));
    }
    @Test void hiddenQuestionsAndInvalidDefinitionsAreRejected() {
        Question hidden=question("hidden",100); hidden.status="DRAFT"; hidden=questions.save(null,hidden); Long hiddenId=hidden.id;
        expectError(404,()->questions.find(hiddenId,false)); expectError(404,()->exams.judge(null,student1,hiddenId,"answer",true));
        assertFalse(questions.list(null,null,false).stream().anyMatch(q->q.id.equals(hiddenId)));
        expectError(400,()->exam(hiddenId.toString(),"PUBLISHED"));
        expectError(400,()->exam(q1+","+q1,"PUBLISHED"));
        Question invalid=question("bad",0); expectError(400,()->questions.save(null,invalid));
        invalid.score=null; expectError(400,()->questions.save(null,invalid));
        invalid.score=100; invalid.standardAnswer=""; expectError(400,()->questions.save(null,invalid));
        Question oversizedSample=question("oversized sample",100); oversizedSample.sampleInput=new String(new char[60001]).replace('\0','x');
        expectError(400,()->questions.save(null,oversizedSample));
        Exam bad=new Exam(); bad.name="bad"; bad.startTime=LocalDateTime.now(); bad.endTime=bad.startTime.minusMinutes(1);
        expectError(400,()->exams.save(null,bad));
    }
    @Test void optionalQuestionSamplesPersistAndOldQuestionsRemainReadable() {
        Question withSamples=question("sampled",100); withSamples.sampleInput="2 3"; withSamples.sampleOutput="5";
        Long id=questions.save(null,withSamples).id;
        Question loaded=questionRows.selectById(id);
        assertEquals("2 3",loaded.sampleInput); assertEquals("5",loaded.sampleOutput);
        Question withoutSamples=question("legacy",100); Long legacyId=questions.save(null,withoutSamples).id;
        assertNull(questionRows.selectById(legacyId).sampleInput);
        assertNull(questionRows.selectById(legacyId).sampleOutput);
    }
    @Test void deadlineUsesEarlierOfDurationAndExamEnd() {
        Exam e=exam(q1.toString(),"PUBLISHED");
        ExamAttempt a=(ExamAttempt)exams.start(e.id,student1).get("attempt");
        assertEquals(a.startTime.plusMinutes(1),a.deadline);
        Exam shortExam=new Exam(); shortExam.name="short"; shortExam.startTime=LocalDateTime.now().minusMinutes(1);
        shortExam.endTime=LocalDateTime.now().plusSeconds(15); shortExam.duration=90; shortExam.status="PUBLISHED"; shortExam.questionIds=q1.toString();
        shortExam=exams.save(null,shortExam);
        assertEquals(examRows.selectById(shortExam.id).endTime,((ExamAttempt)exams.start(shortExam.id,student1).get("attempt")).deadline);
    }
    @Test void simultaneousFinishCreatesOneGrade() throws Exception {
        Exam e=exam(q1.toString(),"PUBLISHED"); exams.start(e.id,student1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->exams.finish(e.id,student1)));
            for(Future<?> future:futures) future.get(10,TimeUnit.SECONDS);
            assertEquals(1,results.selectCount(null));
        } finally { pool.shutdownNow(); }
    }
}
