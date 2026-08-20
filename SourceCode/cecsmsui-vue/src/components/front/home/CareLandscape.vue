<template>
  <section
    ref="sectionRef"
    class="care-landscape"
    :style="sceneStyle"
    :data-render-mode="renderMode"
    aria-labelledby="care-landscape-title"
  >
    <div class="landscape-copy">
      <span class="landscape-eyebrow">SilverPilot · 全天照护图景</span>
      <h2 id="care-landscape-title">一天里的照护，<br><em>都有回应。</em></h2>
      <p class="landscape-intro">
        从晨间活动到夜间守护，把社区服务、健康协助与生活安排放进同一张清晰的照护地图。
      </p>

      <div class="time-switcher" role="group" aria-label="切换照护时段">
        <button
          v-for="(mode, index) in careModes"
          :key="mode.id"
          type="button"
          :class="{ active: activeModeIndex === index }"
          :aria-pressed="activeModeIndex === index"
          @click="selectMode(index)"
        >
          <span>{{ mode.time }}</span>
          <strong>{{ mode.label }}</strong>
          <small>{{ mode.description }}</small>
        </button>
      </div>

      <div class="landscape-actions">
        <button type="button" class="landscape-primary" @click="emit('open-agent')">
          让小伴规划今天
          <span aria-hidden="true">↗</span>
        </button>
        <button type="button" class="landscape-secondary" @click="emit('open-services')">
          查看全部服务
        </button>
      </div>

      <ul class="care-capabilities" aria-label="照护能力">
        <li><span aria-hidden="true"></span>社区活动</li>
        <li><span aria-hidden="true"></span>上门服务</li>
        <li><span aria-hidden="true"></span>健康协助</li>
        <li><span aria-hidden="true"></span>膳食安排</li>
      </ul>
    </div>

    <div class="landscape-stage">
      <div ref="canvasMountRef" class="landscape-canvas-shell" aria-hidden="true">
        <div class="static-landscape">
          <div class="static-horizon"></div>
          <div class="static-grid"></div>
          <span v-for="node in staticNodes" :key="node.className" :class="['static-node', node.className]"></span>
          <span class="static-hub"></span>
        </div>
        <div v-if="renderMode === 'loading'" class="landscape-loader">
          <span></span>正在生成照护图景
        </div>
      </div>

      <div class="stage-topline">
        <span><i aria-hidden="true"></i>{{ activeMode.status }}</span>
        <span>{{ activeMode.time }}</span>
      </div>

      <button
        v-if="renderMode === 'webgl' && !prefersReducedMotion"
        type="button"
        class="motion-toggle"
        :aria-label="isMotionPaused ? '继续景观动态' : '暂停景观动态'"
        :aria-pressed="isMotionPaused"
        @click="toggleMotion"
      >
        <span aria-hidden="true">{{ isMotionPaused ? '▶' : 'Ⅱ' }}</span>
        {{ isMotionPaused ? '继续动态' : '暂停动态' }}
      </button>

      <div class="stage-caption">
        <span>{{ activeMode.signal }}</span>
        <strong>{{ activeMode.headline }}</strong>
        <small>{{ renderLabel }}</small>
      </div>

      <ol class="stage-legend" aria-label="图景节点说明">
        <li v-for="(label, index) in careNodeLabels" :key="label">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>{{ label }}
        </li>
      </ol>
    </div>

    <p class="landscape-disclaimer">
      交互图景用于说明平台能力，不代表实时健康监测或紧急呼叫状态。
    </p>
    <p class="visually-hidden" aria-live="polite">{{ statusAnnouncement }}</p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const emit = defineEmits(['open-agent', 'open-services'])

const careModes = [
  {
    id: 'morning',
    time: '07:00',
    label: '晨间活力',
    description: '活动与早餐提醒',
    status: '晨间服务已就绪',
    signal: '今日第一站',
    headline: '从一场舒展活动开始',
    skyStart: '#143d42',
    skyEnd: '#061a17',
    glow: '#f4c77c',
    fog: '#b8d8c7',
    hemiSky: '#ffdca3',
    hemiGround: '#17372e',
    key: '#fff1cf',
    fill: '#78c4ad',
    accent: '#74d4ad',
    signalColor: '#f3bd63',
    exposure: 1.08
  },
  {
    id: 'day',
    time: '13:30',
    label: '日常照护',
    description: '服务与健康协助',
    status: '日间照护持续响应',
    signal: '照护进行中',
    headline: '需要的服务，就在身边',
    skyStart: '#174959',
    skyEnd: '#071d25',
    glow: '#8ee1cf',
    fog: '#9dcfd1',
    hemiSky: '#c8f4f2',
    hemiGround: '#17313a',
    key: '#d9fffa',
    fill: '#66afdb',
    accent: '#6ed9c0',
    signalColor: '#8bc7ff',
    exposure: 1.02
  },
  {
    id: 'night',
    time: '21:00',
    label: '夜间守护',
    description: '安睡与家属安心',
    status: '夜间守护保持在线',
    signal: '一天的安心收尾',
    headline: '安静守护，也清楚可见',
    skyStart: '#172d4f',
    skyEnd: '#060b18',
    glow: '#9bafff',
    fog: '#7d93bd',
    hemiSky: '#a9bcff',
    hemiGround: '#11192b',
    key: '#dce4ff',
    fill: '#718ee8',
    accent: '#8ca5ff',
    signalColor: '#d4b4ff',
    exposure: 0.92
  }
]

const careNodeLabels = ['活动中心', '上门照护', '健康协助', '营养膳食', '邻里陪伴']
const staticNodes = [
  { className: 'node-one' },
  { className: 'node-two' },
  { className: 'node-three' },
  { className: 'node-four' },
  { className: 'node-five' }
]

const sectionRef = ref(null)
const canvasMountRef = ref(null)
const activeModeIndex = ref(1)
const renderMode = ref('idle')
const isMotionPaused = ref(false)
const prefersReducedMotion = ref(false)
const statusAnnouncement = ref('日常照护图景已选择')

const activeMode = computed(() => careModes[activeModeIndex.value])
const sceneStyle = computed(() => ({
  '--scene-sky-start': activeMode.value.skyStart,
  '--scene-sky-end': activeMode.value.skyEnd,
  '--scene-glow': activeMode.value.glow,
  '--scene-accent': activeMode.value.accent
}))
const renderLabel = computed(() => {
  if (prefersReducedMotion.value) return '已按系统设置使用静态图景'
  if (renderMode.value === 'webgl') return isMotionPaused.value ? '动态已暂停' : '拖动指针可轻微改变视角'
  if (renderMode.value === 'loading') return '正在准备交互图景'
  return '当前设备使用静态图景'
})

let intersectionObserver = null
let resizeObserver = null
let motionMediaQuery = null
let sceneState = null
let animationFrameId = 0
let lastFrameTime = 0
let isInView = false
let isInitializing = false
let disposed = false

const heightAt = (x, z) => {
  const warpedX = x + Math.sin(z * 1.18) * 0.32 + Math.cos((x + z) * 0.74) * 0.11
  const warpedZ = z + Math.cos(x * 1.07) * 0.28 - Math.sin((x - z) * 0.69) * 0.13
  const radius = Math.hypot(x, z)
  const edgeFade = Math.max(0, 1 - (radius / 5.15) ** 2.1)
  const broadShape = Math.sin(warpedX * 1.35) * 0.24 + Math.cos(warpedZ * 1.52) * 0.2
  const detail = Math.sin((warpedX + warpedZ) * 2.18) * 0.08 + Math.cos((warpedX - warpedZ) * 2.47) * 0.055
  const communityRise = Math.exp(-((x + 1.2) ** 2 + (z - 0.55) ** 2) / 2.6) * 0.54
  const quietValley = Math.exp(-((x - 1.5) ** 2 + (z + 0.9) ** 2) / 1.9) * 0.24
  return (broadShape + detail + communityRise - quietValley) * edgeFade - 0.42
}

const buildTerrainGeometry = (THREE) => {
  const rings = 42
  const segments = 96
  const positions = []
  const colors = []
  const indices = []
  const lowColor = new THREE.Color('#17352d')
  const highColor = new THREE.Color('#6f9d78')
  const wetColor = new THREE.Color('#296c66')
  const slopeColor = new THREE.Color('#8aa38b')

  for (let ring = 0; ring <= rings; ring += 1) {
    const radius = (ring / rings) * 5.15
    for (let segment = 0; segment <= segments; segment += 1) {
      const angle = (segment / segments) * Math.PI * 2
      const x = Math.cos(angle) * radius
      const z = Math.sin(angle) * radius
      const y = heightAt(x, z)
      const sampleDistance = 0.045
      const slope = Math.min(1, Math.hypot(
        heightAt(x + sampleDistance, z) - heightAt(x - sampleDistance, z),
        heightAt(x, z + sampleDistance) - heightAt(x, z - sampleDistance)
      ) * 8)
      const moisture = (Math.sin(x * 0.82 - z * 1.04) + Math.cos(z * 0.47 + x * 0.36) + 2) / 4
      const elevation = Math.max(0, Math.min(1, (y + 0.72) / 1.18))
      const color = lowColor.clone().lerp(highColor, elevation * 0.82)
      color.lerp(wetColor, moisture * 0.24)
      color.lerp(slopeColor, slope * 0.27)
      positions.push(x, y, z)
      colors.push(color.r, color.g, color.b)
    }
  }

  for (let ring = 0; ring < rings; ring += 1) {
    for (let segment = 0; segment < segments; segment += 1) {
      const rowLength = segments + 1
      const current = ring * rowLength + segment
      const next = current + rowLength
      indices.push(current, next, current + 1, next, next + 1, current + 1)
    }
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setIndex(indices)
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3))
  geometry.computeVertexNormals()
  geometry.computeBoundingSphere()
  return geometry
}

const addSceneObjects = (THREE, scene, state) => {
  const terrainGeometry = buildTerrainGeometry(THREE)
  const terrainMaterial = new THREE.MeshStandardMaterial({
    vertexColors: true,
    roughness: 0.84,
    metalness: 0.02,
    side: THREE.DoubleSide
  })
  const terrain = new THREE.Mesh(terrainGeometry, terrainMaterial)
  terrain.receiveShadow = false
  scene.add(terrain)
  state.disposables.add(terrainGeometry)
  state.disposables.add(terrainMaterial)

  const routeMaterial = new THREE.MeshBasicMaterial({
    color: activeMode.value.signalColor,
    transparent: true,
    opacity: 0.55,
    depthWrite: false
  })
  state.routeMaterial = routeMaterial
  state.disposables.add(routeMaterial)

  const nodePositions = [
    [-2.55, 0.55],
    [-0.75, -2.35],
    [2.15, -1.5],
    [2.65, 1.15],
    [0.35, 2.65]
  ]
  const hubPosition = new THREE.Vector3(-0.1, heightAt(-0.1, 0.15) + 0.2, 0.15)
  const nodeGeometry = new THREE.IcosahedronGeometry(0.12, 2)
  const haloGeometry = new THREE.TorusGeometry(0.23, 0.012, 8, 40)
  state.disposables.add(nodeGeometry)
  state.disposables.add(haloGeometry)

  nodePositions.forEach(([x, z], index) => {
    const y = heightAt(x, z) + 0.18
    const nodeMaterial = new THREE.MeshStandardMaterial({
      color: index % 2 ? '#d9fff3' : '#ffffff',
      emissive: activeMode.value.accent,
      emissiveIntensity: 0.8,
      roughness: 0.28,
      metalness: 0.12
    })
    const haloMaterial = new THREE.MeshBasicMaterial({
      color: activeMode.value.signalColor,
      transparent: true,
      opacity: 0.58,
      depthWrite: false
    })
    state.disposables.add(nodeMaterial)
    state.disposables.add(haloMaterial)
    const node = new THREE.Mesh(nodeGeometry, nodeMaterial)
    const halo = new THREE.Mesh(haloGeometry, haloMaterial)
    node.position.set(x, y, z)
    halo.position.set(x, y - 0.08, z)
    halo.rotation.x = -Math.PI / 2
    scene.add(node, halo)
    state.nodes.push({ node, halo, nodeMaterial, haloMaterial, phase: index * 1.17 })

    const end = new THREE.Vector3(x, y - 0.03, z)
    const middle = hubPosition.clone().lerp(end, 0.5)
    middle.y += 0.42 + index * 0.045
    const curve = new THREE.QuadraticBezierCurve3(hubPosition, middle, end)
    const routeGeometry = new THREE.TubeGeometry(curve, 32, 0.017, 6, false)
    const route = new THREE.Mesh(routeGeometry, routeMaterial)
    scene.add(route)
    state.disposables.add(routeGeometry)
  })

  const hubGeometry = new THREE.OctahedronGeometry(0.22, 2)
  const hubMaterial = new THREE.MeshStandardMaterial({
    color: '#ffffff',
    emissive: activeMode.value.accent,
    emissiveIntensity: 1.05,
    roughness: 0.2,
    metalness: 0.16
  })
  const hubRingGeometry = new THREE.TorusGeometry(0.42, 0.016, 8, 64)
  const hubRingMaterial = new THREE.MeshBasicMaterial({
    color: activeMode.value.signalColor,
    transparent: true,
    opacity: 0.7,
    depthWrite: false
  })
  const hub = new THREE.Mesh(hubGeometry, hubMaterial)
  const hubRing = new THREE.Mesh(hubRingGeometry, hubRingMaterial)
  hub.position.copy(hubPosition)
  hubRing.position.copy(hubPosition)
  hubRing.position.y -= 0.08
  hubRing.rotation.x = -Math.PI / 2
  scene.add(hub, hubRing)
  Object.assign(state, { hub, hubMaterial, hubRing, hubRingMaterial })
  ;[hubGeometry, hubMaterial, hubRingGeometry, hubRingMaterial].forEach((resource) => state.disposables.add(resource))

  const markerGeometry = new THREE.BoxGeometry(0.035, 0.18, 0.035)
  const markerMaterial = new THREE.MeshStandardMaterial({ color: '#79a98a', roughness: 0.76 })
  const markers = new THREE.InstancedMesh(markerGeometry, markerMaterial, 128)
  const dummy = new THREE.Object3D()
  const goldenAngle = Math.PI * (3 - Math.sqrt(5))
  for (let index = 0; index < 128; index += 1) {
    const radius = 0.7 + 4.05 * Math.sqrt((index + 0.5) / 128)
    const angle = index * goldenAngle
    const x = Math.cos(angle) * radius
    const z = Math.sin(angle) * radius
    const markerHeight = 0.5 + ((index * 37) % 23) / 32
    dummy.position.set(x, heightAt(x, z) + 0.06 * markerHeight, z)
    dummy.rotation.set(0, angle, ((index % 7) - 3) * 0.018)
    dummy.scale.set(1, markerHeight, 1)
    dummy.updateMatrix()
    markers.setMatrixAt(index, dummy.matrix)
  }
  markers.instanceMatrix.needsUpdate = true
  scene.add(markers)
  state.markers = markers
  state.disposables.add(markerGeometry)
  state.disposables.add(markerMaterial)
}

const applyPalette = (delta, immediate = false) => {
  if (!sceneState) return
  const target = activeMode.value
  const mix = immediate ? 1 : 1 - Math.exp(-delta * 2.8)
  const targetColors = {
    fog: target.fog,
    hemiSky: target.hemiSky,
    hemiGround: target.hemiGround,
    key: target.key,
    fill: target.fill,
    accent: target.accent,
    signal: target.signalColor
  }
  Object.entries(targetColors).forEach(([name, color]) => {
    sceneState.colors[name].lerp(sceneState.colorTargets[name].set(color), mix)
  })
  sceneState.scene.fog.color.copy(sceneState.colors.fog)
  sceneState.hemisphereLight.color.copy(sceneState.colors.hemiSky)
  sceneState.hemisphereLight.groundColor.copy(sceneState.colors.hemiGround)
  sceneState.keyLight.color.copy(sceneState.colors.key)
  sceneState.fillLight.color.copy(sceneState.colors.fill)
  sceneState.routeMaterial.color.copy(sceneState.colors.signal)
  sceneState.hubMaterial.emissive.copy(sceneState.colors.accent)
  sceneState.hubRingMaterial.color.copy(sceneState.colors.signal)
  sceneState.nodes.forEach(({ nodeMaterial, haloMaterial }) => {
    nodeMaterial.emissive.copy(sceneState.colors.accent)
    haloMaterial.color.copy(sceneState.colors.signal)
  })
  sceneState.renderer.toneMappingExposure += (target.exposure - sceneState.renderer.toneMappingExposure) * mix
}

const renderScene = (now, delta) => {
  if (!sceneState) return
  applyPalette(delta)
  const elapsed = now * 0.001
  const pointerMix = 1 - Math.exp(-delta * 3.2)
  sceneState.pointer.x += (sceneState.pointerTarget.x - sceneState.pointer.x) * pointerMix
  sceneState.pointer.y += (sceneState.pointerTarget.y - sceneState.pointer.y) * pointerMix
  sceneState.camera.position.x = sceneState.pointer.x * 0.46
  sceneState.camera.position.y = 5.35 - sceneState.pointer.y * 0.24
  sceneState.camera.position.z = 8.35
  sceneState.camera.lookAt(0, -0.3, 0)

  sceneState.nodes.forEach(({ node, halo, phase }, index) => {
    const pulse = 1 + Math.sin(elapsed * 1.25 + phase) * 0.045
    node.position.y += (heightAt(node.position.x, node.position.z) + 0.18 + Math.sin(elapsed * 0.72 + phase) * 0.035 - node.position.y) * 0.12
    node.rotation.y += delta * (0.16 + index * 0.012)
    halo.scale.setScalar(pulse)
    halo.material.opacity = 0.48 + Math.sin(elapsed * 1.05 + phase) * 0.12
  })
  sceneState.hub.rotation.y += delta * 0.24
  sceneState.hubRing.rotation.z += delta * 0.07
  sceneState.markers.rotation.y = Math.sin(elapsed * 0.08) * 0.008
  sceneState.renderer.render(sceneState.scene, sceneState.camera)
}

const stopAnimation = () => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId)
  animationFrameId = 0
  lastFrameTime = 0
  if (sceneState?.canvas) sceneState.canvas.dataset.animationActive = 'false'
}

const animationLoop = (now) => {
  if (!sceneState || !isInView || document.hidden || isMotionPaused.value || prefersReducedMotion.value) {
    stopAnimation()
    return
  }
  const delta = Math.min(lastFrameTime ? (now - lastFrameTime) / 1000 : 1 / 60, 0.05)
  lastFrameTime = now
  renderScene(now, delta)
  animationFrameId = requestAnimationFrame(animationLoop)
}

const startAnimation = () => {
  if (!sceneState || animationFrameId || !isInView || document.hidden || isMotionPaused.value || prefersReducedMotion.value) return
  sceneState.canvas.dataset.animationActive = 'true'
  lastFrameTime = 0
  animationFrameId = requestAnimationFrame(animationLoop)
}

const renderOneFrame = (immediatePalette = false) => {
  if (!sceneState) return
  if (immediatePalette) applyPalette(0, true)
  renderScene(performance.now(), 0)
}

const resizeScene = () => {
  if (!sceneState || !canvasMountRef.value) return
  const { width, height } = canvasMountRef.value.getBoundingClientRect()
  if (width < 1 || height < 1) return
  sceneState.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, width < 640 ? 1 : 1.5))
  sceneState.renderer.setSize(width, height, false)
  sceneState.camera.aspect = width / height
  sceneState.camera.updateProjectionMatrix()
  renderOneFrame()
}

const handlePointerMove = (event) => {
  if (!sceneState || !canvasMountRef.value) return
  const bounds = canvasMountRef.value.getBoundingClientRect()
  sceneState.pointerTarget.x = ((event.clientX - bounds.left) / bounds.width - 0.5) * 2
  sceneState.pointerTarget.y = ((event.clientY - bounds.top) / bounds.height - 0.5) * 2
}

const handlePointerLeave = () => {
  if (!sceneState) return
  sceneState.pointerTarget.set(0, 0)
}

const initialiseScene = async () => {
  if (sceneState || isInitializing || disposed || prefersReducedMotion.value || !canvasMountRef.value) return
  isInitializing = true
  renderMode.value = 'loading'
  let provisionalRenderer = null
  try {
    const THREE = await import('@/utils/careLandscapeThree.js')
    if (disposed || !canvasMountRef.value || prefersReducedMotion.value) return
    const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' })
    provisionalRenderer = renderer
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = activeMode.value.exposure
    renderer.setClearColor(0x000000, 0)
    renderer.domElement.className = 'care-landscape-canvas'
    renderer.domElement.dataset.animationActive = 'false'
    renderer.domElement.setAttribute('aria-hidden', 'true')

    const scene = new THREE.Scene()
    scene.fog = new THREE.FogExp2(activeMode.value.fog, 0.078)
    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 40)
    camera.position.set(0, 5.35, 8.35)
    camera.lookAt(0, -0.3, 0)
    const hemisphereLight = new THREE.HemisphereLight(activeMode.value.hemiSky, activeMode.value.hemiGround, 2.25)
    const keyLight = new THREE.DirectionalLight(activeMode.value.key, 3.35)
    keyLight.position.set(-2.4, 6.8, 4.2)
    const fillLight = new THREE.DirectionalLight(activeMode.value.fill, 1.2)
    fillLight.position.set(4.6, 2.8, -3.2)
    scene.add(hemisphereLight, keyLight, fillLight)

    const colorKeys = ['fog', 'hemiSky', 'hemiGround', 'key', 'fill', 'accent', 'signal']
    const initialColors = {
      fog: activeMode.value.fog,
      hemiSky: activeMode.value.hemiSky,
      hemiGround: activeMode.value.hemiGround,
      key: activeMode.value.key,
      fill: activeMode.value.fill,
      accent: activeMode.value.accent,
      signal: activeMode.value.signalColor
    }
    sceneState = {
      THREE,
      renderer,
      canvas: renderer.domElement,
      scene,
      camera,
      hemisphereLight,
      keyLight,
      fillLight,
      nodes: [],
      pointer: new THREE.Vector2(),
      pointerTarget: new THREE.Vector2(),
      colors: Object.fromEntries(colorKeys.map((key) => [key, new THREE.Color(initialColors[key])])),
      colorTargets: Object.fromEntries(colorKeys.map((key) => [key, new THREE.Color(initialColors[key])])),
      disposables: new Set()
    }
    addSceneObjects(THREE, scene, sceneState)
    canvasMountRef.value.append(renderer.domElement)
    canvasMountRef.value.addEventListener('pointermove', handlePointerMove, { passive: true })
    canvasMountRef.value.addEventListener('pointerleave', handlePointerLeave, { passive: true })
    resizeObserver = new ResizeObserver(resizeScene)
    resizeObserver.observe(canvasMountRef.value)
    resizeScene()
    renderMode.value = 'webgl'
    statusAnnouncement.value = `${activeMode.value.label}交互图景已加载`
    startAnimation()
    provisionalRenderer = null
  } catch (error) {
    if (sceneState) {
      disposeScene()
    } else if (provisionalRenderer) {
      provisionalRenderer.dispose()
      provisionalRenderer.forceContextLoss()
    }
    console.warn('交互照护图景不可用，已切换为静态版本', error)
    renderMode.value = 'static'
    statusAnnouncement.value = '当前设备已使用静态照护图景'
  } finally {
    isInitializing = false
  }
}

const disposeScene = () => {
  stopAnimation()
  resizeObserver?.disconnect()
  resizeObserver = null
  if (canvasMountRef.value) {
    canvasMountRef.value.removeEventListener('pointermove', handlePointerMove)
    canvasMountRef.value.removeEventListener('pointerleave', handlePointerLeave)
  }
  if (!sceneState) return
  sceneState.disposables.forEach((resource) => resource.dispose?.())
  sceneState.renderer.renderLists?.dispose()
  sceneState.renderer.dispose()
  sceneState.renderer.forceContextLoss()
  sceneState.canvas.remove()
  sceneState = null
}

const selectMode = (index) => {
  activeModeIndex.value = index
  statusAnnouncement.value = `${careModes[index].label}图景已选择：${careModes[index].description}`
  if (sceneState) {
    if (isMotionPaused.value || prefersReducedMotion.value) renderOneFrame(true)
    else startAnimation()
  }
}

const toggleMotion = () => {
  isMotionPaused.value = !isMotionPaused.value
  statusAnnouncement.value = isMotionPaused.value ? '照护图景动态已暂停' : '照护图景动态已继续'
  if (isMotionPaused.value) {
    stopAnimation()
    renderOneFrame()
  } else {
    startAnimation()
  }
}

const handleVisibilityChange = () => {
  if (document.hidden) stopAnimation()
  else startAnimation()
}

const handleMotionPreference = (event) => {
  prefersReducedMotion.value = event.matches
  if (event.matches) {
    stopAnimation()
    renderOneFrame(true)
    statusAnnouncement.value = '已按系统设置暂停景观动态'
  } else if (isInView) {
    if (sceneState) startAnimation()
    else initialiseScene()
  }
}

onMounted(() => {
  disposed = false
  const currentHour = new Date().getHours()
  activeModeIndex.value = currentHour < 11 ? 0 : currentHour < 19 ? 1 : 2
  motionMediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  prefersReducedMotion.value = motionMediaQuery.matches
  motionMediaQuery.addEventListener('change', handleMotionPreference)
  document.addEventListener('visibilitychange', handleVisibilityChange)

  if ('IntersectionObserver' in window) {
    intersectionObserver = new IntersectionObserver(([entry]) => {
      isInView = entry.isIntersecting
      if (isInView) {
        if (sceneState) startAnimation()
        else initialiseScene()
      } else {
        stopAnimation()
      }
    }, { rootMargin: '0px', threshold: 0.04 })
    intersectionObserver.observe(canvasMountRef.value)
  } else {
    isInView = true
    initialiseScene()
  }

  if (prefersReducedMotion.value) {
    renderMode.value = 'static'
    statusAnnouncement.value = '已按系统设置使用静态照护图景'
  }
})

onBeforeUnmount(() => {
  disposed = true
  intersectionObserver?.disconnect()
  intersectionObserver = null
  motionMediaQuery?.removeEventListener('change', handleMotionPreference)
  motionMediaQuery = null
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  disposeScene()
})
</script>

<style scoped>
.care-landscape {
  --scene-sky-start: #174959;
  --scene-sky-end: #071d25;
  --scene-glow: #8ee1cf;
  --scene-accent: #6ed9c0;
  position: relative;
  isolation: isolate;
  width: min(var(--sp-content-max), calc(100% - 40px));
  min-height: 740px;
  display: grid;
  grid-template-columns: minmax(340px, .82fr) minmax(480px, 1.18fr);
  gap: clamp(28px, 5vw, 76px);
  margin: 76px auto 0;
  overflow: hidden;
  padding: clamp(42px, 6vw, 82px);
  color: #f5fbf8;
  background:
    radial-gradient(circle at 88% 8%, color-mix(in srgb, var(--scene-glow) 20%, transparent), transparent 34%),
    linear-gradient(140deg, #0c1c18 0%, #07110f 52%, #050b0a 100%);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 40px;
  box-shadow: 0 36px 90px rgb(8 27 21 / 18%);
  transition: background-color 900ms ease;
}

.care-landscape::before {
  content: '';
  position: absolute;
  z-index: -1;
  inset: 0;
  opacity: .38;
  background-image: linear-gradient(rgb(255 255 255 / 2.8%) 1px, transparent 1px), linear-gradient(90deg, rgb(255 255 255 / 2.8%) 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: linear-gradient(to right, #000, transparent 72%);
}

.landscape-copy {
  position: relative;
  z-index: 4;
  align-self: center;
}

.landscape-eyebrow {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 11px;
  color: #c9e9dc;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: .035em;
  background: rgb(255 255 255 / 7%);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 999px;
}

.landscape-copy h2 {
  margin: 22px 0 0;
  color: #f7fbf9;
  font-size: clamp(48px, 5vw, 76px);
  font-weight: 680;
  line-height: .99;
  letter-spacing: -.065em;
}

.landscape-copy h2 em {
  color: #8ba39a;
  font-style: normal;
}

.landscape-intro {
  max-width: 540px;
  margin: 25px 0 0;
  color: #b6c7c0;
  font-size: clamp(16px, 1.35vw, 19px);
  line-height: 1.7;
}

.time-switcher {
  display: grid;
  gap: 7px;
  margin-top: 31px;
}

.time-switcher button {
  position: relative;
  min-height: 64px;
  display: grid;
  grid-template-columns: 54px 92px minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  padding: 10px 15px;
  color: #b8c9c2;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 16px;
  cursor: pointer;
  transition: color 240ms ease, background-color 240ms ease, border-color 240ms ease, transform 240ms ease;
}

.time-switcher button::before {
  content: '';
  position: absolute;
  left: 0;
  width: 3px;
  height: 24px;
  background: var(--scene-accent);
  border-radius: 99px;
  opacity: 0;
  transform: scaleY(.3);
  transition: opacity 240ms ease, transform 240ms ease;
}

.time-switcher button:hover {
  color: #f5fbf8;
  background: rgb(255 255 255 / 5%);
}

.time-switcher button.active {
  color: #fff;
  background: rgb(255 255 255 / 8%);
  border-color: rgb(255 255 255 / 12%);
}

.time-switcher button.active::before {
  opacity: 1;
  transform: scaleY(1);
}

.time-switcher span {
  font-variant-numeric: tabular-nums;
  color: #82978f;
  font-size: 12px;
}

.time-switcher strong {
  color: inherit;
  font-size: 14px;
  font-weight: 650;
}

.time-switcher small {
  color: #90a59d;
  font-size: 11px;
}

.landscape-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 28px;
}

.landscape-actions button {
  min-height: 48px;
  padding: 0 19px;
  font-size: 14px;
  font-weight: 650;
  border-radius: 999px;
  cursor: pointer;
  transition: transform 220ms ease, background-color 220ms ease, border-color 220ms ease;
}

.landscape-actions button:hover { transform: translateY(-1px); }
.landscape-primary { color: #06261e; background: #d9fff0; border: 1px solid #d9fff0; }
.landscape-primary span { margin-left: 5px; }
.landscape-secondary { color: #edf8f4; background: rgb(255 255 255 / 7%); border: 1px solid rgb(255 255 255 / 17%); }
.landscape-secondary:hover { background: rgb(255 255 255 / 11%); border-color: rgb(255 255 255 / 26%); }

.care-capabilities {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 17px;
  margin-top: 26px;
}

.care-capabilities li {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #82978f;
  font-size: 10px;
}

.care-capabilities li span {
  width: 5px;
  height: 5px;
  background: var(--scene-accent);
  border-radius: 50%;
  box-shadow: 0 0 9px color-mix(in srgb, var(--scene-accent) 75%, transparent);
}

.landscape-stage {
  position: relative;
  z-index: 2;
  align-self: stretch;
  min-height: 590px;
  overflow: hidden;
  background:
    radial-gradient(circle at 58% 38%, color-mix(in srgb, var(--scene-glow) 21%, transparent), transparent 28%),
    linear-gradient(180deg, var(--scene-sky-start), var(--scene-sky-end));
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 32px;
  box-shadow: inset 0 1px rgb(255 255 255 / 10%), 0 26px 70px rgb(0 0 0 / 30%);
  transition: background 900ms ease;
}

.landscape-stage::after {
  content: '';
  position: absolute;
  z-index: 2;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, rgb(0 0 0 / 4%), transparent 46%, rgb(2 8 7 / 48%));
}

.landscape-canvas-shell,
.static-landscape,
.landscape-canvas-shell :deep(.care-landscape-canvas) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.landscape-canvas-shell :deep(.care-landscape-canvas) {
  z-index: 1;
  display: block;
  opacity: 0;
  animation: reveal-canvas 700ms ease forwards;
  touch-action: pan-y;
}

.static-landscape {
  z-index: 0;
  overflow: hidden;
}

.static-horizon {
  position: absolute;
  left: -15%;
  right: -15%;
  bottom: -14%;
  height: 68%;
  background:
    radial-gradient(ellipse at 28% 18%, rgb(120 174 134 / 58%), transparent 19%),
    radial-gradient(ellipse at 68% 25%, rgb(57 113 92 / 60%), transparent 23%),
    linear-gradient(180deg, #416d5a, #163c32 44%, #0b241f);
  border-radius: 52% 49% 0 0 / 30% 28% 0 0;
  transform: perspective(520px) rotateX(54deg) scale(1.22);
  transform-origin: center bottom;
}

.static-grid {
  position: absolute;
  left: -18%;
  right: -18%;
  bottom: -3%;
  height: 58%;
  opacity: .23;
  background-image: linear-gradient(rgb(220 255 241 / 32%) 1px, transparent 1px), linear-gradient(90deg, rgb(220 255 241 / 28%) 1px, transparent 1px);
  background-size: 34px 27px;
  mask-image: radial-gradient(ellipse at center, #000, transparent 70%);
  transform: perspective(420px) rotateX(59deg);
  transform-origin: center bottom;
}

.static-node,
.static-hub {
  position: absolute;
  z-index: 2;
  width: 12px;
  height: 12px;
  background: #e3fff4;
  border: 2px solid rgb(255 255 255 / 70%);
  border-radius: 50%;
  box-shadow: 0 0 0 10px rgb(140 230 194 / 10%), 0 0 28px var(--scene-accent);
}

.static-hub { left: 49%; top: 55%; width: 17px; height: 17px; }
.node-one { left: 24%; top: 47%; }
.node-two { left: 37%; top: 72%; }
.node-three { left: 66%; top: 68%; }
.node-four { left: 76%; top: 43%; }
.node-five { left: 55%; top: 31%; }

.landscape-loader {
  position: absolute;
  z-index: 5;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  color: #c5d8d0;
  font-size: 11px;
  background: rgb(4 15 13 / 34%);
  backdrop-filter: blur(6px);
}

.landscape-loader span {
  width: 28px;
  height: 28px;
  border: 2px solid rgb(255 255 255 / 18%);
  border-top-color: #d9fff0;
  border-radius: 50%;
  animation: loader-spin 800ms linear infinite;
}

.stage-topline {
  position: absolute;
  z-index: 5;
  top: 20px;
  left: 21px;
  right: 21px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  color: rgb(238 250 245 / 74%);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  letter-spacing: .025em;
}

.stage-topline span { display: inline-flex; align-items: center; gap: 7px; }
.stage-topline i { width: 6px; height: 6px; background: var(--scene-accent); border-radius: 50%; box-shadow: 0 0 10px var(--scene-accent); }

.motion-toggle {
  position: absolute;
  z-index: 7;
  top: 48px;
  right: 17px;
  min-width: 44px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 12px;
  color: #e7f6f0;
  font-size: 10px;
  background: rgb(4 18 15 / 54%);
  border: 1px solid rgb(255 255 255 / 15%);
  border-radius: 999px;
  backdrop-filter: blur(14px);
  cursor: pointer;
}

.motion-toggle:hover { background: rgb(4 18 15 / 72%); }
.motion-toggle span { width: 12px; color: var(--scene-accent); font-size: 10px; }

.stage-caption {
  position: absolute;
  z-index: 5;
  left: 24px;
  bottom: 84px;
  display: grid;
  gap: 5px;
  pointer-events: none;
}

.stage-caption span { color: var(--scene-accent); font-size: 10px; font-weight: 650; letter-spacing: .04em; }
.stage-caption strong { max-width: 360px; color: #fff; font-size: clamp(20px, 2.2vw, 29px); font-weight: 620; letter-spacing: -.035em; }
.stage-caption small { color: rgb(225 242 235 / 63%); font-size: 9px; }

.stage-legend {
  position: absolute;
  z-index: 5;
  left: 24px;
  right: 24px;
  bottom: 22px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 5px;
  padding-top: 14px;
  border-top: 1px solid rgb(255 255 255 / 13%);
}

.stage-legend li {
  min-width: 0;
  display: grid;
  gap: 2px;
  color: rgb(232 246 240 / 67%);
  font-size: 8px;
  white-space: nowrap;
}

.stage-legend li span { color: var(--scene-accent); font-size: 8px; font-variant-numeric: tabular-nums; }

.landscape-disclaimer {
  position: absolute;
  right: clamp(42px, 6vw, 82px);
  bottom: 18px;
  margin: 0;
  color: rgb(194 214 206 / 54%);
  font-size: 9px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

@keyframes reveal-canvas { to { opacity: 1; } }
@keyframes loader-spin { to { transform: rotate(360deg); } }

@media (max-width: 1100px) {
  .care-landscape { grid-template-columns: minmax(310px, .9fr) minmax(390px, 1.1fr); padding: 48px 42px 56px; }
  .landscape-copy h2 { font-size: clamp(46px, 5.6vw, 64px); }
  .time-switcher button { grid-template-columns: 48px 82px minmax(0, 1fr); padding-inline: 11px; }
  .landscape-stage { min-height: 550px; }
  .landscape-disclaimer { right: 42px; }
}

@media (max-width: 860px) {
  .care-landscape {
    width: calc(100% - 24px);
    grid-template-columns: 1fr;
    gap: 36px;
    margin-top: 58px;
    padding: 48px 28px 58px;
    border-radius: 30px;
  }
  .landscape-copy { text-align: center; }
  .landscape-intro { margin-inline: auto; }
  .time-switcher { max-width: 620px; margin-inline: auto; text-align: left; }
  .landscape-actions, .care-capabilities { justify-content: center; }
  .landscape-stage { min-height: 570px; }
  .landscape-disclaimer { right: 28px; left: 28px; text-align: center; }
}

@media (max-width: 560px) {
  .care-landscape { min-height: 0; padding: 42px 16px 58px; }
  .landscape-eyebrow { font-size: 10px; }
  .landscape-copy h2 { font-size: clamp(43px, 13vw, 58px); }
  .landscape-intro { font-size: 15px; }
  .time-switcher button { grid-template-columns: 46px minmax(0, 1fr); min-height: 66px; }
  .time-switcher small { grid-column: 2; margin-top: -8px; }
  .landscape-actions { display: grid; }
  .landscape-actions button { width: 100%; }
  .care-capabilities { gap: 8px 12px; }
  .landscape-stage { min-height: 470px; border-radius: 23px; }
  .motion-toggle { top: 44px; right: 12px; padding-inline: 10px; }
  .stage-caption { left: 17px; bottom: 84px; }
  .stage-caption strong { max-width: 260px; font-size: 21px; }
  .stage-legend { left: 17px; right: 17px; grid-template-columns: repeat(3, minmax(0, 1fr)); row-gap: 7px; }
  .stage-legend li:nth-child(n + 4) { display: none; }
  .stage-topline { top: 16px; left: 16px; right: 16px; }
}

@media (prefers-reduced-motion: reduce) {
  .care-landscape *,
  .care-landscape *::before,
  .care-landscape *::after {
    scroll-behavior: auto !important;
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: .01ms !important;
  }
}
</style>
