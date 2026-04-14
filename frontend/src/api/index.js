import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 响应拦截器：检查业务错误码 + 自动上报前端接口错误到错误日志
api.interceptors.response.use(
  (response) => {
    // 检查业务层错误码（后端返回 code !== 200 表示业务失败）
    if (response.data && response.data.code && response.data.code !== 200) {
      const url = response.config?.url || ''
      if (!url.includes('/error-log/')) {
        const detail = `[${response.config?.method?.toUpperCase()}] ${url} - 业务错误: ${response.data.message}`
        api.post('/error-log/report', { errorType: '后端业务错误', errorDetail: detail }).catch(() => {})
      }
      const err = new Error(response.data.message || '请求失败')
      err.response = response
      return Promise.reject(err)
    }
    return response
  },
  (error) => {
    const url = error.config?.url || ''
    // 排除错误日志接口自身，避免死循环
    if (!url.includes('/error-log/')) {
      const status = error.response?.status || 'N/A'
      const msg = error.response?.data?.message || error.message || '未知错误'
      const detail = `[${error.config?.method?.toUpperCase()}] ${url} - HTTP ${status}: ${msg}`
      // 异步上报，不阻塞业务
      api.post('/error-log/report', { errorType: '前端接口错误', errorDetail: detail }).catch(() => {})
    }
    return Promise.reject(error)
  }
)

// 数据源
export const sourceApi = {
  list: (params) => api.get('/source/list', { params }),
  getById: (id) => api.get(`/source/${id}`),
  importExcel: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/source/import', formData)
  },
  analyze: (id) => api.post(`/source/${id}/analyze`),
  previewRule: (id) => api.post(`/source/${id}/preview-rule`, null, { timeout: 120000 }),
  confirmRule: (id, rule) => api.post(`/source/${id}/confirm-rule`, rule),
  testRule: (id, rule) => api.post(`/source/${id}/test-rule`, rule, { timeout: 60000 }),
  crawl: (id) => api.post(`/source/${id}/crawl`),
  getRule: (sourceId) => api.get(`/source/rule/${sourceId}`),
  delete: (id) => api.post(`/source/${id}/delete`),
}

// 成果
export const achievementApi = {
  list: (params) => api.get('/achievement/list', { params }),
  getById: (id) => api.get(`/achievement/${id}`),
  delete: (id) => api.delete(`/achievement/${id}`),
}

// 任务
export const taskApi = {
  list: (params) => api.get('/task/list', { params }),
  stop: (id) => api.post(`/task/${id}/stop`),
  retry: (id) => api.post(`/task/${id}/retry`),
  delete: (id) => api.delete(`/task/${id}`),
}

// 链接（AI判断结果）
export const linkApi = {
  list: (params) => api.get('/link/list', { params }),
  judge: (id, isAchievement, reason) => api.post(`/link/${id}/judge`, { isAchievement, reason }),
  batchStructure: () => api.post('/link/batch-structure'),
}

// 仪表盘
export const dashboardApi = {
  overview: () => api.get('/dashboard/overview'),
  trend: (days = 30) => api.get('/dashboard/trend', { params: { days } }),
  topSources: (days = 30, limit = 10) => api.get('/dashboard/top-sources', { params: { days, limit } }),
}

// 错误日志
export const errorLogApi = {
  list: (params) => api.get('/error-log/list', { params }),
  report: (errorType, errorDetail) => api.post('/error-log/report', { errorType, errorDetail }),
}

export default api
