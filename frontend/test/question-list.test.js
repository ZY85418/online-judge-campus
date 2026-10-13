import test from 'node:test'
import assert from 'node:assert/strict'
import { filterQuestions, paginate, QUESTION_PAGE_SIZE } from '../src/question-list.js'

const questions = Array.from({length: 51}, (_, i) => ({ id:i + 1, title:`题目 ${i + 1}`, description:i === 50 ? '特殊描述关键词' : '普通说明', category:i % 2 ? '算法' : '基础', difficulty:i % 2 ? '中等' : '简单', status:i % 3 ? 'PUBLISHED' : 'DRAFT' }))

test('search matches title or description and combines filters', () => {
  assert.equal(filterQuestions(questions, {keyword:'  特殊描述关键词 '}).length, 1)
  assert.equal(filterQuestions(questions, {keyword:'题目', category:'基础', difficulty:'简单'}).length, 26)
  assert.equal(filterQuestions(questions, {keyword:'题目', status:'PUBLISHED'}).length, 34)
})

test('pagination uses 50 items and clamps page', () => {
  const first = paginate(questions, 1)
  assert.equal(QUESTION_PAGE_SIZE, 50); assert.equal(first.items.length, 50); assert.equal(first.pageCount, 2)
  assert.equal(paginate(questions, 2).items.length, 1)
  assert.equal(paginate(questions, 99).currentPage, 2)
})
