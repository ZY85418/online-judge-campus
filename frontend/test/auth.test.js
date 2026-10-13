import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

// Execute the actual App.vue handler with controlled API responses.
const source = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const handler = source.slice(source.indexOf('async function login(event)'), source.indexOf('async function logout()'))
function setup(post, registering = true) {
  const state = {
    api: { post }, registering: { value: registering }, registerName: { value: '学生' },
    loginUsername: { value: 'new-student' }, loginPassword: { value: 'secret' },
    loading: { value: false }, notice: { value: '' }, token: { value: '' },
    user: { value: null }, profileName: { value: '' }, page: { value: '' },
    sessionVersion: 0, authRequestVersion: 0,
    loadingVersion: 0,
    localStorage: { setItem() {} }, load: async () => {}, restoreExam: async () => {},
    FormData: class { get(key) { return key === 'username' ? 'new-student' : 'secret' } }
  }
  state.requestContext = () => ({ token: state.token.value, userId: state.user.value?.id, version: state.sessionVersion })
  state.isCurrentContext = context => context.version === state.sessionVersion && context.token === state.token.value && context.userId === state.user.value?.id
  state.beginLoading = () => { state.loading.value = true; return ++state.loadingVersion }
  state.endLoading = version => { if (version === state.loadingVersion) state.loading.value = false }
  const context = vm.createContext(state)
  vm.runInContext(handler, context)
  return { state, login: () => context.login({ target: {} }) }
}

test('successful registration and login enter the authenticated page', async () => {
  const flow = setup(async url => url.endsWith('register') ? {} : { data: { token: 'test-token', user: { realName: '学生' } } })
  await flow.login()
  assert.equal(flow.state.registering.value, false)
  assert.equal(flow.state.user.value.realName, '学生')
  assert.equal(flow.state.page.value, 'problems')
  assert.equal(flow.state.loading.value, false)
})

test('registration failure preserves registration form and does not call login', async () => {
  const calls = []
  const flow = setup(async url => { calls.push(url); throw { response: { data: { message: '用户名已存在' } } } })
  await flow.login()
  assert.deepEqual(calls, ['/auth/register'])
  assert.equal(flow.state.registering.value, true)
  assert.equal(flow.state.registerName.value, '学生')
  assert.equal(flow.state.notice.value, '用户名已存在')
})

test('failed automatic login clears sensitive fields and retry skips registration', async () => {
  const calls = []
  let failLogin = true
  const flow = setup(async url => {
    calls.push(url)
    if (url.endsWith('register')) return {}
    if (failLogin) throw new Error('connection failed')
    return { data: { token: 'test-token', user: { realName: '学生' } } }
  })
  await flow.login()
  assert.equal(flow.state.registering.value, false)
  assert.equal(flow.state.loginUsername.value, 'new-student')
  assert.equal(flow.state.loginPassword.value, '')
  assert.equal(flow.state.registerName.value, '')
  assert.equal(flow.state.notice.value, '注册成功，但自动登录失败，请重新登录。')
  failLogin = false
  await flow.login()
  assert.deepEqual(calls, ['/auth/register', '/auth/login', '/auth/login'])
  assert.equal(flow.state.user.value.realName, '学生')
})

test('ordinary login failure retains the existing error feedback', async () => {
  const flow = setup(async () => { throw { response: { data: { message: '密码错误' } } } }, false)
  await flow.login()
  assert.equal(flow.state.notice.value, '密码错误')
  assert.equal(flow.state.registering.value, false)
})

test('late login response is ignored after the session is invalidated', async () => {
  let release
  const pending = new Promise(resolve => { release = resolve })
  const flow = setup(async () => pending, false)
  const loginPromise = flow.login()
  flow.state.sessionVersion++
  release({ data: { token: 'stale-token', user: { id: 9, realName: '旧账号' } } })
  await loginPromise
  assert.equal(flow.state.token.value, '')
  assert.equal(flow.state.user.value, null)
  assert.equal(flow.state.page.value, '')
})

test('logout clears local session even when the server already returns 401', async () => {
  const source = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
  const handler = source.slice(source.indexOf('async function logout()'), source.indexOf('function openQuestion'))
  let cleared = 0
  const context = vm.createContext({
    api: { post: async () => { throw { response: { status: 401 } } } },
    clearSession: () => { cleared++ }, notice: { value: 'old' }
  })
  vm.runInContext(handler, context)
  await context.logout()
  assert.equal(cleared, 1)
  assert.equal(context.notice.value, '')
})
