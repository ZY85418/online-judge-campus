CREATE DATABASE IF NOT EXISTS campus_oj DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_oj;

CREATE TABLE IF NOT EXISTS oj_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(50) NOT NULL UNIQUE, password VARCHAR(100) NOT NULL,
  real_name VARCHAR(50), role VARCHAR(20) NOT NULL DEFAULT 'STUDENT', status VARCHAR(20) DEFAULT 'ACTIVE', create_time DATETIME
);
CREATE TABLE IF NOT EXISTS question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(200) NOT NULL, description TEXT, sample_input TEXT, sample_output TEXT, category VARCHAR(50), difficulty VARCHAR(20),
  standard_answer TEXT, score INT DEFAULT 100, status VARCHAR(20) DEFAULT 'PUBLISHED', create_time DATETIME
);
CREATE TABLE IF NOT EXISTS exam (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(200) NOT NULL, start_time DATETIME, end_time DATETIME,
  duration INT, status VARCHAR(20), question_ids VARCHAR(500), create_time DATETIME
);
CREATE TABLE IF NOT EXISTS submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, exam_id BIGINT, question_id BIGINT, code TEXT,
  language VARCHAR(20), result VARCHAR(10), score INT, judge_time BIGINT, submit_time DATETIME(6)
);

CREATE TABLE IF NOT EXISTS exam_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, exam_id BIGINT NOT NULL, question_id BIGINT NOT NULL, sort_order INT NOT NULL,
  UNIQUE KEY uq_exam_question (exam_id, question_id), KEY idx_exam_question_question (question_id),
  CONSTRAINT fk_exam_question_exam FOREIGN KEY (exam_id) REFERENCES exam(id),
  CONSTRAINT fk_exam_question_question FOREIGN KEY (question_id) REFERENCES question(id)
);
CREATE TABLE IF NOT EXISTS exam_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, exam_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  start_time DATETIME(6) NOT NULL, deadline DATETIME(6) NOT NULL, status VARCHAR(20) NOT NULL, submit_time DATETIME(6),
  UNIQUE KEY uq_exam_attempt (exam_id, user_id), KEY idx_attempt_expiry (status, deadline),
  CONSTRAINT fk_attempt_exam FOREIGN KEY (exam_id) REFERENCES exam(id),
  CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES oj_user(id)
);
CREATE TABLE IF NOT EXISTS exam_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, exam_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  total_score INT NOT NULL, submit_time DATETIME(6) NOT NULL,
  UNIQUE KEY uq_exam_result (exam_id, user_id),
  CONSTRAINT fk_result_attempt FOREIGN KEY (exam_id, user_id) REFERENCES exam_attempt(exam_id, user_id)
);
