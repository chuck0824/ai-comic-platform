<template>
  <div class="sso-page">
    <p>{{ message }}</p>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const message = ref('正在登录…')

function sanitizeRedirect(raw) {
  if (!raw || typeof raw !== 'string') return '/home'
  if (!raw.startsWith('/') || raw.startsWith('//')) return '/home'
  return raw
}

onMounted(async () => {
  const ticket = typeof route.query.ticket === 'string' ? route.query.ticket.trim() : ''
  const redirect = sanitizeRedirect(route.query.redirect)
  if (!ticket) {
    message.value = '缺少 SSO 票据'
    router.replace('/login')
    return
  }
  try {
    const res = await authApi.loginBySso(ticket)
    auth.setAuthData(res.data)
    message.value = '登录成功，正在跳转…'
    router.replace(redirect)
  } catch (e) {
    console.error(e)
    message.value = 'SSO 登录失败'
    router.replace('/login')
  }
})
</script>

<style scoped>
.sso-page {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
</style>
