export function serverTimestamp(value) { return Date.parse(/[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}+08:00`) }
export function remainingSeconds(deadline, serverTime, receivedAt, now=Date.now()) {
  return Math.max(0, Math.ceil((serverTimestamp(deadline)-serverTimestamp(serverTime)-(now-receivedAt))/1000))
}
