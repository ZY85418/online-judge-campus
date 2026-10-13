package com.campus.oj.config;

import com.campus.oj.entity.*;
import com.campus.oj.repository.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.campus.oj.service.PasswordService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(name="app.seed-demo",havingValue="true",matchIfMissing=true)
public class DataInitializer {
    @Bean CommandLineRunner initialData(UserRepository users, QuestionRepository questions, PasswordService passwords) { return args -> {
        if (users.selectOne(new QueryWrapper<User>().eq("username", "admin")) == null) { User u=new User(); u.username="admin";u.password=passwords.encode("admin123");u.realName="系统管理员";u.role="ADMIN";users.insert(u); }
        if (users.selectOne(new QueryWrapper<User>().eq("username", "student")) == null) { User u=new User(); u.username="student";u.password=passwords.encode("student123");u.realName="示例学生";users.insert(u); }
        seedQuestion(questions, "两数之和", "基础算法", "简单",
                "读取一行中的两个整数 a 和 b，输出它们的和。整数之间以空格分隔。\n"
                        + "输入格式：一行两个整数 a、b（-10^9 ≤ a,b ≤ 10^9）。\n"
                        + "输出格式：输出一个整数，表示 a+b。",
                "2 3", "5", "System.out.println(a + b)");
        seedQuestion(questions, "奇偶判断", "条件判断", "简单",
                "读取一个整数 n，判断它是奇数还是偶数。\n"
                        + "输入格式：一个整数 n（-10^9 ≤ n ≤ 10^9）。\n"
                        + "输出格式：偶数输出 EVEN，奇数输出 ODD。",
                "-7", "ODD", "n % 2");
        seedQuestion(questions, "连续整数求和", "循环", "简单",
                "读取正整数 n，计算从 1 到 n（包含 n）的整数之和。\n"
                        + "输入格式：一个整数 n（1 ≤ n ≤ 10000）。\n"
                        + "输出格式：输出 1+2+...+n 的结果。",
                "5", "15", "sum += i");
        seedQuestion(questions, "统计元音字母", "字符串", "简单",
                "读取一行仅由英文字母组成的字符串，统计其中元音字母 a、e、i、o、u 的个数，大小写均计入。\n"
                        + "输入格式：一行字符串 s（1 ≤ |s| ≤ 1000）。\n"
                        + "输出格式：输出元音字母总数。",
                "Campus", "2", "aeiou");
        seedQuestion(questions, "有序数组二分查找", "查找", "中等",
                "给定严格递增的整数数组和目标值 x，查找 x 的下标。下标从 0 开始，不存在则输出 -1。要求使用二分查找，时间复杂度 O(log n)。\n"
                        + "输入格式：第一行两个整数 n、x（1 ≤ n ≤ 100000，-10^9 ≤ x ≤ 10^9）；第二行 n 个严格递增的整数，每个数在 [-10^9,10^9] 内。\n"
                        + "输出格式：输出 x 的下标或 -1。",
                "6 7\n1 3 5 7 9 11", "3", "(right - left) / 2");
        seedQuestion(questions, "区间和查询", "前缀和", "中等",
                "给定整数数组和 q 次查询，每次求下标区间 [l,r] 内的元素之和。下标从 1 开始。要求使用前缀和，整体时间复杂度 O(n+q)；使用 long 保存累加结果。\n"
                        + "输入格式：第一行 n、q（1 ≤ n,q ≤ 100000）；第二行 n 个整数（-10^9 ≤ a[i] ≤ 10^9）；接下来 q 行每行两个整数 l、r（1 ≤ l ≤ r ≤ n）。\n"
                        + "输出格式：每行输出一次查询的区间和，共 q 行。",
                "5 3\n2 -1 3 5 4\n1 3\n2 5\n4 4", "4\n11\n5", "prefix[r] - prefix[l - 1]");
        seedQuestion(questions, "括号匹配", "栈", "中等",
                "给定仅包含 ()、[]、{} 的非空字符串，判断括号是否正确匹配：每个右括号必须匹配最近尚未匹配的同类型左括号，最终不能剩余左括号。要求使用栈，时间复杂度 O(n)。\n"
                        + "输入格式：一行括号字符串 s（1 ≤ |s| ≤ 100000），不含空格。\n"
                        + "输出格式：匹配成功输出 YES，否则输出 NO。",
                "{[()()]}", "YES", "stack.pop()");
        seedQuestion(questions, "最大连续子数组和", "动态规划", "中等",
                "给定整数数组，求非空连续子数组的最大元素和。连续子数组至少包含一个元素；全部为负数时不能输出 0。要求时间复杂度 O(n)，使用 long 保存累加结果。\n"
                        + "输入格式：第一行整数 n（1 ≤ n ≤ 100000）；第二行 n 个整数（-10^9 ≤ a[i] ≤ 10^9）。\n"
                        + "输出格式：输出最大连续子数组和。",
                "9\n-2 1 -3 4 -1 2 1 -5 4", "6", "Math.max");
    }; }

    private void seedQuestion(QuestionRepository questions, String title, String category, String difficulty,
                              String description, String sampleInput, String sampleOutput, String standardAnswer) {
        if (questions.selectCount(new QueryWrapper<Question>().eq("title", title).eq("category", category)) > 0) return;
        Question question = new Question();
        question.title = title;
        question.category = category;
        question.difficulty = difficulty;
        question.score = 100;
        question.description = description;
        question.sampleInput = sampleInput;
        question.sampleOutput = sampleOutput;
        question.standardAnswer = standardAnswer;
        question.status = "PUBLISHED";
        questions.insert(question);
    }
}
