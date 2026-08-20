import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/components/front/myActivity/MyActivityView.vue', import.meta.url),
  'utf8'
)

test('activity cancellation popconfirm always receives a concrete reference child', () => {
  assert.match(
    source,
    /<el-popconfirm\s+v-if="scope\.row\.myState === '报名成功' && scope\.row\.aState !== '已结束'"/
  )
  assert.doesNotMatch(
    source,
    /<template #reference>\s*<el-button[^>]*\bv-if=/,
    'a conditional reference child makes Element Plus render ElOnlyChild warnings'
  )
})

test('both desktop cancellation actions pass the row instead of the table index', () => {
  const rowConfirmHandlers = source.match(/@confirm="toCancel\(scope\.row\)"/g) || []
  assert.equal(rowConfirmHandlers.length, 2)
  assert.doesNotMatch(source, /toCancel\(scope\.\$index/)
})
