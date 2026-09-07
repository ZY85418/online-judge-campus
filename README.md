# Campus Judge

校园在线编程考试系统，面向课程实训和毕业实习演示。系统以轻量、可运行、便于答辩为目标，提供学生做题、模拟判题、题库管理、考试管理和提交记录功能。

## 技术组成

- 前端：Vue 3 + Vite
- 后端：Java 8 + Spring Boot 2.7 + Spring Data JPA
- 数据库：MySQL 8
- 判题：本地模拟逻辑，不执行用户提交的代码

## 快速启动

1. 创建 MySQL 数据库。可执行 [database/schema.sql](database/schema.sql)，或让应用首次启动时自动建表。
2. 在 `backend/src/main/resources/application.yml` 配置数据库，默认连接 `localhost:3306/campus_oj`，账号/密码为 `root/root`。也可通过 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 环境变量覆盖。
3. 启动后端：在 `backend` 目录执行 `mvn spring-boot:run`。
4. 启动前端：在 `frontend` 目录执行 `npm.cmd install`，再执行 `npm.cmd run dev`。
5. 浏览器打开前端显示的地址，通常为 `http://localhost:5173`。

初始账号：学生 `student / student123`；管理员 `admin / admin123`。

## 模拟判题规则

- 空代码：`RE`，得分 0。
- 代码含有题目配置的标准答案关键片段：`AC`，得满分。
- 其他代码：`WA`，得分 0。

这项规则避免了真实代码执行带来的安全与部署复杂度，适合作为第一版的课堂演示实现。

## 接口概览

| 模块 | 主要接口 |
| --- | --- |
| 认证 | `POST /api/auth/login`、`POST /api/auth/register`、`GET /api/auth/current` |
| 题库 | `GET/POST /api/questions`、`GET/PUT/DELETE /api/questions/{id}` |
| 考试 | `GET/POST /api/exams`、`PUT /api/exams/{id}`、`POST /api/exams/{id}/start` |
| 判题 | `POST /api/submissions/run`、`POST /api/submissions/submit`、`GET /api/submissions/my` |
| 成绩 | `GET /api/results/my`、`GET /api/results/{examId}` |

## 项目结构

```
backend/     Spring Boot REST API，Controller / Service / Repository / Entity 分层
frontend/    Vue 单页应用
database/    MySQL 建表脚本
```

远程地址已配置为 `https://github.com/ZY85418/online-judge-campus.git`。当前环境无法连接 GitHub，因此该实现从设计文档重新搭建，后续网络可用时可获取远程分支并进行差异合并。
