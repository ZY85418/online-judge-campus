-- Additive upgrade: run schema.sql first, then this script. No existing rows are deleted.
USE campus_oj;
ALTER TABLE submission MODIFY submit_time DATETIME(6);
-- Existing legacy exams cannot be trusted without validated associations and time settings.
UPDATE exam SET status = 'DRAFT' WHERE NOT EXISTS (SELECT 1 FROM exam_question WHERE exam_question.exam_id = exam.id);
-- Legacy exam submissions remain historical records and are not converted into final grades.
-- Review/reselect questions in the administrator screen before publishing legacy drafts.
