<script setup>
import { computed, ref, watch } from 'vue'
import { examFormFromRecord, examPayload, validateExamTime } from '../exam-time.js'
const props=defineProps({ api:[Object, Function], exams:Array, questions:Array })
const emit=defineEmits(['saved','notice'])
const PAGE_SIZE=10
const blank=()=>({ name:'', startTime:'', endTime:'', duration:90, status:'DRAFT', questionIds:[] })
const form=ref(blank()), editing=ref(null), busy=ref(false), timeError=ref('')
const keyword=ref(''), statusFilter=ref(''), page=ref(1), editingMode=ref(false)
const appliedFilters=ref({keyword:'',status:''})
function query(){appliedFilters.value={keyword:keyword.value,status:statusFilter.value};page.value=1}
const filtered=computed(()=>{const text=appliedFilters.value.keyword.trim().toLowerCase(); return (props.exams||[]).filter(e=>(!text||String(e.name||'').toLowerCase().includes(text)||String(e.id).includes(text))&&(!appliedFilters.value.status||e.status===appliedFilters.value.status))})
const pageCount=computed(()=>Math.max(1,Math.ceil(filtered.value.length/PAGE_SIZE)))
const pageItems=computed(()=>filtered.value.slice((page.value-1)*PAGE_SIZE,page.value*PAGE_SIZE))

watch(pageCount,v=>{if(page.value>v) page.value=v})
function statusLabel(s){return s==='PUBLISHED'?'已发布':s==='CLOSED'?'已关闭':'草稿'}
function canEdit(e){
  if(e.status==='DRAFT') return true
  if(e.status!=='PUBLISHED' || !e.endTime) return false
  return Date.now() >= new Date(`${e.endTime}+08:00`).getTime()
}
function edit(e){editing.value=e.id;timeError.value='';form.value=examFormFromRecord(e);editingMode.value=true}
function create(){editing.value=null;timeError.value='';form.value=blank();editingMode.value=true}
function cancel(){editing.value=null;timeError.value='';form.value=blank();editingMode.value=false}
async function save(){if(busy.value)return;timeError.value=validateExamTime(form.value);if(timeError.value)return;busy.value=true;try{const data=examPayload(form.value);await props.api.request({url:editing.value?`/exams/${editing.value}`:'/exams',method:editing.value?'put':'post',data});cancel();emit('notice','考试已保存。');emit('saved')}catch(e){emit('notice',e.response?.data?.message||'保存失败，请检查连接。')}finally{busy.value=false}}
async function close(e){if(busy.value||!window.confirm(`关闭“${e.name}”并收取所有进行中的答卷？`))return;busy.value=true;try{await props.api.post(`/exams/${e.id}/close`);emit('notice','考试已关闭。');emit('saved')}catch(e){emit('notice',e.response?.data?.message||'关闭失败。')}finally{busy.value=false}}
</script>
<template>
  <section class="manage">
    <div v-if="!editingMode" class="exam-manager-list">
      <div class="manager-heading"><div><h2>考试列表</h2><p>共 {{filtered.length}} 场考试，每页 {{PAGE_SIZE}} 场</p></div><button @click="create">新建考试</button></div>
      <form class="filters" @submit.prevent="query"><label>搜索考试<input v-model="keyword" type="search" placeholder="搜索考试名称或编号"></label><label>状态<select v-model="statusFilter"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="CLOSED">已关闭</option></select></label><button>查询</button><button type="button" v-if="keyword||statusFilter" class="secondary" @click="keyword='';statusFilter='';query()">清除条件</button></form>
      <div class="table-wrap"><table><thead><tr><th>编号</th><th>考试名称</th><th>开放时间（北京）</th><th>个人时长</th><th>状态</th><th>题目数</th><th>操作</th></tr></thead><tbody><tr v-for="e in pageItems" :key="e.id"><td>#{{e.id}}</td><td><strong>{{e.name}}</strong></td><td>{{e.startTime?.replace('T',' ')}}<br>{{e.endTime?.replace('T',' ')}}</td><td>{{e.duration}} 分钟</td><td>{{statusLabel(e.status)}}</td><td>{{e.questionIds?e.questionIds.split(',').filter(Boolean).length:0}}</td><td><div class="actions"><button class="secondary" :disabled="busy || !canEdit(e)" :title="canEdit(e) ? '编辑考试' : '考试已发布但尚未结束，或已有答卷记录'" @click="edit(e)">编辑</button><button v-if="e.status!=='CLOSED'" class="secondary" :disabled="busy" @click="close(e)">关闭</button><span v-if="e.status==='CLOSED'">已结束</span></div></td></tr></tbody></table><p v-if="!filtered.length">没有符合条件的考试</p><div v-if="pageCount>1" class="pagination"><button class="secondary" :disabled="page===1" @click="page--">上一页</button><span>第 {{page}} / {{pageCount}} 页</span><button class="secondary" :disabled="page===pageCount" @click="page++">下一页</button></div></div>
    </div>
    <form v-else @submit.prevent="save"><button type="button" class="back" @click="cancel">返回考试列表</button><h2>{{editing?'编辑草稿考试':'新建考试'}}</h2><div class="form-grid"><label>考试名称<input v-model="form.name" maxlength="200" required></label><label>开放开始时间（北京时间）<input v-model="form.startTime" type="datetime-local" step="1" required></label><label>开放结束时间（北京时间）<input v-model="form.endTime" type="datetime-local" step="1" required></label><label>个人答题时长（分钟）<input v-model.number="form.duration" type="number" min="1" max="1440" required></label><label>状态<select v-model="form.status"><option value="DRAFT">草稿</option><option value="PUBLISHED">发布</option></select></label></div><p class="form-hint">开放时间窗口与个人答题时长分别设置。个人截止时间取“个人实际开始时间 + 答题时长”和“开放结束时间”两者中较早的时间。</p><p v-if="timeError" class="error">{{timeError}}</p><fieldset><legend>考试题目</legend><label v-for="q in (questions||[]).filter(q=>q.status==='PUBLISHED')" :key="q.id" class="check-label"><input v-model="form.questionIds" :value="q.id" type="checkbox">#{{q.id}} {{q.title}} · {{q.score}} 分</label></fieldset><div class="actions"><button v-if="editing" type="button" class="secondary" :disabled="busy" @click="cancel">取消编辑</button><button :disabled="busy">{{busy?'保存中…':'保存考试'}}</button></div></form>
  </section>
</template>
