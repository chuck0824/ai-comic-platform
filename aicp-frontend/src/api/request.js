import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/** UTF-8-safe JWT payload decoder (mirrors @/stores/auth.js). */
function decodeJwtPayload(token) {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(json)
  } catch {
    return null
  }
}

/** Persist personal workspace context from a JWT access token. */
function deriveAndStoreWorkspace(token) {
  const payload = decodeJwtPayload(token)
  if (payload) {
    const uid = payload.uid ?? payload.userId ?? payload.user_id ?? payload.sub
    if (uid != null) {
      localStorage.setItem('active_workspace_id', `personal_${uid}`)
      localStorage.setItem('active_workspace_type', 'personal')
      return true
    }
  }
  return false
}

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
        const payload = decodeJwtPayload(token)
        if (payload) {
          // Support both 'uid' (JWT claim name) and 'userId' as fallback
          const uid = payload.uid ?? payload.userId ?? payload.user_id ?? payload.sub
          if (uid != null) {
            workspaceId = `personal_${uid}`
            localStorage.setItem('active_workspace_id', workspaceId)
            localStorage.setItem('active_workspace_type', 'personal')
          } else {
            console.warn('[request] 无法从 JWT 中提取用户ID，payload keys:', Object.keys(payload))
          }
        }
      } catch (e) {
        console.warn('[request] JWT 解码失败，无法推导 workspace ID:', e)
      }
    }
    if (workspaceId && !config.headers['X-Workspace-Id']) {
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
      // 确保 workspace 上下文在新 token 下仍然有效
      if (!localStorage.getItem('active_workspace_id')) {
        deriveAndStoreWorkspace(newToken)
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
