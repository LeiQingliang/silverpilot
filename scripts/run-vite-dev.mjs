import { spawn } from 'node:child_process'
import { access, unlink } from 'node:fs/promises'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const host = '127.0.0.1'
const port = 8081
const loginUrl = `http://${host}:${port}/login`
const identityHeader = 'x-silverpilot-dev-server'
const identityValue = 'cecsmsui-v1'
const scriptRoot = path.dirname(fileURLToPath(import.meta.url))

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

const runProcess = (command, args, options = {}) => new Promise((resolve, reject) => {
  const child = spawn(command, args, {
    stdio: 'inherit',
    windowsHide: true,
    ...options
  })
  child.once('error', reject)
  child.once('exit', (code, signal) => {
    if (Number.isInteger(code)) {
      resolve(code)
    } else {
      console.error(`[FAIL] Child process exited after receiving ${signal || 'an unknown signal'}.`)
      resolve(1)
    }
  })
})

const probeProjectVite = async () => {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 2000)
  try {
    const response = await fetch(loginUrl, {
      cache: 'no-store',
      signal: controller.signal
    })
    return response.ok && response.headers.get(identityHeader) === identityValue
  } catch {
    return false
  } finally {
    clearTimeout(timeout)
  }
}

const probePort = () => new Promise((resolve) => {
  const socket = net.createConnection({ host, port })
  let settled = false
  const finish = (listening) => {
    if (settled) return
    settled = true
    socket.destroy()
    resolve(listening)
  }
  socket.setTimeout(1000)
  socket.once('connect', () => finish(true))
  socket.once('timeout', () => finish(false))
  socket.once('error', () => finish(false))
})

const stopExistingProjectVite = async (frontendRoot) => {
  if (process.platform !== 'win32') {
    console.error('[FAIL] This project Vite is already running. Stop it before transferring ownership to the current terminal.')
    return 1
  }

  const stopScript = path.join(scriptRoot, 'stop-local-frontend.ps1')
  console.log('[INFO] Stopping the existing project Vite so this terminal can take ownership of the dev server ...')
  const code = await runProcess('powershell.exe', [
    '-NoProfile',
    '-ExecutionPolicy',
    'Bypass',
    '-File',
    stopScript,
    '-FrontendRoot',
    frontendRoot
  ])
  if (code !== 0) return code

  const stopMarker = path.join(frontendRoot, 'node_modules', '.cecsms-vite-stop-requested')
  for (let attempt = 0; attempt < 20; attempt += 1) {
    try {
      await access(stopMarker)
      await delay(100)
    } catch {
      return 0
    }
  }
  await unlink(stopMarker).catch((error) => {
    if (error?.code !== 'ENOENT') throw error
  })
  return 0
}

const main = async () => {
  const frontendRoot = process.cwd()
  if (await probeProjectVite()) {
    const stopCode = await stopExistingProjectVite(frontendRoot)
    if (stopCode !== 0) return stopCode
    if (await probePort()) {
      console.error(`[FAIL] Port ${port} remained occupied after the verified project Vite was stopped.`)
      return 1
    }
    console.log('[PASS] Existing project Vite stopped; the current terminal now owns the new dev-server session.')
  }

  if (await probePort()) {
    console.error(`[FAIL] Port ${port} is occupied by a process that is not the identified cecsmsui Vite server.`)
    console.error(`[NEXT] Run .\\scripts\\stop-local-frontend.ps1 from the project root, inspect the remaining port owner, then retry npm run dev.`)
    return 1
  }

  const viteCli = path.join(frontendRoot, 'node_modules', 'vite', 'bin', 'vite.js')
  try {
    await access(viteCli)
  } catch {
    console.error(`[FAIL] Vite CLI was not found at ${viteCli}. Run npm ci first.`)
    return 1
  }

  console.log(`[INFO] Starting this project's Vite server at ${loginUrl} ...`)
  return await runProcess(process.execPath, [viteCli, ...process.argv.slice(2)], {
    cwd: frontendRoot,
    stdio: 'inherit'
  })
}

process.exitCode = await main()
