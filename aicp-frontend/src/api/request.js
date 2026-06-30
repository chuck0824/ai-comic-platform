import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // Attach active workspace context for tenant-safe asset operations
    let workspaceId = localStorage.getItem('active_workspace_id')
    // Fallback: derive personal workspace from token if not yet stored (e.g. sessions
    // created before this logic was deployed).
    if (!workspaceId && token) {
      try {
        const base64Url = token.split('.')[1]
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
        const payload = JSON.parse(atob(base64))
        if (payload && payload.uid != null) {
          workspaceId = `personal_${payload.uid}`
          localStorage.setItem('active_workspace_id', workspaceId)
          localStorage.setItem('active_workspace_type', 'personal')
        }
      } catch { /* ignore malformed token */ }
    }
    if (workspaceId) {
      config.headers['X-Workspace-Id'] = workspaceId
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ---- 静默 Token 刷新 ----
let isRefreshing = false
let pendingRequests = []

function onRefreshed(newToken) {
  pendingRequests.forEach((cb) => cb(newToken))
  pendingRequests = []
}

function addPendingRequest(cb) {
  pendingRequests.push(cb)
}

function clearAuthAndRedirect() {
  isRefreshing = false
  pendingRequests = []
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('user')
  localStorage.removeItem('active_workspace_id')
  localStorage.removeItem('active_workspace_type')
  router.push('/login')
}

async function tryRefreshToken() {
  const refreshToken = localStorage.getItem('refresh_token')
  if (!refreshToken) return null

  try {
    const res = await axios.post('/api/v1/auth/refresh-token', {
      refresh_token: refreshToken
    })
    if (res.data?.code === 0 && res.data?.data?.access_token) {
      const newToken = res.data.data.access_token
      localStorage.setItem('access_token', newToken)
      // 如果后端同时返回了新的 refresh_token
      if (res.data.data.refresh_token) {
        localStorage.setItem('refresh_token', res.data.data.refresh_token)
      }
      return newToken
    }
  } catch {
    // refresh failed
  }
  return null
}

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 40003 || res.code === 41007) {
        clearAuthAndRedirect()
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  async (error) => {
    const { config, response } = error
    const status = response?.status
    const msg = response?.data?.message || error.message || '网络异常'

    // 401: 尝试静默刷新 token，失败再跳转登录
    if (status === 401 && config && !config._retry) {
      if (isRefreshing) {
        // 已在刷新中，排队等待
        return new Promise((resolve) => {
          addPendingRequest((newToken) => {
            config.headers.Authorization = `Bearer ${newToken}`
            config._retry = true
            resolve(request(config))
          })
        })
      }

      config._retry = true
      isRefreshing = true

      const newToken = await tryRefreshToken()

      if (newToken) {
        onRefreshed(newToken)
        isRefreshing = false
        config.headers.Authorization = `Bearer ${newToken}`
        return request(config)
      }

      // 刷新失败，清除登录并跳转
      ElMessage.warning(msg || '登录已过期，请重新登录')
      clearAuthAndRedirect()
      return Promise.reject(error)
    }

    // 403 / 其他错误
    if (status === 403) {
      ElMessage.warning(msg || '无权限')
      return Promise.reject(error)
    }

    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
