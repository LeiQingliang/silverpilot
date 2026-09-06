import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { computed, ref } from 'vue'
import { commentDateTime, compareCommentTimes, formatCommentDateTime, formatCommentTime, parseCommentTime, sortComments } from '../src/utils/comment-time.js'

test('database wall-clock and offset timestamps identify the same Beijing time', () => {
  const instant = Date.parse('2027-04-20T12:41:55Z')
  for (const value of ['2027-04-20 20:41:55', '2027-04-20T20:41:55', '2027-04-20T20:41:55+08:00', '2027-04-20T12:41:55Z']) {
    assert.equal(parseCommentTime(value), instant)
    assert.equal(formatCommentDateTime(value), '2027-04-20 20:41:55')
    assert.equal(commentDateTime(value), '2027-04-20T12:41:55.000Z')
  }
  assert.equal(parseCommentTime('2027-04-20T20:41:55.123456'), instant + 123)
})

test('future records show their actual timestamp instead of just now', () => {
  const now = Date.parse('2026-09-07T04:00:00+08:00')
  assert.equal(formatCommentTime('2027-04-20 20:41:55', now), '2027-04-20 20:41:55（未来时间）')
  assert.equal(formatCommentTime('2026-09-07 04:00:00', now), '2026-09-07 04:00:00')
  assert.equal(formatCommentTime('2026-09-07 03:59:30', now), '2026-09-07 03:59:30')
})

test('invalid dates never normalize to a different real date', () => {
  for (const value of [null, '', 'unknown', '2027-02-29 12:00:00', '2027-02-31 12:00:00', '2027-13-01 12:00:00', '2027-04-20 24:00:00', '2027-04-20T12:00:00+08:99']) {
    assert.equal(parseCommentTime(value), null, String(value))
    assert.equal(formatCommentTime(value), '时间未知')
  }
  assert.notEqual(parseCommentTime('2028-02-29 12:00:00'), null)
})

test('time and reply sorts are stable across ties and never mutate API records', () => {
  const records = Object.freeze([
    { id: 3, createTime: '2027-01-02 08:00:00', replies: [1] },
    { id: 1, createTime: '2026-12-31 23:59:59', replies: [1, 2] },
    { id: 5, createTime: '2027-01-02T00:00:00Z', replies: [1] },
    { id: 8, createTime: null, replies: [] }
  ].map(Object.freeze))
  assert.deepEqual(sortComments(records).map(row => row.id), [5, 3, 1, 8])
  assert.deepEqual(sortComments(records, 'oldest').map(row => row.id), [1, 3, 5, 8])
  assert.deepEqual(sortComments(records, 'most_replies').map(row => row.id), [1, 5, 3, 8])
  assert.deepEqual(records.map(row => row.id), [3, 1, 5, 8])
  assert.equal(compareCommentTimes(records[0], records[2], true), -2)
})

async function componentState(relativePath, response, names) {
  const component = await readFile(new URL(relativePath, import.meta.url), 'utf8')
  const script = component.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .+$/gm, '')
  return vm.runInNewContext(script + `\n;({${names}})`, {
    ref, computed, sortComments, formatTime: formatCommentTime,
    onMounted() {}, useRouter: () => ({}),
    axios: { get: async () => ({ data: { code: 200, result: response() } }) },
    ElMessage: { error(message) { throw new Error(message) } },
    sessionStorage: { getItem: key => key === 'user' ? '{"id":17,"roleId":4}' : 'test-session' }
  })
}

test('the real forum component reapplies the selected ordering after fetching fresh records', async () => {
  let response = [
    { id: 2, createTime: '2027-01-02 00:00:00', replies: [] },
    { id: 1, createTime: '2026-01-01 00:00:00', replies: [1] }
  ]
  const state = await componentState('../src/components/front/forum/ForumHomeView.vue', () => response, 'sortBy,sortedComments,fetchComments')
  state.sortBy.value = 'oldest'
  await state.fetchComments()
  assert.deepEqual(Array.from(state.sortedComments.value, row => row.id), [1, 2])
  response = [...response, { id: 3, createTime: '2026-06-01 00:00:00', replies: [1, 2] }]
  await state.fetchComments()
  assert.equal(state.sortBy.value, 'oldest')
  assert.deepEqual(Array.from(state.sortedComments.value, row => row.id), [1, 3, 2])
  state.sortBy.value = 'most_replies'
  await state.fetchComments()
  assert.deepEqual(Array.from(state.sortedComments.value, row => row.id), [3, 1, 2])
})

test('my replies are read from nested API records and ordered by their own timestamps', async () => {
  const response = [{ id: 1, userId: 99, createTime: '2027-01-01 00:00:00', replies: [
    { id: 2, parentId: 1, userId: 17, createTime: '2027-01-02 00:00:00' },
    { id: 3, parentId: 1, userId: 17, createTime: '2027-01-03 00:00:00' }
  ] }]
  const state = await componentState('../src/components/front/myComment/MyCommentView.vue', () => response, 'myReplies,fetchComments')
  await state.fetchComments()
  assert.deepEqual(Array.from(state.myReplies.value, row => row.id), [3, 2])
})
