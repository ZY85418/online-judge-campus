<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import axios from 'axios'
import { createDraftStore, initialCode } from './drafts.js'
import QuestionManager from './components/QuestionManager.vue'
import ExamManager from './components/ExamManager.vue'
import ResultsPanel from './components/ResultsPanel.vue'
import StudentManager from './components/StudentManager.vue'
import { remainingSeconds } from './time.js'
import { confirmExamEntry, getExamAvailability } from './exam-time.js'
import { personalExamView } from './exam-status.js'
import { filterQuestions, paginate, QUESTION_PAGE_SIZE } from './question-list.js'

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api' })
const user = ref(null)
const token = ref(localStorage.getItem('oj-token') || '')
localStorage.removeItem('oj-user')
const page = ref('problems')
const questions = ref([]), exams = ref([]), examStatuses = ref([]), submissions = ref([]), selected = ref(null), examResult = ref(null)
const code = ref(initialCode)
const drafts = createDraftStore(localStorage)
const verdict = ref(null), notice = ref(''), loading = ref(false)
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const registering=ref(false), registerName=ref(''), profileName=ref(''), category=ref(''), difficulty=ref(''), questionKeyword=ref(''), questionPage=ref(1)
const loginUsername=ref('student'), loginPassword=ref('student123')
function switchAuthMode(){registering.value=!registering.value; loginUsername.value=''; loginPassword.value=''; registerName.value=''; notice.value=''}
const activeExam=ref(null), answerMode=ref('practice'), receivedAt=ref(0), now=ref(Date.now()), record=ref(null), recordReturnPage=ref('records'), finishing=ref(false)
const submissionFilters=ref({userId:'',examId:'',questionId:'',result:''})
const studentSubmissionFilters=ref({examId:'',result:'',keyword:''})
const appliedStudentSubmissionFilters=ref({examId:'',result:'',keyword:''})
function queryStudentSubmissions(){appliedStudentSubmissionFilters.value={...studentSubmissionFilters.value};page.value==='records'&&(notice.value='');}
const visibleStudentSubmissions=computed(()=>{const f=appliedStudentSubmissionFilters.value;const keyword=String(f.keyword||'').trim().toLowerCase();return submissions.value.filter(row=>(!f.examId||(row.examId||'')==f.examId)&&(!f.result||row.result===f.result)&&(!keyword||String(questions.value.find(q=>q.id===row.questionId)?.title||'').toLowerCase().includes(keyword)))})
let sessionVersion=0, examRequestVersion=0, authRequestVersion=0, dataRequestVersion=0, submissionRequestVersion=0, loadingVersion=0
let submissionNoticeTimer
function beginLoading(){loading.value=true; return ++loadingVersion}
function endLoading(version){if(version===loadingVersion) loading.value=false}
function requestContext(){return {token:token.value,userId:user.value?.id,version:sessionVersion}}
function isCurrentContext(context){return context.version===sessionVersion&&context.token===token.value&&context.userId===user.value?.id}
function showSubmissionNotice(message) {
  clearTimeout(submissionNoticeTimer)
  notice.value = message
  submissionNoticeTimer = setTimeout(() => {
    if (notice.value === message) notice.value = ''
  }, 4000)
}
const appliedQuestionFilters=ref({keyword:'',category:'',difficulty:''})
function queryQuestions(){appliedQuestionFilters.value={keyword:questionKeyword.value,category:category.value,difficulty:difficulty.value};questionPage.value=1}
const filteredQuestions=computed(()=>filterQuestions(questions.value,appliedQuestionFilters.value))
const questionPageData=computed(()=>paginate(filteredQuestions.value,questionPage.value))

const categories=computed(()=>[...new Set(questions.value.map(q=>q.category))])
const examRunning=computed(()=>activeExam.value?.attempt.status==='IN_PROGRESS')
const seconds=computed(()=>examRunning.value?remainingSeconds(activeExam.value.attempt.deadline,activeExam.value.serverTime,receivedAt.value,now.value):0)
const countdown=computed(()=>`${Math.floor(seconds.value/60)}:${String(seconds.value%60).padStart(2,'0')}`)
function examAvailabilityLabel(exam){
  const state=getExamAvailability(exam,now.value)
  if(state==='upcoming') return '未开放'
  if(state==='open') return '开放中'
  if(state==='ended') return '已结束'
  if(exam?.status==='DRAFT') return '草稿'
  if(exam?.status==='CLOSED') return '已关闭'
  return '暂不可参加'
}
function adminExamStatusLabel(exam){
  if(exam?.status==='DRAFT') return '草稿'
  if(exam?.status==='CLOSED') return '已关闭'
  const state=getExamAvailability(exam,now.value)
  return state==='upcoming'?'未开放':state==='open'?'开放中':'已结束'
}
const title=computed(()=>({problems:'题库',manage:'题库管理',exams:'考试中心',examManage:'考试管理',students:'学生管理',records:'提交记录',results:isAdmin.value?'学生答卷与成绩':'成绩与错题',profile:'个人信息',record:'提交详情',exam:'考试答题',examResult:'考试成绩详情',editor:selected.value?.title||'在线编程'})[page.value])
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
  clearTimeout(submissionNoticeTimer)
  submissionNoticeTimer = undefined
  sessionVersion++
  examRequestVersion++
  authRequestVersion++
  dataRequestVersion++
  submissionRequestVersion++
  loadingVersion++; loading.value=false
  localStorage.removeItem('oj-token'); token.value=''; user.value=null
  selected.value=null; submissions.value=[]; questions.value=[]; exams.value=[]; examStatuses.value=[]; examResult.value=null; studentSubmissionFilters.value={examId:'',result:'',keyword:''}; appliedStudentSubmissionFilters.value={examId:'',result:'',keyword:''}
  code.value=initialCode; verdict.value=null; page.value='problems'; appliedQuestionFilters.value={keyword:'',category:'',difficulty:''};questionKeyword.value='';category.value='';difficulty.value='';questionPage.value=1
  activeExam.value=null; record.value=null
  loginUsername.value=''; loginPassword.value=''; registerName.value=''; profileName.value=''
}

watch(page, (nextPage, previousPage) => {
  if (previousPage === 'editor' && nextPage !== 'editor' && notice.value.startsWith('已提交，编号 #')) {
    clearTimeout(submissionNoticeTimer)
    submissionNoticeTimer = undefined
    notice.value = ''
  }
})

async function load() {
  if (!user.value) return
  const context = requestContext(), requestVersion = ++dataRequestVersion
  const requests = [
    api.get('/questions'),
    api.get('/exams'),
    api.get('/submissions/my')
  ]
  if (!isAdmin.value) requests.push(api.get('/exams/my-status'))
  const results = await Promise.allSettled(requests)
  if (!isCurrentContext(context) || requestVersion !== dataRequestVersion) return
  const failures = []
  if (results[0].status === 'fulfilled') questions.value = results[0].value.data
  else if (results[0].reason?.response?.status !== 401) failures.push('题库')
  if (results[1].status === 'fulfilled') exams.value = results[1].value.data
  else if (results[1].reason?.response?.status !== 401) failures.push('考试')
  if (results[2].status === 'fulfilled') submissions.value = results[2].value.data
  else if (results[2].reason?.response?.status !== 401) failures.push('提交记录')
  if (!isAdmin.value) {
    if (results[3]?.status === 'fulfilled') examStatuses.value = results[3].value.data
    else if (results[3]?.reason?.response?.status !== 401) failures.push('个人考试状态')
  }
  if (failures.length) notice.value = `${failures.join('、')}加载失败，请稍后重试。`
}
async function login(event) {
  const data=new FormData(event.target), requestVersion=++authRequestVersion, context=requestContext()
  const busyVersion=beginLoading(); notice.value=''
  let registrationSucceeded=false
  try {
    if(registering.value) {
      await api.post('/auth/register',{username:data.get('username'),password:data.get('password'),realName:registerName.value})
      if(requestVersion!==authRequestVersion || context.version!==sessionVersion) return
      registrationSucceeded=true
      registering.value=false
      registerName.value=''
    }
    const res=await api.post('/auth/login',{username:data.get('username'),password:data.get('password')})
    if(requestVersion!==authRequestVersion || context.version!==sessionVersion) return
    token.value=res.data.token; localStorage.setItem('oj-token',token.value); user.value=res.data.user; profileName.value=user.value.realName||''; page.value='problems'; await load(); await restoreExam()
  }
  catch (e) {
    if(requestVersion!==authRequestVersion || context.version!==sessionVersion) return
    if(registrationSucceeded) {
      loginPassword.value=''
      notice.value='注册成功，但自动登录失败，请重新登录。'
    } else notice.value=e.response?.data?.message || '登录失败，请检查账号和密码。'
  } finally { if(requestVersion===authRequestVersion) endLoading(busyVersion) }
}
async function logout(){
  try { await api.post('/auth/logout'); clearSession(); notice.value='' }
  catch (e) {
    if (e.response?.status === 401) { clearSession(); notice.value='' }
    else notice.value='退出失败，请检查连接后重试。'
  }
}
function openQuestion(q){
  selected.value=null; code.value=drafts.load(user.value.id,draftId(q))
  selected.value=q; page.value='editor'; verdict.value=null
}
async function judge(save) { if (!selected.value || loading.value) return; const busyVersion=beginLoading(); verdict.value=null
  const context=requestContext(), requestVersion=++submissionRequestVersion, questionId = selected.value.id
  try {
    const url=save?'/submissions/submit':'/submissions/run'
    const res=await api.post(url,{questionId,code:code.value,...(answerMode.value==='exam'&&activeExam.value?.exam.id?{examId:activeExam.value.exam.id}: {})})
    if (!isCurrentContext(context) || requestVersion !== submissionRequestVersion) return
    if (selected.value?.id === questionId) verdict.value=res.data
    if(save) {
      showSubmissionNotice(`已提交，编号 #${res.data.submissionId}`)
      try { const records=await api.get('/submissions/my'); if (isCurrentContext(context) && requestVersion === submissionRequestVersion) submissions.value=records.data }
      catch(e){if (isCurrentContext(context) && requestVersion === submissionRequestVersion) notice.value=e.response?.status===401?errorMessage(e,''):`提交 #${res.data.submissionId} 已成功，记录刷新失败，请稍后刷新。`}
    }
  }
  catch (e) { if (isCurrentContext(context) && requestVersion === submissionRequestVersion) notice.value=errorMessage(e,'提交失败，请检查后端连接。') }
  finally { endLoading(busyVersion) }
}
function setExam(data){activeExam.value=data; receivedAt.value=Date.now(); now.value=Date.now()}
function examView(exam){return personalExamView(exam,examStatuses.value.find(item=>item.examId===exam.id),now.value)}
async function viewExamResult(exam){
  const context=requestContext()
  try{const response=await api.get(`/results/${exam.id}/detail`); if(!isCurrentContext(context)) return; examResult.value=response.data; page.value='examResult'}
  catch(e){if(!isCurrentContext(context)) return; notice.value=errorMessage(e,'成绩详情读取失败。')}
}
async function startExam(e){
  if(loading.value) return
  if(examRunning.value && activeExam.value.exam.id!==e.id){notice.value='请先交卷当前考试。'; return}
  if(getExamAvailability(e,now.value)!=='open'){notice.value=`该考试${examAvailabilityLabel(e)}，当前不能进入。`; return}
  const startRequest=confirmExamEntry(message=>window.confirm(message),()=>api.post(`/exams/${e.id}/start`))
  if(!startRequest) return
  const busyVersion=beginLoading()
  const context=requestContext(), requestVersion=++examRequestVersion
  try {const response=await startRequest; if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; const data=response.data; setExam(data); answerMode.value='exam'; selected.value=null; page.value='exam'; localStorage.setItem(`oj-exam:${user.value.id}`,String(e.id))}
  catch(e){if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; notice.value=errorMessage(e,'开始考试失败。')}
  finally{endLoading(busyVersion)}
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
  try{const response=await api.post(`/exams/${examId}/submit`); if(!isCurrentContext(context)||requestVersion!==examRequestVersion||activeExam.value?.exam.id!==examId) return; setExam(response.data); answerMode.value='exam'; selected.value=null; page.value='exam'; await load(); notice.value='答卷已收取，成绩已生成。'}
  catch(e){if(!isCurrentContext(context)||requestVersion!==examRequestVersion) return; notice.value=errorMessage(e,'收卷失败，服务器仍会按截止时间收卷。')}
  finally{finishing.value=false}
}
async function showRecord(id){
  const context=requestContext(), returnPage=page.value
  try{const response=await api.get(`/submissions/${id}`); if(!isCurrentContext(context)) return; record.value=response.data; recordReturnPage.value=returnPage; page.value='record'}
  catch(e){if(!isCurrentContext(context)) return; notice.value=errorMessage(e,'记录读取失败。')}
}
async function showSubmissions(){
  page.value='records'
  if(!isAdmin.value) return
  const context=requestContext(), requestVersion=++dataRequestVersion
  try {
    const params=Object.fromEntries(Object.entries(submissionFilters.value).filter(([,value])=>value!==''))
    const response=await api.get('/submissions/admin',{params})
    if(isCurrentContext(context) && requestVersion===dataRequestVersion) submissions.value=response.data
  } catch(e) { if(isCurrentContext(context) && requestVersion===dataRequestVersion) notice.value=errorMessage(e,'提交记录加载失败。') }
}
async function refreshPage(){
  if(page.value==='records'&&isAdmin.value) return showSubmissions()
  await load(); await refreshExam()
}
async function saveProfile(){
  if(loading.value) return; const busyVersion=beginLoading()
  const context=requestContext()
  try{const response=await api.put('/auth/profile',{realName:profileName.value}); if(!isCurrentContext(context)) return; user.value=response.data; notice.value='个人信息已保存。'}
  catch(e){if(!isCurrentContext(context)) return; notice.value=errorMessage(e,'保存失败。')}
  finally{endLoading(busyVersion)}
}
function practice(){selected.value=null; answerMode.value='practice'; page.value='problems'}
function showExam(){if(activeExam.value){answerMode.value='exam'; page.value='exam'}}
let ticks=0
const timer=setInterval(()=>{now.value=Date.now(); if(examRunning.value){if(seconds.value===0) finishExam(true); else if(++ticks%15===0) refreshExam()}},1000)
onUnmounted(()=>{clearInterval(timer); clearTimeout(submissionNoticeTimer)})
onMounted(async () => {
  if (!token.value) return
  const context=requestContext()
  try {
    const current=(await api.get('/auth/current')).data
    if(!isCurrentContext(context)) return
    user.value=current; profileName.value=user.value.realName||''; await load(); await restoreExam()
  }
  catch (e) { if (isCurrentContext(context) && e.response?.status !== 401) notice.value='无法验证登录状态，请检查后端连接。' }
})
</script>

<template>
  <main v-if="!user" class="login-shell">
    <section class="login-copy"><span class="brand-mark">OJ</span><h1>Campus Judge</h1><p>校园在线编程考试系统</p><div class="features"><span>题库练习</span><span>模拟判题</span><span>考试管理</span></div></section>
    <form class="login-form" @submit.prevent="login"><h2>{{registering?'注册学生账号':'登录系统'}}</h2><label>用户名<input name="username" v-model="loginUsername" maxlength="50" autocomplete="username" required /></label><label>密码<input name="password" v-model="loginPassword" type="password" :autocomplete="registering?'new-password':'current-password'" required /></label><label v-if="registering">姓名<input v-model="registerName" maxlength="50" required></label><button :disabled="loading">{{ loading ? '处理中...' : registering?'注册并登录':'登录' }}</button><button class="secondary" type="button" :disabled="loading" @click="switchAuthMode">{{registering?'返回登录':'注册账号'}}</button><small>管理员：admin / admin123</small><p v-if="notice" class="error">{{ notice }}</p></form>
  </main>
  <main v-else class="app-shell">
    <aside><div class="logo"><b>OJ</b><span>Campus Judge</span></div><nav><button :disabled="examRunning" :class="{active:page==='problems'}" @click="practice">题库</button><button v-if="!isAdmin" :disabled="examRunning" :class="{active:page==='exams'}" @click="page='exams'">考试</button><button :disabled="examRunning" :class="{active:page==='records'}" @click="showSubmissions">提交记录</button><button :disabled="examRunning" @click="page='results'">{{isAdmin?'学生答卷与成绩':'成绩与错题'}}</button><button v-if="isAdmin" :disabled="examRunning" :class="{active:page==='manage'}" @click="page='manage'">题库管理</button><button v-if="isAdmin" :disabled="examRunning" @click="page='examManage'">考试管理</button><button v-if="isAdmin" :disabled="examRunning" :class="{active:page==='students'}" @click="page='students'">学生管理</button></nav><div class="profile"><strong>{{ user.realName || user.username }}</strong><span>{{ isAdmin?'管理员':'学生' }}</span><button :disabled="examRunning" @click="page='profile'">个人信息</button><button @click="logout">退出登录</button></div></aside>
    <section class="workspace"><header><div><h1>{{title}}</h1><p v-if="examRunning">{{activeExam.exam.name}} · 剩余 {{countdown}}</p></div><button class="refresh" @click="refreshPage">刷新</button></header><p v-if="notice" class="notice">{{notice}}</p>
      <form v-if="page==='problems'" class="filters" @submit.prevent="queryQuestions"><label>搜索题目<input v-model="questionKeyword" type="search" placeholder="搜索标题或题目描述"></label><label>分类<select v-model="category" aria-label="分类"><option value="">全部</option><option v-for="c in categories" :key="c">{{c}}</option></select></label><label>难度<select v-model="difficulty" aria-label="难度"><option value="">全部</option><option>简单</option><option>中等</option><option>困难</option></select></label><button>查询</button><button type="button" v-if="questionKeyword||category||difficulty" class="secondary" @click="questionKeyword='';category='';difficulty='';queryQuestions()">清除条件</button></form>
      <section v-if="page==='problems'" class="table-wrap question-list"><p>共 {{filteredQuestions.length}} 道题目，每页 {{QUESTION_PAGE_SIZE}} 道</p><table><thead><tr><th>题号</th><th>题目</th><th>分类</th><th>难度</th><th>分值</th><th>操作</th></tr></thead><tbody><tr v-for="q in questionPageData.items" :key="q.id"><td>#{{q.id}}</td><td><strong>{{q.title}}</strong><p class="question-description">{{q.description}}</p></td><td>{{q.category}}</td><td>{{q.difficulty}}</td><td>{{q.score}}</td><td><button @click="openQuestion(q)">开始作答</button></td></tr></tbody></table><p v-if="!filteredQuestions.length">没有符合条件的题目</p><div v-if="questionPageData.pageCount>1" class="pagination"><button class="secondary" :disabled="questionPageData.currentPage===1" @click="questionPage--">上一页</button><span>第 {{questionPageData.currentPage}} / {{questionPageData.pageCount}} 页</span><button class="secondary" :disabled="questionPageData.currentPage===questionPageData.pageCount" @click="questionPage++">下一页</button></div></section>
      <section v-else-if="page==='editor' && selected" class="editor-layout"><article class="statement"><button class="back" @click="answerMode==='exam'?showExam():practice()">{{answerMode==='exam'?'返回答卷':'返回题库'}}</button><span>{{selected.category}} · {{selected.difficulty}}</span><h2>{{selected.title}}</h2><p>{{selected.description}}</p><div v-if="selected.sampleInput" class="sample-block"><h3>样例输入</h3><pre>{{selected.sampleInput}}</pre></div><div v-if="selected.sampleOutput" class="sample-block"><h3>样例输出</h3><pre>{{selected.sampleOutput}}</pre></div></article><article class="code-panel"><div class="editor-bar"><b>Main.java</b><span>Java</span></div><textarea v-model="code" spellcheck="false" :disabled="answerMode==='exam'&&!examRunning"></textarea><div class="actions"><button class="secondary" :disabled="loading||(answerMode==='exam'&&!examRunning)" @click="judge(false)">运行</button><button :disabled="loading||(answerMode==='exam'&&!examRunning)" @click="judge(true)">提交判题</button></div><div v-if="verdict" class="verdict" :class="verdict.result"><strong>{{verdict.result}}</strong><span>{{verdict.message}}</span><small>{{verdict.score}} 分 · {{verdict.judgeTime}} ms<span v-if="verdict.submissionId"> · #{{verdict.submissionId}}</span></small></div></article></section>
      <section v-else-if="page==='exams'" class="table-wrap"><table><thead><tr><th>考试名称</th><th>开放时间（北京）</th><th>个人答题时长</th><th>{{isAdmin?'考试状态':'个人答卷状态'}}</th><th>{{isAdmin?'说明':'成绩'}}</th><th>操作</th></tr></thead><tbody><tr v-for="e in exams" :key="e.id"><td>{{e.name}}</td><td>{{e.startTime?.replace('T',' ')}}<br>{{e.endTime?.replace('T',' ')}}</td><td>{{e.duration}} 分钟</td><td><span class="badge">{{isAdmin?adminExamStatusLabel(e):examView(e).label}}</span><small v-if="!isAdmin&&examView(e).deadline"><br>个人截止 {{examView(e).deadline.replace('T',' ')}}</small></td><td>{{isAdmin?'管理员可在考试管理中维护':(examView(e).score==null?'—':`${examView(e).score} 分`)}}</td><td><button v-if="!isAdmin&&examView(e).action==='start'" :disabled="loading" @click="startExam(e)">进入考试</button><button v-else-if="!isAdmin&&examView(e).action==='result'" class="secondary" @click="viewExamResult(e)">查看成绩</button><span v-else-if="!isAdmin&&examView(e).action==='in-progress'">答题进行中</span><span v-else>—</span></td></tr></tbody></table><p v-if="!exams.length">暂无考试</p></section>
      <section v-else-if="page==='records'"><form v-if="isAdmin" class="filters" @submit.prevent="showSubmissions"><label>学生编号<input v-model="submissionFilters.userId" type="number" min="1"></label><label>考试编号<input v-model="submissionFilters.examId" type="number" min="1"></label><label>题目编号<input v-model="submissionFilters.questionId" type="number" min="1"></label><label>结果<select v-model="submissionFilters.result"><option value="">全部</option><option>AC</option><option>WA</option><option>RE</option></select></label><button>查询</button></form><form v-else class="filters" @submit.prevent="queryStudentSubmissions"><label>考试<select v-model="studentSubmissionFilters.examId"><option value="">全部考试</option><option v-for="e in exams" :key="e.id" :value="String(e.id)">{{e.name}}</option></select></label><label>题目关键词<input v-model="studentSubmissionFilters.keyword" placeholder="搜索题目名称"></label><label>结果<select v-model="studentSubmissionFilters.result"><option value="">全部结果</option><option>AC</option><option>WA</option><option>RE</option></select></label><button>查询</button><button type="button" class="secondary" @click="studentSubmissionFilters={examId:'',result:'',keyword:''};queryStudentSubmissions()">清除条件</button></form><p v-if="isAdmin">最多显示符合条件的最近 200 条；可用筛选项缩小范围。</p><p v-else>共 {{visibleStudentSubmissions.length}} 条本人提交记录；点击提交编号查看代码。</p><div class="table-wrap"><table><thead><tr><th>提交编号</th><th v-if="isAdmin">学生编号</th><th>题目</th><th>考试</th><th>结果</th><th>得分</th><th>判题耗时</th><th>提交时间（北京）</th></tr></thead><tbody><tr v-for="s in (isAdmin?submissions:visibleStudentSubmissions)" :key="s.id"><td><button class="secondary" @click="showRecord(s.id)">#{{s.id}}</button></td><td v-if="isAdmin">{{s.userId}}</td><td>{{questions.find(q=>q.id===s.questionId)?.title||`#${s.questionId}`}}</td><td>{{s.examId?(exams.find(e=>e.id===s.examId)?.name||`#${s.examId}`):'练习'}}</td><td><b :class="s.result">{{s.result}}</b></td><td>{{s.score}}</td><td>{{s.judgeTime}} ms</td><td>{{s.submitTime?.replace('T',' ')}}</td></tr></tbody></table><p v-if="!(isAdmin?submissions:visibleStudentSubmissions).length">{{isAdmin?'还没有提交记录':'没有符合条件的提交记录'}}</p></div></section>
      <QuestionManager v-else-if="page==='manage'&&isAdmin" :api="api" :questions="questions" @saved="load" @notice="notice=$event" />
      <ExamManager v-else-if="page==='examManage'&&isAdmin" :api="api" :questions="questions" :exams="exams" @saved="load" @notice="notice=$event" />
      <ResultsPanel v-else-if="page==='results'" :api="api" :admin="isAdmin" :exams="exams" :session-key="`${token}:${user?.id || ''}`" @notice="notice=$event" @record="showRecord" />
      <section v-else-if="page==='exam'&&activeExam"><h2>{{activeExam.exam.name}}</h2><p>状态：{{activeExam.attempt.status}} · 截止 {{activeExam.attempt.deadline?.replace('T',' ')}}</p><div class="actions"><button v-if="examRunning" :disabled="finishing" @click="finishExam(false)">交卷</button><button v-else @click="page='results'">查看成绩</button></div><div class="table-wrap"><table><thead><tr><th>题目</th><th>分值</th><th>已保存提交</th><th></th></tr></thead><tbody><tr v-for="q in activeExam.questions" :key="q.id"><td>{{q.title}}</td><td>{{q.score}}</td><td>{{submissions.filter(s=>s.examId===activeExam.exam.id&&s.questionId===q.id).length}}</td><td><button :disabled="!examRunning" @click="openQuestion(q)">作答</button></td></tr></tbody></table></div></section>
      <section v-else-if="page==='examResult'&&examResult" class="result-section"><button class="secondary" @click="page='exams'">返回考试列表</button><h2>{{examResult.examName}} · {{examResult.result.totalScore}} 分</h2><p>答卷状态：{{examStatuses.find(item=>item.examId===examResult.result.examId)?.status||'已结束'}} · 收卷 {{examResult.result.submitTime?.replace('T',' ')}}</p><div class="table-wrap"><table><thead><tr><th>题目</th><th>结果</th><th>得分</th><th>提交编号</th></tr></thead><tbody><tr v-for="d in examResult.details" :key="d.questionId"><td>{{d.title}}</td><td>{{d.result}}</td><td>{{d.score}} / {{d.maxScore}}</td><td><button v-if="d.submissionId" class="secondary" @click="showRecord(d.submissionId)">查看代码 #{{d.submissionId}}</button><span v-else>未提交</span></td></tr></tbody></table></div></section>
      <StudentManager v-else-if="page==='students'&&isAdmin" :api="api" :exams="exams" @notice="notice=$event" @record="showRecord" />
      <section v-else-if="page==='profile'" class="manage"><form @submit.prevent="saveProfile"><h2>个人信息</h2><div class="profile-summary"><p>用户编号：#{{user.id}}</p><p>用户名：{{user.username}}</p><p>角色：{{isAdmin?'管理员':'学生'}}</p><p>账号状态：{{user.status==='ACTIVE'?'正常':'停用'}}</p><p>注册时间：{{user.createTime?.replace('T',' ')||'—'}}</p></div><label>姓名<input v-model="profileName" maxlength="50" required></label><button :disabled="loading">保存姓名</button></form></section>
      <section v-else-if="page==='record'&&record"><button class="secondary" @click="page=recordReturnPage">返回{{recordReturnPage==='examResult'?'成绩详情':'记录'}}</button><h2>提交 #{{record.id}}</h2><p>题目 #{{record.questionId}} · {{record.result}} · {{record.score}} 分</p><pre class="saved-code">{{record.code}}</pre></section>
    </section>
  </main>
</template>
