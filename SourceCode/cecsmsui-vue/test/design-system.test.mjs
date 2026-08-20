import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { isRenderableRecipeImage, optimizedImageUrl } from '../src/utils/media.js'

const css = await readFile(new URL('../src/assets/main.css', import.meta.url), 'utf8')

const block = (selector) => {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return css.match(new RegExp(`${escaped}\\s*\\{([\\s\\S]*?)\\n\\}`))?.[1] || ''
}

const tokens = (source) => Object.fromEntries(
  [...source.matchAll(/(--sp-[\w-]+):\s*(#[\da-fA-F]{6})\s*;/g)].map((match) => [match[1], match[2]])
)

const luminance = (hex) => {
  const channels = hex.slice(1).match(/.{2}/g).map((value) => Number.parseInt(value, 16) / 255)
    .map((value) => value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
}

const contrast = (foreground, background) => {
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (lighter + 0.05) / (darker + 0.05)
}

const listVueFiles = async (directory) => {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? listVueFiles(path) : (entry.name.endsWith('.vue') ? [path] : [])
  }))
  return nested.flat()
}

test('fixed-theme design tokens satisfy core WCAG AA contrast pairs', () => {
  const light = tokens(block(':root'))
  const pairs = [
    ['--sp-text', '--sp-bg'],
    ['--sp-text', '--sp-surface'],
    ['--sp-text-secondary', '--sp-surface'],
    ['--sp-text-muted', '--sp-surface'],
    ['--sp-on-brand', '--sp-brand'],
    ['--sp-warning', '--sp-warning-soft'],
    ['--sp-on-night', '--sp-night'],
    ['--sp-on-night-muted', '--sp-night']
  ]
  for (const [foreground, background] of pairs) {
    assert.ok(
      contrast(light[foreground], light[background]) >= 4.5,
      `${foreground} on ${background} must reach 4.5:1`
    )
  }
  assert.doesNotMatch(css, /:root\[data-theme=['"]dark['"]\]/)
})

test('global interaction contract includes focus, reduced motion and mobile targets', () => {
  assert.match(css, /:focus-visible\s*\{/)
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/)
  assert.match(css, /@media \(max-width: 640px\)[\s\S]*?\.el-button\s*\{\s*min-height: 44px;/)
  assert.match(css, /\.skip-link:focus/)
})

test('product page families keep restrained motion, Apple-like tokens and asset ownership explicit', async () => {
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  const homepage = await readFile(new URL('../src/components/front/home/FrontHomeView.vue', import.meta.url), 'utf8')
  const spotlight = await readFile(new URL('../src/components/front/home/SpotlightReveal.vue', import.meta.url), 'utf8')
  const publicHeader = await readFile(new URL('../src/components/front/home/FrontHeaderMenu.vue', import.meta.url), 'utf8')
  const entry = await readFile(new URL('../src/main.js', import.meta.url), 'utf8')
  const files = await listVueFiles(fileURLToPath(new URL('../src', import.meta.url)))

  assert.match(css, /--sp-gradient-aurora:/)
  assert.match(css, /--sp-ease-expressive:/)
  assert.match(css, /--sp-bg:\s*#f5f5f7/)
  assert.match(css, /--sp-text:\s*#1d1d1f/)
  assert.match(css, /--sp-brand:\s*#0066cc/)
  assert.match(css, /\.route-family-(?:auth|assistant|admin|account|public)/)
  assert.match(css, /\.el-button--primary\.is-plain/)
  assert.match(css, /\.el-button--primary:not\(\.is-plain\):not\(\.is-link\):not\(\.is-text\)/)
  assert.match(app, /const routeFamily = computed/)
  assert.match(app, /class="surface-backdrop"/)
  assert.doesNotMatch(app, /class="motion-atmosphere"/)
  assert.match(app, /:data-route-family="routeFamily"/)
  assert.match(homepage, /class="product-hero"/)
  assert.match(homepage, /<SpotlightReveal/)
  assert.match(homepage, /const SPOTLIGHT_R = 260/)
  assert.match(homepage, /smooth\.value\.x \+= \(mouse\.value\.x - smooth\.value\.x\) \* 0\.1/)
  assert.match(homepage, /window\.requestAnimationFrame\(animateSpotlight\)/)
  assert.match(spotlight, /canvas\.toDataURL\(\)/)
  assert.match(spotlight, /gradient\.addColorStop\(0\.88, 'rgba\(255,255,255,0\.12\)'\)/)
  assert.match(spotlight, /reveal\.style\.webkitMaskImage = maskImage/)
  assert.match(publicHeader, /'hero-mode': showHeroHeader/)
  assert.match(entry, /@fontsource\/inter\/latin-300\.css/)
  assert.match(entry, /@fontsource\/playfair-display\/latin-600-italic\.css/)
  assert.match(css, /@keyframes heroReveal/)
  assert.match(css, /@keyframes heroZoom/)

  for (const file of files) {
    const source = await readFile(file, 'utf8')
    assert.doesNotMatch(
      source,
      /https?:\/\/(?:www\.)?motionsites\.ai|pub-bb2e103a32db4e198524a2e9ed8f35b4\.r2\.dev/i,
      `${file} must not hotlink or copy reference-site assets`
    )
  }
})

test('Element Plus styles load before project tokens to prevent route style flashes', async () => {
  const main = await readFile(new URL('../src/main.js', import.meta.url), 'utf8')
  assert.ok(main.indexOf("element-plus/dist/index.css") < main.indexOf("./assets/main.css"))
})

test('table columns use supported pixel widths instead of invalid percentages', async () => {
  const files = await listVueFiles(fileURLToPath(new URL('../src', import.meta.url)))
  for (const file of files) {
    const source = await readFile(file, 'utf8')
    assert.doesNotMatch(source, /<el-table-column[^>]+width="\d+%"/, file)
    assert.doesNotMatch(
      source,
      /:global\(:root[^)]*\)\s+[^,{]+\{/,
      `${file} must keep the complete descendant selector inside :global(...)`
    )
  }
})

test('Element Plus bindings avoid known render warnings and invalid pagination contracts', async () => {
  const files = await listVueFiles(fileURLToPath(new URL('../src', import.meta.url)))
  for (const file of files) {
    const source = await readFile(file, 'utf8')
    assert.doesNotMatch(source, /<el-option[^>]+:value="null"/, `${file} must not pass null to el-option`)
    assert.doesNotMatch(source, /<el-button[^>]+type="text"/, `${file} must use the supported link/text binding`)
    assert.doesNotMatch(source, /<el-pagination[^>]+v-model:total=/, `${file} must treat pagination total as read-only`)
    assert.doesNotMatch(source, /<el-pagination[^>]+:small="small"/, `${file} must use the supported size prop`)
    assert.doesNotMatch(source, /:visible\.sync=/, `${file} must use Vue 3 v-model arguments`)
    assert.doesNotMatch(source, /<el-(?:radio|radio-button)\b[^>]*\blabel=/, `${file} must use value instead of the deprecated radio label value`)
    assert.doesNotMatch(source, /<el-(?:checkbox|checkbox-button)\b[^>]*\b(?:true-label|false-label)=/, `${file} must use true-value/false-value`)
    assert.doesNotMatch(source, /\bcustom-class=/, `${file} must use the supported class binding`)
    for (const comment of source.match(/<!--[\s\S]*?-->/g) || []) {
      assert.doesNotMatch(
        comment,
        /<el-|<template\b|<[A-Z][A-Za-z]+\b|:\w+=|@\w+=/,
        `${file} must not retain disabled component code in comments`
      )
    }
    assert.ok(source.trim().length > 0, `${file} must not be an empty placeholder`)
  }
})

test('application shell provides one global Chinese Element Plus configuration', async () => {
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  assert.match(app, /<el-config-provider\s+:locale="zhCn"\s+:message="messageConfig">/)
  assert.match(app, /grouping:\s*true/)
  assert.match(app, /max:\s*2/)
  assert.match(app, /element-plus\/dist\/locale\/zh-cn\.mjs/)
  assert.match(app, /<el-button type="primary" @click="reloadPage">/)
})

test('chart and admin shell include the display-critical registrations and tablet breakpoint', async () => {
  const echarts = await readFile(new URL('../src/utils/echarts.js', import.meta.url), 'utf8')
  const adminShell = await readFile(new URL('../src/views/HomeView.vue', import.meta.url), 'utf8')
  const adminHeader = await readFile(new URL('../src/components/serve/home/HomeHeader.vue', import.meta.url), 'utf8')

  assert.match(echarts, /GraphicComponent/)
  assert.match(adminShell, /matchMedia\('\(max-width: 900px\)'\)/)
  assert.match(adminShell, /@media \(max-width: 900px\)/)
  assert.match(adminHeader, /@media \(max-width: 900px\)/)
})

test('application shell handles route chunk failures and offline recovery without a blank page', async () => {
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  assert.match(app, /router\.onError/)
  assert.match(app, /addEventListener\('offline'/)
  assert.match(app, /class="network-status"/)
  assert.match(app, /class="route-error"/)
})

test('bundled edge proxy overwrites untrusted forwarding headers', async () => {
  const nginx = await readFile(new URL('../docker/nginx.conf', import.meta.url), 'utf8')
  assert.match(nginx, /proxy_set_header X-Forwarded-For \$remote_addr;/)
  assert.doesNotMatch(nginx, /proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;/)
})

test('recipe media renders only when a non-empty path is present', () => {
  assert.equal(isRenderableRecipeImage('/image/20260430/db135a6ca652498f9dc8d9c4429383a5.png'), true)
  assert.equal(isRenderableRecipeImage('http://localhost:8083/image/20260430/8001357b86af4903addd5e813ba010b1.png'), true)
  assert.equal(isRenderableRecipeImage(''), false)
  assert.equal(isRenderableRecipeImage('   '), false)
})

test('repository images prefer WebP companions while retaining safe passthroughs', () => {
  assert.equal(optimizedImageUrl('/image/20260327/24.png'), '/image/20260327/24.webp')
  assert.equal(optimizedImageUrl('/image/example.JPG?version=2'), '/image/example.webp?version=2')
  assert.equal(optimizedImageUrl('data:image/png;base64,abc'), 'data:image/png;base64,abc')
  assert.equal(optimizedImageUrl('/image/example.webp'), '/image/example.webp')
})
