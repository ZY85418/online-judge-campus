# Campus Judge

校园在线编程考试系统，定位为毕业设计项目。系统以轻量、可运行、便于演示、测试和复现部署为目标，提供学生做题、模拟判题、题库管理、考试管理和提交记录功能。

## 技术组成

- 前端：Vue 3 + Vite
- 后端：Java 8 + Spring Boot 2.7 + MyBatis-Plus
- 数据库：MySQL 8
- 判题：本地模拟逻辑，不执行用户提交的代码
- 前端环境：Node.js 20.19+（20 系列）或 22.12+；建议使用 Node.js 24

## 快速启动

1. 创建 MySQL 数据库并执行 [database/schema.sql](database/schema.sql)。
   升级已有项目时，先备份并在数据库副本验证。旧版考试库升级时，先执行 `database/schema.sql` 补齐考试相关表，再执行 `database/upgrade-exams.sql`；若旧版 `question` 表尚无 `sample_input`、`sample_output` 字段，再执行 `database/upgrade-question-samples.sql`。样例升级脚本只适用于缺少这两个字段的旧表，不可重复执行。升级脚本会调整提交时间精度并将无有效选题关联的旧考试转为草稿；不会删除已有提交记录。不要把脚本审阅视为已验证升级成功。
2. 在 `backend/src/main/resources/application.yml` 配置数据库，默认连接 `localhost:3306/campus_oj`，账号/密码为 `root/123456`。也可通过 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 环境变量覆盖。
3. 启动后端：在 `backend` 目录执行 `mvn spring-boot:run`。
4. 启动前端：在 `frontend` 目录执行 `npm.cmd ci`，再执行 `npm.cmd run dev`。
5. 浏览器打开前端显示的地址，通常为 `http://localhost:5173`。

### Windows 一键启动和关闭

安装好 Java、Maven、Node.js，并在 `frontend` 目录运行过一次 `npm.cmd ci` 后，可在项目根目录双击：

- `start-dev.bat`：启动 Spring Boot 后端和 Vite 前端开发服务。
- `stop-dev.bat`：停止由启动脚本启动的前后端服务。

脚本不会启动或关闭 MySQL，请先确保 MySQL 正在运行。启动日志保存在项目根目录的 `.run` 文件夹；该文件夹已加入 Git 忽略。默认使用后端 `8080`、前端 `5173` 端口；若端口已被占用，脚本会提示并停止启动，不会结束占用端口的其他程序。

启用演示数据时的本地初始账号：学生 `student / student123`；管理员 `admin / admin123`。这些固定账号只用于本地课程演示，不得在公开部署中沿用；GitHub 上的示例凭据不等于安全的生产凭据。

首次启动且启用演示数据时，系统会逐条补齐八道 Java 练习题：四道简单题覆盖整数输入输出、奇偶条件、循环累加和字符串统计；四道中等题为有序数组二分查找、区间和查询、括号匹配和最大连续子数组和。每题提供输入/输出格式及样例；若同标题和分类的题目已存在，初始化会保留现有内容。设置 `SEED_DEMO=false` 时不创建演示账号或练习题。

## 登录与权限

- 登录和注册公开，其余业务接口需要 `Authorization: Bearer <token>`。
- 登录会话有效期为 8 小时，退出立即失效；后端重启后重新登录。
- 管理员可管理题目和考试、查询全考试成绩并查看单场考试学情统计；学生可浏览、提交和查询个人记录。
- 管理员可在提交记录页按学生编号、考试编号、题目编号或判题结果筛选最近200条提交摘要，并查看单条完整代码；学生仍只能查看本人提交。
- 管理员可为题目填写可选样例输入和输出，学生题面可查看；样例只用于说明，不参与模拟判题。
- 个人信息页展示账号资料并允许修改姓名；管理员可按关键词、编号和状态分页查询学生，查看其考试答卷摘要和最近提交摘要。
- 服务端从 Token 确定用户，忽略客户端伪造的 `X-User-Id`。
- 用户响应不包含密码，学生题目响应不包含标准答案；管理员仍可读取标准答案。
- 新账号使用 BCrypt 保存密码；已有明文账号首次成功登录后自动转换，原密码保持可用。未登录的旧账号仍保留原存储，备份数据库后应逐步完成迁移。
- 草稿保存在当前浏览器，按用户和题目隔离；退出清空当前编辑状态，但保留个人草稿。清除浏览器数据会删除草稿。
- 练习与不同考试的草稿分别保存。题目草稿及关闭状态不向学生展示，不能直接提交。
- 部署到外部环境时设置 `SEED_DEMO=false` 关闭默认账号初始化，并更换已有演示账号密码；第一版接口仅提供姓名修改，没有自助修改密码。

## 构建与部署

在 `frontend` 执行 `npm.cmd run build`，静态文件输出到 `dist/`。执行 `npm.cmd run preview` 可本地验证构建结果，预览服务不用于生产部署。

前端默认请求同源 `/api`。开发和预览服务会代理至 `http://localhost:8080`；后端端口不同时通过 `VITE_DEV_PROXY_TARGET` 指定目标，可参考 `frontend/.env.example`。生产部署将 `dist/` 放入 Web 服务器，并将 `/api/` 反向代理到后端。例如 Nginx：

```nginx
location / {
    root /path/to/frontend/dist;
    try_files $uri $uri/ /index.html;
}
location /api/ {
    proxy_pass http://127.0.0.1:8080;
}
```

需要跨域部署时，在构建前设置 `VITE_API_BASE_URL` 为完整 API 地址，在后端通过 `CORS_ALLOWED_ORIGINS` 设置允许的前端来源，多个来源用逗号分隔。浏览器接口地址不能使用服务器自身的 `localhost`；前端环境变量是公开配置，不能存放秘密。

后端在 `backend` 执行 `mvn package`，随后用 `java -jar target/online-judge-1.0.0.jar` 启动，环境变量沿用快速启动部分的约定。

## 测试

- 后端：在 `backend` 执行 `mvn test`，覆盖身份权限、敏感字段、密码转换、参数错误、模拟判题，以及 H2 测试数据库中的考试流程、关联保护、计分、并发交卷和失败回滚。H2 测试不连接现有 MySQL。
- 前端：在 `frontend` 执行 `npm.cmd test`，验证草稿隔离与恢复、认证状态、时间解析与校验、考试入口状态和确认流程；执行 `npm.cmd run build` 验证生产构建。

## 模拟判题规则

- 空代码：`RE`，得分 0。
- 代码含有题目配置的标准答案关键片段：`AC`，得满分。
- 其他代码：`WA`，得分 0。

这项规则避免了真实代码执行带来的安全与部署复杂度，适合作为第一版的课堂演示实现。
判题提示明确标记模拟模式。`judgeTime` 是字符串匹配逻辑耗时，不是程序运行耗时；注释中包含关键片段也可能通过。提交响应返回 `submissionId`，运行不保存记录。

## 考试与成绩规则

- 管理员创建草稿考试、选择已发布题目、编辑并发布，或直接创建发布考试。草稿可编辑；已结束且没有答卷或提交记录的已发布考试可修订；开放中的考试或已有答卷/提交记录的考试不能编辑。题目被考试或提交引用后不能修改删除，需新建版本。
- 管理员分别设置考试开放开始时间、开放结束时间和个人答题时长；三项数据独立保存。学生只能在开放窗口内开始考试，个人截止时间取“个人实际开始时间 + 答题时长”与“考试开放结束时间”两者中较早值。
- 学生进入前会看到确认提示。确认后立即开始个人倒计时；取消确认不会创建答卷或启动计时。考试按一次连续答题设计，不提供暂停或常规的“继续答题”模式；刷新、断网或重新登录后的恢复仅恢复同一答卷和原截止时间。
- 学生考试列表逐场显示本人答卷状态；已完成、超时或关闭收卷后显示成绩并可进入逐题详情；历史已关闭考试仍可查看个人状态和成绩。
- 每题取该学生本次考试有效时间内的最高分，未答题0分；重复提交不重复加分。提前交卷、超时和管理员关闭均生成唯一最终成绩。
- 截止时仅统计已保存的提交，未提交的浏览器草稿不参与计分。后端每10秒扫描过期答卷；重启后扫描恢复，期间任何超时提交都会被拒绝。
- 已交卷成绩冻结，个人成绩和错题仅本人可查看；管理员可按考试/学生查询并查看平均分与交卷人次。
- 所有考试时间按北京时间填写。前端倒计时基于后端返回时间，后端时间为最终依据。

## 演示流程

1. 管理员登录，在题库管理新增题目；没有引用时可以编辑、删除。学生题库与管理列表支持按标题/描述搜索、组合筛选并每页50题。考试进行中的答卷显示“答题进行中”，不提供常规继续答题入口；刷新或重新登录仍会恢复原答卷。
2. 在考试管理分别设置开放开始时间、开放结束时间、个人答题时长和题目，保存草稿后编辑发布，或直接发布。
3. 学生登录（可从登录页注册），查看考试开放状态；考试开放后点击进入并确认，作答并提交。查看提交编号、记录和保存代码。
4. 提前交卷，或等待截止；在成绩与错题查看总分和题目最高分。
5. 管理员按学生编号或考试查询成绩、查看记录；关闭考试会收取进行中的答卷。
6. 在个人信息修改本人姓名。手机端个人信息与退出入口保持可见。

页面交互约定见 [docs/interaction-spec.md](docs/interaction-spec.md)，功能及体验验收清单见 [docs/acceptance.md](docs/acceptance.md)；接口见 [docs/api.md](docs/api.md)，课程实践材料模板见 [docs/practice-records.md](docs/practice-records.md)。交互文档明确区分当前实现与待迭代目标。
历史浏览器验证记录见 [docs/verification-2026-10-07.md](docs/verification-2026-10-07.md)；最终验收检查及尚未覆盖事项见 [docs/final-acceptance-review-2026-10-13.md](docs/final-acceptance-review-2026-10-13.md)。

## 接口概览

| 模块 | 主要接口 |
| --- | --- |
| 认证 | `POST /api/auth/login`、`POST /api/auth/register`、`GET /api/auth/current`、`PUT /api/auth/profile` |
| 学生管理 | `GET /api/users/students`、`GET /api/users/students/{id}`（管理员只读查询） |
| 题库 | `GET/POST /api/questions`、`GET/PUT/DELETE /api/questions/{id}` |
| 考试 | `GET/POST /api/exams`、`GET /api/exams/my-status`（本人逐场答卷状态和成绩）、`PUT /api/exams/{id}`、`POST /api/exams/{id}/start`、`GET /api/exams/{id}/session`、`POST /api/exams/{id}/submit`、`POST /api/exams/{id}/close` |
| 判题与提交记录 | `POST /api/submissions/run`、`POST /api/submissions/submit`、`GET /api/submissions/my`、`GET /api/submissions/admin`（管理员筛选摘要）、`GET /api/submissions/{id}`（有权查看完整记录） |
| 成绩与学情统计 | `GET /api/results/my`、`GET /api/results`、`GET /api/results/{examId}`、`GET /api/results/{examId}/detail`、`GET /api/results/analytics?examId=...`（管理员） |

## 项目结构

```
backend/     Spring Boot REST API，Controller / Service / Mapper / Entity 分层
frontend/    Vue 单页应用
database/    MySQL 建表脚本
```

远程仓库地址：`https://github.com/ZY85418/online-judge-campus.git`。
