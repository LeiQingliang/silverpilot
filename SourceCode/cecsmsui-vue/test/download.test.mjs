import test from 'node:test'
import assert from 'node:assert/strict'
import { filenameFromContentDisposition, sanitizeDownloadFilename, saveBlobResponse } from '../src/utils/download.js'

test('download filenames decode UTF-8 headers and remove unsafe path characters', () => {
  assert.equal(filenameFromContentDisposition("attachment; filename*=UTF-8''%E6%8A%A5%E5%90%8D%E5%90%8D%E5%8D%95.xlsx"), '报名名单.xlsx')
  assert.equal(sanitizeDownloadFilename('../名单<>.xlsx'), '.._名单__.xlsx')
  assert.equal(sanitizeDownloadFilename('', '名单.xlsx'), '名单.xlsx')
})

test('blob downloads click one temporary link and always revoke the object URL', async () => {
  const events = []
  const anchor = { hidden: false, click: () => events.push('click'), remove: () => events.push('remove') }
  const documentRef = {
    createElement: (name) => {
      assert.equal(name, 'a')
      return anchor
    },
    body: { appendChild: (node) => { assert.equal(node, anchor); events.push('append') } }
  }
  const urlApi = {
    createObjectURL: (blob) => { assert.equal(blob.size, 3); events.push('create'); return 'blob:test' },
    revokeObjectURL: (url) => { assert.equal(url, 'blob:test'); events.push('revoke') }
  }
  const filename = await saveBlobResponse({
    status: 200,
    data: new Blob(['abc']),
    headers: { 'content-disposition': 'attachment; filename="report.xlsx"' }
  }, 'fallback.xlsx', { documentRef, urlApi })

  assert.equal(filename, 'report.xlsx')
  assert.equal(anchor.href, 'blob:test')
  assert.equal(anchor.download, 'report.xlsx')
  assert.deepEqual(events, ['create', 'append', 'click', 'remove', 'revoke'])
})

test('JSON and empty download responses fail before creating a browser link', async () => {
  await assert.rejects(
    saveBlobResponse({ status: 200, data: new Blob([], { type: 'application/octet-stream' }), headers: {} }, 'empty.xlsx'),
    /空文件/
  )
  await assert.rejects(
    saveBlobResponse({ status: 200, data: new Blob(['{"msg":"没有权限"}'], { type: 'application/json' }), headers: { 'content-type': 'application/json' } }, 'error.xlsx'),
    /没有权限/
  )
})
