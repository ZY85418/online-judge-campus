import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { ref, computed, watch, nextTick } from 'vue'
import { filterQuestions, paginate, QUESTION_PAGE_SIZE } from '../src/question-list.js'
function component(file, props) {
  const source=readFileSync(new URL('../src/components/'+file, import.meta.url),'utf8')
  const script=source.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
  const context=vm.createContext({ref,computed,watch,defineProps:()=>props,defineEmits:()=>()=>{},filterQuestions,paginate,QUESTION_PAGE_SIZE,onMounted:()=>{}})
  vm.runInContext(script,context)
  return expression=>vm.runInContext(expression,context)
}
test('question filters and page change only when query is applied', async()=>{
  const run=component('QuestionManager.vue',{questions:[{id:1,category:'基础'},{id:2,category:'算法'}]})
  run("page.value=2;category.value='算法'")
  await nextTick()
  assert.equal(run('page.value'),2)
  assert.equal(run('filtered.value.length'),2)
  run('query()')
  assert.equal(run('page.value'),1)
  assert.equal(run('filtered.value[0].id'),2)
})
test('exam search and status are applied together only on query',()=>{
  const run=component('ExamManager.vue',{exams:[{id:1,name:'期末',status:'DRAFT'},{id:2,name:'期末',status:'PUBLISHED'}]})
  run("keyword.value='期末';statusFilter.value='DRAFT'")
  assert.equal(run('filtered.value.length'),2)
  run('query()')
  assert.equal(run('filtered.value.length'),1)
  run("statusFilter.value='PUBLISHED'")
  assert.equal(run('filtered.value[0].id'),1)
})
test('student filters do not issue requests until query and pagination uses applied filters',async()=>{
  const calls=[]
  const run=component('StudentManager.vue',{api:{get:async(url,options)=>{calls.push(options.params);return {data:{items:[],total:120}}}}})
  await nextTick()
  const count=calls.length
  run("keyword.value='甲';status.value='ACTIVE'")
  await nextTick()
  assert.equal(calls.length,count)
  run('query()')
  await nextTick()
  assert.equal(calls.at(-1).keyword,'甲')
  run("keyword.value='乙';page.value=2")
  await nextTick()
  assert.equal(calls.at(-1).keyword,'甲')
})
test('administrator opens one students answer sheet and returns without losing rows',()=>{
  const run=component('ResultsPanel.vue',{admin:true,sessionKey:'one',api:{},exams:[]})
  run('rows.value=[{result:{id:1,userId:11}},{result:{id:2,userId:22}}]')
  assert.equal(run('visibleRows.value.length'),0)
  run('selectedResultId.value=2')
  assert.equal(run('visibleRows.value[0].result.userId'),22)
  run('selectedResultId.value=null')
  assert.equal(run('rows.value.length'),2)
})
