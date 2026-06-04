<template>
  <div class="process-panel">
    <h2 class="panel-title">工艺流程监控</h2>
    <canvas ref="canvasRef" class="process-canvas" @click="onCanvasClick"></canvas>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  stageData: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['stage-click'])

const canvasRef = ref(null)
let animFrameId = null
let flowAnimOffset = 0
let offscreenCanvas = null
let offscreenCtx = null
let lastDrawnStatus = {}
let canvasW = 0
let canvasH = 420

const STAGES = [
  { key: 'raw_water', name: '原水监测站', x: 60, y: 80, w: 140, h: 80 },
  { key: 'flocculation', name: '絮凝池', x: 280, y: 80, w: 140, h: 80 },
  { key: 'sedimentation', name: '沉淀池', x: 500, y: 80, w: 140, h: 80 },
  { key: 'filtration', name: '滤池', x: 280, y: 280, w: 140, h: 80 },
  { key: 'outlet', name: '出水', x: 500, y: 280, w: 140, h: 80 }
]

const PIPES = [
  { from: 0, to: 1 },
  { from: 1, to: 2 },
  { from: 2, to: 3 },
  { from: 3, to: 4 }
]

function getStatusColor(status) {
  if (status === 'normal') return { fill: 'rgba(76,175,80,0.25)', stroke: '#4caf50', text: '#4caf50' }
  if (status === 'warning') return { fill: 'rgba(255,179,0,0.25)', stroke: '#ffb300', text: '#ffb300' }
  if (status === 'alarm') return { fill: 'rgba(244,67,54,0.25)', stroke: '#f44336', text: '#f44336' }
  return { fill: 'rgba(144,164,174,0.15)', stroke: '#607d8b', text: '#607d8b' }
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.lineTo(x + w - r, y)
  ctx.quadraticCurveTo(x + w, y, x + w, y + r)
  ctx.lineTo(x + w, y + h - r)
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h)
  ctx.lineTo(x + r, y + h)
  ctx.quadraticCurveTo(x, y + h, x, y + h - r)
  ctx.lineTo(x, y + r)
  ctx.quadraticCurveTo(x, y, x + r, y)
  ctx.closePath()
}

function cacheStaticLayer(W, H) {
  const dpr = window.devicePixelRatio || 1
  offscreenCanvas = document.createElement('canvas')
  offscreenCanvas.width = W * dpr
  offscreenCanvas.height = H * dpr
  offscreenCtx = offscreenCanvas.getContext('2d')
  offscreenCtx.scale(dpr, dpr)

  const scaleX = W / 720
  const scaleY = H / 420

  offscreenCtx.strokeStyle = '#1e3a5c'
  offscreenCtx.lineWidth = 0.5
  for (let x = 0; x < W; x += 40) {
    offscreenCtx.beginPath(); offscreenCtx.moveTo(x, 0); offscreenCtx.lineTo(x, H); offscreenCtx.stroke()
  }
  for (let y = 0; y < H; y += 40) {
    offscreenCtx.beginPath(); offscreenCtx.moveTo(0, y); offscreenCtx.lineTo(W, y); offscreenCtx.stroke()
  }

  PIPES.forEach(pipe => {
    const from = STAGES[pipe.from]
    const to = STAGES[pipe.to]
    const fx = (from.x + from.w / 2) * scaleX
    const fy = (from.y + from.h / 2) * scaleY
    const tx = (to.x + to.w / 2) * scaleX
    const ty = (to.y + to.h / 2) * scaleY
    const isH = Math.abs(fy - ty) < 30

    offscreenCtx.beginPath()
    offscreenCtx.strokeStyle = '#1565c0'
    offscreenCtx.lineWidth = 6
    offscreenCtx.lineCap = 'round'
    offscreenCtx.lineJoin = 'round'

    if (isH) {
      offscreenCtx.moveTo(fx + from.w / 2 * scaleX * 0.5, fy)
      offscreenCtx.lineTo(tx - to.w / 2 * scaleX * 0.5, ty)
    } else {
      offscreenCtx.moveTo(fx + from.w / 2 * scaleX * 0.5, fy)
      offscreenCtx.lineTo(fx + from.w / 2 * scaleX * 0.5 + (tx - fx) * 0.3, fy)
      offscreenCtx.lineTo(tx - to.w / 2 * scaleX * 0.5 - (tx - fx) * 0.3, ty)
      offscreenCtx.lineTo(tx - to.w / 2 * scaleX * 0.5, ty)
    }
    offscreenCtx.stroke()
  })
}

function needsRedraw() {
  const currentStatus = {}
  STAGES.forEach(s => {
    currentStatus[s.key] = props.stageData[s.key]?.status || 'unknown'
  })
  const changed = JSON.stringify(currentStatus) !== JSON.stringify(lastDrawnStatus)
  if (changed) lastDrawnStatus = currentStatus
  return changed
}

let stageLayerCache = null
let stageLayerDirty = true

watch(() => props.stageData, () => { stageLayerDirty = true }, { deep: true })

function drawFrame() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1
  const rect = canvas.getBoundingClientRect()

  if (canvasW !== rect.width) {
    canvasW = rect.width
    canvas.width = rect.width * dpr
    canvas.height = canvasH * dpr
    ctx.scale(dpr, dpr)
    canvas.style.height = canvasH + 'px'
    cacheStaticLayer(rect.width, canvasH)
    stageLayerDirty = true
  }

  const W = rect.width
  const H = canvasH

  ctx.clearRect(0, 0, W, H)

  if (offscreenCanvas) {
    ctx.drawImage(offscreenCanvas, 0, 0, W, H)
  }

  const scaleX = W / 720
  const scaleY = H / 420

  PIPES.forEach(pipe => {
    const from = STAGES[pipe.from]
    const to = STAGES[pipe.to]
    const fx = (from.x + from.w / 2) * scaleX
    const fy = (from.y + from.h / 2) * scaleY
    const tx = (to.x + to.w / 2) * scaleX
    const ty = (to.y + to.h / 2) * scaleY
    const isH = Math.abs(fy - ty) < 30

    ctx.setLineDash([8, 12])
    ctx.lineDashOffset = -flowAnimOffset
    ctx.beginPath()
    ctx.strokeStyle = 'rgba(66,165,245,0.8)'
    ctx.lineWidth = 3
    if (isH) {
      ctx.moveTo(fx + from.w / 2 * scaleX * 0.5, fy)
      ctx.lineTo(tx - to.w / 2 * scaleX * 0.5, ty)
    } else {
      ctx.moveTo(fx + from.w / 2 * scaleX * 0.5, fy)
      ctx.lineTo(fx + from.w / 2 * scaleX * 0.5 + (tx - fx) * 0.3, fy)
      ctx.lineTo(tx - to.w / 2 * scaleX * 0.5 - (tx - fx) * 0.3, ty)
      ctx.lineTo(tx - to.w / 2 * scaleX * 0.5, ty)
    }
    ctx.stroke()
    ctx.setLineDash([])

    const lastPt = isH
      ? { x: tx - to.w / 2 * scaleX * 0.5, y: ty }
      : { x: tx - to.w / 2 * scaleX * 0.5, y: ty }
    const prevPt = isH
      ? { x: fx + from.w / 2 * scaleX * 0.5, y: fy }
      : { x: tx - to.w / 2 * scaleX * 0.5 - (tx - fx) * 0.3, y: ty }
    const angle = Math.atan2(lastPt.y - prevPt.y, lastPt.x - prevPt.x)
    ctx.save()
    ctx.translate(lastPt.x, lastPt.y)
    ctx.rotate(angle)
    ctx.fillStyle = '#42a5f5'
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(-12, -6)
    ctx.lineTo(-12, 6)
    ctx.closePath()
    ctx.fill()
    ctx.restore()
  })

  STAGES.forEach(stage => {
    const x = stage.x * scaleX
    const y = stage.y * scaleY
    const w = stage.w * scaleX
    const h = stage.h * scaleY
    const data = props.stageData[stage.key]
    const status = data ? data.status : 'unknown'
    const colors = getStatusColor(status)

    ctx.shadowColor = colors.stroke
    ctx.shadowBlur = 12
    ctx.fillStyle = colors.fill
    ctx.strokeStyle = colors.stroke
    ctx.lineWidth = 2
    roundRect(ctx, x, y, w, h, 8)
    ctx.fill()
    ctx.stroke()
    ctx.shadowBlur = 0

    ctx.fillStyle = '#e0e0e0'
    ctx.font = `bold ${Math.max(13, 14 * scaleX)}px Microsoft YaHei`
    ctx.textAlign = 'center'
    ctx.fillText(stage.name, x + w / 2, y + 24 * scaleY)

    ctx.font = `${Math.max(10, 11 * scaleX)}px Microsoft YaHei`
    ctx.fillStyle = colors.text
    if (data && data.turbidity != null) {
      ctx.fillText('浊度: ' + data.turbidity.toFixed(2) + ' NTU', x + w / 2, y + 42 * scaleY)
      ctx.fillText('pH: ' + (data.ph ? data.ph.toFixed(1) : '--'), x + w / 2, y + 56 * scaleY)
      if (data.flowRate) {
        ctx.fillStyle = '#90a4ae'
        ctx.fillText('流量: ' + data.flowRate.toFixed(0) + ' m³/h', x + w / 2, y + 70 * scaleY)
      }
    } else {
      ctx.fillText('暂无数据', x + w / 2, y + h / 2 + 4)
    }
  })

  flowAnimOffset = (flowAnimOffset + 1.5) % 40
}

function animate() {
  drawFrame()
  animFrameId = requestAnimationFrame(animate)
}

function onCanvasClick(e) {
  const rect = canvasRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  const scaleX = rect.width / 720
  const scaleY = canvasH / 420

  STAGES.forEach(stage => {
    const sx = stage.x * scaleX
    const sy = stage.y * scaleY
    const sw = stage.w * scaleX
    const sh = stage.h * scaleY
    if (x >= sx && x <= sx + sw && y >= sy && y <= sy + sh) {
      emit('stage-click', stage.key)
    }
  })
}

onMounted(() => { animate() })
onUnmounted(() => { if (animFrameId) cancelAnimationFrame(animFrameId) })
</script>

<style scoped>
.process-panel {
  background: #0d2137;
  border-radius: 8px;
  border: 1px solid #1e3a5c;
  padding: 12px;
}
.panel-title {
  font-size: 14px;
  color: #4fc3f7;
  margin-bottom: 8px;
}
.process-canvas {
  width: 100%;
  cursor: pointer;
}
</style>
