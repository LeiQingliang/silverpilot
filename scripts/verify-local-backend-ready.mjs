const requiredHealthEndpoints = [
  '/actuator/health',
  '/actuator/health/redis'
]

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

const probeHealth = async (path) => {
  const url = `${backendTarget}${path}`
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 3000)
  try {
    const response = await fetch(url, {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
      signal: controller.signal
    })
    if (!response.ok) throw new Error(`${path} returned HTTP ${response.status}`)

    const body = await response.json()
    if (body?.status !== 'UP') throw new Error(`${path} reported ${body?.status || 'an invalid response'}`)
  } finally {
    clearTimeout(timeout)
  }
}

const main = async () => {
  let parsedTarget
  try {
    parsedTarget = new URL(backendTarget)
  } catch {
    console.error(`[FAIL] Invalid local backend URL: ${backendTarget}`)
    return 1
  }

  if (!['http:', 'https:'].includes(parsedTarget.protocol)) {
    console.error(`[FAIL] Local backend URL must use HTTP or HTTPS: ${backendTarget}`)
    return 1
  }

  const deadline = Date.now() + waitSeconds * 1000
  let lastFailure = 'backend did not respond'

  console.log(`[INFO] Waiting up to ${waitSeconds}s for local backend and Docker Redis at ${backendTarget} ...`)
  do {
    try {
      await Promise.all(requiredHealthEndpoints.map(probeHealth))
      console.log('[PASS] Local backend and required Docker Redis are ready.')
      return 0
    } catch (error) {
      lastFailure = error instanceof Error ? error.message : String(error)
    }

    if (Date.now() < deadline) await delay(1000)
  } while (Date.now() < deadline)

  console.error(`[FAIL] Frontend was not started because the local backend stack is not ready: ${lastFailure}`)
  console.error('[NEXT] From the project root, run .\\scripts\\docker-dev.ps1 redis, start CecsmsServeApplication in IDEA, wait for http://127.0.0.1:8083/actuator/health to report UP, then retry npm run dev.')
  return 1
}

const backendTarget = (
  process.env.CECSMS_LOCAL_BACKEND_URL
  || process.env.VITE_BACKEND_TARGET
  || 'http://127.0.0.1:8083'
).replace(/\/+$/, '')

const configuredWaitSeconds = Number.parseInt(
  process.env.CECSMS_LOCAL_BACKEND_WAIT_SECONDS || '60',
  10
)
const waitSeconds = Number.isInteger(configuredWaitSeconds)
  ? Math.min(300, Math.max(1, configuredWaitSeconds))
  : 60

process.exitCode = await main()
