import test from 'node:test'
import assert from 'node:assert/strict'
import { confirmExamEntry, examFormFromRecord, examPayload, formatLocalDateTime, getExamAvailability, parseLocalDateTime, validateExamTime } from '../src/exam-time.js'
test('rejects empty, invalid and reversed exam times', () => {
  assert.match(validateExamTime({ startTime: '', endTime: '', duration: 90 }), /必须填写有效/)
  assert.match(validateExamTime({ startTime: '2026-02-30T10:00', endTime: '2026-03-01T10:00', duration: 90 }), /必须填写有效/)
  assert.match(validateExamTime({ startTime: '2026-10-08T11:00', endTime: '2026-10-08T10:00', duration: 90 }), /晚于开始/)
  assert.match(validateExamTime({ startTime: '2026-10-08T10:00', endTime: '2026-10-08T11:00', duration: 0 }), /1至1440/)
})
test('parses and formats datetime-local values exactly', () => {
  const date = parseLocalDateTime('2026-10-08T09:05')
  assert.equal(formatLocalDateTime(date), '2026-10-08T09:05:00')
  assert.equal(parseLocalDateTime('2026-10-08T09:05:61'), null)
})
test('keeps opening times independent in create payload and draft edit echo', () => {
  const saved = { name:'独立时间考试', startTime:'2026-10-08T09:00:00', endTime:'2026-10-08T16:00:00', duration:90, status:'DRAFT', questionIds:'4,8' }
  const form = examFormFromRecord(saved)
  assert.equal(form.startTime, saved.startTime)
  assert.equal(form.endTime, saved.endTime)
  assert.equal(form.duration, saved.duration)
  form.startTime = '2026-10-08T10:00:00'
  form.duration = 120
  const payload = examPayload(form)
  assert.equal(payload.startTime, form.startTime)
  assert.equal(payload.endTime, saved.endTime)
  assert.equal(payload.duration, form.duration)
  assert.equal(payload.questionIds, '4,8')
})
test('shows upcoming, open and ended states using Beijing local exam times', () => {
  const exam = { status:'PUBLISHED', startTime:'2026-10-08T09:00:00', endTime:'2026-10-08T16:00:00' }
  assert.equal(getExamAvailability(exam, Date.parse('2026-10-08T08:59:59+08:00')), 'upcoming')
  assert.equal(getExamAvailability(exam, Date.parse('2026-10-08T09:00:00+08:00')), 'open')
  assert.equal(getExamAvailability(exam, Date.parse('2026-10-08T15:59:59+08:00')), 'open')
  assert.equal(getExamAvailability(exam, Date.parse('2026-10-08T16:00:00+08:00')), 'ended')
  assert.equal(getExamAvailability({ ...exam, status:'DRAFT' }, Date.parse('2026-10-08T10:00:00+08:00')), 'unavailable')
  assert.equal(getExamAvailability({ ...exam, endTime:'bad' }, Date.parse('2026-10-08T10:00:00+08:00')), 'unavailable')
})
test('cancelled entry confirmation does not call the start request; confirmation calls it once', async () => {
  let requests = 0
  let prompt = ''
  assert.equal(confirmExamEntry(message => { prompt = message; return false }, () => { requests++ }), null)
  assert.equal(requests, 0)
  assert.match(prompt, /立即开始个人倒计时/)
  assert.match(prompt, /不会暂停计时/)
  assert.match(prompt, /重新进入可恢复/)
  assert.match(prompt, /不能取消或重置答卷/)
  const expected = { data:{ attempt:{ id:1 } } }
  const response = await confirmExamEntry(() => true, async () => { requests++; return expected })
  assert.equal(requests, 1)
  assert.equal(response, expected)
})
