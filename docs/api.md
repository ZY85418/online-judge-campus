# 第一版接口约定

除登录注册外均使用 `Authorization: Bearer <token>`。用户由Token决定，不接受客户端指定个人身份。时间使用北京时间ISO本地字符串。401表示未登录，403表示越权，400表示非法输入，404表示不存在或对学生不可见，409表示状态冲突。

| 方法与路径 | 权限 | 输入与返回 |
| --- | --- | --- |
| POST /api/auth/register | 公开 | username/password/realName；强制STUDENT，响应无密码 |
| POST /api/auth/login | 公开 | username/password；返回token、user |
| GET /api/auth/current | 已登录 | 本人信息，无密码 |
| POST /api/auth/logout | 已登录 | Token立即失效 |
| PUT /api/auth/profile | 已登录 | 仅realName，修改本人 |
| GET /api/users/students | 管理员 | keyword按用户名/姓名模糊搜索；userId、status可选；page默认1、pageSize默认50（最大100）；仅返回STUDENT及安全摘要，密码永不返回 |
| GET /api/users/students/{id} | 管理员 | 返回学生安全资料、答卷摘要和最近20条提交摘要；完整代码继续通过已有提交详情接口读取 |
| GET /api/questions | 已登录 | category/difficulty可选；学生只见PUBLISHED，无答案 |
| GET /api/questions/{id} | 已登录 | 学生仅已发布，管理员可见答案 |
| POST /api/questions | 管理员 | title/category/difficulty/description/sampleInput?/sampleOutput?/standardAnswer/score/status；样例可选，单项最多60000字节 |
| PUT /api/questions/{id} | 管理员 | 同新增；已被考试或提交引用则409 |
| DELETE /api/questions/{id} | 管理员 | 无引用可删除，成功204 |
| GET /api/exams | 已登录 | 学生可见PUBLISHED与CLOSED考试；管理员返回全部考试，考试管理前端按名称/编号、状态筛选并分页展示 |
| GET /api/exams/my-status | 已登录 | 当前用户已有答卷的examId/status/deadline/submitTime/totalScore；身份从Token解析，不接受userId参数 |
| POST /api/exams | 管理员 | name/startTime/endTime/duration/status/questionIds，题号逗号分隔，最多50题 |
| PUT /api/exams/{id} | 管理员 | 草稿可编辑；已结束且没有答卷或提交记录的已发布考试可修订；已有历史记录或仍在开放中的考试不可编辑；完整字段，与新增一致 |
| POST /api/exams/{id}/close | 管理员 | 关闭并收取进行中的答卷 |
| POST /api/exams/{id}/start | 已登录 | 幂等；返回exam/attempt/questions/submissions/serverTime |
| GET /api/exams/{id}/session | 已登录 | 恢复本人已开始的答卷，必要时收卷 |
| POST /api/exams/{id}/submit | 已登录 | 幂等交卷并生成成绩，返回同session |
| POST /api/submissions/run | 已登录 | questionId/code/examId可选；模拟结果，不保存 |
| POST /api/submissions/submit | 已登录 | 同run；保存并返回submissionId；考试需先start |
| GET /api/submissions/my | 已登录 | 本人全部练习与考试提交 |
| GET /api/submissions/admin | 管理员 | userId/examId/questionId/result可选；按时间倒序最多返回200条摘要，不含代码正文 |
| GET /api/submissions/{id} | 本人或管理员 | 已保存代码与判题记录 |
| GET /api/results/my | 已登录 | 本人最终考试成绩列表，各含result/examName/details |
| GET /api/results | 管理员 | userId/examId可选；独立成绩列表，前端统计平均分和交卷人次 |
| GET /api/results/analytics?examId={examId} | 管理员 | examId必填；返回attemptCounts（started/completed/inProgress/submitted/timedOut/closed/completionRate，完成率为百分数）、questionStats（questionId/title/maxScore/eligibleAttemptCount/passCount/notPassedCount/passRate，比例为百分数，分母包括已结束答卷中的未作答者）、studentsToReview（userId/realName/totalScore/notPassedCount/questionIds）。非法参数400，不存在考试404，学生访问403 |
| GET /api/results/{examId} | 管理员 | 该考试成绩列表，userId可选 |
| GET /api/results/{examId}/detail | 本人或管理员 | 本人成绩详情，管理员可指定userId，未交卷404 |

题目状态DRAFT/PUBLISHED/CLOSED。学生题目响应包含可选sampleInput/sampleOutput，但不包含standardAnswer。样例仅供人工阅读，不用于判题。考试保存状态DRAFT/PUBLISHED，通过close进入CLOSED；草稿可编辑，已结束且无历史记录的已发布考试可修订，已有答卷或提交记录的考试不可编辑。个人状态IN_PROGRESS/SUBMITTED/TIMED_OUT/CLOSED。各题details含questionId/title/maxScore/score/result/submissionId，未答结果UNANSWERED。



## 管理员学生答卷标识（2026-10-09）

管理员 `GET /api/results` 和 `GET /api/results/{examId}` 的每条成绩新增 `student: {id, username, realName}` 摘要。不返回密码或完整用户实体；学生成绩接口不附加此管理员摘要。现有 result/examName/details 字段保持兼容，权限和计分规则不变。
