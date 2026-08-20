import { createHmac } from 'node:crypto'
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptRoot = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptRoot, '..')
const frontendRoot = join(projectRoot, 'SourceCode', 'cecsmsui-vue')
const baseUrl = process.env.CECSMS_QA_BASE_URL || 'http://127.0.0.1:8081'
const jwtSecret = process.env.CECSMS_QA_JWT_SECRET || process.env.SILVERPILOT_JWT_SECRET || ''
// The application intentionally ships one fixed, accessible light theme. Keep
// the default release gate aligned with that supported contract and its two
// documented breakpoints; callers can still opt into exploratory combinations.
const requestedThemes = (process.env.CECSMS_QA_THEMES || 'light').split(',').filter(Boolean)
const requestedViewports = (process.env.CECSMS_QA_VIEWPORTS || 'desktop,mobile').split(',').filter(Boolean)
const screenshotMode = process.env.CECSMS_QA_SCREENSHOTS || 'failures'
const screenshotParent = resolve(process.env.CECSMS_QA_SCREENSHOT_DIR || tmpdir())
const requestedRoutes = (process.env.CECSMS_QA_ROUTES || '').split(',').map((route) => route.trim()).filter(Boolean)
const performanceAuditEnabled = process.env.CECSMS_QA_PERFORMANCE === 'true'
const reducedMotionEnabled = process.env.CECSMS_QA_REDUCED_MOTION === 'true'
const reportPath = process.env.CECSMS_QA_REPORT_PATH ? resolve(process.env.CECSMS_QA_REPORT_PATH) : ''

if (jwtSecret.length < 32) {
  throw new Error('CECSMS_QA_JWT_SECRET or SILVERPILOT_JWT_SECRET must match the 32+ character local JWT secret used by the backend.')
}

const edgeCandidates = [
  process.env.EDGE_PATH,
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe'
].filter(Boolean)
const edgePath = edgeCandidates.find(existsSync)
if (!edgePath) throw new Error('Microsoft Edge was not found. Set EDGE_PATH to the browser executable.')

const viewports = {
  desktop: { width: 1440, height: 1000, mobile: false, deviceScaleFactor: 1 },
  tablet: { width: 768, height: 1024, mobile: true, deviceScaleFactor: 1 },
  mobile: { width: 390, height: 844, mobile: true, deviceScaleFactor: 1 }
}

const routeGroups = [
  {
    role: 'public',
    user: null,
    routes: ['/login', '/register']
  },
  {
    role: 'user',
    user: { id: 17, roleId: 4, username: 'linge', name: '林格' },
    routes: [
      '/front/home/FrontHomeView',
      '/front/activity/FrontActivityView',
      '/front/activity/ActivityDetailView',
      '/front/activity/ActivityDetailView?Id=1',
      '/front/service/FrontServiceView',
      '/front/recipe/RecipeListView',
      '/front/recipe/RecipeDetailView/1',
      '/front/recipe/MyRecipeOrders',
      '/front/forum/ForumHomeView',
      '/front/personal/PersonalCenter',
      '/front/myActivity/MyActivityView',
      '/front/myService/MyServiceView',
      '/front/myComment/MyCommentView',
      '/front/myReport/MyReportView',
      '/front/ai/AiLogin',
      '/front/ai/AiChat'
    ]
  },
  {
    role: 'admin',
    user: { id: 1, roleId: 1, username: 'admin', name: '系统管理员' },
    routes: [
      '/IndexView',
      '/AgentOperationsView',
      '/CountRate',
      '/ActivityMenageView',
      '/ActivityTypeView',
      '/ServiceTypeView',
      '/ServiceOrderView',
      '/HealthOrderView',
      '/HealthMenageView',
      '/UserMenageView',
      '/WorkerMenageView',
      '/DoctorMenageView',
      '/ForumManageView',
      '/RecipeManageView',
      '/RecipeOrderManageView',
      '/RecipeEditView/1'
    ]
  }
]

const sleep = (milliseconds) => new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds))
const compactMessage = (value, maximumLength = 800) => String(value || '').replace(/\s+/g, ' ').slice(0, maximumLength)
const waitForProcessExit = (childProcess, timeout = 3_000) => new Promise((resolvePromise) => {
  if (!childProcess || childProcess.exitCode !== null) return resolvePromise(true)
  const timer = setTimeout(() => resolvePromise(false), timeout)
  childProcess.once('exit', () => {
    clearTimeout(timer)
    resolvePromise(true)
  })
})
const getAvailablePort = () => new Promise((resolvePromise, reject) => {
  const server = createServer()
  server.once('error', reject)
  server.listen(0, '127.0.0.1', () => {
    const address = server.address()
    server.close((error) => error ? reject(error) : resolvePromise(address.port))
  })
})

const waitForHttp = async (url, timeout = 30_000) => {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch { /* server is still starting */ }
    await sleep(250)
  }
  throw new Error(`Timed out waiting for ${url}`)
}

const encodeBase64Url = (value) => Buffer.from(JSON.stringify(value)).toString('base64url')
const buildToken = (userId) => {
  const now = Math.floor(Date.now() / 1000)
  const header = encodeBase64Url({ alg: 'HS256', typ: 'JWT' })
  const payload = encodeBase64Url({ iss: 'cecsms-serve', sub: String(userId), aud: String(userId), iat: now, exp: now + 10_800 })
  const unsignedToken = `${header}.${payload}`
  const signature = createHmac('sha256', jwtSecret).update(unsignedToken).digest('base64url')
  return `${unsignedToken}.${signature}`
}

class CdpClient {
  constructor(webSocketUrl) {
    this.webSocket = new WebSocket(webSocketUrl)
    this.sequence = 0
    this.pending = new Map()
    this.handlers = new Map()
  }

  async connect() {
    await new Promise((resolvePromise, reject) => {
      this.webSocket.addEventListener('open', resolvePromise, { once: true })
      this.webSocket.addEventListener('error', reject, { once: true })
    })
    this.webSocket.addEventListener('message', ({ data }) => {
      const message = JSON.parse(data)
      if (message.id) {
        const pendingRequest = this.pending.get(message.id)
        if (!pendingRequest) return
        this.pending.delete(message.id)
        if (message.error) pendingRequest.reject(new Error(message.error.message))
        else pendingRequest.resolve(message.result)
        return
      }
      for (const handler of this.handlers.get(message.method) || []) handler(message.params)
    })
  }

  send(method, params = {}) {
    const id = ++this.sequence
    return new Promise((resolvePromise, reject) => {
      this.pending.set(id, { resolve: resolvePromise, reject })
      this.webSocket.send(JSON.stringify({ id, method, params }))
    })
  }

  on(method, handler) {
    const handlers = this.handlers.get(method) || []
    handlers.push(handler)
    this.handlers.set(method, handlers)
  }

  close() {
    this.webSocket.close()
  }
}

const getPageTarget = async (debugPort) => {
  const deadline = Date.now() + 15_000
  while (Date.now() < deadline) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json()
      const target = targets.find((entry) => entry.type === 'page')
      if (target?.webSocketDebuggerUrl) return target
    } catch { /* browser is still starting */ }
    await sleep(200)
  }
  throw new Error('Timed out waiting for the Edge DevTools target.')
}

const evaluate = async (client, expression) => {
  const result = await client.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'Browser evaluation failed')
  return result.result.value
}

const waitForDocument = async (client, expectedRoute, timeout = 15_000) => {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    try {
      const state = await evaluate(client, `({ ready: document.readyState, route: location.pathname + location.search })`)
      if (state.ready === 'complete' && state.route === expectedRoute) {
        await sleep(700)
        await evaluate(client, `new Promise((resolve) => requestAnimationFrame(() => { window.scrollTo(0, 0); requestAnimationFrame(resolve); }))`)
        return
      }
    } catch { /* execution context can be replaced during navigation */ }
    await sleep(100)
  }
  throw new Error(`Timed out waiting for route ${expectedRoute}`)
}

const sanitizeFileName = (route) => route.replace(/^\//, '').replace(/[^a-zA-Z0-9_-]+/g, '-') || 'root'

const sampleAnimationState = async (client, stage, scrollExpression) => {
  await evaluate(client, `new Promise((resolve) => {
    ${scrollExpression};
    requestAnimationFrame(() => requestAnimationFrame(resolve));
  })`)
  await sleep(350)
  return evaluate(client, `(async () => {
    const isInViewport = (element) => {
      const rect = element.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.bottom > 0
        && rect.left < innerWidth && rect.top < innerHeight;
    };
    const animations = document.getAnimations().map((animation) => {
      const target = animation.effect?.target;
      return {
        playState: animation.playState,
        inViewport: target instanceof Element ? isInViewport(target) : null,
        target: target instanceof Element
          ? [target.tagName.toLowerCase(), String(target.className || '').trim().split(/\\s+/).slice(0, 2).join('.')].filter(Boolean).join('.')
          : ''
      };
    });
    const canvases = [...document.querySelectorAll('canvas')].map((canvas) => ({
      className: String(canvas.className || ''),
      inViewport: isInViewport(canvas),
      animationActive: canvas.dataset.animationActive || 'unreported',
      width: canvas.width,
      height: canvas.height
    }));
    const frameDurations = [];
    await new Promise((resolve) => {
      let previous = performance.now();
      const deadline = previous + 650;
      const step = (now) => {
        frameDurations.push(now - previous);
        previous = now;
        if (frameDurations.length >= 30 || now >= deadline) resolve();
        else requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    });
    const sortedDurations = [...frameDurations].sort((left, right) => left - right);
    const percentileIndex = Math.max(0, Math.ceil(sortedDurations.length * 0.95) - 1);
    return {
      stage: ${JSON.stringify(stage)},
      scrollY: Math.round(scrollY),
      animations: animations.length,
      runningAnimations: animations.filter(({ playState }) => playState === 'running').length,
      offscreenRunningAnimations: animations.filter(({ playState, inViewport }) => playState === 'running' && inViewport === false),
      canvases,
      framesSampled: frameDurations.length,
      averageFrameMs: Number((frameDurations.reduce((sum, duration) => sum + duration, 0) / Math.max(1, frameDurations.length)).toFixed(2)),
      p95FrameMs: Number((sortedDurations[percentileIndex] || 0).toFixed(2)),
      longFrames: frameDurations.filter((duration) => duration > 50).length
    };
  })()`)
}

const run = async () => {
  let viteProcess = null
  let edgeProcess = null
  let client = null
  let browserProfile = null
  let screenshotRoot = null
  const auditStartedAt = Date.now()
  const results = []
  const runtimeErrors = []
  const consoleErrors = []
  const consoleWarnings = []
  const badResponses = []

  try {
    try {
      await waitForHttp(`${baseUrl}/login`, 1_000)
    } catch {
      viteProcess = spawn(process.execPath, [join(frontendRoot, 'node_modules', 'vite', 'bin', 'vite.js'), '--logLevel', 'silent'], {
        cwd: frontendRoot,
        windowsHide: true,
        stdio: ['ignore', 'pipe', 'pipe']
      })
      viteProcess.stdout.on('data', () => {})
      viteProcess.stderr.on('data', (chunk) => process.stderr.write(chunk))
      await waitForHttp(`${baseUrl}/login`)
    }

    if (screenshotMode !== 'none') {
      mkdirSync(screenshotParent, { recursive: true })
      screenshotRoot = mkdtempSync(join(screenshotParent, 'silverpilot-display-audit-'))
    }

    const debugPort = await getAvailablePort()
    browserProfile = mkdtempSync(join(tmpdir(), 'silverpilot-edge-audit-'))
    edgeProcess = spawn(edgePath, [
      '--headless=new',
      `--remote-debugging-port=${debugPort}`,
      `--user-data-dir=${browserProfile}`,
      '--no-first-run',
      '--disable-background-networking',
      '--disable-component-update',
      '--disable-sync',
      `${baseUrl}/login`
    ], { windowsHide: true, stdio: 'ignore' })

    const target = await getPageTarget(debugPort)
    client = new CdpClient(target.webSocketDebuggerUrl)
    await client.connect()
    await Promise.all([
      client.send('Page.enable'),
      client.send('Runtime.enable'),
      client.send('Network.enable'),
      client.send('Log.enable')
    ])
    client.on('Runtime.exceptionThrown', ({ exceptionDetails }) => {
      runtimeErrors.push(compactMessage(exceptionDetails.exception?.description || exceptionDetails.text || 'Runtime exception'))
    })
    client.on('Runtime.consoleAPICalled', ({ type, args }) => {
      if (type === 'error' || type === 'assert') {
        consoleErrors.push(compactMessage(args.map((arg) => arg.value || arg.description || '').join(' ')))
      }
      if (type === 'warning') consoleWarnings.push(compactMessage(args[0]?.value || args[0]?.description || 'Console warning'))
    })
    client.on('Log.entryAdded', ({ entry }) => {
      if (entry.level === 'error') consoleErrors.push(compactMessage(entry.text))
    })
    client.on('Network.responseReceived', ({ response }) => {
      if (response.status >= 400) badResponses.push({ status: response.status, url: response.url })
    })

    await waitForDocument(client, '/login')

    for (const viewportName of requestedViewports) {
      const viewport = viewports[viewportName]
      if (!viewport) throw new Error(`Unknown viewport: ${viewportName}`)
      await client.send('Emulation.setDeviceMetricsOverride', {
        width: viewport.width,
        height: viewport.height,
        deviceScaleFactor: viewport.deviceScaleFactor,
        mobile: viewport.mobile
      })
      await client.send('Emulation.setEmulatedMedia', {
        features: [{ name: 'prefers-reduced-motion', value: reducedMotionEnabled ? 'reduce' : 'no-preference' }]
      })

      for (const theme of requestedThemes) {
        if (!['light', 'dark'].includes(theme)) throw new Error(`Unknown theme: ${theme}`)

        for (const group of routeGroups) {
          const user = group.user
          const token = user ? buildToken(user.id) : null
          for (const route of group.routes) {
            if (requestedRoutes.length && !requestedRoutes.includes(route)) continue
            const runtimeStart = runtimeErrors.length
            const consoleStart = consoleErrors.length
            const warningStart = consoleWarnings.length
            const responseStart = badResponses.length
            const storageState = JSON.stringify({ theme, token, user })
            await evaluate(client, `(() => {
              const state = ${storageState};
              sessionStorage.clear();
              localStorage.setItem('silverpilot-theme', state.theme);
              document.documentElement.dataset.theme = state.theme;
              document.documentElement.style.colorScheme = state.theme;
              if (state.user) {
                sessionStorage.setItem('token', state.token);
                sessionStorage.setItem('user', JSON.stringify(state.user));
                sessionStorage.setItem('id', String(state.user.id));
                sessionStorage.setItem('roleId', String(state.user.roleId));
              }
            })()`)

            await client.send('Page.navigate', { url: `${baseUrl}${route}` })
            const expectedUrl = new URL(route, baseUrl)
            await waitForDocument(client, `${expectedUrl.pathname}${expectedUrl.search}`)

            const metrics = await evaluate(client, `(() => {
              const visible = (element) => {
                const style = getComputedStyle(element);
                const rect = element.getBoundingClientRect();
                return style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity) > 0
                  && rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.bottom > 0
                  && rect.left < innerWidth && rect.top < innerHeight;
              };
              const allElements = [...document.body.querySelectorAll('*')];
              const visibleElements = allElements.filter(visible);
              const brokenImages = [...document.images].filter((image) => visible(image) && image.complete && image.naturalWidth === 0).map((image) => image.currentSrc || image.src);
              const brokenMedia = [...document.querySelectorAll('video, audio')]
                .filter((media) => visible(media) && (media.error || media.networkState === HTMLMediaElement.NETWORK_NO_SOURCE))
                .map((media) => media.currentSrc || media.src || media.tagName.toLowerCase());
              const overflowCandidates = visibleElements.filter((element) => {
                if (element.clientWidth <= 0 || element.scrollWidth <= element.clientWidth + 2) return false;
                const style = getComputedStyle(element);
                return ['visible', 'clip'].includes(style.overflowX);
              }).slice(0, 12).map((element) => ({ tag: element.tagName, className: String(element.className || '').slice(0, 100), clientWidth: element.clientWidth, scrollWidth: element.scrollWidth }));
              const interactiveElements = [...document.querySelectorAll('button, a[href], input, select, textarea, [role="button"]')]
                .filter((element, index, elements) => visible(element) && elements.indexOf(element) === index && !element.disabled);
              const interactiveOverlaps = [];
              for (let leftIndex = 0; leftIndex < interactiveElements.length && interactiveOverlaps.length < 12; leftIndex += 1) {
                const left = interactiveElements[leftIndex];
                const leftRect = left.getBoundingClientRect();
                for (let rightIndex = leftIndex + 1; rightIndex < interactiveElements.length && interactiveOverlaps.length < 12; rightIndex += 1) {
                  const right = interactiveElements[rightIndex];
                  if (left.contains(right) || right.contains(left)) continue;
                  const rightRect = right.getBoundingClientRect();
                  const overlapWidth = Math.min(leftRect.right, rightRect.right) - Math.max(leftRect.left, rightRect.left);
                  const overlapHeight = Math.min(leftRect.bottom, rightRect.bottom) - Math.max(leftRect.top, rightRect.top);
                  if (overlapWidth > 3 && overlapHeight > 3) {
                    interactiveOverlaps.push({
                      left: (left.textContent || left.getAttribute('aria-label') || left.tagName).trim().slice(0, 50),
                      right: (right.textContent || right.getAttribute('aria-label') || right.tagName).trim().slice(0, 50),
                      overlapWidth: Math.round(overlapWidth),
                      overlapHeight: Math.round(overlapHeight)
                    });
                  }
                }
              }
              const app = document.querySelector('#app');
              const appStyle = app ? getComputedStyle(app) : null;
              const root = document.documentElement;
              return {
                actualPath: location.pathname,
                actualRoute: location.pathname + location.search,
                title: document.title,
                theme: root.dataset.theme,
                readyState: document.readyState,
                textLength: (document.body.innerText || '').trim().length,
                visibleElementCount: visibleElements.length,
                appHeight: app?.getBoundingClientRect().height || 0,
                appDisplay: appStyle?.display || '',
                appVisibility: appStyle?.visibility || '',
                appOpacity: appStyle?.opacity || '',
                bodyBackground: getComputedStyle(document.body).backgroundColor,
                documentWidth: root.scrollWidth,
                viewportWidth: innerWidth,
                horizontalOverflow: root.scrollWidth > innerWidth + 2,
                overflowCandidates,
                interactiveOverlaps,
                brokenImages,
                brokenMedia,
                routeError: Boolean(document.querySelector('.route-error')),
                errorMessages: [...document.querySelectorAll('.el-message--error, .el-message-box')].map((element) => element.textContent.trim()).filter(Boolean),
                stuckLoadingMasks: [...document.querySelectorAll('.el-loading-mask')].filter(visible).length
              };
            })()`)

            const animationSamples = performanceAuditEnabled
              ? [
                  await sampleAnimationState(client, 'page-top', 'window.scrollTo(0, 0)'),
                  await sampleAnimationState(client, 'care-landscape-visible', "document.querySelector('.care-landscape')?.scrollIntoView({ block: 'center' })"),
                  await sampleAnimationState(client, 'page-end', 'window.scrollTo(0, document.documentElement.scrollHeight)'),
                  await sampleAnimationState(client, 'care-landscape-restored', "document.querySelector('.care-landscape')?.scrollIntoView({ block: 'center' })")
                ]
              : []

            const newRuntimeErrors = runtimeErrors.slice(runtimeStart)
            const newConsoleErrors = consoleErrors.slice(consoleStart).filter((message) => !message.includes('favicon.ico'))
            const newConsoleWarnings = consoleWarnings.slice(warningStart)
            const newBadResponses = badResponses.slice(responseStart).filter(({ url }) => !url.endsWith('/favicon.ico'))
            const failures = []
            if (metrics.actualRoute !== route) failures.push(`redirected to ${metrics.actualRoute}`)
            if (metrics.theme !== theme) failures.push(`theme became ${metrics.theme}`)
            if (metrics.routeError) failures.push('route error boundary is visible')
            if (metrics.textLength < 8 || metrics.visibleElementCount < 3 || metrics.appHeight < 80) failures.push('page is effectively blank')
            if (metrics.appDisplay === 'none' || metrics.appVisibility === 'hidden' || Number(metrics.appOpacity) === 0) failures.push('app root is hidden')
            if (metrics.horizontalOverflow) failures.push(`document overflows by ${metrics.documentWidth - metrics.viewportWidth}px`)
            if (metrics.interactiveOverlaps.length) failures.push(`${metrics.interactiveOverlaps.length} interactive control overlap(s)`)
            if (metrics.brokenImages.length) failures.push(`${metrics.brokenImages.length} visible image(s) failed`)
            if (metrics.brokenMedia.length) failures.push(`${metrics.brokenMedia.length} visible video/audio resource(s) failed`)
            if (metrics.stuckLoadingMasks) failures.push(`${metrics.stuckLoadingMasks} loading mask(s) remained visible`)
            if (newRuntimeErrors.length) failures.push(`${newRuntimeErrors.length} runtime exception(s)`)
            if (newConsoleErrors.length) failures.push(`${newConsoleErrors.length} console error(s)`)
            if (newConsoleWarnings.length) failures.push(`${newConsoleWarnings.length} console warning(s)`)
            if (newBadResponses.some(({ status }) => status >= 500)) failures.push(`${newBadResponses.filter(({ status }) => status >= 500).length} HTTP 5xx response(s)`)
            const failedAssets = newBadResponses.filter(({ status, url }) => status >= 400 && /\/(?:assets|image|file|video)\//i.test(url))
            if (failedAssets.length) failures.push(`${failedAssets.length} image, video or file request(s) failed`)
            const offscreenActiveCanvases = animationSamples
              .filter(({ stage }) => stage === 'page-end')
              .flatMap(({ canvases }) => canvases)
              .filter(({ inViewport, animationActive }) => !inViewport && animationActive === 'true')
            if (offscreenActiveCanvases.length) failures.push(`${offscreenActiveCanvases.length} offscreen canvas animation loop(s) remained active`)

            const result = {
              route,
              role: group.role,
              theme,
              viewport: viewportName,
              passed: failures.length === 0,
              failures,
              metrics,
              animationSamples,
              runtimeErrors: newRuntimeErrors,
              consoleErrors: newConsoleErrors,
              consoleWarnings: newConsoleWarnings,
              badResponses: newBadResponses
            }
            results.push(result)

            if (screenshotMode === 'all' || (!result.passed && screenshotMode === 'failures')) {
              const { data } = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
              const screenshotPath = join(screenshotRoot, `${group.role}-${viewportName}-${theme}-${sanitizeFileName(route)}.png`)
              writeFileSync(screenshotPath, Buffer.from(data, 'base64'), { flag: 'wx', mode: 0o600 })
            }
            process.stdout.write(result.passed ? '.' : 'F')
          }
        }
      }
    }

    process.stdout.write('\n')
    if (!results.length) throw new Error(`No routes matched CECSMS_QA_ROUTES=${requestedRoutes.join(',')}`)
    const failures = results.filter((result) => !result.passed)
    const report = {
      generatedAt: new Date().toISOString(),
      durationMs: Date.now() - auditStartedAt,
      browser: basename(edgePath),
      screenshotDirectory: screenshotRoot,
      cases: results.length,
      passed: results.length - failures.length,
      failed: failures.length,
      reducedMotion: reducedMotionEnabled,
      performanceAudits: performanceAuditEnabled
        ? results.map(({ route, viewport, theme, animationSamples }) => ({ route, viewport, theme, animationSamples }))
        : [],
      failures
    }
    if (reportPath) {
      mkdirSync(dirname(reportPath), { recursive: true })
      writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, { flag: 'w', mode: 0o600 })
    }
    console.log(JSON.stringify(report, null, 2))
    if (failures.length) process.exitCode = 1
  } finally {
    if (client) {
      try { await client.send('Browser.close') } catch { /* browser may already be closed */ }
      client.close()
    }
    if (edgeProcess && !(await waitForProcessExit(edgeProcess))) {
      edgeProcess.kill()
      await waitForProcessExit(edgeProcess)
    }
    if (viteProcess && viteProcess.exitCode === null) {
      viteProcess.kill()
      await waitForProcessExit(viteProcess)
    }
    if (browserProfile && browserProfile.startsWith(resolve(tmpdir())) && basename(browserProfile).startsWith('silverpilot-edge-audit-')) {
      try {
        rmSync(browserProfile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 })
      } catch (error) {
        console.warn(`Browser profile cleanup deferred: ${compactMessage(error.message, 180)}`)
      }
    }
  }
}

await run()
