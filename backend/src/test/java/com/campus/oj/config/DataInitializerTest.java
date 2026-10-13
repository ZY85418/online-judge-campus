package com.campus.oj.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.oj.entity.Question;
import com.campus.oj.entity.User;
import com.campus.oj.repository.QuestionRepository;
import com.campus.oj.repository.UserRepository;
import com.campus.oj.service.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {
    @Test void seedsUsefulQuestionsIdempotentlyWithoutChangingExistingRows() throws Exception {
        UserRepository users = mock(UserRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        List<Question> stored = new ArrayList<>();
        Question existing = new Question();
        existing.title = "两数之和"; existing.category = "基础算法"; existing.description = "人工维护的题面";
        stored.add(existing);
        when(users.selectOne(any(QueryWrapper.class))).thenReturn(new User());
        when(questions.selectCount(any(QueryWrapper.class))).thenAnswer(invocation -> {
            QueryWrapper<Question> query=invocation.getArgument(0);
            query.getSqlSegment();
            return stored.stream().anyMatch(row -> query.getParamNameValuePairs().containsValue(row.title)
                    && query.getParamNameValuePairs().containsValue(row.category)) ? 1L : 0L;
        });
        doAnswer(invocation -> { stored.add(invocation.getArgument(0)); return 1; })
                .when(questions).insert(any(Question.class));

        CommandLineRunner initializer = new DataInitializer().initialData(users, questions, passwords);
        initializer.run();
        initializer.run();

        assertEquals(8, stored.size());
        assertEquals("人工维护的题面", existing.description);
        assertNull(existing.sampleInput);
        assertTrue(stored.stream().filter(question -> question != existing).allMatch(question -> question.status.equals("PUBLISHED")
                && question.description.contains("输入格式") && question.description.contains("输出格式")
                && question.sampleInput != null && question.sampleOutput != null));
        assertEquals("人工维护的题面", stored.get(0).description);
        assertQuestion(stored, "奇偶判断", "-7", "ODD");
        assertQuestion(stored, "连续整数求和", "5", "15");
        assertQuestion(stored, "统计元音字母", "Campus", "2");
        assertQuestion(stored, "有序数组二分查找", "6 7\n1 3 5 7 9 11", "3");
        assertQuestion(stored, "区间和查询", "5 3\n2 -1 3 5 4\n1 3\n2 5\n4 4", "4\n11\n5");
        assertQuestion(stored, "括号匹配", "{[()()]}", "YES");
        assertQuestion(stored, "最大连续子数组和", "9\n-2 1 -3 4 -1 2 1 -5 4", "6");
        assertEquals(4,stored.stream().filter(row -> "中等".equals(row.difficulty)).count());
        assertTrue(stored.stream().filter(row -> "中等".equals(row.difficulty)).allMatch(row -> row.score==100
                && row.standardAnswer!=null && !row.standardAnswer.trim().isEmpty()));
        verify(questions, times(7)).insert(any(Question.class));
    }

    private void assertQuestion(List<Question> questions, String title, String input, String output) {
        Question question = questions.stream().filter(row -> title.equals(row.title)).findFirst().orElseThrow(AssertionError::new);
        assertEquals(input, question.sampleInput);
        assertEquals(output, question.sampleOutput);
    }
}
