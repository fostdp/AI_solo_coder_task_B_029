<template>
  <div class="modal-overlay" :class="{ active: visible }" @click.self="close">
    <div class="modal">
      <h3 class="modal-title">
        <span>{{ stageName }} - 近24小时趋势</span>
        <button class="close-btn" @click="close">&times;</button>
      </h3>
      <div class="chart-row">
        <div class="chart-box">
          <h4>水质趋势（近24小时）</h4>
          <canvas ref="wqCanvasRef" class="trend-canvas"></canvas>
        </div>
        <div class="chart-box">
          <h4>加药量变化（近24小时）</h4>
          <canvas ref="dosingCanvasRef" class="trend-canvas"></canvas>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useApi } from '../composables/useApi.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  stageKey: { type: String, default: '' }
})

const emit = defineEmits(['close'])

const wqCanvasRef = ref(null)
const dosingCanvasRef = ref(null)

const STAGE_NAMES = {
  raw_water: '原水监测站',
  flocculation: '絮凝池',
  sedimentation: '沉淀池',
  filtration: '滤池',
  outlet: '出水'
}

const stageName = ref('')

const { get } = useApi()

watch(() => props.visible, async (v) => {
  if (v && props.stageKey) {
    stageName.value = STAGE_NAMES[props.stageKey] || props.stageKey
    await nextTick()
    loadTrendData()
  }
})

async function loadTrendData() {
  try {
    const data = await get('/api/trend/' + props.stageKey)
    await nextTick()
    drawWaterQualityChart(data.waterQuality || [])
    drawDosingChart(data.dosing || [])
  } catch (e) {
    console.error('Failed to load trend data:', e)
  }
}

function drawTrendChart(canvas, labels, datasets) {
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1
  const rect = canvas.getBoundingClientRect()
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  ctx.scale(dpr, dpr)

  const W = rect.width
  const H = rect.height
  const pad = { top: 20, right: 16, bottom: 30, left: 50 }
  const chartW = W - pad.left - pad.right
  const chartH = H - pad.top - pad.bottom

  ctx.clearRect(0, 0, W, H)

  let allValues = []
  datasets.forEach(ds => ds.data.forEach(v => { if (v != null) allValues.push(v) }))
  if (allValues.length === 0) return

  const minVal = Math.min(...allValues)
  const maxVal = Math.max(...allValues)
  const range = maxVal - minVal || 1
  const yMin = minVal - range * 0.1
  const yMax = maxVal + range * 0.1

  ctx.strokeStyle = '#1e3a5c'
  ctx.lineWidth = 0.5
  for (let i = 0; i <= 4; i++) {
    const y = pad.top + chartH * i / 4
    ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(W - pad.right, y); ctx.stroke()
    const val = yMax - (yMax - yMin) * i / 4
    ctx.fillStyle = '#607d8b'
    ctx.font = '10px Microsoft YaHei'
    ctx.textAlign = 'right'
    ctx.fillText(val.toFixed(1), pad.left - 6, y + 3)
  }

  const labelStep = Math.max(1, Math.floor(labels.length / 6))
  ctx.fillStyle = '#607d8b'
  ctx.font = '9px Microsoft YaHei'
  ctx.textAlign = 'center'
  labels.forEach((label, i) => {
    if (i % labelStep === 0) {
      const x = pad.left + (i / Math.max(1, labels.length - 1)) * chartW
      ctx.fillText(label, x, H - 6)
    }
  })

  datasets.forEach(ds => {
    ctx.beginPath()
    ctx.strokeStyle = ds.color
    ctx.lineWidth = 2
    ds.data.forEach((v, i) => {
      if (v == null) return
      const x = pad.left + (i / Math.max(1, ds.data.length - 1)) * chartW
      const y = pad.top + (1 - (v - yMin) / (yMax - yMin)) * chartH
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
    })
    ctx.stroke()

    if (ds.fill) {
      ctx.globalAlpha = 0.1
      ctx.fillStyle = ds.color
      ctx.lineTo(pad.left + chartW, pad.top + chartH)
      ctx.lineTo(pad.left, pad.top + chartH)
      ctx.closePath()
      ctx.fill()
      ctx.globalAlpha = 1
    }
  })

  const legendY = pad.top - 6
  let legendX = pad.left
  datasets.forEach(ds => {
    ctx.fillStyle = ds.color
    ctx.fillRect(legendX, legendY - 6, 12, 3)
    ctx.fillStyle = '#90a4ae'
    ctx.font = '10px Microsoft YaHei'
    ctx.textAlign = 'left'
    ctx.fillText(ds.label, legendX + 16, legendY)
    legendX += ctx.measureText(ds.label).width + 36
  })
}

function drawWaterQualityChart(wqData) {
  const labels = wqData.map(d => {
    const dt = new Date(d.time)
    return dt.getHours() + ':' + String(dt.getMinutes()).padStart(2, '0')
  })
  drawTrendChart(wqCanvasRef.value, labels, [
    { label: '浊度', color: '#42a5f5', data: wqData.map(d => d.turbidity), fill: true },
    { label: 'pH', color: '#66bb6a', data: wqData.map(d => d.ph) },
    { label: '氨氮', color: '#ffa726', data: wqData.map(d => d.ammonia) }
  ])
}

function drawDosingChart(dosingData) {
  const labels = dosingData.map(d => {
    const dt = new Date(d.time)
    return dt.getHours() + ':' + String(dt.getMinutes()).padStart(2, '0')
  })
  drawTrendChart(dosingCanvasRef.value, labels, [
    { label: '实际投加量', color: '#ffa726', data: dosingData.map(d => d.actualDose) },
    { label: '预测投加量', color: '#66bb6a', data: dosingData.map(d => d.predictedDose) }
  ])
}

function close() {
  emit('close')
}
</script>

<style scoped>
.modal-overlay {
  display: none;
  position: fixed;
  top: 0; left: 0;
  width: 100%; height: 100%;
  background: rgba(0,0,0,0.6);
  z-index: 1000;
  justify-content: center;
  align-items: center;
}
.modal-overlay.active { display: flex; }
.modal {
  background: #0d2137;
  border: 1px solid #1e88e5;
  border-radius: 10px;
  padding: 20px;
  width: 800px;
  max-width: 95vw;
  max-height: 90vh;
  overflow-y: auto;
}
.modal-title {
  color: #4fc3f7;
  margin-bottom: 12px;
  font-size: 16px;
  display: flex;
  justify-content: space-between;
}
.close-btn {
  background: none;
  border: none;
  color: #90a4ae;
  font-size: 22px;
  cursor: pointer;
}
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.chart-box {
  background: #112240;
  border-radius: 6px;
  padding: 10px;
}
.chart-box h4 {
  font-size: 12px;
  color: #90a4ae;
  margin-bottom: 6px;
}
.trend-canvas {
  width: 100%;
  height: 180px;
}
</style>
