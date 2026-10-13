<script setup>
import { ref, computed, onMounted, watch } from 'vue'
const props=defineProps({api:[Object, Function],admin:Boolean,exams:Array,sessionKey:String})
const emit=defineEmits(['notice','record'])
const rows=ref([]),busy=ref(false),studentId=ref(''),examId=ref(''),wrongOnly=ref(false)
const selectedResultId=ref(null)
const visibleRows=computed(()=>props.admin?rows.value.filter(row=>row.result.id===selectedResultId.value):rows.value)
const analytics=ref(null),analyticsBusy=ref(false)
const stats=computed(()=>({count:rows.value.length,average:rows.value.length?(rows.value.reduce((sum,r)=>sum+r.result.totalScore,0)/rows.value.length).toFixed(1):'0'}))
async function load(){
  const requestId=++load.sequence, sessionKey=props.sessionKey
  busy.value=true
  try{const response=await props.api.get(props.admin?'/results':'/results/my',{params:props.admin?{userId:studentId.value||undefined,examId:examId.value||undefined}:undefined}); if(requestId===load.sequence&&sessionKey===props.sessionKey) {rows.value=response.data;selectedResultId.value=null}}
  catch(e){if(requestId===load.sequence&&sessionKey===props.sessionKey) emit('notice',e.response?.data?.message||'成绩加载失败。')}
  finally{if(requestId===load.sequence&&sessionKey===props.sessionKey) busy.value=false}
}
load.sequence=0
let analyticsRequest=0
async function loadAnalytics(){
  if(!examId.value){emit('notice','请先选择一场考试。'); return}
  const requestId=++analyticsRequest, sessionKey=props.sessionKey, selectedExam=examId.value
  analyticsBusy.value=true; analytics.value=null
  try{const response=await props.api.get('/results/analytics',{params:{examId:selectedExam}}); if(requestId===analyticsRequest&&sessionKey===props.sessionKey&&selectedExam===examId.value) analytics.value=response.data}
  catch(e){if(requestId===analyticsRequest&&sessionKey===props.sessionKey&&selectedExam===examId.value) emit('notice',e.response?.data?.message||'学情统计加载失败。')}
  finally{if(requestId===analyticsRequest&&sessionKey===props.sessionKey&&selectedExam===examId.value) analyticsBusy.value=false}
}
function viewStudentResults(userId){studentId.value=String(userId); load()}
onMounted(load)
watch(() => props.sessionKey, () => { rows.value=[]; analytics.value=null; analyticsRequest++; load() })
watch(examId, () => { analytics.value=null; analyticsRequest++ })
</script>
<template>
  <section>
    <form v-if="admin" class="filters" @submit.prevent="load"><label>学生编号<input v-model="studentId" type="number" min="1"></label><label>考试<select v-model="examId" aria-label="考试"><option value="">全部考试</option><option v-for="e in exams" :key="e.id" :value="e.id">{{e.name}}</option></select></label><button :disabled="busy">查询</button><button type="button" class="secondary" :disabled="analyticsBusy||!examId" @click="loadAnalytics">查看本场学情统计</button></form>
    <section v-if="admin&&analytics" class="result-section" aria-label="学情统计">
      <h2>{{analytics.examName}} · 学情统计</h2>
      <p>已开始 {{analytics.attemptCounts.started}} 份答卷 · 已结束 {{analytics.attemptCounts.completed}} 份 · 进行中 {{analytics.attemptCounts.inProgress}} 份 · 完成率 {{analytics.attemptCounts.completionRate}}%（已结束 / 已开始）</p>
      <p>主动交卷 {{analytics.attemptCounts.submitted}} · 超时收卷 {{analytics.attemptCounts.timedOut}} · 关闭收卷 {{analytics.attemptCounts.closed}}</p>
      <h3>题目通过情况</h3>
      <div class="table-wrap"><table><thead><tr><th>题目</th><th>满分</th><th>统计答卷</th><th>通过人数</th><th>未通过/未答人数</th><th>通过率</th></tr></thead><tbody><tr v-for="q in analytics.questionStats" :key="q.questionId"><td>{{q.title}}</td><td>{{q.maxScore}}</td><td>{{q.eligibleAttemptCount}}</td><td>{{q.passCount}}</td><td>{{q.notPassedCount}}</td><td>{{q.passRate===null?'暂无数据':`${q.passRate}%`}}</td></tr></tbody></table></div>
      <h3>需要进一步查看的学生</h3>
      <div class="table-wrap"><table><thead><tr><th>学生编号</th><th>姓名</th><th>总分</th><th>未通过题数</th><th>题目编号</th><th>操作</th></tr></thead><tbody><tr v-for="s in analytics.studentsToReview" :key="s.userId"><td>{{s.userId}}</td><td>{{s.realName||'未填写'}}</td><td>{{s.totalScore}}</td><td>{{s.notPassedCount}}</td><td>{{s.questionIds.join('、')}}</td><td><button class="secondary" @click="viewStudentResults(s.userId)">查看成绩明细</button></td></tr></tbody></table><p v-if="!analytics.studentsToReview.length">暂无需要进一步查看的学生</p></div>
    </section>
    <p v-if="analyticsBusy">学情统计加载中…</p>
    <div class="filters"><label class="check-label"><input v-model="wrongOnly" type="checkbox">仅显示错题 / 未答题</label><button class="secondary" :disabled="busy" @click="load">刷新成绩</button></div>
    <p v-if="admin">已交卷 {{stats.count}} 人次 · 平均分 {{stats.average}}</p><p v-if="busy">加载中…</p>
    <div v-if="admin && selectedResultId===null" class="table-wrap"><table><thead><tr><th>姓名</th><th>账号</th><th>学生编号</th><th>考试</th><th>总分</th><th>收卷时间（北京）</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.result.id"><td>{{row.student?.realName||'未填写'}}</td><td>{{row.student?.username||'账号不可用'}}</td><td>#{{row.result.userId}}</td><td>{{row.examName}}</td><td>{{row.result.totalScore}}</td><td>{{row.result.submitTime?.replace('T',' ')}}</td><td><button class="secondary" @click="selectedResultId=row.result.id">查看答卷</button></td></tr></tbody></table></div>
    <button v-if="admin && selectedResultId!==null" class="back" @click="selectedResultId=null">返回学生答卷列表</button>
    <article v-for="row in visibleRows" :key="row.result.id" class="result-section"><h2>{{row.examName}} · {{row.result.totalScore}} 分</h2><p>{{admin?(row.student?.realName||'未填写姓名'):'本人'}} · {{admin?(row.student?.username||'账号不可用'):''}} · 学生 #{{row.result.userId}} · 交卷 {{row.result.submitTime?.replace('T',' ')}}</p><div class="table-wrap"><table><thead><tr><th>题目</th><th>结果</th><th>得分</th><th>提交编号</th></tr></thead><tbody><tr v-for="d in row.details.filter(d=>!wrongOnly||d.score<d.maxScore)" :key="d.questionId"><td>{{d.title}}</td><td>{{d.result}}</td><td>{{d.score}} / {{d.maxScore}}</td><td><button v-if="d.submissionId" class="secondary" @click="emit('record',d.submissionId)">#{{d.submissionId}}</button><span v-else>未提交</span></td></tr></tbody></table></div></article>
    <p v-if="!busy&&!rows.length">暂无已交卷成绩</p>
  </section>
</template>
