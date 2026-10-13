export const initialCode = 'public class Main {\n  public static void main(String[] args) {\n    // 在此编写代码\n  }\n}'

export function createDraftStore(storage) {
  const key = (userId, questionId) => `oj-draft:${userId}:${questionId}`
  return {
    load(userId, questionId) {
      try { return storage.getItem(key(userId, questionId)) ?? initialCode }
      catch { return initialCode }
    },
    save(userId, questionId, code) {
      try { storage.setItem(key(userId, questionId), code); return true }
      catch { return false }
    }
  }
}
