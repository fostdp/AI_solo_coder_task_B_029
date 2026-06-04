<template>
  <div class="membrane-panel">
    <div class="section-header">
      <h3>膜滤工艺监控</h3>
      <div class="header-actions">
        <span v-if="needAttentionCount > 0" class="attention-badge">
          {{ needAttentionCount }} 组需关注
        </span>
      </div>
    </div>

    <div class="membrane-list">
      <div v-for="module in modules" :key="module.moduleCode"
           class="membrane-card"
           :class="getUrgencyClass(module.cleanUrgency)"
           @click="selectModule(module)">
        <div class="module-header">
          <span class="module-name">{{ module.moduleName }}</span>
          <span class="module-type">{{ getTypeLabel(module.membraneType) }}</span>
        </div>

        <div class="module-metrics">
          <div class="metric">
            <span class="metric-label">通量</span>
            <span class="metric-value">{{ module.flux?.toFixed(1) }}
              <span class="metric-unit">LMH</span>
            </span>
          </div>
          <div class="metric">
            <span class="metric-label">TMD</span>
            <span class="metric-value">{{ module.tmd?.toFixed(2) }}
              <span class="metric-unit">bar</span>
            </span>
          </div>
        </div>

        <div class="fouling-indicator">
          <div class="fouling-bar">
            <div class="fouling-fill" :style="{ width: (module.foulingIndex * 100) + '%' }"
                 :class="getFoulingClass(module.foulingIndex)"></div>
          </div>
          <div class="fouling-info">
            <span>污染指数: {{ (module.foulingIndex * 100).toFixed(0) }}%</span>
            <span class="clean-prediction" :class="module.cleanUrgency">
              {{ getCleanPredictionText(module) }}
            </span>
          </div>
        </div>

        <div v-if="module.cleanUrgency !== 'normal'" class="urgency-indicator">
          <span v-if="module.cleanUrgency === 'urgent'" class="urgent">紧急</span>
          <span v-else-if="module.cleanUrgency === 'soon'" class="soon">即将</span>
        </div>
      </div>
    </div>

    <div v-if="cleaningSchedule.length > 0" class="cleaning-schedule">
      <h4>清洗计划表</h4>
      <div class="schedule-list">
        <div v-for="item in cleaningSchedule" :key="item.moduleCode" class="schedule-item">
          <span class="schedule-urgency" :class="item.cleanUrgency">
            {{ item.cleanUrgency === 'urgent' ? '!' : '○' }}
          </span>
          <span class="schedule-name">{{ item.moduleName }}</span>
          <span class="schedule-days">{{ item.predictedCleanDays }}天后</span>
        </div>
      </div>
    </div>

    <div v-if="selectedModule" class="module-detail">
      <div class="detail-header">
        <h4>{{ selectedModule.moduleName }} - 污染趋势</h4>
        <button @click="selectedModule = null" class="btn-close">×</button>
      </div>
      <div class="detail-chart">
        <canvas ref="trendCanvas" width="350" height="120"></canvas>
      </div>
      <div class="detail-actions">
        <button @click="recordCleaning(selectedModule)" class="btn-record">
          记录清洗
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { useAdvancedApi } from '../composables/useApi.js'

const props = defineProps({
  refreshTrigger: {
    type: Number,
    default: 0
  }
})

const api = useAdvancedApi()
const modules = ref([])
const cleaningSchedule = ref([])
const needAttentionCount = ref(0)
const selectedModule = ref(null)
const trendCanvas = ref(null)

const getTypeLabel = (type) => {
  return type === 'ultrafiltration' ? '超滤' : type === 'reverse_osmosis' ? '反渗透' : type
}

const getUrgencyClass = (urgency) => {
  return urgency === 'urgent' ? 'urgent' : urgency === 'soon' ? 'soon' : ''
}

const getFoulingClass = (index) => {
  if (index >= 0.75) return 'good'
  if (index >= 0.5) return 'warning'
  return 'danger'
}

const getCleanPredictionText = (module) => {
  if (module.cleanUrgency === 'urgent') return '立即清洗'
  if (module.cleanUrgency === 'soon') return `${module.predictedCleanDays}天后清洗`
  return '运行正常'
}

const loadData = async () => {
  try {
    const data = await api.getMembraneModules()
    modules.value = data.modules || []
    needAttentionCount.value = data.needAttentionCount || 0
    cleaningSchedule.value = await api.getCleaningSchedule()
  } catch (e) {
    console.error('Load membrane data failed:', e)
  }
}

const selectModule = async (module) => {
  selectedModule.value = module
  try {
    const trend = await api.getFoulingTrend(module.moduleCode, 24)
    await nextTick()
    drawTrendChart(trend)
  } catch (e) {
    console.error('Load trend failed:', e)
  }
}

const drawTrendChart = (data) => {
  if (!trendCanvas.value || !data.length) return

  const canvas = trendCanvas.value
  const ctx = canvas.getContext('2d')
  const w = canvas.width
  const h = canvas.height

  ctx.clearRect(0, 0, w, h)

  const padding = { left: 40, right: 10, top: 10, bottom: 25 }
  const chartW = w - padding.left - padding.right
  const chartH = h - padding.top - padding.bottom

  ctx.strokeStyle = '#ecf0f1'
  ctx.lineWidth = 1
  for (let i = 0; i <= 4; i++) {
    const y = padding.top + (chartH / 4) * i
    ctx.beginPath()
    ctx.moveTo(padding.left, y)
    ctx.lineTo(w - padding.right, y)
    ctx.stroke()
  }

  const values = data.map(d => d.foulingIndex)
  const minVal = 0
  const maxVal = 1

  const xStep = data.length > 1 ? chartW / (data.length - 1) : chartW

  ctx.beginPath()
  ctx.strokeStyle = '#3498db'
  ctx.lineWidth = 2
  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const y = padding.top + chartH - ((values[i] - minVal) / (maxVal - minVal)) * chartH
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  ctx.stroke()

  ctx.fillStyle = '#3498db'
  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const y = padding.top + chartH - ((values[i] - minVal) / (maxVal - minVal)) * chartH
    ctx.beginPath()
    ctx.arc(x, y, 3, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.fillStyle = '#7f8c8d'
  ctx.font = '10px Arial'
  ctx.textAlign = 'right'
  for (let i = 0; i <= 4; i++) {
    const val = maxVal - (i * (maxVal - minVal) / 4)
    const y = padding.top + (chartH / 4) * i
    ctx.fillText(val.toFixed(1), padding.left - 5, y + 3)
  }
}

const recordCleaning = async (module) => {
  const record = {
    moduleCode: module.moduleCode,
    moduleName: module.moduleName,
    cleanType: 'CIP',
    cleanReason: module.cleanUrgency === 'urgent' ? '污染严重' : '预防性维护',
    chemicalType: '柠檬酸+NaOH',
    chemicalDosage: 200,
    cleanDurationMinutes: 180,
    temperature: 35,
    ph: 2.5,
    fluxBefore: module.flux,
    fluxAfter: module.membraneType === 'ultrafiltration' ? 58 : 24,
    tmdBefore: module.tmd,
    tmdAfter: module.membraneType === 'ultrafiltration' ? 0.85 : 10.5,
    operator: 'system'
  }

  try {
    await api.recordCleaning(record)
    selectedModule.value = null
    await loadData()
  } catch (e) {
    console.error('Record cleaning failed:', e)
  }
}

watch(() => props.refreshTrigger, () => {
  loadData()
})

onMounted(() => {
  loadData()
})

defineExpose({ modules, needAttentionCount, cleaningSchedule })
</script>

<style scoped>
.membrane-panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.08);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: #2c3e50;
}

.attention-badge {
  padding: 2px 8px;
  background: #e74c3c;
  color: #fff;
  border-radius: 10px;
  font-size: 11px;
}

.membrane-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.membrane-card {
  position: relative;
  padding: 12px;
  border: 2px solid #e1e8ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafbfc;
}

.membrane-card:hover {
  border-color: #3498db;
  transform: translateY(-2px);
}

.membrane-card.urgent {
  border-color: #e74c3c;
  background: #fdf2f2;
  animation: pulse-urgent 2s infinite;
}

.membrane-card.soon {
  border-color: #f39c12;
  background: #fef8e7;
  animation: pulse-soon 2s infinite;
}

@keyframes pulse-urgent {
  0%, 100% { box-shadow: 0 0 0 0 rgba(231, 76, 60, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(231, 76, 60, 0); }
}

@keyframes pulse-soon {
  0%, 100% { box-shadow: 0 0 0 0 rgba(243, 156, 18, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(243, 156, 18, 0); }
}

.module-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.module-name {
  font-size: 13px;
  font-weight: 600;
  color: #2c3e50;
}

.module-type {
  font-size: 10px;
  padding: 2px 6px;
  background: #3498db;
  color: #fff;
  border-radius: 4px;
}

.module-metrics {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-label {
  font-size: 10px;
  color: #7f8c8d;
}

.metric-value {
  font-size: 16px;
  font-weight: bold;
  color: #2c3e50;
}

.metric-unit {
  font-size: 10px;
  color: #95a5a6;
  font-weight: normal;
}

.fouling-indicator {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fouling-bar {
  height: 8px;
  background: #ecf0f1;
  border-radius: 4px;
  overflow: hidden;
}

.fouling-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}

.fouling-fill.good { background: #2ecc71; }
.fouling-fill.warning { background: #f39c12; }
.fouling-fill.danger { background: #e74c3c; }

.fouling-info {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #7f8c8d;
}

.clean-prediction.urgent {
  color: #e74c3c;
  font-weight: bold;
}

.clean-prediction.soon {
  color: #f39c12;
  font-weight: bold;
}

.urgency-indicator {
  position: absolute;
  top: 8px;
  right: 8px;
}

.urgency-indicator span {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: bold;
}

.urgency-indicator .urgent {
  background: #e74c3c;
  color: #fff;
}

.urgency-indicator .soon {
  background: #f39c12;
  color: #fff;
}

.cleaning-schedule {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #eee;
}

.cleaning-schedule h4 {
  margin: 0 0 10px 0;
  font-size: 13px;
  color: #34495e;
}

.schedule-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.schedule-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #f8f9fa;
  border-radius: 4px;
  font-size: 12px;
}

.schedule-urgency.urgent {
  color: #e74c3c;
  font-weight: bold;
}

.schedule-urgency.soon {
  color: #f39c12;
  font-weight: bold;
}

.schedule-name {
  flex: 1;
  color: #34495e;
}

.schedule-days {
  color: #7f8c8d;
}

.module-detail {
  margin-top: 16px;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.detail-header h4 {
  margin: 0;
  font-size: 13px;
  color: #34495e;
}

.btn-close {
  width: 24px;
  height: 24px;
  border: none;
  background: #bdc3c7;
  color: #fff;
  border-radius: 50%;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.detail-chart {
  margin-bottom: 10px;
}

.detail-actions {
  text-align: right;
}

.btn-record {
  padding: 6px 16px;
  border: none;
  background: #27ae60;
  color: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}
</style>
