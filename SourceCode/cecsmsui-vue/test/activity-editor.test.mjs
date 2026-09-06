import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { createPinia, defineStore } from 'pinia'
import { activityDateShortcut, getActivityDateError, isActivityDateDisabled } from '../src/utils/activity-date.js'

const storeSource = await readFile(new URL('../src/stores/activity.js', import.meta.url), 'utf8')

// Execute the actual store actions with real Pinia reactivity. Only transport
// and notifications are replaced, so deferred/error responses are controllable.
function createEditor(transport = {}) {
  const calls = []
  const notices = []
  const response = { code: 200, result: [], msg: '0' }
  const client = Object.fromEntries(['get', 'post', 'put'].map(method => [method, async (...args) => {
    calls.push({ method, args })
    return transport[method] ? transport[method](...args) : { data: response }
  }]))
  const notify = message => notices.push(message)
  notify.warning = message => notices.push({ type: 'warning', message })
  notify.error = message => notices.push({ type: 'error', message })
  const source = storeSource.replace(/^import .+$/gm, '')
    .replace('export const useActivityStore =', 'const useActivityStore =')
  const useStore = vm.runInNewContext(source + '\nuseActivityStore', {
    defineStore, $axios: client, ElMessage: notify
  }, { filename: 'src/stores/activity.js' })
  const store = useStore(createPinia())
  store.dialogFormVisible = true
  store.formdata = { id: 41, activityName: 'Date edit', activityDate: '2027-11-06', startTime: '09:00:00', endTime: '10:00:00' }
  return { store, calls, notices }
}

test('today is selectable for new activities and historical dates can be corrected when editing', () => {
  const now = new Date(2026, 8, 7, 15, 30)
  assert.equal(isActivityDateDisabled(new Date(2026, 8, 7), false, now), false)
  assert.equal(getActivityDateError('2026-09-07', false, now), '')
  assert.notEqual(getActivityDateError('2026-09-06', false, now), '')
  assert.equal(getActivityDateError('2026-09-06', true, now), '')
  assert.equal(isActivityDateDisabled(new Date(2024, 0, 1), true, now), false)
})

test('typed invalid dates cannot silently fall back to the old model date', () => {
  for (const value of ['', '2027-02-29', '2027-02-31', '2027-13-01', '2027-00-10', '2027/10/12', 'not-a-date']) {
    assert.notEqual(getActivityDateError(value, true), '', value)
  }
  assert.equal(getActivityDateError('2028-02-29', true), '')
  assert.equal(getActivityDateError('2027-10-12', true), '')
})

test('date shortcuts return actual calendar dates across a year boundary', () => {
  const next = activityDateShortcut(2, new Date(2026, 11, 31, 23, 50))
  assert.ok(next instanceof Date)
  assert.equal(next.getFullYear(), 2027)
  assert.equal(next.getMonth(), 0)
  assert.equal(next.getDate(), 2)
  assert.equal(next.getHours(), 0)
})

test('pending save keeps the draft and prevents duplicate submissions', async () => {
  let resolveRequest
  const pending = new Promise(resolve => { resolveRequest = resolve })
  const { store, calls } = createEditor({ post: () => pending })
  const save = store.save()
  assert.equal(store.saving, true)
  assert.equal(store.dialogFormVisible, true)
  assert.equal(store.formdata.id, 41)
  assert.equal(store.formdata.activityDate, '2027-11-06')
  assert.equal(await store.save(), false)
  assert.equal(calls.filter(call => call.method === 'post').length, 1)
  assert.equal(calls[0].args[1].activityDate, '2027-11-06')
  resolveRequest({ data: { code: 200 } })
  assert.equal(await save, true)
  assert.equal(store.saving, false)
  assert.equal(store.dialogFormVisible, false)
  assert.equal(store.formdata.id, -1)
})

test('a rejected save keeps the edited date and retries the same activity ID', async () => {
  let attempts = 0
  const { store, calls } = createEditor({ post: () => ({ data: ++attempts === 1
    ? { code: 422, msg: '结束时间必须晚于开始时间' }
    : { code: 200 } }) })
  assert.equal(await store.save(), false)
  assert.equal(store.dialogFormVisible, true)
  assert.equal(store.formdata.id, 41)
  assert.equal(store.formdata.activityDate, '2027-11-06')
  assert.equal(store.saveError, '结束时间必须晚于开始时间')
  store.formdata.endTime = '11:00:00'
  assert.equal(await store.save(), true)
  const writes = calls.filter(call => call.method !== 'get')
  assert.deepEqual(writes.map(call => [call.method, call.args[0], call.args[1].id]), [
    ['post', '/activity/update', 41], ['post', '/activity/update', 41]
  ])
  assert.equal(writes[1].args[1].endTime, '11:00:00')
})

test('network errors preserve the draft and release the save lock', async () => {
  const { store } = createEditor({ post: () => { throw new Error('Network unavailable') } })
  assert.equal(await store.save(), false)
  assert.equal(store.saving, false)
  assert.equal(store.dialogFormVisible, true)
  assert.equal(store.formdata.activityDate, '2027-11-06')
  assert.equal(store.saveError, 'Network unavailable')
})

test('successful save refreshes the active search so the edited activity stays in the result', async () => {
  const row = { id: 41, activityDate: '2027-11-06' }
  const { store, calls } = createEditor({ get: () => ({ data: { code: 200, result: [row], msg: '1' } }) })
  store.aName = 'Date edit'
  assert.equal(await store.save(), true)
  assert.equal(calls.find(call => call.method === 'get').args[0], '/activity/selectByNameByPage/Date edit/1/3')
  assert.equal(store.tableData[0].activityDate, '2027-11-06')
  assert.equal(store.total, 1)
})

test('a refresh failure does not report the already committed update as a failed save', async () => {
  const { store, notices } = createEditor({ get: () => { throw new Error('Refresh failed') } })
  assert.equal(await store.save(), true)
  assert.equal(store.dialogFormVisible, false)
  assert.equal(store.saveError, '')
  assert.ok(notices.some(notice => notice.type === 'warning'))
})

test('opening a new activity after editing clears the old ID and draft', async () => {
  const { store, calls } = createEditor()
  store.preInfo4Add()
  assert.equal(store.formdata.id, -1)
  assert.equal(store.formdata.activityDate, undefined)
  store.formdata.activityDate = '2027-12-06'
  assert.equal(await store.save(), true)
  assert.equal(calls.filter(call => call.method === 'post').length, 0)
  assert.equal(calls.find(call => call.method === 'put').args[0], '/activity/insert')
})
