<template>
  <div class="water-distribution">
    <div class="section-header">
      <h3>多水源配水优化</h3>
      <div class="header-actions">
        <button @click="runOptimization" :disabled="loading" class="btn-sm">
          {{ loading ? '优化中...' : '重新优化' }}
        </button>
      </div>
    </div>

    <div v-if="distribution && distribution.optimized" class="distribution-content">
      <div class="chart-container">
        <canvas ref="pieCanvas" width="220" height="220"></canvas>
        <div class="chart-center">
          <div class="total-demand">{{ formatNumber(distribution.totalDemand) }}</div>
          <div class="demand-unit">m³/h</div>
        </div>
      </div>

      <div class="source-list">
        <div v-for="(item, index) in distribution.allocations" :key="item.sourceCode" class="source-item">
          <div class="source-color" :style="{ backgroundColor: colors[index % colors.length] }"></div>
          <div class="source-info">
            <div class="source-name">{{ item.sourceName }}</div>
            <div class="source-details">
              <span class="ratio">{{ item.ratio }}%</span>
              <span class="volume">{{ formatNumber(item.volume) }} m³/h</span>
            </div>
          </div>
        </div>
      </div>

      <div class="mix-info">
        <div class="mix-item">
          <span class="mix-label">混合后浊度:</span>
          <span class="mix-value" :class="{ warning: distribution.mixedTurbidity > 15 }">
            {{ distribution.mixedTurbidity }} NTU
          </span>
        </div>
        <div class="mix-item">
          <span class="mix-label">混合后pH:</span>
          <span class="mix-value">{{ distribution.mixedPh }}</span>
        </div>
        <div class="mix-item total">
          <span class="mix-label">原水成本:</span>
          <span class="mix-value cost">¥{{ formatNumber(distribution.totalCost) }}/h</span>
        </div>
      </div>
    </div>

    <div v-else class="no-data">
      <p>暂无配水优化数据</p>
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
const pieCanvas = ref(null)
const distribution = ref(null)
const loading = ref(false)

const colors = ['#3498db', '#2ecc71', '#f39c12', '#e74c3c', '#9b59b6']

const formatNumber = (num) => {
  if (num == null) return '-'
  return Number(num).toLocaleString('zh-CN', { maximumFractionDigits: 1 })
}

const loadData = async () => {
  try {
    distribution.value = await api.getCurrentDistribution()
    await nextTick()
    drawPieChart()
  } catch (e) {
    console.error('Load distribution failed:', e)
  }
}

const runOptimization = async () => {
  loading.value = true
  try {
    await api.runOptimization()
    await loadData()
  } catch (e) {
    console.error('Optimization failed:', e)
  } finally {
    loading.value = false
  }
}

const drawPieChart = () => {
  if (!pieCanvas.value || !distribution.value?.allocations) return

  const canvas = pieCanvas.value
  const ctx = canvas.getContext('2d')
  const allocations = distribution.value.allocations

  ctx.clearRect(0, 0, canvas.width, canvas.height)

  const cx = canvas.width / 2
  const cy = canvas.height / 2
  const outerRadius = 100
  const innerRadius = 70

  let startAngle = -Math.PI / 2

  for (let i = 0; i < allocations.length; i++) {
    const item = allocations[i]
    const ratio = item.ratio / 100
    const endAngle = startAngle + ratio * 2 * Math.PI

    ctx.beginPath()
    ctx.arc(cx, cy, outerRadius, startAngle, endAngle)
    ctx.arc(cx, cy, innerRadius, endAngle, startAngle, true)
    ctx.closePath()
    ctx.fillStyle = colors[i % colors.length]
    ctx.fill()

    const midAngle = (startAngle + endAngle) / 2
    const labelRadius = (outerRadius + innerRadius) / 2
    const lx = cx + Math.cos(midAngle) * labelRadius
    const ly = cy + Math.sin(midAngle) * labelRadius

    ctx.fillStyle = '#fff'
    ctx.font = 'bold 14px Arial'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(item.ratio + '%', lx, ly)

    startAngle = endAngle
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
.water-distribution {
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

.distribution-content {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 20px;
  align-items: start;
}

.chart-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-center {
  position: absolute;
  text-align: center;
}

.total-demand {
  font-size: 24px;
  font-weight: bold;
  color: #2c3e50;
}

.demand-unit {
  font-size: 12px;
  color: #7f8c8d;
}

.source-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.source-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: #f8f9fa;
  border-radius: 6px;
}

.source-color {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}

.source-info {
  flex: 1;
}

.source-name {
  font-size: 13px;
  font-weight: 500;
  color: #34495e;
  margin-bottom: 2px;
}

.source-details {
  font-size: 11px;
  color: #7f8c8d;
  display: flex;
  gap: 12px;
}

.ratio {
  color: #2c3e50;
  font-weight: 600;
}

.mix-info {
  grid-column: 1 / -1;
  display: flex;
  gap: 20px;
  padding-top: 12px;
  border-top: 1px solid #eee;
  margin-top: 12px;
}

.mix-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mix-label {
  font-size: 11px;
  color: #7f8c8d;
}

.mix-value {
  font-size: 15px;
  font-weight: 600;
  color: #2c3e50;
}

.mix-value.warning {
  color: #e67e22;
}

.mix-value.cost {
  color: #27ae60;
}

.no-data {
  text-align: center;
  padding: 40px;
  color: #95a5a6;
}
</style>
