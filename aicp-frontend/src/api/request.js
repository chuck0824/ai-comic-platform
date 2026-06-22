import axios from 'axios'
import { ElMessage } from 'element-plus'

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
    return config
  },
  (error) => Promise.reject(error)
)

// 清除登录信息并跳转登录页
function clearAuthAndRedirect() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('user')
  window.location.href = '/login'
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
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.message || error.message || '网络异常'

    // 401: 未认证 / 403: 无权限 → 清除登录信息并跳转登录页
    if (status === 401 || status === 403) {
      // 优先显示后端返回的中文消息，其次显示通用提示
      ElMessage.warning(msg || '登录已过期，请重新登录')
      clearAuthAndRedirect()
      return Promise.reject(new Error(msg || '登录已过期，请重新登录'))
    }

    // 其他错误：显示后端返回的消息或 axios 默认消息
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  }
)

export default request
