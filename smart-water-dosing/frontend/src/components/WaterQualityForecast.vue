<template>
  <div class="water-quality-forecast">
    <div class="section-header">
      <h3>出厂水质预测预警</h3>
      <div class="header-actions">
        <button @click="runForecast" :disabled="loading" class="btn-sm">
          {{ loading ? '预测中...' : '重新预测' }}
        </button>
        <span v-if="hasAlarm" class="alarm-badge">
          预警
        </span>
        <span v-else-if="hasWarning" class="warning-badge">
          注意
        </span>
      </div>
    </div>

    <div v-if="forecastSummary" class="forecast-summary">
      <div class="summary-grid">
        <div class="summary-item" :class="{ alarm: forecastSummary.turbidityAlarm, warning: forecastSummary.turbidityWarning }">
          <div class="summary-label">浊度预测</div>
          <div class="summary-value">
            <span class="value">{{ forecastSummary.maxTurbidityForecast?.toFixed(2) }}</span>
            <span class="unit">NTU</span>
          </div>
          <div class="summary-threshold">阈值: {{ forecastSummary.turbidityThreshold }}</div>
        </div>

        <div class="summary-item" :class="{ alarm: forecastSummary.chlorineAlarm, warning: forecastSummary.chlorineWarning }">
          <div class="summary-label">余氯预测</div>
          <div class="summary-value">
            <span class="value">{{ forecastSummary.chlorineForecast?.toFixed(2) }}</span>
            <span class="unit">mg/L</span>
          </div>
          <div class="summary-threshold">阈值: {{ forecastSummary.chlorineThreshold }}</div>
        </div>

        <div class="summary-item">
          <div class="summary-label">预测时长</div>
          <div class="summary-value">
            <span class="value">{{ forecastSummary.forecastMinutes }}</span>
            <span class="unit">分钟</span>
          </div>
          <div class="summary-threshold">模型: {{ forecastSummary.modelVersion }}</div>
        </div>
      </div>
    </div>

    <div class="forecast-chart">
      <h4>未来1小时出厂水浊度预测</h4>
      <canvas ref="turbidityCanvas" width="500" height="150"></canvas>
    </div>

    <div class="forecast-chart">
      <h4>未来1小时出厂水余氯预测</h4>
      <canvas ref="chlorineCanvas" width="500" height="150"></canvas>
    </div>

    <div v-if="activeWarnings.length > 0" class="warnings-section">
      <h4>预警信息</h4>
      <div class="warning-list">
        <div v-for="(warn, idx) in activeWarnings" :key="idx"
             class="warning-item"
             :class="warn.level">
          <div class="warning-header">
            <span class="warning-level">
              {{ warn.level === 'alarm' ? '⚠️ 告警' : '⚡ 预警' }}
            </span>
            <span class="warning-param">{{ getParamLabel(warn.parameter) }}</span>
            <span class="warning-time">{{ formatTime(warn.targetTime) }}</span>
          </div>
          <div class="warning-content">
            <p class="warning-value">
              预测值: <strong>{{ warn.predictedValue?.toFixed(3) }}</strong>
              (阈值: {{ warn.threshold }})
            </p>
            <div v-if="warn.dosingAdjustment" class="adjustment">
              <span class="adjustment-label">💊 加药调整建议:</span>
              <p class="adjustment-content">{{ warn.dosingAdjustment }}</p>
            </div>
            <div v-if="warn.processAdjustment" class="adjustment">
              <span class="adjustment-label">⚙️ 工艺调整建议:</span>
              <p class="adjustment-content">{{ warn.processAdjustment }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="forecastSummary?.suggestedAdjustments?.length > 0" class="suggestions-section">
      <h4>操作建议</h4>
      <div class="suggestion-list">
        <div v-for="(sugg, idx) in forecastSummary.suggestedAdjustments" :key="idx" class="suggestion-item">
          <span class="suggestion-icon">💡</span>
          <span class="suggestion-text">{{ sugg }}</span>
        </div>
      </div>
    </div>

    <div v-if="forecastData.length > 0" class="forecast-table">
      <h4>详细预测数据</h4>
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>预测时间</th>
              <th>浊度(NTU)</th>
              <th>浊度区间</th>
              <th>状态</th>
              <th>余氯(mg/L)</th>
              <th>余氯区间</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(data, idx) in forecastData" :key="idx">
              <td>{{ formatTime(data.time) }}</td>
              <td>{{ data.turbidity?.toFixed(3) }}</td>
              <td>[{{ data.turbidityLower?.toFixed(2) }}, {{ data.turbidityUpper?.toFixed(2) }}]</td>
              <td>
                <span v-if="data.turbidityWarning === 'alarm'" class="status-tag alarm">告警</span>
                <span v-else-if="data.turbidityWarning === 'warning'" class="status-tag warning">预警</span>
                <span v-else class="status-tag normal">正常</span>
              </td>
              <td>{{ data.residualChlorine?.toFixed(3) }}</td>
              <td>[{{ data.chlorineLower?.toFixed(2) }}, {{ data.chlorineUpper?.toFixed(2) }}]</td>
              <td>
                <span v-if="data.chlorineWarning === 'alarm'" class="status-tag alarm">告警</span>
                <span v-else-if="data.chlorineWarning === 'warning'" class="status-tag warning">预警</span>
                <span v-else class="status-tag normal">正常</span>
              </td>
            </tr>
          </tbody>
        </table>
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
const turbidityCanvas = ref(null)
const chlorineCanvas = ref(null)
const forecastData = ref([])
const forecastSummary = ref(null)
const activeWarnings = ref([])
const loading = ref(false)
const hasAlarm = ref(false)
const hasWarning = ref(false)

const getParamLabel = (param) => {
  return param === 'turbidity' ? '浊度' : param === 'residual_chlorine' ? '余氯' : param
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  return timeStr.substring(11, 16)
}

const loadData = async () => {
  try {
    forecastData.value = await api.getLatestForecast('outlet')
    forecastSummary.value = await api.getForecastSummary('outlet')
    activeWarnings.value = await api.getActiveWarnings()

    hasAlarm.value = forecastSummary.value?.hasAlarm ||
      activeWarnings.value.some(w => w.level === 'alarm')
    hasWarning.value = forecastSummary.value?.hasWarning ||
      activeWarnings.value.some(w => w.level === 'warning')

    await nextTick()
    drawTurbidityChart()
    drawChlorineChart()
  } catch (e) {
    console.error('Load forecast failed:', e)
  }
}

const runForecast = async () => {
  loading.value = true
  try {
    await api.runForecast()
    await loadData()
  } catch (e) {
    console.error('Run forecast failed:', e)
  } finally {
    loading.value = false
  }
}

const drawTurbidityChart = () => {
  if (!turbidityCanvas.value || !forecastData.value.length) return

  const canvas = turbidityCanvas.value
  const ctx = canvas.getContext('2d')
  const w = canvas.width
  const h = canvas.height

  ctx.clearRect(0, 0, w, h)

  const padding = { left: 50, right: 20, top: 15, bottom: 30 }
  const chartW = w - padding.left - padding.right
  const chartH = h - padding.top - padding.bottom

  const data = forecastData.value
  const values = data.map(d => d.turbidity || 0)
  const lower = data.map(d => d.turbidityLower || 0)
  const upper = data.map(d => d.turbidityUpper || 0)
  const threshold = data[0]?.turbidityThreshold || 0.5

  const maxVal = Math.max(...upper, threshold) * 1.1
  const minVal = 0

  const xStep = data.length > 1 ? chartW / (data.length - 1) : chartW

  ctx.strokeStyle = '#ecf0f1'
  ctx.lineWidth = 1
  for (let i = 0; i <= 4; i++) {
    const y = padding.top + (chartH / 4) * i
    ctx.beginPath()
    ctx.moveTo(padding.left, y)
    ctx.lineTo(w - padding.right, y)
    ctx.stroke()
  }

  const thresholdY = padding.top + chartH - ((threshold - minVal) / (maxVal - minVal)) * chartH
  ctx.strokeStyle = '#e74c3c'
  ctx.setLineDash([5, 5])
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(padding.left, thresholdY)
  ctx.lineTo(w - padding.right, thresholdY)
  ctx.stroke()
  ctx.setLineDash([])

  ctx.fillStyle = '#e74c3c'
  ctx.font = '10px Arial'
  ctx.textAlign = 'left'
  ctx.fillText('阈值', padding.left + 5, thresholdY - 3)

  ctx.fillStyle = 'rgba(52, 152, 219, 0.2)'
  ctx.beginPath()
  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const yUpper = padding.top + chartH - ((upper[i] - minVal) / (maxVal - minVal)) * chartH
    if (i === 0) ctx.moveTo(x, yUpper)
    else ctx.lineTo(x, yUpper)
  }
  for (let i = data.length - 1; i >= 0; i--) {
    const x = padding.left + i * xStep
    const yLower = padding.top + chartH - ((lower[i] - minVal) / (maxVal - minVal)) * chartH
    ctx.lineTo(x, yLower)
  }
  ctx.closePath()
  ctx.fill()

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

  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const y = padding.top + chartH - ((values[i] - minVal) / (maxVal - minVal)) * chartH
    ctx.beginPath()
    ctx.arc(x, y, 3, 0, Math.PI * 2)
    ctx.fillStyle = data[i].turbidityWarning === 'alarm' ? '#e74c3c' :
                    data[i].turbidityWarning === 'warning' ? '#f39c12' : '#3498db'
    ctx.fill()
  }

  ctx.fillStyle = '#7f8c8d'
  ctx.font = '10px Arial'
  ctx.textAlign = 'center'
  for (let i = 0; i < data.length; i += 2) {
    const x = padding.left + i * xStep
    ctx.fillText(formatTime(data[i].time), x, h - 10)
  }

  ctx.textAlign = 'right'
  for (let i = 0; i <= 4; i++) {
    const val = maxVal - (i * (maxVal - minVal) / 4)
    const y = padding.top + (chartH / 4) * i
    ctx.fillText(val.toFixed(2), padding.left - 5, y + 3)
  }
}

const drawChlorineChart = () => {
  if (!chlorineCanvas.value || !forecastData.value.length) return

  const canvas = chlorineCanvas.value
  const ctx = canvas.getContext('2d')
  const w = canvas.width
  const h = canvas.height

  ctx.clearRect(0, 0, w, h)

  const padding = { left: 50, right: 20, top: 15, bottom: 30 }
  const chartW = w - padding.left - padding.right
  const chartH = h - padding.top - padding.bottom

  const data = forecastData.value
  const values = data.map(d => d.residualChlorine || 0)
  const lower = data.map(d => d.chlorineLower || 0)
  const upper = data.map(d => d.chlorineUpper || 0)
  const threshold = data[0]?.chlorineThreshold || 0.8

  const maxVal = Math.max(...upper, threshold) * 1.1
  const minVal = 0

  const xStep = data.length > 1 ? chartW / (data.length - 1) : chartW

  ctx.strokeStyle = '#ecf0f1'
  ctx.lineWidth = 1
  for (let i = 0; i <= 4; i++) {
    const y = padding.top + (chartH / 4) * i
    ctx.beginPath()
    ctx.moveTo(padding.left, y)
    ctx.lineTo(w - padding.right, y)
    ctx.stroke()
  }

  const thresholdY = padding.top + chartH - ((threshold - minVal) / (maxVal - minVal)) * chartH
  ctx.strokeStyle = '#e74c3c'
  ctx.setLineDash([5, 5])
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(padding.left, thresholdY)
  ctx.lineTo(w - padding.right, thresholdY)
  ctx.stroke()
  ctx.setLineDash([])

  ctx.fillStyle = '#e74c3c'
  ctx.font = '10px Arial'
  ctx.textAlign = 'left'
  ctx.fillText('上限', padding.left + 5, thresholdY - 3)

  ctx.fillStyle = 'rgba(46, 204, 113, 0.2)'
  ctx.beginPath()
  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const yUpper = padding.top + chartH - ((upper[i] - minVal) / (maxVal - minVal)) * chartH
    if (i === 0) ctx.moveTo(x, yUpper)
    else ctx.lineTo(x, yUpper)
  }
  for (let i = data.length - 1; i >= 0; i--) {
    const x = padding.left + i * xStep
    const yLower = padding.top + chartH - ((lower[i] - minVal) / (maxVal - minVal)) * chartH
    ctx.lineTo(x, yLower)
  }
  ctx.closePath()
  ctx.fill()

  ctx.beginPath()
  ctx.strokeStyle = '#2ecc71'
  ctx.lineWidth = 2
  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const y = padding.top + chartH - ((values[i] - minVal) / (maxVal - minVal)) * chartH
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  ctx.stroke()

  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const y = padding.top + chartH - ((values[i] - minVal) / (maxVal - minVal)) * chartH
    ctx.beginPath()
    ctx.arc(x, y, 3, 0, Math.PI * 2)
    ctx.fillStyle = data[i].chlorineWarning === 'alarm' ? '#e74c3c' :
                    data[i].chlorineWarning === 'warning' ? '#f39c12' : '#2ecc71'
    ctx.fill()
  }

  ctx.fillStyle = '#7f8c8d'
  ctx.font = '10px Arial'
  ctx.textAlign = 'center'
  for (let i = 0; i < data.length; i += 2) {
    const x = padding.left + i * xStep
    ctx.fillText(formatTime(data[i].time), x, h - 10)
  }

  ctx.textAlign = 'right'
  for (let i = 0; i <= 4; i++) {
    const val = maxVal - (i * (maxVal - minVal) / 4)
    const y = padding.top + (chartH / 4) * i
    ctx.fillText(val.toFixed(2), padding.left - 5, y + 3)
  }
}

watch(() => props.refreshTrigger, () => {
  loadData()
})

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.water-quality-forecast {
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

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.btn-sm {
  padding: 4px 12px;
  font-size: 12px;
  border: none;
  border-radius: 4px;
  background: #3498db;
  color: #fff;
  cursor: pointer;
}

.btn-sm:disabled {
  background: #bdc3c7;
  cursor: not-allowed;
}

.alarm-badge {
  padding: 2px 8px;
  background: #e74c3c;
  color: #fff;
  border-radius: 10px;
  font-size: 11px;
  animation: pulse-alarm 1s infinite;
}

.warning-badge {
  padding: 2px 8px;
  background: #f39c12;
  color: #fff;
  border-radius: 10px;
  font-size: 11px;
}

@keyframes pulse-alarm {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.forecast-summary {
  margin-bottom: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.summary-item {
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
  border-left: 3px solid #3498db;
}

.summary-item.alarm {
  border-left-color: #e74c3c;
  background: #fdf2f2;
}

.summary-item.warning {
  border-left-color: #f39c12;
  background: #fef8e7;
}

.summary-label {
  font-size: 11px;
  color: #7f8c8d;
  margin-bottom: 4px;
}

.summary-value {
  margin-bottom: 4px;
}

.summary-value .value {
  font-size: 22px;
  font-weight: bold;
  color: #2c3e50;
}

.summary-value .unit {
  font-size: 12px;
  color: #95a5a6;
  margin-left: 4px;
}

.summary-threshold {
  font-size: 10px;
  color: #95a5a6;
}

.forecast-chart {
  margin-bottom: 16px;
}

.forecast-chart h4 {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #34495e;
}

.warnings-section {
  margin-bottom: 16px;
}

.warnings-section h4,
.suggestions-section h4,
.forecast-table h4 {
  margin: 0 0 10px 0;
  font-size: 13px;
  color: #34495e;
}

.warning-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.warning-item {
  padding: 12px;
  border-radius: 6px;
  border-left: 4px solid #f39c12;
  background: #fef8e7;
}

.warning-item.alarm {
  border-left-color: #e74c3c;
  background: #fdf2f2;
}

.warning-header {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}

.warning-level {
  font-weight: bold;
  font-size: 12px;
}

.warning-level.alarm { color: #e74c3c; }
.warning-level.warning { color: #f39c12; }

.warning-param {
  font-size: 12px;
  color: #34495e;
  font-weight: 600;
}

.warning-time {
  font-size: 11px;
  color: #7f8c8d;
  margin-left: auto;
}

.warning-content {
  font-size: 12px;
}

.warning-value {
  margin: 0 0 8px 0;
  color: #2c3e50;
}

.adjustment {
  margin-bottom: 6px;
  padding: 8px;
  background: rgba(255,255,255,0.7);
  border-radius: 4px;
}

.adjustment-label {
  font-weight: 600;
  color: #34495e;
  font-size: 11px;
}

.adjustment-content {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: #555;
  line-height: 1.4;
}

.suggestions-section {
  margin-bottom: 16px;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.suggestion-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  background: #e8f4fd;
  border-radius: 6px;
  font-size: 12px;
  color: #2c3e50;
}

.suggestion-icon {
  font-size: 14px;
}

.forecast-table {
  margin-top: 16px;
}

.table-container {
  overflow-x: auto;
}

.forecast-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

.forecast-table th,
.forecast-table td {
  padding: 6px 8px;
  text-align: left;
  border-bottom: 1px solid #e1e8ed;
}

.forecast-table th {
  background: #e9ecef;
  font-weight: 600;
  color: #34495e;
  white-space: nowrap;
}

.status-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
}

.status-tag.normal {
  background: #2ecc71;
  color: #fff;
}

.status-tag.warning {
  background: #f39c12;
  color: #fff;
}

.status-tag.alarm {
  background: #e74c3c;
  color: #fff;
}
</style>
