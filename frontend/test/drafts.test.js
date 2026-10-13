import test from 'node:test'
import assert from 'node:assert/strict'
import { createDraftStore, initialCode } from '../src/drafts.js'

test('drafts persist across store instances and are isolated by user and question', () => {
  const data = new Map()
  const storage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) }
  const drafts = createDraftStore(storage)
  assert.equal(drafts.save(1, 7, 'user-one-code'), true)
  assert.equal(drafts.save(1, 8, 'other-question'), true)
  assert.equal(drafts.save(2, 7, 'user-two-code'), true)
  const reloaded = createDraftStore(storage)
  assert.equal(reloaded.load(1, 7), 'user-one-code')
  assert.equal(reloaded.load(1, 8), 'other-question')
  assert.equal(reloaded.load(2, 7), 'user-two-code')
  assert.equal(reloaded.load(2, 8), initialCode)
})

test('empty drafts remain empty rather than becoming template code', () => {
  const drafts = createDraftStore({ getItem: () => '', setItem: () => {} })
  assert.equal(drafts.load(1, 7), '')
})

test('storage failure does not interrupt editing and can be reported', () => {
  const drafts = createDraftStore({ getItem: () => { throw new Error('blocked') }, setItem: () => { throw new Error('full') } })
  assert.equal(drafts.load(1, 7), initialCode)
  assert.equal(drafts.save(1, 7, 'code'), false)
})
