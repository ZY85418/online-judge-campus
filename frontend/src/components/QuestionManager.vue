<script setup>
import { computed, ref, watch } from 'vue'
import { filterQuestions, paginate, QUESTION_PAGE_SIZE } from '../question-list.js'
const props = defineProps({ api: [Object, Function], questions: Array })
const emit = defineEmits(['saved', 'notice'])
const blank = () => ({ title: '', category: '基础算法', difficulty: '简单', score: 100, description: '', sampleInput: '', sampleOutput: '', standardAnswer: '', status: 'PUBLISHED' })
const form = ref(blank()), editing = ref(null), busy = ref(false)
const keyword=ref(''), category=ref(''), difficulty=ref(''), status=ref(''), page=ref(1), editingMode=ref(false)
const categories=computed(()=>[...new Set(props.questions.map(q=>q.category).filter(Boolean))])
const appliedFilters=ref({keyword:'',category:'',difficulty:'',status:''})
function query(){appliedFilters.value={keyword:keyword.value,category:category.value,difficulty:difficulty.value,status:status.value};page.value=1}
const filtered=computed(()=>filterQuestions(props.questions,appliedFilters.value))
const pageData=computed(()=>paginate(filtered.value,page.value))

function edit(q) { editing.value=q.id; form.value={ ...q }; editingMode.value=true }
function create() { editing.value=null; form.value=blank(); editingMode.value=true }
function cancel() { editing.value=null; form.value=blank(); editingMode.value=false }
async function save() {
  if (busy.value) return
  busy.value=true
  try {
    await props.api.request({ url: editing.value ? `/questions/${editing.value}` : '/questions', method: editing.value ? 'put' : 'post', data: form.value })
    editingMode.value=false; editing.value=null; form.value=blank(); emit('notice','题目已保存。'); emit('saved')
  } catch (e) { emit('notice',e.response?.data?.message || '保存失败，请检查连接。') }
  finally { busy.value=false }
}
async function remove(q) {
  if (busy.value || !window.confirm(`删除题目“${q.title}”？`)) return
  busy.value=true
  try { await props.api.delete(`/questions/${q.id}`); if(editing.value===q.id) cancel(); emit('notice','题目已删除。'); emit('saved') }
  catch(e) { emit('notice',e.response?.data?.message || '删除失败，请检查连接。') }
  finally { busy.value=false }
}
</script>
<template>
  <section class="manage">
    <div v-if="!editingMode" class="question-manager-list">
      <div class="manager-heading"><div><h2>题目列表</h2><p>共 {{filtered.length}} 道题目，每页 {{QUESTION_PAGE_SIZE}} 道</p></div><button @click="create">新建题目</button></div>
      <form class="filters" @submit.prevent="query"><label>搜索题目<input v-model="keyword" type="search" placeholder="搜索标题或题目描述"></label><label>分类<select v-model="category"><option value="">全部分类</option><option v-for="item in categories" :key="item">{{item}}</option></select></label><label>难度<select v-model="difficulty"><option value="">全部难度</option><option>简单</option><option>中等</option><option>困难</option></select></label><label>状态<select v-model="status"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="CLOSED">已关闭</option></select></label><button>查询</button><button type="button" v-if="keyword||category||difficulty||status" class="secondary" @click="keyword='';category='';difficulty='';status='';query()">清除条件</button></form>
      <div class="table-wrap"><table><thead><tr><th>题号</th><th>题目</th><th>分类</th><th>难度</th><th>分值</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="q in pageData.items" :key="q.id"><td>#{{q.id}}</td><td><strong>{{q.title}}</strong><p class="question-description">{{q.description}}</p></td><td>{{q.category}}</td><td>{{q.difficulty}}</td><td>{{q.score}}</td><td>{{q.status==='PUBLISHED'?'已发布':q.status==='DRAFT'?'草稿':'已关闭'}}</td><td><div class="actions"><button class="secondary" @click="edit(q)">编辑</button><button class="secondary" :disabled="busy" @click="remove(q)">删除</button></div></td></tr></tbody></table><p v-if="!filtered.length">没有符合条件的题目</p><div v-if="pageData.pageCount>1" class="pagination"><button class="secondary" :disabled="pageData.currentPage===1" @click="page--">上一页</button><span>第 {{pageData.currentPage}} / {{pageData.pageCount}} 页</span><button class="secondary" :disabled="pageData.currentPage===pageData.pageCount" @click="page++">下一页</button></div></div>
    </div>
    <form v-else @submit.prevent="save"><button type="button" class="back" @click="cancel">返回题目列表</button><h2>{{ editing ? '编辑题目' : '新建题目' }}</h2>
      <div class="form-grid">
        <label>题目标题<input v-model="form.title" maxlength="200" required></label>
        <label>分类<input v-model="form.category" maxlength="50" required></label>
        <label>难度<select v-model="form.difficulty" aria-label="难度"><option>简单</option><option>中等</option><option>困难</option></select></label>
        <label>分值<input v-model.number="form.score" type="number" min="1" max="1000" required></label>
        <label>状态<select v-model="form.status" aria-label="状态"><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="CLOSED">已关闭</option></select></label>
      </div>
      <label>题目描述<textarea v-model="form.description" required></textarea></label>
      <label>样例输入（可选）<textarea v-model="form.sampleInput" maxlength="60000" placeholder="填写题面示例输入"></textarea></label>
      <label>样例输出（可选）<textarea v-model="form.sampleOutput" maxlength="60000" placeholder="填写对应的预期输出"></textarea></label>
      <label>答案关键片段<textarea v-model="form.standardAnswer" required></textarea></label>
      <div class="actions"><button v-if="editing" type="button" class="secondary" :disabled="busy" @click="cancel">取消编辑</button><button :disabled="busy">{{ busy ? '保存中…' : '保存题目' }}</button></div>
    </form>
  </section>
</template>
