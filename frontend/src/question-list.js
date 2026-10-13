export const QUESTION_PAGE_SIZE = 50

export function filterQuestions(questions, { keyword = '', category = '', difficulty = '', status = '' } = {}) {
  const term = keyword.trim().toLocaleLowerCase()
  return questions.filter(question => {
    const textMatches = !term || `${question.title || ''}\n${question.description || ''}`.toLocaleLowerCase().includes(term)
    return textMatches && (!category || question.category === category) &&
      (!difficulty || question.difficulty === difficulty) && (!status || question.status === status)
  })
}

export function paginate(items, page, pageSize = QUESTION_PAGE_SIZE) {
  const pageCount = Math.max(1, Math.ceil(items.length / pageSize))
  const currentPage = Math.min(Math.max(1, page), pageCount)
  return { items: items.slice((currentPage - 1) * pageSize, currentPage * pageSize), currentPage, pageCount }
}
