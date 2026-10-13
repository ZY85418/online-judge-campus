<script setup>
import { computed, ref, watch } from 'vue'
const props=defineProps({api:[Object,Function],exams:Array})
const emit=defineEmits(['notice','record'])
const keyword=ref(''), userId=ref(''), status=ref(''), page=ref(1), pageSize=50, data=ref({items:[],total:0}), busy=ref(false), selected=ref(null), detailBusy=ref(false)
const appliedFilters=ref({keyword:'',userId:'',status:''})
function query(){appliedFilters.value={keyword:keyword.value.trim(),userId:userId.value,status:status.value};if(page.value!==1) page.value=1;else load()}
const pageCount=computed(()=>Math.max(1,Math.ceil(data.value.total/pageSize)))
async function load(){busy.value=true; try{const response=await props.api.get('/users/students',{params:{keyword:appliedFilters.value.keyword||undefined,userId:appliedFilters.value.userId||undefined,status:appliedFilters.value.status||undefined,page:page.value,pageSize}}); data.value=response.data; if(page.value>pageCount.value){page.value=pageCount.value; await load()}}catch(e){emit('notice',e.response?.data?.message||'学生列表加载失败。')}finally{busy.value=false}}
async function open(id){detailBusy.value=true; try{selected.value=(await props.api.get(`/users/students/${id}`)).data}catch(e){emit('notice',e.response?.data?.message||'学生详情加载失败。')}finally{detailBusy.value=false}}
function reset(){keyword.value='';userId.value='';status.value='';query()}

watch(page,()=>load())
load()
</script>
<template>
  <section v-if="!selected">
    <form class="filters" @submit.prevent="query"><label>搜索学生<input v-model="keyword" placeholder="用户名或姓名"></label><label>学生编号<input v-model="userId" type="number" min="1"></label><label>状态<select v-model="status"><option value="">全部状态</option><option value="ACTIVE">正常</option><option value="INACTIVE">停用</option></select></label><button :disabled="busy">查询</button><button type="button" class="secondary" @click="reset">清除</button></form>
    <p>共 {{data.total}} 名学生，每页 {{pageSize}} 名</p><div class="table-wrap"><table><thead><tr><th>编号</th><th>用户名</th><th>姓名</th><th>状态</th><th>注册时间</th><th>完成考试</th><th>最近活动</th><th>操作</th></tr></thead><tbody><tr v-for="student in data.items" :key="student.id"><td>#{{student.id}}</td><td>{{student.username}}</td><td>{{student.realName||'未填写'}}</td><td>{{student.status==='ACTIVE'?'正常':'停用'}}</td><td>{{student.createTime?.replace('T',' ')||'—'}}</td><td>{{student.completedExamCount}}</td><td>{{student.recentActivityTime?.replace('T',' ')||'—'}}</td><td><button class="secondary" @click="open(student.id)">查看详情</button></td></tr></tbody></table><p v-if="!busy&&!data.items.length">没有符合条件的学生</p><p v-if="busy">加载中…</p></div><div v-if="pageCount>1" class="pagination"><button class="secondary" :disabled="page===1" @click="page--">上一页</button><span>第 {{page}} / {{pageCount}} 页</span><button class="secondary" :disabled="page===pageCount" @click="page++">下一页</button></div>
  </section>
  <section v-else class="student-detail"><button class="back" @click="selected=null">返回学生列表</button><h2>{{selected.realName||selected.username}}</h2><div class="profile-summary"><p>编号：#{{selected.id}}</p><p>用户名：{{selected.username}}</p><p>角色：学生</p><p>状态：{{selected.status==='ACTIVE'?'正常':'停用'}}</p><p>注册时间：{{selected.createTime?.replace('T',' ')||'—'}}</p></div><h3>考试记录</h3><div class="table-wrap"><table><thead><tr><th>考试编号</th><th>答卷状态</th><th>开始时间</th><th>截止时间</th></tr></thead><tbody><tr v-for="item in selected.attempts" :key="item.id"><td>#{{item.examId}}</td><td>{{item.status}}</td><td>{{item.startTime?.replace('T',' ')}}</td><td>{{item.deadline?.replace('T',' ')}}</td></tr></tbody></table><p v-if="!selected.attempts.length">暂无考试记录</p></div><h3>最近提交</h3><div class="table-wrap"><table><thead><tr><th>提交编号</th><th>考试</th><th>题目</th><th>结果</th><th>得分</th><th>时间</th><th></th></tr></thead><tbody><tr v-for="item in selected.recentSubmissions" :key="item.id"><td>#{{item.id}}</td><td>{{item.examId?`#${item.examId}`:'练习'}}</td><td>#{{item.questionId}}</td><td>{{item.result}}</td><td>{{item.score}}</td><td>{{item.submitTime?.replace('T',' ')}}</td><td><button class="secondary" @click="emit('record',item.id)">查看代码</button></td></tr></tbody></table><p v-if="!selected.recentSubmissions.length">暂无提交记录</p></div></section>
</template>
