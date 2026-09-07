<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'

const api = axios.create({ baseURL: 'http://localhost:8080/api' })
const user = ref(JSON.parse(localStorage.getItem('oj-user') || 'null'))
const page = ref('problems')
const questions = ref([]), exams = ref([]), submissions = ref([]), selected = ref(null)
const code = ref('public class Main {\n  public static void main(String[] args) {\n    // 在此编写代码\n  }\n}')
const verdict = ref(null), notice = ref(''), loading = ref(false)
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const headers = computed(() => ({ 'X-User-Id': user.value?.id || 1 }))

async function load() {
  try { const [q, e] = await Promise.all([api.get('/questions'), api.get('/exams')]); questions.value=q.data; exams.value=e.data
    if (user.value) submissions.value=(await api.get('/submissions/my',{headers:headers.value})).data
  } catch { notice.value='无法连接后端，请确认服务已启动。' }
}
async function login(event) {
  const data=new FormData(event.target); loading.value=true; notice.value=''
  try { const res=await api.post('/auth/login',{username:data.get('username'),password:data.get('password')}); user.value=res.data.user; localStorage.setItem('oj-user',JSON.stringify(res.data.user)); page.value='problems'; await load() }
  catch (e) { notice.value=e.response?.data?.message || '登录失败，请检查账号和密码。' } finally { loading.value=false }
}
function logout(){ localStorage.removeItem('oj-user'); user.value=null; selected.value=null; page.value='problems' }
function openQuestion(q){ selected.value=q; page.value='editor'; verdict.value=null }
async function judge(save) { if (!selected.value) return; loading.value=true; verdict.value=null
  try { const url=save?'/submissions/submit':'/submissions/run'; const res=await api.post(url,{questionId:selected.value.id,code:code.value},{headers:headers.value}); verdict.value=res.data; if(save) submissions.value=(await api.get('/submissions/my',{headers:headers.value})).data }
  catch { notice.value='提交失败，请确认后端和数据库已启动。' } finally { loading.value=false }
}
async function createQuestion(event) { const d=new FormData(event.target); const body=Object.fromEntries(d.entries()); body.score=Number(body.score); await api.post('/questions',body); event.target.reset(); notice.value='题目已创建。'; await load() }
onMounted(load)
</script>

<template>
  <main v-if="!user" class="login-shell">
    <section class="login-copy"><span class="brand-mark">OJ</span><h1>Campus Judge</h1><p>校园在线编程考试系统</p><div class="features"><span>题库练习</span><span>模拟判题</span><span>考试管理</span></div></section>
    <form class="login-form" @submit.prevent="login"><h2>登录系统</h2><p>使用演示账号快速体验</p><label>用户名<input name="username" value="student" required /></label><label>密码<input name="password" value="student123" type="password" required /></label><button :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button><small>管理员：admin / admin123</small><p v-if="notice" class="error">{{ notice }}</p></form>
  </main>
  <main v-else class="app-shell">
    <aside><div class="logo"><b>OJ</b><span>Campus Judge</span></div><nav><button :class="{active:page==='problems'}" @click="page='problems'">题库</button><button :class="{active:page==='exams'}" @click="page='exams'">考试</button><button :class="{active:page==='records'}" @click="page='records'">提交记录</button><button v-if="isAdmin" :class="{active:page==='manage'}" @click="page='manage'">题库管理</button></nav><div class="profile"><strong>{{ user.realName || user.username }}</strong><span>{{ isAdmin?'管理员':'学生' }}</span><button @click="logout">退出登录</button></div></aside>
    <section class="workspace"><header><div><h1>{{ page==='editor'?selected?.title: page==='manage'?'题库管理':page==='exams'?'考试中心':page==='records'?'提交记录':'题库' }}</h1><p>{{ page==='editor'?'编写 Java 代码并运行模拟判题':'欢迎回来，开始今天的编程练习' }}</p></div><button class="refresh" @click="load">刷新</button></header><p v-if="notice" class="notice">{{notice}}</p>
      <section v-if="page==='problems'" class="problem-grid"><article v-for="q in questions" :key="q.id" class="problem-card"><div class="card-top"><span>#{{q.id}}</span><i :class="q.difficulty">{{q.difficulty}}</i></div><h2>{{q.title}}</h2><p>{{q.description}}</p><footer><span>{{q.category}} · {{q.score}} 分</span><button @click="openQuestion(q)">开始作答</button></footer></article><p v-if="!questions.length">暂无题目</p></section>
      <section v-else-if="page==='editor' && selected" class="editor-layout"><article class="statement"><button class="back" @click="page='problems'">返回题库</button><span>{{selected.category}} · {{selected.difficulty}}</span><h2>{{selected.title}}</h2><p>{{selected.description}}</p><h3>提交说明</h3><p>仅支持 Java。点击运行可查看模拟结果，提交会保存到个人记录。</p></article><article class="code-panel"><div class="editor-bar"><b>Main.java</b><span>Java</span></div><textarea v-model="code" spellcheck="false"></textarea><div class="actions"><button class="secondary" :disabled="loading" @click="judge(false)">运行</button><button :disabled="loading" @click="judge(true)">提交判题</button></div><div v-if="verdict" class="verdict" :class="verdict.result"><strong>{{verdict.result}}</strong><span>{{verdict.message}}</span><small>{{verdict.score}} 分 · {{verdict.judgeTime}} ms</small></div></article></section>
      <section v-else-if="page==='exams'" class="table-wrap"><table><thead><tr><th>考试名称</th><th>时长</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="e in exams" :key="e.id"><td>{{e.name}}</td><td>{{e.duration}} 分钟</td><td><span class="badge">{{e.status}}</span></td><td><button @click="notice='考试入口已准备，发布后可开始作答。'">进入考试</button></td></tr></tbody></table><p v-if="!exams.length">暂无已创建的考试</p></section>
      <section v-else-if="page==='records'" class="table-wrap"><table><thead><tr><th>题目</th><th>结果</th><th>得分</th><th>提交时间</th></tr></thead><tbody><tr v-for="s in submissions" :key="s.id"><td>#{{s.questionId}}</td><td><b :class="s.result">{{s.result}}</b></td><td>{{s.score}}</td><td>{{new Date(s.submitTime).toLocaleString()}}</td></tr></tbody></table><p v-if="!submissions.length">还没有提交记录</p></section>
      <section v-else-if="page==='manage'" class="manage"><form @submit.prevent="createQuestion"><h2>新建题目</h2><div class="form-grid"><label>题目标题<input name="title" required /></label><label>分类<input name="category" value="基础算法" required /></label><label>难度<select name="difficulty"><option>简单</option><option>中等</option><option>困难</option></select></label><label>分值<input name="score" type="number" value="100" required /></label></div><label>题目描述<textarea name="description" required></textarea></label><label>标准答案关键片段<textarea name="standardAnswer" required></textarea></label><button>保存题目</button></form><div class="mini-list"><h2>现有题目</h2><p v-for="q in questions" :key="q.id">#{{q.id}} {{q.title}} <span>{{q.difficulty}}</span></p></div></section>
    </section>
  </main>
</template>
