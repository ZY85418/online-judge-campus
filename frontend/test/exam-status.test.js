import test from 'node:test'
import assert from 'node:assert/strict'
import { personalExamView } from '../src/exam-status.js'

const exam = { status:'PUBLISHED', startTime:'2026-10-08T09:00:00', endTime:'2026-10-08T16:00:00' }
const during = Date.parse('2026-10-08T10:00:00+08:00')

test('unattempted exams clearly show availability and only open exams can start', () => {
  assert.deepEqual(personalExamView(exam, null, Date.parse('2026-10-08T08:00:00+08:00')).action, 'none')
  assert.match(personalExamView(exam, null, during).label, /开放中.*未参加/)
  assert.equal(personalExamView(exam, null, during).action, 'start')
  assert.match(personalExamView(exam, null, Date.parse('2026-10-08T17:00:00+08:00')).label, /已结束.*未参加/)
  assert.equal(personalExamView({ ...exam, status:'CLOSED' }, null, during).label, '已关闭 · 未参加')
})

test('each personal attempt exposes its state, score and appropriate action', () => {
  assert.equal(personalExamView(exam, {status:'IN_PROGRESS',deadline:'2026-10-08T11:00:00'}, during).action, 'in-progress')
  const completed = personalExamView(exam, {status:'SUBMITTED',totalScore:85}, during)
  assert.equal(completed.label, '已完成'); assert.equal(completed.score, 85); assert.equal(completed.action, 'result')
  assert.equal(personalExamView(exam, {status:'TIMED_OUT',totalScore:40}, during).label, '超时收卷')
  assert.equal(personalExamView(exam, {status:'CLOSED',totalScore:30}, during).action, 'result')
})
