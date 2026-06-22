import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('access_token') || '')
  const refreshToken = ref(localStorage.getItem('refresh_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const userDisplayName = computed(() => user.value?.nickname || '用户')
  const memberLevel = computed(() => user.value?.member_level || 'free')

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
    router.push('/login')
  }

  return {
    token, refreshToken, user, isLoggedIn, userDisplayName, memberLevel,
    login, loginBySms, register, logout, clearAuth
  }
})
