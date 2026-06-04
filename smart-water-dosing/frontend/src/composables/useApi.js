import { ref, onMounted, onUnmounted } from 'vue'

const API = ''

export function usePolling(url, intervalMs, immediate = true) {
  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)
  let timer = null

  async function loadData() {
    loading.value = true
    try {
      const resp = await window.fetch(API + url)
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      data.value = await resp.json()
      error.value = null
    } catch (e) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    if (immediate) loadData()
    if (intervalMs > 0) {
      timer = setInterval(loadData, intervalMs)
    }
  })

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return { data, loading, error, refetch: loadData }
}

export function useApi() {
  async function get(url) {
    const resp = await window.fetch(API + url)
    return resp.json()
  }

  async function post(url, params, body) {
    let fullUrl = API + url
    if (params) {
      fullUrl += '?' + new URLSearchParams(params).toString()
    }
    const options = { method: 'POST' }
    if (body) {
      options.headers = { 'Content-Type': 'application/json' }
      options.body = JSON.stringify(body)
    }
    const resp = await window.fetch(fullUrl, options)
    return resp.json()
  }

  return { get, post }
}

export function useAdvancedApi() {
  const { get, post } = useApi()

  return {
    getWaterSources: () => get('/api/water-sources'),
    getCurrentDistribution: () => get('/api/water-distribution/current'),
    runOptimization: () => post('/api/water-distribution/optimize'),
    getWaterCostTrend: (days = 7) => get(`/api/water-distribution/cost-trend?days=${days}`),

    getMembraneModules: () => get('/api/membrane/modules'),
    getMembraneModule: (code) => get(`/api/membrane/${code}`),
    getFoulingTrend: (code, hours = 24) => get(`/api/membrane/${code}/fouling-trend?hours=${hours}`),
    evaluateFouling: () => post('/api/membrane/evaluate'),
    getCleaningSchedule: () => get('/api/membrane/cleaning-schedule'),
    recordCleaning: (record) => post('/api/membrane/cleaning', null, record),
    getCleaningHistory: (moduleCode, days = 30) => get(`/api/membrane/cleaning-history?moduleCode=${moduleCode || ''}&days=${days}`),

    getAllInventory: () => get('/api/chemical/inventory'),
    getInventory: (code) => get(`/api/chemical/inventory/${code}`),
    getDemandForecast: (code) => get(`/api/chemical/${code}/demand-forecast`),
    updateInventory: () => post('/api/chemical/inventory/update'),
    getLowStockAlerts: () => get('/api/chemical/low-stock-alerts'),
    getPendingRequisitions: () => get('/api/chemical/purchase/pending'),
    createRequisition: (req) => post('/api/chemical/purchase', null, req),
    approveRequisition: (code, approvedBy = 'admin') => post(`/api/chemical/purchase/${code}/approve`, { approvedBy }),
    completeRequisition: (code, receivedQty) => post(`/api/chemical/purchase/${code}/complete`, { receivedQty }),

    runForecast: () => post('/api/forecast/run'),
    getLatestForecast: (stage = 'outlet') => get(`/api/forecast/latest?stage=${stage}`),
    getForecastSummary: (stage = 'outlet') => get(`/api/forecast/summary?stage=${stage}`),
    getActiveWarnings: () => get('/api/forecast/warnings'),

    getAdvancedStatus: () => get('/api/advanced/status')
  }
}

export function useModuleApi() {
  const { get, post } = useApi()

  return {
    waterSourceOptimizer: {
      getModuleInfo: () => get('/api/v1/water-source-optimizer/module-info'),
      getAllSources: () => get('/api/v1/water-source-optimizer/sources'),
      getLatestSourceData: () => get('/api/v1/water-source-optimizer/sources/latest'),
      runOptimization: () => post('/api/v1/water-source-optimizer/optimize'),
      runOptimizationAsync: () => post('/api/v1/water-source-optimizer/optimize/async'),
      getOptimizationStatus: () => get('/api/v1/water-source-optimizer/status'),
      getDailyCostTrend: (days = 7) => get(`/api/v1/water-source-optimizer/cost-trend?days=${days}`)
    },

    membraneMonitor: {
      getModuleInfo: () => get('/api/v1/membrane-monitor/module-info'),
      getAllModulesStatus: () => get('/api/v1/membrane-monitor/status'),
      getModuleLatest: (code) => get(`/api/v1/membrane-monitor/module/${code}/latest`),
      getFoulingTrend: (code, hours = 24) => get(`/api/v1/membrane-monitor/module/${code}/fouling-trend?hours=${hours}`),
      evaluateFouling: () => post('/api/v1/membrane-monitor/evaluate'),
      evaluateFoulingAsync: () => post('/api/v1/membrane-monitor/evaluate/async'),
      getCleaningSchedule: () => get('/api/v1/membrane-monitor/cleaning-schedule'),
      recordCleaning: (record) => post('/api/v1/membrane-monitor/cleaning', null, record),
      getStreamStatus: () => get('/api/v1/membrane-monitor/stream/status')
    },

    chemicalInventory: {
      getModuleInfo: () => get('/api/v1/chemical-inventory/module-info'),
      getAllInventoryStatus: () => get('/api/v1/chemical-inventory/status'),
      getInventoryLatest: (code) => get(`/api/v1/chemical-inventory/chemical/${code}/latest`),
      get7DayForecast: (code) => get(`/api/v1/chemical-inventory/chemical/${code}/forecast`),
      updateInventory: () => post('/api/v1/chemical-inventory/update'),
      updateInventoryAsync: () => post('/api/v1/chemical-inventory/update/async'),
      getLowStockAlerts: () => get('/api/v1/chemical-inventory/alerts/low-stock'),
      getSupplierReliability: (name) => get(`/api/v1/chemical-inventory/supplier-reliability?supplierName=${encodeURIComponent(name)}`),
      getPendingRequisitions: () => get('/api/v1/chemical-inventory/requisitions/pending'),
      approveRequisition: (code, approvedBy = 'system') => post(`/api/v1/chemical-inventory/requisitions/${code}/approve?approvedBy=${approvedBy}`),
      completeRequisition: (code, receivedQty) => post(`/api/v1/chemical-inventory/requisitions/${code}/complete?receivedQty=${receivedQty}`)
    },

    waterQualityForecaster: {
      getModuleInfo: () => get('/api/v1/water-quality-forecaster/module-info'),
      forecastSync: (stage = 'outlet') => post(`/api/v1/water-quality-forecaster/forecast/${stage}`),
      forecastAsync: (stage = 'outlet') => post(`/api/v1/water-quality-forecaster/forecast/${stage}/async`),
      getLatestForecast: (stage = 'outlet') => get(`/api/v1/water-quality-forecaster/forecast/${stage}/latest`),
      getForecastSummary: (stage = 'outlet') => get(`/api/v1/water-quality-forecaster/forecast/${stage}/summary`),
      getActiveWarnings: () => get('/api/v1/water-quality-forecaster/warnings/active'),
      getRunningTasks: () => get('/api/v1/water-quality-forecaster/tasks/running')
    }
  }
}
