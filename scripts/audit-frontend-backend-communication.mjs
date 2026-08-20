import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { createHmac, randomUUID } from 'node:crypto'
import { existsSync, unlinkSync } from 'node:fs'
import { dirname, join, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptRoot = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptRoot, '..')
const baseUrl = (process.env.CECSMS_QA_BASE_URL || 'http://127.0.0.1:8081').replace(/\/$/, '')
const jwtSecret = process.env.CECSMS_QA_JWT_SECRET || process.env.SILVERPILOT_JWT_SECRET || ''
const mcpApiKey = process.env.CECSMS_QA_MCP_API_KEY || process.env.SILVERPILOT_MCP_API_KEY || ''
const normalUserId = Number(process.env.CECSMS_QA_USER_ID || 17)
const adminUserId = Number(process.env.CECSMS_QA_ADMIN_ID || 1)
const allowLocalUploadWrite = process.env.CECSMS_QA_UPLOAD_WRITE === 'true'
const dockerBackendContainer = process.env.CECSMS_QA_DOCKER_CONTAINER || ''
const runLiveReportAnalysis = process.env.CECSMS_QA_LIVE_REPORT_AI === 'true'

if (jwtSecret.length < 32) {
  throw new Error('Set CECSMS_QA_JWT_SECRET or SILVERPILOT_JWT_SECRET to the same 32+ character local JWT secret used by the backend.')
}
if (!mcpApiKey) {
  throw new Error('Set CECSMS_QA_MCP_API_KEY or SILVERPILOT_MCP_API_KEY before running the communication audit.')
}
if (!Number.isInteger(normalUserId) || normalUserId <= 0 || !Number.isInteger(adminUserId) || adminUserId <= 0) {
  throw new Error('CECSMS_QA_USER_ID and CECSMS_QA_ADMIN_ID must be positive integers.')
}
if (dockerBackendContainer && !/^[a-zA-Z0-9_.-]+$/.test(dockerBackendContainer)) {
  throw new Error('CECSMS_QA_DOCKER_CONTAINER contains unsupported characters.')
}

const results = []
let uploadedFile = null
let uploadedContainerFile = null

const encodeJson = (value) => Buffer.from(JSON.stringify(value)).toString('base64url')
const buildToken = (userId) => {
  const now = Math.floor(Date.now() / 1000)
  const header = encodeJson({ alg: 'HS256', typ: 'JWT' })
  const payload = encodeJson({
    iss: 'cecsms-serve',
    sub: String(userId),
    aud: String(userId),
    iat: now,
    exp: now + 1800
  })
  const unsigned = `${header}.${payload}`
  const signature = createHmac('sha256', jwtSecret).update(unsigned).digest('base64url')
  return `${unsigned}.${signature}`
}

const userToken = buildToken(normalUserId)
const adminToken = buildToken(adminUserId)

const request = async (path, options = {}) => {
  const {
    method = 'GET',
    token,
    headers = {},
    body,
    expectedHttp = [200],
    responseType = 'json',
    timeout = 15_000
  } = options
  const requestHeaders = { Accept: responseType === 'json' ? 'application/json' : '*/*', ...headers }
  if (token) requestHeaders.Authorization = `Bearer ${token}`

  let requestBody = body
  if (body !== undefined && !(body instanceof FormData) && !(body instanceof URLSearchParams)) {
    requestHeaders['Content-Type'] = requestHeaders['Content-Type'] || 'application/json'
    requestBody = requestHeaders['Content-Type'].includes('application/json') ? JSON.stringify(body) : body
  }
  if (body instanceof URLSearchParams) {
    requestHeaders['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8'
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: requestHeaders,
    body: requestBody,
    signal: AbortSignal.timeout(timeout),
    redirect: 'manual'
  })
  const bytes = Buffer.from(await response.arrayBuffer())
  assert.ok(expectedHttp.includes(response.status), `${method} ${path} returned HTTP ${response.status}, expected ${expectedHttp.join('/')}`)

  let data = bytes
  if (responseType === 'json') {
    const contentType = response.headers.get('content-type') || ''
    assert.match(contentType, /json/i, `${method} ${path} did not return JSON (${contentType || 'missing content type'})`)
    try {
      data = JSON.parse(bytes.toString('utf8'))
    } catch {
      throw new Error(`${method} ${path} returned malformed JSON`)
    }
  } else if (responseType === 'text') {
    data = bytes.toString('utf8')
  }
  return { response, data, bytes }
}

const envelope = (data, expectedCodes = [200]) => {
  assert.ok(data && typeof data === 'object' && !Array.isArray(data), 'Expected a JSON business response object')
  assert.ok(Number.isInteger(data.code), 'Business response is missing an integer code')
  assert.ok(expectedCodes.includes(data.code), `Business code ${data.code} was not one of ${expectedCodes.join('/')}`)
  assert.ok(data.timestamp === undefined || Number.isFinite(data.timestamp), 'Business timestamp is invalid')
  return data.result
}

const nonServerEnvelope = (data) => {
  assert.ok(data && typeof data === 'object' && Number.isInteger(data.code), 'Expected a JSON business response object')
  assert.ok(data.code < 500, `Business request failed with server code ${data.code}`)
  return data.result
}

const runCheck = async (name, operation) => {
  try {
    const value = await operation()
    results.push({ name, status: 'PASS' })
    console.log(`[PASS] ${name}`)
    return value
  } catch (error) {
    const message = String(error?.message || error).replace(/\s+/g, ' ').slice(0, 500)
    results.push({ name, status: 'FAIL', message })
    console.error(`[FAIL] ${name}: ${message}`)
    return undefined
  }
}

const apiEnvelope = async (name, path, token, expectedCodes = [200], options = {}) => runCheck(name, async () => {
  const { validate, ...requestOptions } = options
  const { data } = await request(`/api${path}`, { token, ...requestOptions })
  const result = envelope(data, expectedCodes)
  if (validate) validate(result)
  return result
})

const apiBoundary = async (name, path, token, options = {}) => runCheck(name, async () => {
  const { data } = await request(`/api${path}`, { token, ...options })
  return nonServerEnvelope(data)
})

const recordsOf = (value) => {
  if (Array.isArray(value)) return value
  if (!value || typeof value !== 'object') return []
  for (const key of ['records', 'list', 'rows', 'data']) {
    if (Array.isArray(value[key])) return value[key]
  }
  for (const nested of Object.values(value)) {
    if (Array.isArray(nested)) return nested
  }
  return []
}

const collectAssetPaths = (value, found = new Set()) => {
  if (typeof value === 'string') {
    const match = value.match(/\/(image|file|video)\/[^?#\s]+/i)
    if (match) found.add(match[0])
    return found
  }
  if (Array.isArray(value)) {
    for (const item of value) collectAssetPaths(item, found)
    return found
  }
  if (value && typeof value === 'object') {
    for (const item of Object.values(value)) collectAssetPaths(item, found)
  }
  return found
}

const validPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64'
)

const cleanupUpload = () => {
  if (uploadedFile) {
    const imageRoot = resolve(projectRoot, 'image')
    const normalizedRoot = imageRoot.endsWith(sep) ? imageRoot : `${imageRoot}${sep}`
    assert.ok(uploadedFile.startsWith(normalizedRoot), 'Refusing to clean an upload outside the project image directory')
    if (existsSync(uploadedFile)) unlinkSync(uploadedFile)
    uploadedFile = null
  }
  if (uploadedContainerFile) {
    assert.match(uploadedContainerFile, /^\/app\/uploads\/image\/\d{8}\/[a-f0-9]{32}\.png$/)
    const remove = spawnSync('docker', ['exec', dockerBackendContainer, 'rm', '--', uploadedContainerFile], {
      encoding: 'utf8', windowsHide: true
    })
    assert.equal(remove.status, 0, `Could not clean the exact Docker upload fixture: ${String(remove.stderr || '').trim()}`)
    uploadedContainerFile = null
  }
}

const run = async () => {
  let activityRows = []
  let activityTypeRows = []
  let parentServiceRows = []
  let recipeRows = []
  let commentRows = []
  let reportRows = []
  let recipeOrderRows = []
  const observedPayloads = []

  await runCheck('frontend SPA shell', async () => {
    const { data, response } = await request('/login', { responseType: 'text' })
    assert.match(response.headers.get('content-type') || '', /text\/html/i)
    assert.match(data, /id=["']app["']/)
  })

  await runCheck('Vite or Nginx /api health proxy', async () => {
    const { data } = await request('/api/actuator/health')
    assert.equal(data.status, 'UP')
  })

  const captchaKey = `communication-${randomUUID().replaceAll('-', '')}`
  await runCheck('captcha binary response through /api', async () => {
    const { response, bytes } = await request(`/api/user/getVerificationCode/${captchaKey}`, { responseType: 'buffer' })
    assert.match(response.headers.get('content-type') || '', /^image\//i)
    assert.ok(bytes.length > 500, 'Captcha image is unexpectedly small')
    assert.match(response.headers.get('cache-control') || '', /no-store/i)
  })

  await runCheck('login form encoding and error contract', async () => {
    const form = new URLSearchParams({ loginName: 'communication-audit', password: 'invalid', code: 'WRONG' })
    const { data } = await request(`/api/user/login/${captchaKey}`, { method: 'POST', body: form })
    envelope(data, [108])
  })

  await runCheck('protected API rejects a missing token', async () => {
    const { data } = await request('/api/activity/findAll', { expectedHttp: [401] })
    envelope(data, [401])
  })

  const ownUser = await apiEnvelope('normal-user identity lookup', `/user/selectById/${normalUserId}`, userToken)
  if (ownUser) observedPayloads.push(ownUser)
  await apiEnvelope('cross-user data isolation', `/user/selectById/${adminUserId}`, userToken, [403])
  if (ownUser?.username) {
    await apiEnvelope('normal-user username lookup', `/user/selectByUsername/${encodeURIComponent(ownUser.username)}`, userToken)
    await apiEnvelope('admin user search', `/user/selectByNameOrIdNum/${encodeURIComponent(ownUser.username)}`, adminToken)
  }
  await apiEnvelope('admin user pagination', '/user/selectByRidByPage/4/1/5', adminToken)
  await apiEnvelope('admin worker directory', '/user/selectByRid/2', adminToken)
  await apiEnvelope('admin doctor directory', '/user/selectByRid/3', adminToken)
  await apiEnvelope('role-aware navigation', '/sysFunction/selectByRid/4', userToken)
  await apiEnvelope('admin navigation catalog', '/sysFunction/selectAll', adminToken)

  const activities = await apiEnvelope('activity pagination', '/activity/selectAllByPage/1/8', userToken)
  if (activities) {
    observedPayloads.push(activities)
    activityRows = recordsOf(activities)
  }
  const allActivities = await apiEnvelope('activity full list', '/activity/findAll', userToken)
  if (activityRows.length === 0) activityRows = recordsOf(allActivities)
  await apiEnvelope('upcoming activity list', '/activity/selectNotBegin', userToken)
  const firstActivity = activityRows[0]
  if (firstActivity?.id) {
    await apiEnvelope('activity detail', `/activity/selectById/${firstActivity.id}`, userToken, [200], {
      validate: (activity) => {
        assert.ok(Number(activity?.dId) > 0, 'Activity response lost dId')
        assert.ok(Number(activity?.activityTypeId) > 0, 'Activity response lost activityTypeId')
      }
    })
    await apiBoundary('user activity-registration lookup', `/userActivity/selectByuIdByaId/${normalUserId}/${firstActivity.id}`, userToken)
    await apiEnvelope('admin activity attendee lookup', `/userActivity/selectUserByaId/${firstActivity.id}`, adminToken)
    await runCheck('Excel export through /api', async () => {
      const { response, bytes } = await request(`/api/download/excel/${firstActivity.id}`, {
        method: 'POST', token: adminToken, responseType: 'buffer', timeout: 30_000
      })
      assert.match(response.headers.get('content-type') || '', /spreadsheetml/i)
      assert.ok(bytes.length > 100, 'Excel export is unexpectedly small')
      assert.equal(bytes.subarray(0, 2).toString('ascii'), 'PK')
    })
  } else {
    await runCheck('activity seed data available for detail communication', async () => assert.fail('No activity record was returned'))
  }

  const activityTypes = await apiEnvelope('active activity types', '/activityType/selectByState1', userToken)
  if (activityTypes) activityTypeRows = recordsOf(activityTypes)
  await apiEnvelope('admin activity-type catalog', '/activityType/selectAll', adminToken)
  const firstActivityTypeId = firstActivity?.activityTypeId || firstActivity?.activityType?.id
  if (firstActivityTypeId) {
    await apiEnvelope('activity filter by type', `/activity/selectByType/${firstActivityTypeId}`, userToken)
  }
  if (firstActivity?.activityName) {
    await apiEnvelope('activity search pagination', `/activity/selectByNameByPage/${encodeURIComponent(firstActivity.activityName)}/1/5`, userToken)
  }
  const firstActivityTypeName = activityTypeRows[0]?.type || activityTypeRows[0]?.typeName
  if (firstActivityTypeName) {
    await apiEnvelope('activity-type search', `/activityType/selectByName/${encodeURIComponent(firstActivityTypeName)}`, adminToken)
  }

  const parentServices = await apiEnvelope('active service categories', '/serviceType/selectFather1', userToken)
  if (parentServices) {
    observedPayloads.push(parentServices)
    parentServiceRows = recordsOf(parentServices)
  }
  await apiEnvelope('admin parent service catalog', '/serviceType/selectAllFather', adminToken)
  await apiEnvelope('admin child service catalog', '/serviceType/selectAllChildren', adminToken)
  const firstParent = parentServiceRows[0]
  if (firstParent?.id) {
    await apiEnvelope('active child services', `/serviceType/selectChildren1ByFather/${firstParent.id}`, userToken)
    await apiEnvelope('home-page child services', `/serviceType/selectAllChildrenByFather/${firstParent.id}`, userToken)
  } else {
    await runCheck('service seed data available for dependent communication', async () => assert.fail('No active parent service was returned'))
  }

  await apiEnvelope('normal-user service orders', `/serviceOrder/selectByUId/${normalUserId}`, userToken, [200], {
    validate: (orders) => {
      const rows = recordsOf(orders)
      if (rows.length > 0) assert.equal(Number(rows[0].uId), normalUserId, 'Service-order response lost uId')
    }
  })
  for (const state of [0, 2, 3]) {
    await apiEnvelope(`normal-user service orders in state ${state}`, `/serviceOrder/selectByuIdByState/${normalUserId}/${state}`, userToken)
  }
  await apiEnvelope('admin service-order pagination', '/serviceOrder/selectServiceByPage/1/8', adminToken)
  await apiEnvelope('admin health-order pagination', '/serviceOrder/selectHealthByPage/1/10', adminToken)

  await apiEnvelope('normal-user activity registrations', `/userActivity/selectAllByuId/${normalUserId}`, userToken, [200], {
    validate: (registrations) => {
      const rows = recordsOf(registrations)
      assert.ok(rows.length > 0, 'Registration seed data is missing')
      assert.equal(Number(rows[0].uId), normalUserId, 'Registration response lost uId')
      assert.equal(typeof rows[0].aState, 'string', 'Registration response lost aState')
    }
  })
  await apiEnvelope('normal-user upcoming registrations', `/userActivity/selectByUIdByState/${normalUserId}/${encodeURIComponent('未开始')}`, userToken)
  await apiEnvelope('normal-user cancelled registrations', `/userActivity/selectByUIdBymyState/${normalUserId}/${encodeURIComponent('已取消报名')}`, userToken)
  await apiEnvelope('admin registration pagination', '/userActivity/selectAllByPage/1/8', adminToken)

  const recipes = await apiEnvelope('recipe pagination', '/recipe/list?current=1&size=10', userToken)
  if (recipes) {
    observedPayloads.push(recipes)
    recipeRows = recordsOf(recipes)
  }
  await apiEnvelope('recipe full list', '/recipe/all', adminToken)
  const firstRecipe = recipeRows[0]
  if (firstRecipe?.id) {
    await apiEnvelope('recipe detail', `/recipe/${firstRecipe.id}`, userToken)
    await apiEnvelope('recipe search', `/recipe/search?keyword=${encodeURIComponent(firstRecipe.name || '')}&current=1&size=5`, userToken)
    const categorizedRecipe = recipeRows.find((recipe) => recipe?.category)
    if (categorizedRecipe) {
      await apiEnvelope('recipe category filter', `/recipe/category/${encodeURIComponent(categorizedRecipe.category)}`, userToken)
    }
  } else {
    await runCheck('recipe seed data available for detail communication', async () => assert.fail('No recipe record was returned'))
  }

  const myRecipeOrders = await apiEnvelope('normal-user recipe orders', '/recipe-order/my-orders?current=1&size=10', userToken)
  if (myRecipeOrders) recipeOrderRows = recordsOf(myRecipeOrders)
  await apiEnvelope('admin recipe-order pagination', '/recipe-order/all?current=1&size=10', adminToken)
  if (recipeOrderRows[0]?.id) {
    await apiEnvelope('normal-user recipe-order detail', `/recipe-order/${recipeOrderRows[0].id}`, userToken)
  }

  const comments = await apiEnvelope('forum comment tree', '/comment/list', userToken)
  if (comments) {
    observedPayloads.push(comments)
    commentRows = recordsOf(comments)
  }
  const managedComments = await apiEnvelope('admin forum moderation list', '/comment/manage', adminToken)
  if (commentRows.length === 0) commentRows = recordsOf(managedComments)
  await apiEnvelope('normal-user own comments', '/comment/my', userToken)
  const firstComment = commentRows[0]
  if (firstComment?.id) {
    await apiEnvelope('admin comment detail', `/comment/detail/${firstComment.id}`, adminToken)
    await apiEnvelope('admin comment replies', `/comment/replies/${firstComment.id}`, adminToken)
  }

  const reports = await apiEnvelope('normal-user health reports', `/report/selectByuId/${normalUserId}`, userToken, [200], {
    validate: (healthReports) => {
      const rows = recordsOf(healthReports)
      if (rows.length > 0) assert.equal(Number(rows[0].uId), normalUserId, 'Health-report response lost uId')
    }
  })
  if (reports) {
    observedPayloads.push(reports)
    reportRows = recordsOf(reports)
  }
  if (runLiveReportAnalysis) {
    if (reportRows[0]?.id) {
      await apiEnvelope('live health-report AI analysis', `/report/analyze/${reportRows[0].id}`, userToken, [200], { timeout: 90_000 })
    } else {
      await runCheck('live health-report AI analysis', async () => assert.fail('No health report is available for analysis'))
    }
  }

  for (const endpoint of ['getUsersSum', 'getDoctorSum', 'getWorkerSum', 'countRate', 'countSignedUpNum', 'countActivitySort']) {
    await apiEnvelope(`dashboard metric ${endpoint}`, `/count/${endpoint}`, adminToken)
  }
  for (const roleId of [2, 3, 4]) {
    await apiEnvelope(`dashboard role total ${roleId}`, `/count/getSum/${roleId}`, adminToken)
  }

  await runCheck('Agent runtime status through /api', async () => {
    const { data } = await request('/api/chat/status', { token: userToken })
    assert.equal(data.configured, true)
    assert.ok(Number(data.capabilityCount) >= 13)
    assert.equal(data.knowledge?.ready, true)
  })
  await runCheck('Agent provider catalog through /api', async () => {
    const { data } = await request('/api/chat/providers', { token: userToken })
    assert.ok(Array.isArray(data) || Array.isArray(data.providers), 'Provider catalog is not an array')
  })
  await runCheck('Agent analytics through /api', async () => {
    const { data } = await request('/api/chat/analytics?days=7', { token: userToken })
    assert.ok(Number.isFinite(Number(data.totalRuns)))
  })
  await runCheck('Agent action history through /api', async () => {
    const { data } = await request('/api/chat/actions?limit=10', { token: userToken })
    assert.ok(Array.isArray(data.actions))
  })

  await runCheck('WorkBuddy MCP initialize through /api', async () => {
    const { data } = await request('/api/mcp', {
      method: 'POST',
      headers: { Authorization: `Bearer ${mcpApiKey}`, Accept: 'application/json, text/event-stream' },
      body: {
        jsonrpc: '2.0', id: 1, method: 'initialize',
        params: { protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'communication-audit', version: '1.0.0' } }
      }
    })
    assert.equal(data.result?.serverInfo?.name, 'silverpilot-cecsms')
    assert.equal(data.result?.protocolVersion, '2025-03-26')
  })
  await runCheck('WorkBuddy MCP read-only tool catalog through /api', async () => {
    const { data } = await request('/api/mcp', {
      method: 'POST',
      headers: { Authorization: `Bearer ${mcpApiKey}`, Accept: 'application/json, text/event-stream' },
      body: { jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} }
    })
    assert.equal(data.result?.tools?.length, 6)
    assert.ok(data.result.tools.every((tool) => tool.annotations?.readOnlyHint === true && tool.annotations?.destructiveHint !== true))
  })

  const assetPaths = new Set()
  for (const payload of observedPayloads) collectAssetPaths(payload, assetPaths)
  if (assetPaths.size > 0) {
    await runCheck('all persisted business assets and WebP companions through the media proxy', async () => {
      const requestedPaths = new Set(assetPaths)
      for (const assetPath of assetPaths) {
        if (/^\/image\/.*\.(?:png|jpe?g)(?:[?#].*)?$/i.test(assetPath)) {
          requestedPaths.add(assetPath.replace(/\.(?:png|jpe?g)(?=([?#]|$))/i, '.webp'))
        }
      }
      for (const assetPath of requestedPaths) {
        const { response, bytes } = await request(assetPath, { responseType: 'buffer' })
        const contentType = response.headers.get('content-type') || ''
        assert.ok(bytes.length > 0, `Persisted asset is empty: ${assetPath}`)
        assert.doesNotMatch(contentType, /(?:text\/html|application\/json)/i, `Persisted asset returned an error document: ${assetPath}`)
        if (assetPath.startsWith('/image/')) {
          assert.match(contentType, /^image\//i, `Persisted image has an invalid content type: ${assetPath}`)
        }
      }
    })
  } else {
    await runCheck('persisted business assets available for proxy validation', async () => assert.fail('No persisted asset path was returned by the business APIs'))
  }

  const deniedUpload = new FormData()
  deniedUpload.append('imageFile', new Blob([validPng], { type: 'image/png' }), 'communication-audit.png')
  await apiEnvelope('multipart upload permission boundary', '/upload/image', userToken, [403], {
    method: 'POST', body: deniedUpload
  })

  if (allowLocalUploadWrite) {
    await runCheck('admin multipart upload, asset readback, and cleanup', async () => {
      const form = new FormData()
      form.append('imageFile', new Blob([validPng], { type: 'image/png' }), 'communication-audit.png')
      const { data } = await request('/api/upload/image', { method: 'POST', token: adminToken, body: form, timeout: 30_000 })
      const uploadedPath = envelope(data, [200])
      assert.match(uploadedPath, /^\/image\/\d{8}\/[a-f0-9]{32}\.png$/)
      const candidate = resolve(projectRoot, `.${uploadedPath}`)
      const imageRoot = resolve(projectRoot, 'image')
      assert.ok(candidate.startsWith(`${imageRoot}${sep}`), 'Uploaded file resolved outside the project image directory')
      if (existsSync(candidate)) {
        uploadedFile = candidate
      } else if (dockerBackendContainer) {
        uploadedContainerFile = `/app/uploads${uploadedPath}`
        assert.match(uploadedContainerFile, /^\/app\/uploads\/image\/\d{8}\/[a-f0-9]{32}\.png$/)
        const exists = spawnSync('docker', ['exec', dockerBackendContainer, 'test', '-f', uploadedContainerFile], {
          encoding: 'utf8', windowsHide: true
        })
        assert.equal(exists.status, 0, 'Backend reported success but the uploaded file does not exist in the Docker volume')
      } else {
        assert.fail('Backend reported success but the uploaded file cannot be located for safe cleanup')
      }
      const readback = await request(uploadedPath, { responseType: 'buffer' })
      assert.match(readback.response.headers.get('content-type') || '', /^image\/png/i)
      assert.deepEqual(readback.bytes, validPng)
      cleanupUpload()
    })
  }

  await apiBoundary('activity create validation route', '/activity/insert', adminToken, { method: 'PUT', body: {} })
  await apiBoundary('activity update validation route', '/activity/update', adminToken, { method: 'POST', body: { id: 0 } })
  await apiBoundary('activity delete validation route', '/activity/del/0', adminToken, { method: 'POST' })
  await apiBoundary('activity-type create validation route', '/activityType/insert', adminToken, { method: 'PUT', body: {} })
  await apiBoundary('activity-type update validation route', '/activityType/update', adminToken, { method: 'POST', body: {} })
  await apiBoundary('service-order create validation route', '/serviceOrder/insert', userToken, { method: 'PUT', body: {} })
  await apiBoundary('service-order update validation route', '/serviceOrder/update', userToken, { method: 'POST', body: {} })
  await apiBoundary('service-type create validation route', '/serviceType/insert', adminToken, { method: 'PUT', body: {} })
  await apiBoundary('service-type update validation route', '/serviceType/update', adminToken, { method: 'POST', body: {} })
  await apiBoundary('activity registration create validation route', '/userActivity/insert', userToken, { method: 'PUT', body: { aId: 0 } })
  await apiBoundary('activity registration update validation route', '/userActivity/update', userToken, { method: 'POST', body: { id: 0 } })
  await apiBoundary('forum create validation route', '/comment/add', userToken, { method: 'POST', body: { content: ' ' } })
  await apiBoundary('forum reply validation route', '/comment/reply', userToken, { method: 'POST', body: { content: ' ' } })
  await apiBoundary('forum delete validation route', '/comment/delete/0', userToken, { method: 'DELETE' })
  await apiBoundary('forum restore validation route', '/comment/restore/0', adminToken, { method: 'POST' })
  await apiBoundary('health-report create permission route', '/report/insert', userToken, { method: 'PUT', body: {} })
  await apiBoundary('health-report update validation route', '/report/update', adminToken, { method: 'POST', body: { id: 0 } })
  await apiBoundary('recipe create validation route', '/recipe', adminToken, { method: 'POST', body: {} })
  await apiBoundary('recipe update validation route', '/recipe', adminToken, { method: 'PUT', body: {} })
  await apiBoundary('recipe delete validation route', '/recipe/0', adminToken, { method: 'DELETE' })
  await apiBoundary('recipe-order create validation route', '/recipe-order/order', userToken, { method: 'POST', body: {} })
  await apiBoundary('recipe-order status validation route', '/recipe-order/status', adminToken, { method: 'PUT', body: { id: 0, status: 0 } })
  await apiBoundary('recipe-order cancel validation route', '/recipe-order/cancel/0', userToken, { method: 'POST' })
  await apiEnvelope('public registration validation route', '/user/register', undefined, [400], { method: 'PUT', body: {} })
  await apiEnvelope('user create permission boundary', '/user/insert', userToken, [403], { method: 'PUT', body: {} })
  await apiEnvelope('user update permission boundary', '/user/update', userToken, [403], { method: 'POST', body: { id: adminUserId } })

  cleanupUpload()
  const failed = results.filter((result) => result.status === 'FAIL')
  console.log(`\nCommunication audit: ${results.length - failed.length}/${results.length} checks passed via ${baseUrl}.`)
  if (failed.length > 0) {
    console.error('Failed checks:')
    for (const failure of failed) console.error(`- ${failure.name}: ${failure.message}`)
    process.exitCode = 1
  }
}

try {
  await run()
} finally {
  cleanupUpload()
}
