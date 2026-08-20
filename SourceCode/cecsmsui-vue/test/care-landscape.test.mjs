import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = fileURLToPath(new URL('..', import.meta.url))
const homepage = await readFile(new URL('../src/components/front/home/FrontHomeView.vue', import.meta.url), 'utf8')
const landscape = await readFile(new URL('../src/components/front/home/CareLandscape.vue', import.meta.url), 'utf8')
const threeFacade = await readFile(new URL('../src/utils/careLandscapeThree.js', import.meta.url), 'utf8')
const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))

const listSourceFiles = async (directory) => {
  const entries = await readdir(directory, { withFileTypes: true })
  const children = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? listSourceFiles(path) : [path]
  }))
  return children.flat()
}

test('homepage embeds the care landscape with working project navigation events', () => {
  assert.match(homepage, /import CareLandscape from '\.\/CareLandscape\.vue'/)
  assert.match(homepage, /<CareLandscape/)
  assert.match(homepage, /@open-agent="goAgent"/)
  assert.match(homepage, /@open-services="goMore\('communityService'\)"/)
})

test('care landscape is self-contained, lazy-loaded and reports its animation state', () => {
  assert.equal(packageJson.dependencies.three, '^0.185.1')
  assert.match(landscape, /await import\('@\/utils\/careLandscapeThree\.js'\)/)
  assert.match(threeFacade, /WebGLRenderer/)
  assert.match(landscape, /IntersectionObserver/)
  assert.match(landscape, /ResizeObserver/)
  assert.match(landscape, /dataset\.animationActive = 'true'/)
  assert.match(landscape, /dataset\.animationActive = 'false'/)
  assert.match(landscape, /cancelAnimationFrame\(animationFrameId\)/)
  assert.match(landscape, /renderer\.forceContextLoss\(\)/)
  assert.doesNotMatch(landscape, /<img\b|<video\b|https?:\/\//i)
})

test('care landscape includes reduced-motion, fallback and accessible control contracts', () => {
  assert.match(landscape, /prefers-reduced-motion: reduce/)
  assert.match(landscape, /renderMode\.value = 'static'/)
  assert.match(landscape, /class="static-landscape"/)
  assert.match(landscape, /aria-live="polite"/)
  assert.match(landscape, /aria-pressed="activeModeIndex === index"/)
  assert.match(landscape, /交互图景用于说明平台能力，不代表实时健康监测或紧急呼叫状态/)
})

test('shipped frontend contains no copied reference names, URLs or asset paths', async () => {
  const files = await listSourceFiles(join(frontendRoot, 'src'))
  for (const file of files) {
    const source = await readFile(file, 'utf8')
    assert.doesNotMatch(source, /MengTo\/sylva|github\.com\/MengTo\/sylva|\bsylva\b/i, file)
    assert.doesNotMatch(source, /moss|fern|pollen|liquid[-_ ]?metal/i, file)
  }
})
