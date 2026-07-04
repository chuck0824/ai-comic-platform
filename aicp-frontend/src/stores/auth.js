import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import router from '@/router'

/** Decode the payload of a JWT without verification. */
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

function clearWorkspace() {
  localStorage.removeItem('active_workspace_id')
  localStorage.removeItem('active_workspace_type')
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('access_token') || '')
  const refreshToken = ref(localStorage.getItem('refresh_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const userDisplayName = computed(() => user.value?.nickname || '用户')
  const memberLevel = computed(() => user.value?.member_level || 'free')

  /** Extract user ID from JWT payload for workspace fallback. */
  function getUserId() {
    const payload = decodeJwtPayload(token.value)
    if (payload) {
      return payload.uid ?? payload.userId ?? payload.user_id ?? payload.sub
    }
    return null
  }

  async function login(credentials) {
    const res = await authApi.login(credentials)
    setAuthData(res.data)
    return res
  }

  async function loginBySms(phone, code) {
    const res = await authApi.loginBySms(phone, code)
    setAuthData(res.data)
    return res
  }

  async function register(data) {
    const res = await authApi.register(data)
    setAuthData(res.data)
    return res
  }

  function setAuthData(data) {
    token.value = data.token.access_token
    refreshToken.value = data.token.refresh_token
    user.value = data.user
    localStorage.setItem('access_token', token.value)
    localStorage.setItem('refresh_token', refreshToken.value)
    localStorage.setItem('user', JSON.stringify(user.value))
    initWorkspaceAfterLogin()
  }

  /**
   * Initialize workspace after login: set personal fallback, then let the
   * workspace store resolve the actual membership on first page load.
   */
  function initWorkspaceAfterLogin() {
    const uid = getUserId()
    if (uid != null) {
      localStorage.setItem('active_workspace_id', `personal_${uid}`)
      localStorage.setItem('active_workspace_type', 'personal')
    }
  }

  async function logout() {
    try {
      await authApi.logout(refreshToken.value)
    } catch (e) { /* ignore */ }
    clearAuth()
  }

  function clearAuth() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('user')
    clearWorkspace()
    router.push('/login')
  }

  // Page-refresh: ensure workspace context initialized from existing token.
  if (token.value && !localStorage.getItem('active_workspace_id')) {
    initWorkspaceAfterLogin()
  }

  return {
    token, refreshToken, user, isLoggedIn, userDisplayName, memberLevel,
    login, loginBySms, register, logout, clearAuth, getUserId, initWorkspaceAfterLogin
  }
})
