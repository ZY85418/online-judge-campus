const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/
export function parseLocalDateTime(value) {
  if (typeof value !== 'string') return null
  const match = value.match(LOCAL_DATE_TIME)
  if (!match) return null
  const [, year, month, day, hour, minute, second = '00'] = match
  const date = new Date(Date.UTC(+year, +month - 1, +day, +hour, +minute, +second))
  if (date.getUTCFullYear() !== +year || date.getUTCMonth() !== +month - 1 || date.getUTCDate() !== +day || date.getUTCHours() !== +hour || date.getUTCMinutes() !== +minute || date.getUTCSeconds() !== +second) return null
  return date
}
export function formatLocalDateTime(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return ''
  const pad = value => String(value).padStart(2, '0')
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`
}
export const EXAM_ENTRY_CONFIRMATION = '确认进入考试？确认后将立即开始个人倒计时。关闭页面或断开网络不会暂停计时，重新进入可恢复答卷；开始后不能取消或重置答卷。'
export function confirmExamEntry(confirmFn, startRequest) {
  if (typeof confirmFn !== 'function' || !confirmFn(EXAM_ENTRY_CONFIRMATION)) return null
  return startRequest()
}
export function examFormFromRecord(exam) {
  return { ...exam, questionIds: exam.questionIds ? exam.questionIds.split(',').map(Number) : [] }
}
export function examPayload(form) {
  return { ...form, questionIds: form.questionIds.join(',') }
}
export function getExamAvailability(exam, now = Date.now()) {
  if (!exam || exam.status !== 'PUBLISHED') return 'unavailable'
  const start = parseLocalDateTime(exam.startTime)?.getTime() - 8 * 60 * 60 * 1000
  const end = parseLocalDateTime(exam.endTime)?.getTime() - 8 * 60 * 60 * 1000
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return 'unavailable'
  if (now < start) return 'upcoming'
  if (now >= end) return 'ended'
  return 'open'
}
export function validateExamTime({ startTime, endTime, duration }) {
  const start = parseLocalDateTime(startTime), end = parseLocalDateTime(endTime)
  if (!start || !end) return '开始时间和结束时间必须填写有效的日期时间。'
  if (!Number.isInteger(duration) || duration < 1 || duration > 1440) return '考试时长必须是1至1440分钟的整数。'
  if (end <= start) return '结束时间必须晚于开始时间。'
  return ''
}
