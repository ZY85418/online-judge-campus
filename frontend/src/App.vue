<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import axios from 'axios'
import { createDraftStore, initialCode } from './drafts.js'
import QuestionManager from './components/QuestionManager.vue'
import ExamManager from './components/ExamManager.vue'
import ResultsPanel from './components/ResultsPanel.vue'
import { remainingSeconds } from './time.js'

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api' })
const user = ref(null)
const token = ref(localStorage.getItem('oj-token') || '')
localStorage.removeItem('oj-user')
const page = ref('problems')
const questions = ref([]), exams = ref([]), submissions = ref([]), selected = ref(null)
const code = ref(initialCode)
const drafts = createDraftStore(localStorage)
const verdict = ref(null), notice = ref(''), loading = ref(false)
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const registering=ref(false), registerName=ref(''), profileName=ref(''), category=ref(''), difficulty=ref('')
const loginUsername=ref('student'), loginPassword=ref('student123')
function switchAuthMode(){registering.value=!registering.value; loginUsername.value=''; loginPassword.value=''; registerName.value=''; notice.value=''}
const activeExam=ref(null), answerMode=ref('practice'), receivedAt=ref(0), now=ref(Date.now()), record=ref(null), finishing=ref(false)
let sessionVersion=0, examRequestVersion=0
function requestContext(){return {token:token.value,userId:user.value?.id,version:sessionVersion}}
function isCurrentContext(context){return context.version===sessionVersion&&context.token===token.value&&context.userId===user.value?.id}
const filteredQuestions=computed(()=>questions.value.filter(q=>(!category.value||q.category===category.value)&&(!difficulty.value||q.difficulty===difficulty.value)))
const categories=computed(()=>[...new Set(questions.value.map(q=>q.category))])
const examRunning=computed(()=>activeExam.value?.attempt.status==='IN_PROGRESS')
const seconds=computed(()=>examRunning.value?remainingSeconds(activeExam.value.attempt.deadline,activeExam.value.serverTime,receivedAt.value,now.value):0)
const countdown=computed(()=>`${Math.floor(seconds.value/60)}:${String(seconds.value%60).padStart(2,'0')}`)
const title=computed(()=>({problems:'题库',manage:'题库管理',exams:'考试中心',examManage:'考试管理',records:'提交记录',results:'成绩与错题',profile:'个人信息',record:'提交详情',exam:'考试答题',editor:selected.value?.title||'在线编程'})[page.value])
const draftId=(q)=>`${answerMode.value==='exam'&&activeExam.value?.exam.id?activeExam.value.exam.id:'practice'}:${q.id}`
watch(code, value => {
  if (user.value && selected.value && !drafts.save(user.value.id, draftId(selected.value), value)) {
    notice.value = '草稿保存失败，请检查浏览器存储空间。'
  }
}, { flush: 'sync' })
function errorMessage(error, fallback) {
  if (error.response?.status === 401) return '登录已失效，请重新登录。'
  if (error.response?.status === 403) return '没有操作权限。'
  return error.response?.data?.message || fallback
}
api.interceptors.request.use(config => {
  if (token.value) config.headers.Authorization = `Bearer ${token.value}`
  return config
})
api.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401 && error.config?.headers?.Authorization === `Bearer ${token.value}`) {
    clearSession()
    notice.value = '登录已失效，请重新登录。'
  }
  return Promise.reject(error)
})
function clearSession() {
  sessionVersion++
  examRequestVersion++
  localStorage.removeItem('oj-token'); token.value=''; user.value=null
  selected.value=null; submissions.value=[]; questions.value=[]; exams.value=[]
  code.value=initialCode; verdict.value=null; page.value='problems'
  activeExam.value=null; record.value=null
  loginUsername.value=''; loginPassword.value=''; registerName.value=''; profileName.value=''
}

async function load() {
  if (!user.value) return
  const session = token.value
  const results = await Promise.allSettled([
    api.get('/questions'),
    api.get('/exams'),
    api.get('/submissions/my')
  ])
  if (session !== token.value) return
  const failures = []
  if (results[0].status === 'fulfilled') questions.value = results[0].value.data
  else if (results[0].reason?.response?.status !== 401) failures.push('题库')
  if (results[1].status === 'fulfilled') exams.value = results[1].value.data
  else if (results[1].reason?.response?.status !== 401) failures.push('考试')
  if (results[2].status === 'fulfilled') submissions.value = results[2].value.data
  else if (results[2].reason?.response?.status !== 401) failures.push('提交记录')
  if (failures.length) notice.value = `${failures.join('、')}加载失败，请稍后重试。`
}
async function login(event) {
  const data=new FormData(event.target); loading.value=true; notice.value=''
  try {
    if(registering.value) await api.post('/auth/register',{username:data.get('username'),password:data.get('password'),realName:registerName.value})
    const res=await api.post('/auth/login',{username:data.get('username'),password:data.get('password')}); token.value=res.data.token; localStorage.setItem('oj-token',token.value); user.value=res.data.user; profileName.value=user.value.realName||''; page.value='problems'; await load(); await restoreExam()
  }
  catch (e) { notice.value=e.response?.data?.message || '登录失败，请检查账号和密码。' } finally { loading.value=false }
}
async function logout(){
  try { await api.post('/auth/logout'); clearSession(); notice.value='' }
  catch (e) { if (e.response?.status !== 401) notice.value='退出失败，请检查连接后重试。' }
}
function openQuestion(q){
  selected.value=null; code.value=drafts.load(user.value.id,draftId(q))
  selected.value=q; page.value='editor'; verdict.value=null
}
async function judge(save) { if (!selected.value || loading.value) return; loading.value=true; verdict.value=null
  const session = token.value, questionId = selected.value.id
  try {
    const url=save?'/submissions/submit':'/submissions/run'
    const res=await api.post(url,{questionId,code:code.value,...(answerMode.value==='exam'&&activeExam.value?.exam.id?{examId:activeExam.value.exam.id}: {})})
    if (session !== token.value) return
    if (selected.value?.id === questionId) verdict.value=res.data
    if(save) {
      notice.value=`已提交，编号 #${res.data.submissionId}`
      try { const records=await api.get('/submissions/my'); if (session===token.value) submissions.value=records.data }
      catch(e){notice.value=e.response?.status===401?errorMessage(e,''):`提交 #${res.data.submissionId} 已成功，记录刷新失败，请稍后刷新。`}
    }
  }
  catch (e) { notice.value=errorMessage(e,'提交失败，请检查后端连接。') } finally { loading.value=false }
}
function setExam(data){activeExam.value=data; receivedAt.value=Date.now(); now.value=Date.now()}
async function startExam(e){
  if(loading.value) return
  if(examRunning.value && activeExam.value.exam.id!==e.id){notice.value='请先交卷当前考试。'; return}
  loading.value=true
  const context=requestContext(), requestVersion=++examRequestVersion
  try {const response=await api.post(`/exams/${e.id}/start`); if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; const data=response.data; setExam(data); answerMode.value='exam'; selected.value=null; page.value='exam'; localStorage.setItem(`oj-exam:${user.value.id}`,String(e.id))}
  catch(e){if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; notice.value=errorMessage(e,'开始考试失败。')}
  finally{loading.value=false}
}
async function restoreExam(){
  const id=localStorage.getItem(`oj-exam:${user.value.id}`)
  if(!id) return
  const context=requestContext(), requestVersion=++examRequestVersion
  try{const response=await api.get(`/exams/${id}/session`); if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; setExam(response.data); answerMode.value='exam'; page.value='exam'}
  catch(e){if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; if([400,404].includes(e.response?.status)) localStorage.removeItem(`oj-exam:${user.value.id}`); else notice.value=errorMessage(e,'考试恢复失败，请从考试列表重试。')}
}
async function refreshExam(){
  if(!activeExam.value) return
  const examId=activeExam.value.exam.id, context=requestContext(), requestVersion=++examRequestVersion
  try{const response=await api.get(`/exams/${examId}/session`); if(!isCurrentContext(context)||requestVersion!==examRequestVersion||activeExam.value?.exam.id!==examId) return; setExam(response.data)}
  catch(e){if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; notice.value=errorMessage(e,'考试状态刷新失败。')}
}
async function finishExam(automatic=false){
  if(finishing.value||!examRunning.value) return
  if(!automatic&&!window.confirm('确认交卷？交卷后不能继续提交。')) return
  finishing.value=true
  const examId=activeExam.value.exam.id, context=requestContext(), requestVersion=++examRequestVersion
  try{const response=await api.post(`/exams/${examId}/submit`); if(!isCurrentContext(context)||requestVersion!==examRequestVersion||activeExam.value?.exam.id!==examId) return; setExam(response.data); answerMode.value='exam'; selected.value=null; page.value='exam'; notice.value='答卷已收取，成绩已生成。'}
  catch(e){if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; notice.value=errorMessage(e,'收卷失败，服务器仍会按截止时间收卷。')}
  finally{finishing.value=false}
}
async function showRecord(id){
  const context=requestContext()
  try{const response=await api.get(`/submissions/${id}`); if(!isCurrentContext(context)) return; record.value=response.data; page.value='record'}
  catch(e){if(!isCurrentContext(context)) return; notice.value=errorMessage(e,'记录读取失败。')}
}
async function saveProfile(){
  if(loading.value) return; loading.value=true
  const context=requestContext()
  try{const response=await api.put('/auth/profile',{realName:profileName.value}); if(!isCurrentContext(context)) return; user.value=response.data; notice.value='个人信息已保存。'}
  catch(e){if(!isCurrentContext(context)) return; notice.value=errorMessage(e,'保存失败。')}
  finally{loading.value=false}
}
function practice(){selected.value=null; answerMode.value='practice'; page.value='problems'}
function showExam(){if(activeExam.value){answerMode.value='exam'; page.value='exam'}}
let ticks=0
const timer=setInterval(()=>{now.value=Date.now(); if(examRunning.value){if(seconds.value===0) finishExam(true); else if(++ticks%15===0) refreshExam()}},1000)
onUnmounted(()=>clearInterval(timer))
onMounted(async () => {
  if (!token.value) return
  try { user.value=(await api.get('/auth/current')).data; profileName.value=user.value.realName||''; await load(); await restoreExam() }
  catch (e) { if (e.response?.status !== 401) notice.value='无法验证登录状态，请检查后端连接。' }
})
</script>

<template>
  <main v-if="!user" class="login-shell">
    <section class="login-copy"><span class="brand-mark">OJ</span><h1>Campus Judge</h1><p>校园在线编程考试系统</p><div class="features"><span>题库练习</span><span>模拟判题</span><span>考试管理</span></div></section>
    <form class="login-form" @submit.prevent="login"><h2>{{registering?'注册学生账号':'登录系统'}}</h2><label>用户名<input name="username" v-model="loginUsername" maxlength="50" autocomplete="username" required /></label><label>密码<input name="password" v-model="loginPassword" type="password" :autocomplete="registering?'new-password':'current-password'" required /></label><label v-if="registering">姓名<input v-model="registerName" maxlength="50" required></label><button :disabled="loading">{{ loading ? '处理中...' : registering?'注册并登录':'登录' }}</button><button class="secondary" type="button" :disabled="loading" @click="switchAuthMode">{{registering?'返回登录':'注册账号'}}</button><small>管理员：admin / admin123</small><p v-if="notice" class="error">{{ notice }}</p></form>
  </main>
  <main v-else class="app-shell">
    <aside><div class="logo"><b>OJ</b><span>Campus Judge</span></div><nav><button :class="{active:page==='problems'}" @click="practice">题库</button><button :class="{active:page==='exams'}" @click="page='exams'">考试</button><button v-if="activeExam" @click="showExam">当前答卷</button><button :class="{active:page==='records'}" @click="page='records'">提交记录</button><button @click="page='results'">成绩与错题</button><button v-if="isAdmin" :class="{active:page==='manage'}" @click="page='manage'">题库管理</button><button v-if="isAdmin" @click="page='examManage'">考试管理</button></nav><div class="profile"><strong>{{ user.realName || user.username }}</strong><span>{{ isAdmin?'管理员':'学生' }}</span><button @click="page='profile'">个人信息</button><button @click="logout">退出登录</button></div></aside>
    <section class="workspace"><header><div><h1>{{title}}</h1><p v-if="examRunning">{{activeExam.exam.name}} · 剩余 {{countdown}}</p></div><button class="refresh" @click="load();refreshExam()">刷新</button></header><p v-if="notice" class="notice">{{notice}}</p>
      <div v-if="page==='problems'" class="filters"><label>分类<select v-model="category" aria-label="分类"><option value="">全部</option><option v-for="c in categories" :key="c">{{c}}</option></select></label><label>难度<select v-model="difficulty" aria-label="难度"><option value="">全部</option><option>简单</option><option>中等</option><option>困难</option></select></label></div>
      <section v-if="page==='problems'" class="problem-grid"><article v-for="q in filteredQuestions" :key="q.id" class="problem-card"><div class="card-top"><span>#{{q.id}}</span><i :class="q.difficulty">{{q.difficulty}}</i></div><h2>{{q.title}}</h2><p>{{q.description}}</p><footer><span>{{q.category}} · {{q.score}} 分</span><button @click="openQuestion(q)">开始作答</button></footer></article><p v-if="!filteredQuestions.length">暂无符合条件的题目</p></section>
      <section v-else-if="page==='editor' && selected" class="editor-layout"><article class="statement"><button class="back" @click="answerMode==='exam'?showExam():practice()">{{answerMode==='exam'?'返回答卷':'返回题库'}}</button><span>{{selected.category}} · {{selected.difficulty}}</span><h2>{{selected.title}}</h2><p>{{selected.description}}</p></article><article class="code-panel"><div class="editor-bar"><b>Main.java</b><span>Java</span></div><textarea v-model="code" spellcheck="false" :disabled="answerMode==='exam'&&!examRunning"></textarea><div class="actions"><button class="secondary" :disabled="loading||(answerMode==='exam'&&!examRunning)" @click="judge(false)">运行</button><button :disabled="loading||(answerMode==='exam'&&!examRunning)" @click="judge(true)">提交判题</button></div><div v-if="verdict" class="verdict" :class="verdict.result"><strong>{{verdict.result}}</strong><span>{{verdict.message}}</span><small>{{verdict.score}} 分 · {{verdict.judgeTime}} ms<span v-if="verdict.submissionId"> · #{{verdict.submissionId}}</span></small></div></article></section>
      <section v-else-if="page==='exams'" class="table-wrap"><table><thead><tr><th>考试名称</th><th>考试时间（北京）</th><th>时长</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="e in exams" :key="e.id"><td>{{e.name}}</td><td>{{e.startTime?.replace('T',' ')}}<br>{{e.endTime?.replace('T',' ')}}</td><td>{{e.duration}} 分钟</td><td><span class="badge">{{e.status}}</span></td><td><button :disabled="loading||e.status!=='PUBLISHED'" @click="startExam(e)">进入考试</button></td></tr></tbody></table><p v-if="!exams.length">暂无可参加的考试</p></section>
      <section v-else-if="page==='records'" class="table-wrap"><table><thead><tr><th>提交编号</th><th>题目</th><th>考试</th><th>结果</th><th>得分</th><th>提交时间（北京）</th></tr></thead><tbody><tr v-for="s in submissions" :key="s.id"><td><button class="secondary" @click="showRecord(s.id)">#{{s.id}}</button></td><td>{{questions.find(q=>q.id===s.questionId)?.title||`#${s.questionId}`}}</td><td>{{s.examId?`#${s.examId}`:'练习'}}</td><td><b :class="s.result">{{s.result}}</b></td><td>{{s.score}}</td><td>{{s.submitTime?.replace('T',' ')}}</td></tr></tbody></table><p v-if="!submissions.length">还没有提交记录</p></section>
      <QuestionManager v-else-if="page==='manage'&&isAdmin" :api="api" :questions="questions" @saved="load" @notice="notice=$event" />
      <ExamManager v-else-if="page==='examManage'&&isAdmin" :api="api" :questions="questions" :exams="exams" @saved="load" @notice="notice=$event" />
      <ResultsPanel v-else-if="page==='results'" :api="api" :admin="isAdmin" :exams="exams" @notice="notice=$event" @record="showRecord" />
      <section v-else-if="page==='exam'&&activeExam"><h2>{{activeExam.exam.name}}</h2><p>状态：{{activeExam.attempt.status}} · 截止 {{activeExam.attempt.deadline?.replace('T',' ')}}</p><div class="actions"><button v-if="examRunning" :disabled="finishing" @click="finishExam(false)">交卷</button><button v-else @click="page='results'">查看成绩</button></div><div class="table-wrap"><table><thead><tr><th>题目</th><th>分值</th><th>已保存提交</th><th></th></tr></thead><tbody><tr v-for="q in activeExam.questions" :key="q.id"><td>{{q.title}}</td><td>{{q.score}}</td><td>{{submissions.filter(s=>s.examId===activeExam.exam.id&&s.questionId===q.id).length}}</td><td><button :disabled="!examRunning" @click="openQuestion(q)">作答</button></td></tr></tbody></table></div></section>
      <section v-else-if="page==='profile'" class="manage"><form @submit.prevent="saveProfile"><h2>{{user.username}}</h2><label>姓名<input v-model="profileName" maxlength="50" required></label><button :disabled="loading">保存姓名</button></form></section>
      <section v-else-if="page==='record'&&record"><button class="secondary" @click="page='records'">返回记录</button><h2>提交 #{{record.id}}</h2><p>题目 #{{record.questionId}} · {{record.result}} · {{record.score}} 分</p><pre class="saved-code">{{record.code}}</pre></section>
    </section>
  </main>
</template>
