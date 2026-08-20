<template>
  <canvas
    ref="canvasRef"
    class="spotlight-canvas"
    aria-hidden="true"
  ></canvas>
  <div
    ref="revealRef"
    class="spotlight-reveal"
    :style="{ backgroundImage: `url(${image})` }"
    aria-hidden="true"
  ></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  image: {
    type: String,
    required: true
  },
  cursorX: {
    type: Number,
    required: true
  },
  cursorY: {
    type: Number,
    required: true
  },
  radius: {
    type: Number,
    default: 260
  }
})

const canvasRef = ref()
const revealRef = ref()

const drawMask = () => {
  const canvas = canvasRef.value
  const reveal = revealRef.value
  const context = canvas?.getContext('2d')
  if (!canvas || !reveal || !context) return

  context.clearRect(0, 0, canvas.width, canvas.height)
  const gradient = context.createRadialGradient(
    props.cursorX,
    props.cursorY,
    0,
    props.cursorX,
    props.cursorY,
    props.radius
  )
  gradient.addColorStop(0, 'rgba(255,255,255,1)')
  gradient.addColorStop(0.4, 'rgba(255,255,255,1)')
  gradient.addColorStop(0.6, 'rgba(255,255,255,0.75)')
  gradient.addColorStop(0.75, 'rgba(255,255,255,0.4)')
  gradient.addColorStop(0.88, 'rgba(255,255,255,0.12)')
  gradient.addColorStop(1, 'rgba(255,255,255,0)')

  context.beginPath()
  context.arc(props.cursorX, props.cursorY, props.radius, 0, Math.PI * 2)
  context.fillStyle = gradient
  context.fill()

  const maskImage = `url("${canvas.toDataURL()}")`
  reveal.style.maskImage = maskImage
  reveal.style.webkitMaskImage = maskImage
  reveal.style.maskSize = '100% 100%'
  reveal.style.webkitMaskSize = '100% 100%'
}

const resizeCanvas = () => {
  if (!canvasRef.value) return
  canvasRef.value.width = window.innerWidth
  canvasRef.value.height = window.innerHeight
  drawMask()
}

watch(
  () => [props.cursorX, props.cursorY, props.radius],
  drawMask,
  { flush: 'post' }
)

onMounted(() => {
  resizeCanvas()
  window.addEventListener('resize', resizeCanvas)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCanvas)
})
</script>

<style scoped>
.spotlight-canvas {
  position: absolute;
  inset: 0;
  display: none;
  pointer-events: none;
}

.spotlight-reveal {
  position: absolute;
  z-index: 30;
  inset: 0;
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
  pointer-events: none;
}
</style>
