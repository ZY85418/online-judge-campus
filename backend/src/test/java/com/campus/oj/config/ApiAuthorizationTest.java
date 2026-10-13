package com.campus.oj.config;

import com.campus.oj.controller.*;
import com.campus.oj.entity.*;
import com.campus.oj.repository.*;
import com.campus.oj.service.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiAuthorizationTest {
    private UserRepository users;
    private QuestionRepository questions;
    private ExamRepository exams;
    private SubmissionRepository submissions;
    private SessionService sessions;
    private ExamQuestionRepository links;
    private ExamAttemptRepository attempts;
    private ExamResultRepository results;
    private UserService userService;
    private MockMvc mvc;
    private User student;
    private String studentToken;
    private String adminToken;

    @BeforeEach void setUp() {
        users = mock(UserRepository.class);
        questions = mock(QuestionRepository.class);
        exams = mock(ExamRepository.class);
        submissions = mock(SubmissionRepository.class);
        links=mock(ExamQuestionRepository.class); attempts=mock(ExamAttemptRepository.class); results=mock(ExamResultRepository.class);
        userService=mock(UserService.class);
        sessions = new SessionService(users);
        student = user(2L, "STUDENT");
        User admin = user(1L, "ADMIN");
        when(users.selectById(2L)).thenReturn(student);
        when(users.selectById(1L)).thenReturn(admin);
        studentToken = "Bearer " + sessions.create(student);
        adminToken = "Bearer " + sessions.create(admin);
        WebConfig web = new WebConfig();
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        ExamService examService=new ExamService(exams,questions,links,attempts,results,submissions,users,new JudgeService(submissions));
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(users, sessions, new PasswordService()),
                new QuestionController(new QuestionService(questions,links,submissions)), new ExamController(examService),
                new SubmissionController(questions, submissions, examService),
                new ResultController(examService), new UserController(userService))
                .setControllerAdvice(new SensitiveResponseAdvice(json), new ApiExceptionHandler())
                .addFilters(web.apiCorsFilter().getFilter(), new ApiAuthorizationFilter(sessions, json))
                .build();
    }

    @Test void studentCannotReadStudentManagement() throws Exception {
        mvc.perform(get("/api/users/students").header("Authorization", studentToken)).andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test void adminCanReadStudentManagement() throws Exception {
        when(userService.students(any(), any(), any(), anyInt(), anyInt())).thenReturn(new java.util.LinkedHashMap<String,Object>());
        mvc.perform(get("/api/users/students").param("page", "1").header("Authorization", adminToken)).andExpect(status().isOk());
        verify(userService).students(isNull(), isNull(), isNull(), eq(1), eq(50));
    }

    private User user(Long id, String role) {
        User user = new User(); user.id = id; user.username = role; user.password = "demo"; user.role = role;
        return user;
    }

    @ParameterizedTest @ValueSource(strings = {"/api/questions", "/api/exams", "/api/exams/my-status", "/api/submissions/my", "/api/submissions/admin", "/api/results/my", "/api/results/1"})
    void anonymousBusinessReadsAreRejected(String path) throws Exception {
        mvc.perform(get(path).header("X-User-Id", "1")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/questions", "/api/exams", "/api/submissions/run", "/api/submissions/submit", "/api/exams/1/start", "/api/exams/1/submit"})
    void anonymousBusinessWritesAreRejected(String path) throws Exception {
        mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"invalid", "Bearer invalid", "Bearer "})
    void invalidTokensAreRejected(String authorization) throws Exception {
        mvc.perform(get("/api/questions").header("Authorization", authorization)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void studentCannotManageQuestionsOrExams(String method) throws Exception {
        for (String root : new String[]{"/api/questions", "/api/exams"}) {
            String path = "POST".equals(method) ? root : root + "/1";
            mvc.perform(MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path)
                    .header("Authorization", studentToken).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(questions, exams);
    }

    @Test void studentCannotReadExamWideResults() throws Exception {
        mvc.perform(get("/api/results/1").header("Authorization", studentToken)).andExpect(status().isForbidden());
        verifyNoInteractions(submissions);
    }

    @Test void studentCannotReadExamAnalytics() throws Exception {
        mvc.perform(get("/api/results/analytics").param("examId","1").header("Authorization",studentToken)).andExpect(status().isForbidden());
        verifyNoInteractions(attempts,links,results,submissions);
    }

    @Test void adminAnalyticsRouteRequiresAnExistingExam() throws Exception {
        mvc.perform(get("/api/results/analytics").param("examId","999").header("Authorization",adminToken)).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/questions;extra=1", "/api/questions/", "/api/exams;extra=1", "/api/exams/"})
    void alternatePathsCannotBypassRoleChecks(String path) throws Exception {
        mvc.perform(post(path).header("Authorization", studentToken).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(questions, exams);
    }

    @Test void adminCanManageAndReadResults() throws Exception {
        mvc.perform(post("/api/questions").header("Authorization", adminToken)
                .contentType("application/json").content("{\"title\":\"demo\",\"category\":\"test\",\"description\":\"test\",\"standardAnswer\":\"answer\",\"difficulty\":\"简单\",\"score\":100}")).andExpect(status().isCreated());
        mvc.perform(post("/api/exams").header("Authorization", adminToken)
                .contentType("application/json").content("{\"name\":\"demo\",\"startTime\":\"2026-01-01T00:00:00\",\"endTime\":\"2099-01-01T00:00:00\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/results/1").header("Authorization", adminToken)).andExpect(status().isOk());
        verify(questions).insert(any(Question.class));
        verify(exams).insert(any(Exam.class));
    }

    @Test void forgedUserIdCannotChangeSubmissionOwner() throws Exception {
        Question question = new Question(); question.id = 7L; question.standardAnswer = "answer";
        when(questions.lockById(7L)).thenReturn(question);
        mvc.perform(post("/api/submissions/submit").header("Authorization", studentToken).header("X-User-Id", "1")
                .contentType("application/json").content("{\"questionId\":7,\"code\":\"answer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("AC"));
        ArgumentCaptor<Submission> captured = ArgumentCaptor.forClass(Submission.class);
        verify(submissions).insert(captured.capture());
        assertEquals(2L, captured.getValue().userId);
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"}) void forgedUserIdCannotChangeRecordQueries() throws Exception {
        mvc.perform(get("/api/submissions/my").header("Authorization", studentToken).header("X-User-Id", "1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/results/my").header("Authorization", studentToken).header("X-User-Id", "1"))
                .andExpect(status().isOk());
        ArgumentCaptor<QueryWrapper> captured = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(submissions).selectList(captured.capture());
        verify(results).selectList(any());
        for (QueryWrapper query : captured.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("user_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(2L));
            assertFalse(query.getParamNameValuePairs().containsValue(1L));
        }
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"}) void personalExamStatusUsesAuthenticatedIdentityOnly() throws Exception {
        mvc.perform(get("/api/exams/my-status").header("Authorization", studentToken).header("X-User-Id", "1"))
                .andExpect(status().isOk());
        ArgumentCaptor<QueryWrapper> attemptsQuery=ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper> resultsQuery=ArgumentCaptor.forClass(QueryWrapper.class);
        verify(attempts).selectList(attemptsQuery.capture()); verify(results).selectList(resultsQuery.capture());
        for(QueryWrapper query:new QueryWrapper[]{attemptsQuery.getValue(),resultsQuery.getValue()}) {
            assertTrue(query.getSqlSegment().contains("user_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(2L));
            assertFalse(query.getParamNameValuePairs().containsValue(1L));
        }
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"}) void adminSubmissionListSupportsFiltersAndOmitsCode() throws Exception {
        Submission listed = new Submission(); listed.id=42L; listed.userId=2L; listed.code="secret source"; listed.result="AC";
        when(submissions.selectList(any())).thenReturn(java.util.Collections.singletonList(listed));
        mvc.perform(get("/api/submissions/admin").param("userId", "2").param("examId", "5")
                        .param("questionId", "7").param("result", "AC").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].code").doesNotExist());
        ArgumentCaptor<QueryWrapper> captured = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(submissions).selectList(captured.capture());
        QueryWrapper query = captured.getValue();
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getSqlSegment().contains("exam_id"));
        assertTrue(query.getSqlSegment().contains("question_id"));
        assertTrue(query.getSqlSegment().contains("result"));
        assertFalse(query.getSqlSelect().contains("code"));
        assertTrue(query.getSqlSegment().contains("LIMIT 200"));
    }

    @Test void studentCannotUseAdminSubmissionList() throws Exception {
        mvc.perform(get("/api/submissions/admin").header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        verifyNoInteractions(submissions);
    }

    @Test void invalidAdminSubmissionResultFilterIsRejected() throws Exception {
        mvc.perform(get("/api/submissions/admin").param("result", "UNKNOWN").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(submissions);
    }

    @Test void submissionDetailSeparatesOwnerAndAdminPermissions() throws Exception {
        Submission row = new Submission(); row.id=42L; row.userId=3L; row.code="complete source";
        when(submissions.selectById(42L)).thenReturn(row);
        mvc.perform(get("/api/submissions/42").header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/submissions/42").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("complete source"));
    }

    @Test void studentCanReadAndStartOrFinishExam() throws Exception {
        Exam exam = new Exam(); exam.id = 1L; exam.status = "PUBLISHED";
        exam.startTime=java.time.LocalDateTime.now().minusMinutes(1); exam.endTime=java.time.LocalDateTime.now().plusMinutes(10);
        when(exams.lockById(1L)).thenReturn(exam);
        ExamQuestion link=new ExamQuestion(); link.examId=1L; link.questionId=7L;
        when(links.selectList(any())).thenReturn(java.util.Collections.singletonList(link));
        when(questions.selectById(7L)).thenReturn(new Question());
        mvc.perform(get("/api/questions").header("Authorization", studentToken)).andExpect(status().isOk());
        mvc.perform(get("/api/exams").header("Authorization", studentToken)).andExpect(status().isOk());
        mvc.perform(post("/api/exams/1/start").header("Authorization", studentToken)).andExpect(status().isOk());
        ExamAttempt attempt=new ExamAttempt(); attempt.examId=1L; attempt.userId=2L; attempt.status="IN_PROGRESS"; attempt.deadline=exam.endTime;
        when(attempts.selectOne(any())).thenReturn(attempt);
        mvc.perform(post("/api/exams/1/submit").header("Authorization", studentToken)).andExpect(status().isOk());
    }

    @Test void publicLoginAndRegistrationStillWork() throws Exception {
        when(users.selectOne(any())).thenReturn(student);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"STUDENT\",\"password\":\"demo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        when(users.selectOne(any())).thenReturn(null);
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"new\",\"password\":\"demo\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test void allUserResponsesOmitPasswordAndLegacyLoginMigratesOnlyPassword() throws Exception {
        when(users.selectOne(any())).thenReturn(student);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"STUDENT\",\"password\":\"demo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.password").doesNotExist());
        ArgumentCaptor<User> update = ArgumentCaptor.forClass(User.class);
        verify(users).updateById(update.capture());
        assertTrue(new PasswordService().matches("demo", update.getValue().password));
        assertEquals(2L, update.getValue().id);
        assertNull(update.getValue().role); assertNull(update.getValue().status); assertNull(update.getValue().createTime);
        mvc.perform(get("/api/auth/current").header("Authorization", studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        when(users.selectOne(any())).thenReturn(null);
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"new\",\"password\":\"demo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        ArgumentCaptor<User> insert = ArgumentCaptor.forClass(User.class);
        verify(users).insert(insert.capture());
        assertTrue(new PasswordService().isEncoded(insert.getValue().password));
        assertTrue(new PasswordService().matches("demo", insert.getValue().password));
    }

    @Test void encodedLoginWorksAndWrongPasswordDoesNotUpdateDatabase() throws Exception {
        student.password = new PasswordService().encode("demo");
        when(users.selectOne(any())).thenReturn(student);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"STUDENT\",\"password\":\"demo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"STUDENT\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").isNotEmpty());
        verify(users, never()).updateById(any(User.class));
    }

    @ParameterizedTest @ValueSource(strings = {"", "   "})
    void emptyPasswordsAreRejected(String password) throws Exception {
        String body = new ObjectMapper().writeValueAsString(java.util.Collections.singletonMap("password", password));
        body = body.substring(0, body.length() - 1) + ",\"username\":\"new\"}";
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(users);
    }

    @Test void studentCannotReadAnswersButAdminCanAndEntityIsNotMutated() throws Exception {
        Question question = new Question(); question.id = 7L; question.standardAnswer = "secret";
        question.sampleInput = "2 3"; question.sampleOutput = "5";
        when(questions.selectById(7L)).thenReturn(question);
        when(questions.selectList(any())).thenReturn(java.util.Collections.singletonList(question));
        mvc.perform(get("/api/questions").header("Authorization", studentToken).header("X-User-Id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].standardAnswer").doesNotExist())
                .andExpect(jsonPath("$[0].sampleInput").value("2 3"))
                .andExpect(jsonPath("$[0].sampleOutput").value("5"));
        mvc.perform(get("/api/questions/7").header("Authorization", studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.standardAnswer").doesNotExist())
                .andExpect(jsonPath("$.sampleInput").value("2 3"))
                .andExpect(jsonPath("$.sampleOutput").value("5"));
        mvc.perform(get("/api/questions/7").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.standardAnswer").value("secret"));
        assertEquals("secret", question.standardAnswer);
    }

    @Test void questionSamplesCanBeSavedAndReadWithoutExposingAnswer() throws Exception {
        when(questions.insert(any(Question.class))).thenAnswer(call -> {
            Question saved=call.getArgument(0); saved.id=8L; return 1;
        });
        mvc.perform(post("/api/questions").header("Authorization", adminToken).contentType("application/json")
                .content("{\"title\":\"sum\",\"category\":\"math\",\"difficulty\":\"简单\",\"description\":\"Add two numbers\",\"sampleInput\":\"2 3\",\"sampleOutput\":\"5\",\"standardAnswer\":\"a+b\",\"score\":100,\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sampleInput").value("2 3"))
                .andExpect(jsonPath("$.sampleOutput").value("5"));
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"questionId\":\"oops\"}", "{\"questionId\":1.5}", "{\"questionId\":0}", "{\"questionId\":-1}", "{\"questionId\":999999999999999999999}"})
    void invalidQuestionIdentifiersAreBadRequests(String body) throws Exception {
        mvc.perform(post("/api/submissions/run").header("Authorization", studentToken)
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(questions, submissions);
    }

    @Test void invalidCodeAndExamIdentifierAreRejectedWithoutSaving() throws Exception {
        Question question = new Question(); question.id = 7L; question.standardAnswer = "answer";
        when(questions.lockById(7L)).thenReturn(question);
        for (String body : new String[]{"{\"questionId\":7,\"code\":123}", "{\"questionId\":7,\"examId\":\"wrong\"}",
                "{\"questionId\":7,\"code\":\"" + new String(new char[60001]).replace('\0', 'a') + "\"}"}) {
            mvc.perform(post("/api/submissions/submit").header("Authorization", studentToken)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(submissions);
    }

    @ParameterizedTest @ValueSource(strings = {"", "answer", "wrong"})
    void simulatedJudgingIsExplicitAndSubmissionHasIdentifier(String code) throws Exception {
        Question question = new Question(); question.id = 7L; question.standardAnswer = "answer";
        when(questions.lockById(7L)).thenReturn(question);
        when(submissions.insert(any(Submission.class))).thenAnswer(invocation -> {
            ((Submission) invocation.getArgument(0)).id = 42L; return 1;
        });
        String body = "{\"questionId\":7,\"code\":\"" + code + "\"}";
        String verdict = code.isEmpty() ? "RE" : "answer".equals(code) ? "AC" : "WA";
        mvc.perform(post("/api/submissions/run").header("Authorization", studentToken).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(verdict))
                .andExpect(jsonPath("$.judgeMode").value("SIMULATED")).andExpect(jsonPath("$.submissionId").doesNotExist());
        verify(submissions, never()).insert(any(Submission.class));
        mvc.perform(post("/api/submissions/submit").header("Authorization", studentToken).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(verdict)).andExpect(jsonPath("$.submissionId").value(42));
    }

    @Test void malformedJsonAndDuplicateRegistrationHaveClearErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        when(users.selectOne(any())).thenReturn(student);
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"existing\",\"password\":\"demo\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test void logoutRevokesToken() throws Exception {
        mvc.perform(get("/api/auth/current").header("Authorization", studentToken)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", studentToken)).andExpect(status().isOk());
        mvc.perform(get("/api/questions").header("Authorization", studentToken)).andExpect(status().isUnauthorized());
    }

    @Test void accountStatusAndRoleAreReadFromDatabase() throws Exception {
        student.status = "DISABLED";
        mvc.perform(get("/api/questions").header("Authorization", studentToken)).andExpect(status().isUnauthorized());
        assertThrows(ResponseStatusException.class, () -> sessions.create(student));
        student.status = "ACTIVE"; student.role = "ADMIN";
        mvc.perform(get("/api/results/1").header("Authorization", studentToken)).andExpect(status().isOk());
        student.role = "UNKNOWN";
        mvc.perform(get("/api/questions").header("Authorization", studentToken)).andExpect(status().isForbidden());
    }

    @Test void corsPreflightAndAuthenticationErrorsAreReadable() throws Exception {
        mvc.perform(options("/api/questions").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(get("/api/questions").header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
