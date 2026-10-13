import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const handler = source.slice(source.indexOf('async function showSubmissions()'), source.indexOf('async function refreshPage()'))

test('older admin submission filter response cannot replace the latest query', async () => {
  const requests = []
  const state = {
    page: { value: '' }, isAdmin: { value: true },
    submissionFilters: { value: { userId: '', examId: '', questionId: '', result: '' } },
    submissions: { value: [] }, notice: { value: '' },
    token: { value: 'token-a' }, user: { value: { id: 1 } }, sessionVersion: 0, dataRequestVersion: 0,
    requestContext() { return { token: state.token.value, userId: state.user.value.id, version: state.sessionVersion } },
    isCurrentContext(context) { return context.token === state.token.value && context.userId === state.user.value?.id && context.version === state.sessionVersion },
    errorMessage: () => 'error',
    api: { get: (url, options) => new Promise(resolve => requests.push({ resolve, params: options.params })) }
  }
  const context = vm.createContext(state)
  vm.runInContext(handler, context)

  const first = context.showSubmissions()
  state.submissionFilters.value.userId = '2'
  const second = context.showSubmissions()
  requests[1].resolve({ data: [{ id: 'latest' }] })
  await second
  requests[0].resolve({ data: [{ id: 'stale' }] })
  await first

  assert.equal(state.submissions.value[0].id, 'latest')
})
