<template>
  <div class="chemical-inventory">
    <div class="section-header">
      <h3>药剂库存管理</h3>
      <div class="header-actions">
        <span v-if="lowStockCount > 0" class="low-badge">
          {{ lowStockCount }} 种库存不足
        </span>
      </div>
    </div>

    <div class="inventory-list">
      <div v-for="item in inventory" :key="item.chemicalCode"
           class="inventory-card"
           :class="getStockClass(item.stockStatus)"
           @click="selectChemical(item)">
        <div class="inv-header">
          <span class="inv-name">{{ item.chemicalName }}</span>
          <span class="inv-type">{{ getTypeLabel(item.chemicalType) }}</span>
        </div>

        <div class="inv-stock">
          <div class="stock-bar">
            <div class="stock-fill" :style="{ width: getStockPercent(item) + '%' }"
                 :class="getStockClass(item.stockStatus)"></div>
          </div>
          <div class="stock-info">
            <span class="current">{{ item.currentStock?.toFixed(0) }} {{ item.stockUnit }}</span>
            <span class="safety">安全库存: {{ item.safetyStock }}</span>
          </div>
        </div>

        <div class="inv-details">
          <div class="detail-item">
            <span class="detail-label">日均消耗</span>
            <span class="detail-value">{{ item.dailyConsumption?.toFixed(1) }} {{ item.stockUnit }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">预计可用</span>
            <span class="detail-value" :class="getDaysClass(item.predictedDaysRemaining)">
              {{ item.predictedDaysRemaining }} 天
            </span>
          </div>
          <div class="detail-item">
            <span class="detail-label">单价</span>
            <span class="detail-value">¥{{ item.unitPrice }}/{{ item.stockUnit }}</span>
          </div>
        </div>

        <div v-if="item.stockStatus !== 'normal'" class="status-tag">
          <span v-if="item.stockStatus === 'critical'" class="critical">危急</span>
          <span v-else-if="item.stockStatus === 'low'" class="low">偏低</span>
          <span v-else-if="item.stockStatus === 'out_of_stock'" class="out">缺货</span>
        </div>
      </div>
    </div>

    <div v-if="selectedChemical" class="chemical-detail">
      <div class="detail-header">
        <h4>{{ selectedChemical.chemicalName }} - 7天需求预测</h4>
        <button @click="selectedChemical = null" class="btn-close">×</button>
      </div>

      <div class="forecast-chart">
        <canvas ref="forecastCanvas" width="400" height="120"></canvas>
      </div>

      <div class="forecast-table">
        <table>
          <thead>
            <tr>
              <th>日期</th>
              <th>预计消耗</th>
              <th>预计库存</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(day, idx) in demandForecast" :key="idx">
              <td>{{ day.date }}</td>
              <td>{{ day.predictedConsumption }} {{ selectedChemical.stockUnit }}</td>
              <td :class="{ warning: day.belowSafetyStock }">{{ day.estimatedStock }} {{ selectedChemical.stockUnit }}</td>
              <td>
                <span v-if="day.belowSafetyStock" class="warning-tag">低于安全库存</span>
                <span v-else class="normal-tag">正常</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="detail-actions">
        <button @click="createRequisition(selectedChemical)" class="btn-purchase"
                :disabled="hasPendingRequisition(selectedChemical.chemicalCode)">
          {{ hasPendingRequisition(selectedChemical.chemicalCode) ? '已有采购申请' : '生成采购申请' }}
        </button>
      </div>
    </div>

    <div v-if="pendingRequisitions.length > 0" class="requisitions">
      <h4>待处理采购申请</h4>
      <div class="req-list">
        <div v-for="req in pendingRequisitions" :key="req.requisitionCode" class="req-item">
          <div class="req-info">
            <span class="req-code">{{ req.requisitionCode }}</span>
            <span class="req-name">{{ req.chemicalName }}</span>
            <span class="req-qty">{{ req.requestedQty?.toFixed(0) }} {{ req.stockUnit }}</span>
            <span class="req-cost">¥{{ req.estimatedCost?.toFixed(0) }}</span>
          </div>
          <div class="req-actions">
            <span class="req-urgency" :class="req.urgencyLevel">
              {{ req.urgencyLevel === 'critical' ? '紧急' : req.urgencyLevel === 'normal' ? '普通' : '低' }}
            </span>
            <button @click="approveRequisition(req)" class="btn-approve">审批</button>
          </div>
        </div>
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
const inventory = ref([])
const lowStockCount = ref(0)
const pendingRequisitions = ref([])
const selectedChemical = ref(null)
const demandForecast = ref([])
const forecastCanvas = ref(null)

const getTypeLabel = (type) => {
  const labels = {
    coagulant: '混凝剂',
    coagulant_aid: '助凝剂',
    disinfectant: '消毒剂',
    ph_adjuster: 'pH调节剂'
  }
  return labels[type] || type
}

const getStockClass = (status) => {
  if (status === 'critical' || status === 'out_of_stock') return 'critical'
  if (status === 'low') return 'low'
  return ''
}

const getStockPercent = (item) => {
  if (!item.currentStock || !item.maxStock) return 50
  return Math.min(100, (item.currentStock / item.maxStock) * 100)
}

const getDaysClass = (days) => {
  if (days <= 3) return 'critical'
  if (days <= 7) return 'low'
  return ''
}

const loadData = async () => {
  try {
    const data = await api.getAllInventory()
    inventory.value = data.inventory || []
    lowStockCount.value = data.lowStockCount + data.criticalCount || 0
    pendingRequisitions.value = await api.getPendingRequisitions()
  } catch (e) {
    console.error('Load inventory failed:', e)
  }
}

const selectChemical = async (item) => {
  selectedChemical.value = item
  try {
    demandForecast.value = await api.getDemandForecast(item.chemicalCode)
    await nextTick()
    drawForecastChart()
  } catch (e) {
    console.error('Load forecast failed:', e)
  }
}

const drawForecastChart = () => {
  if (!forecastCanvas.value || !demandForecast.value.length) return

  const canvas = forecastCanvas.value
  const ctx = canvas.getContext('2d')
  const w = canvas.width
  const h = canvas.height

  ctx.clearRect(0, 0, w, h)

  const padding = { left: 45, right: 10, top: 10, bottom: 25 }
  const chartW = w - padding.left - padding.right
  const chartH = h - padding.top - padding.bottom

  const data = demandForecast.value
  const stocks = data.map(d => d.estimatedStock)
  const safetyStock = data[0]?.safetyStock || 0

  const maxVal = Math.max(...stocks, safetyStock) * 1.1
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

  const safetyY = padding.top + chartH - ((safetyStock - minVal) / (maxVal - minVal)) * chartH
  ctx.strokeStyle = '#e74c3c'
  ctx.setLineDash([5, 5])
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(padding.left, safetyY)
  ctx.lineTo(w - padding.right, safetyY)
  ctx.stroke()
  ctx.setLineDash([])

  ctx.fillStyle = '#e74c3c'
  ctx.font = '10px Arial'
  ctx.textAlign = 'left'
  ctx.fillText('安全库存', padding.left + 5, safetyY - 3)

  ctx.beginPath()
  ctx.strokeStyle = '#27ae60'
  ctx.lineWidth = 2
  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const y = padding.top + chartH - ((stocks[i] - minVal) / (maxVal - minVal)) * chartH
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  ctx.stroke()

  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    const y = padding.top + chartH - ((stocks[i] - minVal) / (maxVal - minVal)) * chartH
    ctx.beginPath()
    ctx.arc(x, y, 4, 0, Math.PI * 2)
    ctx.fillStyle = data[i].belowSafetyStock ? '#e74c3c' : '#27ae60'
    ctx.fill()
  }

  ctx.fillStyle = '#7f8c8d'
  ctx.font = '10px Arial'
  ctx.textAlign = 'center'
  for (let i = 0; i < data.length; i++) {
    const x = padding.left + i * xStep
    ctx.fillText(data[i].date.substring(5), x, h - 5)
  }

  ctx.textAlign = 'right'
  for (let i = 0; i <= 4; i++) {
    const val = maxVal - (i * (maxVal - minVal) / 4)
    const y = padding.top + (chartH / 4) * i
    ctx.fillText(val.toFixed(0), padding.left - 5, y + 3)
  }
}

const hasPendingRequisition = (code) => {
  return pendingRequisitions.value.some(r => r.chemicalCode === code)
}

const createRequisition = async (item) => {
  const req = {
    chemicalCode: item.chemicalCode,
    chemicalName: item.chemicalName,
    requestedQty: (item.maxStock || item.dailyConsumption * 30) - (item.currentStock || 0),
    stockUnit: item.stockUnit,
    urgencyLevel: item.stockStatus === 'critical' ? 'critical' : 'normal',
    currentStock: item.currentStock,
    safetyStock: item.safetyStock,
    dailyConsumption: item.dailyConsumption,
    estimatedCost: ((item.maxStock || item.dailyConsumption * 30) - (item.currentStock || 0)) * (item.unitPrice || 0),
    supplier: item.supplier,
    reason: item.stockStatus === 'critical' ? '库存危急，急需补充' : '库存偏低，建议采购',
    createdBy: 'operator'
  }

  try {
    await api.createRequisition(req)
    selectedChemical.value = null
    await loadData()
  } catch (e) {
    console.error('Create requisition failed:', e)
  }
}

const approveRequisition = async (req) => {
  try {
    await api.approveRequisition(req.requisitionCode, 'admin')
    await loadData()
  } catch (e) {
    console.error('Approve requisition failed:', e)
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
.chemical-inventory {
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

.low-badge {
  padding: 2px 8px;
  background: #e67e22;
  color: #fff;
  border-radius: 10px;
  font-size: 11px;
}

.inventory-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.inventory-card {
  position: relative;
  padding: 12px;
  border: 2px solid #e1e8ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafbfc;
}

.inventory-card:hover {
  border-color: #3498db;
  transform: translateY(-2px);
}

.inventory-card.critical {
  border-color: #e74c3c;
  background: #fdf2f2;
}

.inventory-card.low {
  border-color: #f39c12;
  background: #fef8e7;
}

.inv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.inv-name {
  font-size: 13px;
  font-weight: 600;
  color: #2c3e50;
}

.inv-type {
  font-size: 10px;
  padding: 2px 6px;
  background: #95a5a6;
  color: #fff;
  border-radius: 4px;
}

.inv-stock {
  margin-bottom: 10px;
}

.stock-bar {
  height: 8px;
  background: #ecf0f1;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 4px;
}

.stock-fill {
  height: 100%;
  border-radius: 4px;
  background: #2ecc71;
  transition: width 0.3s;
}

.stock-fill.critical { background: #e74c3c; }
.stock-fill.low { background: #f39c12; }

.stock-info {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
}

.current {
  color: #2c3e50;
  font-weight: 600;
}

.safety {
  color: #95a5a6;
}

.inv-details {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  font-size: 10px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-label {
  color: #7f8c8d;
}

.detail-value {
  color: #2c3e50;
  font-weight: 600;
}

.detail-value.critical { color: #e74c3c; }
.detail-value.low { color: #f39c12; }

.status-tag {
  position: absolute;
  top: 8px;
  right: 8px;
}

.status-tag span {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: bold;
  color: #fff;
}

.status-tag .critical { background: #e74c3c; }
.status-tag .low { background: #f39c12; }
.status-tag .out { background: #95a5a6; }

.chemical-detail {
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

.forecast-chart {
  margin-bottom: 10px;
}

.forecast-table {
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
}

.forecast-table td.warning {
  color: #e74c3c;
  font-weight: 600;
}

.warning-tag {
  padding: 2px 6px;
  background: #e74c3c;
  color: #fff;
  border-radius: 4px;
  font-size: 10px;
}

.normal-tag {
  padding: 2px 6px;
  background: #2ecc71;
  color: #fff;
  border-radius: 4px;
  font-size: 10px;
}

.detail-actions {
  margin-top: 12px;
  text-align: right;
}

.btn-purchase {
  padding: 6px 16px;
  border: none;
  background: #3498db;
  color: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.btn-purchase:disabled {
  background: #bdc3c7;
  cursor: not-allowed;
}

.requisitions {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #eee;
}

.requisitions h4 {
  margin: 0 0 10px 0;
  font-size: 13px;
  color: #34495e;
}

.req-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.req-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #fff8e1;
  border-radius: 6px;
  border-left: 3px solid #f39c12;
}

.req-info {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 12px;
}

.req-code {
  font-family: monospace;
  color: #7f8c8d;
}

.req-name {
  font-weight: 600;
  color: #2c3e50;
}

.req-qty {
  color: #34495e;
}

.req-cost {
  color: #e67e22;
  font-weight: 600;
}

.req-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.req-urgency {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.req-urgency.critical {
  background: #e74c3c;
  color: #fff;
}

.req-urgency.normal {
  background: #f39c12;
  color: #fff;
}

.btn-approve {
  padding: 4px 12px;
  border: none;
  background: #27ae60;
  color: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
}
</style>
