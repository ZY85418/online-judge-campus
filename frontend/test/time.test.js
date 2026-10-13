import test from 'node:test'
import assert from 'node:assert/strict'
import { remainingSeconds, serverTimestamp } from '../src/time.js'
test('exam countdown follows server elapsed time rather than local wall clock',()=>{
  const received=1000000
  assert.equal(remainingSeconds('2026-10-07T10:01:00','2026-10-07T10:00:00',received,received+10000),50)
  assert.equal(remainingSeconds('2026-10-07T10:01:00','2026-10-07T10:00:00',received,received+70000),0)
  assert.equal(serverTimestamp('2026-10-07T10:00:00'),Date.parse('2026-10-07T02:00:00Z'))
})
