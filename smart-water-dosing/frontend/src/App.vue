<template>
  <div class="app">
    <header class="header">
      <h1>智慧水厂絮凝沉淀工艺智能加药系统</h1>
      <div class="header-right">
        <span><span class="status-dot online"></span>系统在线</span>
        <span>{{ currentTime }}</span>
      </div>
    </header>

    <nav class="tabs">
      <button v-for="tab in tabs" :key="tab.key"
              :class="['tab-btn', { active: currentTab === tab.key }]"
              @click="currentTab = tab.key">
        <span class="tab-icon">{{ tab.icon }}</span>
        <span class="tab-label">{{ tab.label }}</span>
        <span v-if="tab.badge && tab.badge() > 0" class="tab-badge">{{ tab.badge() }}</span>
      </button>
    </nav>

    <div class="main-container">
      <template v-if="currentTab === 'home'">
        <ProcessFlow :stageData="stageData" @stage-click="onStageClick" />

        <div class="right-col">
          <div class="panel">
            <h2 class="panel-title">智能加药预测 <span class="model-status" :style="{color: modelStatusColor}">{{ modelStatusText }}</span></h2>
            <div class="prediction-grid">
              <div class="pred-item">
                <div class="label">原水浊度</div>
                <div class="value blue">{{ predTurbidity }}</div>
              </div>
              <div class="pred-item">
                <div class="label">当前流量 m³/h</div>
                <div class="value blue">{{ predFlow }}</div>
              </div>
              <div class="pred-item">
                <div class="label">预测投加量 mg/L</div>
                <div class="value green">{{ predDose }}</div>
              </div>
              <div class="pred-item">
                <div class="label">实际投加量 mg/L</div>
                <div class="value yellow">{{ actualDose }}</div>
              </div>
              <div class="pred-item" style="grid-column:1/-1;">
                <div class="label">偏差百分比</div>
                <div class="value" :class="deviationClass">{{ deviationPct }}</div>
              </div>
            </div>
          </div>

          <div class="panel">
            <h2 class="panel-title">告警信息 <span class="badge">{{ alertCount }}</span></h2>
            <div class="alert-list">
              <div v-if="alerts.length === 0" class="no-alert">暂无告警</div>
              <div v-for="a in alerts" :key="a.id" class="alert-item" :class="'level' + a.level">
                <div>
                  <strong>[{{ a.level === 1 ? '一级' : '二级' }}]</strong> {{ a.type }}<br>
                  <small>{{ a.message }}</small><br>
                  <small class="time-text">{{ formatTime(a.time) }}</small>
                </div>
                <button class="ack-btn" @click="ackAlert(a.id)">确认</button>
              </div>
            </div>
          </div>
        </div>

        <div class="bottom-bar">
          <div class="cost-panel">
            <h2 class="panel-title">关键成本指标日趋势</h2>
            <canvas ref="costCanvasRef" class="cost-canvas"></canvas>
          </div>
        </div>
      </template>

      <template v-else-if="currentTab === 'distribution'">
        <div class="full-width">
          <WaterDistributionPie :refreshTrigger="refreshTrigger" />
        </div>
      </template>

      <template v-else-if="currentTab === 'membrane'">
        <div class="full-width">
          <MembranePanel ref="membranePanelRef" :refreshTrigger="refreshTrigger" />
        </div>
      </template>

      <template v-else-if="currentTab === 'inventory'">
        <div class="full-width">
          <ChemicalInventory ref="inventoryRef" :refreshTrigger="refreshTrigger" />
        </div>
      </template>

      <template v-else-if="currentTab === 'forecast'">
        <div class="full-width">
          <WaterQualityForecast :refreshTrigger="refreshTrigger" />
        </div>
      </template>
    </div>

    <WaterQualityTrend :visible="trendVisible" :stageKey="trendStage" @close="trendVisible = false" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import ProcessFlow from './components/ProcessFlow.vue'
import WaterQualityTrend from './components/WaterQualityTrend.vue'
import WaterDistributionPie from './components/WaterDistributionPie.vue'
import MembranePanel from './components/MembranePanel.vue'
import ChemicalInventory from './components/ChemicalInventory.vue'
import WaterQualityForecast from './components/WaterQualityForecast.vue'
import { useApi } from './composables/useApi.js'

const { get, post } = useApi()

const currentTab = ref('home')
const refreshTrigger = ref(0)
const membranePanelRef = ref(null)
const inventoryRef = ref(null)

const currentTime = ref('')
const stageData = ref({})
const predTurbidity = ref('--')
const predFlow = ref('--')
const predDose = ref('--')
const actualDose = ref('--')
const deviationPct = ref('--')
const deviationClass = ref('')
const modelStatusText = ref('')
const modelStatusColor = ref('#90a4ae')
const alerts = ref([])
const alertCount = ref(0)
const costData = ref([])
const costCanvasRef = ref(null)
const trendVisible = ref(false)
const trendStage = ref('')

const tabs = [
  { key: 'home', label: '监控首页', icon: '🏠' },
  { key: 'distribution', label: '配水优化', icon: '💧',
    badge: () => 0 },
  { key: 'membrane', label: '膜滤工艺', icon: '🔬',
    badge: () => membranePanelRef.value?.needAttentionCount || 0 },
  { key: 'inventory', label: '药剂库存', icon: '📦',
    badge: () => inventoryRef.value?.lowStockCount || 0 },
  { key: 'forecast', label: '水质预测', icon: '📊',
    badge: () => 0 }
]

let timers = []

function formatTime(t) {
  return new Date(t).toLocaleString('zh-CN')
}

async function fetchStatus() {
  try { stageData.value = await get('/api/status') } catch {}
}

async function fetchPrediction() {
  try {
    const data = await get('/api/dosing/prediction')
    predTurbidity.value = data.turbidity != null ? data.turbidity.toFixed(1) : '--'
    predFlow.value = data.flowRate != null ? data.flowRate.toFixed(0) : '--'
    predDose.value = data.predictedDose != null ? data.predictedDose.toFixed(2) : '--'
    actualDose.value = data.actualDose != null ? data.actualDose.toFixed(2) : '--'
    if (data.deviationPct != null) {
      deviationPct.value = data.deviationPct.toFixed(1) + '%'
      const abs = Math.abs(data.deviationPct)
      deviationClass.value = abs <= 10 ? 'green' : abs <= 20 ? 'yellow' : 'red'
    }
    const model = data.model || {}
    const isDegraded = model.degradedMode
    modelStatusColor.value = model.status === 'READY' ? '#4caf50' : isDegraded ? '#ffb300' : '#f44336'
    modelStatusText.value = (isDegraded ? '⚠️ ' : '') + (model.statusMessage || model.statusText || '')
  } catch {}
}

async function fetchAlerts() {
  try {
    alerts.value = await get('/api/alerts/unacknowledged')
    alertCount.value = alerts.value.length
  } catch {}
}

async function ackAlert(id) {
  try { await post('/api/alerts/' + id + '/acknowledge') } catch {}
  fetchAlerts()
}

async function fetchCost() {
  try {
    costData.value = await get('/api/cost/trend?days=7')
    drawCostChart()
  } catch {}
}

function drawCostChart() {
  const canvas = costCanvasRef.value
  if (!canvas || costData.value.length === 0) return
  const ctx = canvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1
  const rect = canvas.getBoundingClientRect()
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  ctx.scale(dpr, dpr)

  const W = rect.width
  const H = rect.height
  const pad = { top: 20, right: 60, bottom: 30, left: 50 }
  const chartW = W - pad.left - pad.right
  const chartH = H - pad.top - pad.bottom

  ctx.clearRect(0, 0, W, H)

  const labels = costData.value.map(d => {
    const dt = new Date(d.time)
    return (dt.getMonth() + 1) + '/' + dt.getDate()
  })

  const leftSets = [
    { label: '矾耗 (kg)', color: '#42a5f5', data: costData.value.map(d => d.alumConsumption) },
    { label: '氯耗 (kg)', color: '#66bb6a', data: costData.value.map(d => d.chlorineConsumption) }
  ]
  const rightSet = { label: '电耗 (kWh)', color: '#ffa726', data: costData.value.map(d => d.electricityConsumption) }

  let leftVals = []
  leftSets.forEach(ds => ds.data.forEach(v => { if (v != null) leftVals.push(v) }))
  const leftMin = Math.min(...leftVals)
  const leftMax = Math.max(...leftVals)
  const leftRange = leftMax - leftMin || 1
  const yLeftMin = leftMin - leftRange * 0.1
  const yLeftMax = leftMax + leftRange * 0.1

  const rightVals = rightSet.data.filter(v => v != null)
  const rightMin = Math.min(...rightVals)
  const rightMax = Math.max(...rightVals)
  const rightRange = rightMax - rightMin || 1
  const yRightMin = rightMin - rightRange * 0.1
  const yRightMax = rightMax + rightRange * 0.1

  ctx.strokeStyle = '#1e3a5c'
  ctx.lineWidth = 0.5
  for (let i = 0; i <= 4; i++) {
    const y = pad.top + chartH * i / 4
    ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(W - pad.right, y); ctx.stroke()
    const lv = yLeftMax - (yLeftMax - yLeftMin) * i / 4
    ctx.fillStyle = '#607d8b'
    ctx.font = '9px Microsoft YaHei'
    ctx.textAlign = 'right'
    ctx.fillText(lv.toFixed(1), pad.left - 4, y + 3)
    const rv = yRightMax - (yRightMax - yRightMin) * i / 4
    ctx.textAlign = 'left'
    ctx.fillText(rv.toFixed(0), W - pad.right + 4, y + 3)
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

  function drawLine(ds, yMin, yMax) {
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
  }

  leftSets.forEach(ds => drawLine(ds, yLeftMin, yLeftMax))
  drawLine(rightSet, yRightMin, yRightMax)

  let legendX = pad.left
  const allSets = [...leftSets, rightSet]
  allSets.forEach(ds => {
    ctx.fillStyle = ds.color
    ctx.fillRect(legendX, pad.top - 12, 12, 3)
    ctx.fillStyle = '#90a4ae'
    ctx.font = '10px Microsoft YaHei'
    ctx.textAlign = 'left'
    ctx.fillText(ds.label, legendX + 16, pad.top - 8)
    legendX += ctx.measureText(ds.label).width + 36
  })
}

function onStageClick(key) {
  trendStage.value = key
  trendVisible.value = true
}

function updateTime() {
  currentTime.value = new Date().toLocaleString('zh-CN')
}

onMounted(() => {
  updateTime()
  timers.push(setInterval(updateTime, 1000))

  fetchStatus()
  fetchPrediction()
  fetchAlerts()
  fetchCost()

  timers.push(setInterval(fetchStatus, 30000))
  timers.push(setInterval(fetchPrediction, 30000))
  timers.push(setInterval(fetchAlerts, 15000))
  timers.push(setInterval(fetchCost, 60000))

  timers.push(setInterval(() => {
    refreshTrigger.value++
  }, 60000))
})

onUnmounted(() => {
  timers.forEach(clearInterval)
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: 'Microsoft YaHei', sans-serif; background: #0a1929; color: #e0e0e0; overflow-x: hidden; }
</style>

<style scoped>
.app { min-height: 100vh; }
.header {
  background: linear-gradient(135deg, #0d2137, #1a3a5c);
  padding: 12px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 2px solid #1e88e5;
}
.header h1 { font-size: 20px; color: #4fc3f7; letter-spacing: 2px; }
.header-right { display: flex; gap: 16px; align-items: center; font-size: 13px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; margin-right: 4px; }
.status-dot.online { background: #4caf50; box-shadow: 0 0 6px #4caf50; }

.tabs {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  background: #0a1929;
  border-bottom: 1px solid #1e3a5c;
  overflow-x: auto;
}
.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #0d2137;
  border: 1px solid #1e3a5c;
  border-radius: 6px 6px 0 0;
  color: #90a4ae;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
  white-space: nowrap;
}
.tab-btn:hover {
  background: #112240;
  color: #e0e0e0;
}
.tab-btn.active {
  background: #1a3a5c;
  color: #4fc3f7;
  border-color: #4fc3f7;
  border-bottom-color: #1a3a5c;
}
.tab-icon { font-size: 16px; }
.tab-label { font-weight: 500; }
.tab-badge {
  background: #f44336;
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
}

.main-container { display: grid; grid-template-columns: 1fr 340px; grid-template-rows: auto auto; gap: 12px; padding: 12px; }
.full-width {
  grid-column: 1 / -1;
  background: #f5f5f5;
  min-height: 400px;
}
.right-col { display: flex; flex-direction: column; gap: 12px; }
.panel { background: #0d2137; border-radius: 8px; border: 1px solid #1e3a5c; padding: 12px; }
.panel-title { font-size: 14px; color: #4fc3f7; margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center; }
.model-status { font-size: 11px; }
.badge { background: #f44336; color: #fff; font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.prediction-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.pred-item { background: #112240; border-radius: 6px; padding: 8px; text-align: center; }
.pred-item .label { font-size: 11px; color: #90a4ae; margin-bottom: 4px; }
.pred-item .value { font-size: 18px; font-weight: bold; }
.pred-item .value.green { color: #4caf50; }
.pred-item .value.yellow { color: #ffb300; }
.pred-item .value.red { color: #f44336; }
.pred-item .value.blue { color: #42a5f5; }
.alert-list { max-height: 180px; overflow-y: auto; }
.no-alert { text-align: center; color: #607d8b; padding: 16px; }
.alert-item { padding: 6px 8px; border-radius: 4px; margin-bottom: 4px; font-size: 12px; display: flex; justify-content: space-between; align-items: center; }
.alert-item.level1 { background: rgba(244,67,54,0.15); border-left: 3px solid #f44336; }
.alert-item.level2 { background: rgba(255,179,0,0.15); border-left: 3px solid #ffb300; }
.ack-btn { background: #1e88e5; color: #fff; border: none; padding: 2px 8px; border-radius: 3px; cursor: pointer; font-size: 11px; }
.time-text { color: #607d8b; }
.bottom-bar { grid-column: 1 / -1; }
.cost-panel { background: #0d2137; border-radius: 8px; border: 1px solid #1e3a5c; padding: 12px; }
.cost-canvas { width: 100%; height: 160px; border-radius: 6px; }
::-webkit-scrollbar { width: 4px; }
::-webkit-scrollbar-thumb { background: #1e3a5c; border-radius: 2px; }
</style>
