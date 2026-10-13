import { getExamAvailability } from './exam-time.js'

export function personalExamView(exam, personalStatus, now = Date.now()) {
  if (personalStatus) {
    const labels = { IN_PROGRESS: '进行中', SUBMITTED: '已完成', TIMED_OUT: '超时收卷', CLOSED: '已关闭并收卷' }
    const status = personalStatus.status
    return {
      label: labels[status] || '答卷状态未知',
      action: status === 'IN_PROGRESS' ? 'in-progress' : personalStatus.totalScore != null ? 'result' : 'none',
      score: personalStatus.totalScore,
      deadline: personalStatus.deadline
    }
  }
  if (exam?.status === 'CLOSED') return { label: '已关闭 · 未参加', action: 'none', score: null, deadline: null }
  const availability = getExamAvailability(exam, now)
  if (availability === 'upcoming') return { label: '未开放 · 未参加', action: 'none', score: null, deadline: null }
  if (availability === 'open') return { label: '开放中 · 未参加', action: 'start', score: null, deadline: null }
  if (availability === 'ended') return { label: '已结束 · 未参加', action: 'none', score: null, deadline: null }
  return { label: '暂不可参加', action: 'none', score: null, deadline: null }
}
