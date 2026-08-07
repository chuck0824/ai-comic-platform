<template>
  <div class="login-page">
    <div class="login-card">
      <div style="text-align:center;margin-bottom:24px">
        <div style="width:48px;height:48px;border-radius:12px;background:var(--accent);display:inline-flex;align-items:center;justify-content:center;margin-bottom:16px">
          <el-icon :size="24" color="#fff"><VideoCamera /></el-icon>
        </div>
        <h1>AI漫剧中转平台</h1>
        <p class="sub">从创意到成片，一个画布搞定</p>
      </div>

      <el-tabs v-model="loginType" class="mb-lg" style="margin-bottom:20px">
        <el-tab-pane label="短信登录" name="sms" />
        <el-tab-pane label="密码登录" name="pwd" />
      </el-tabs>

      <template v-if="loginType === 'sms'">
        <el-form label-position="top" @submit.prevent="handleSmsLogin">
          <el-form-item label="手机号">
            <el-input v-model="phone" placeholder="请输入手机号" size="large" autocomplete="tel" />
          </el-form-item>
          <el-form-item label="验证码">
            <el-input v-model="smsCode" placeholder="6位验证码" size="large" autocomplete="one-time-code">
              <template #append>
                <el-button size="small" :disabled="countdown > 0" @click="sendSms">
                  {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
                </el-button>
              </template>
            </el-input>
          </el-form-item>
          <p class="login-hint">开发环境可直接用验证码 <b>123456</b>（无需先获取）</p>
          <el-button
            type="primary"
            size="large"
            class="w-full"
            native-type="submit"
            :loading="loading"
            @click="handleSmsLogin"
          >
            登 录
          </el-button>
        </el-form>
      </template>

      <template v-else>
        <el-form label-position="top" @submit.prevent="handlePasswordLogin">
          <el-form-item label="账号">
            <el-input v-model="account" placeholder="手机号 / 邮箱" size="large" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="password" type="password" placeholder="请输入密码" size="large" show-password autocomplete="current-password" />
          </el-form-item>
          <p class="login-hint">开发账号：<b>admin</b> / <b>admin123</b></p>
          <el-button
            type="primary"
            size="large"
            class="w-full"
            native-type="submit"
            :loading="loading"
            @click="handlePasswordLogin"
          >
            登 录
          </el-button>
        </el-form>
      </template>

      <div class="flex gap-sm items-center justify-center mt-md text-sm text-muted">
        <span>其他方式：</span>
        <el-button link type="primary" @click="handleWechatLogin">微信扫码</el-button>
        <span>·</span>
        <el-button link type="primary" @click="showRegister = true">注册账号</el-button>
      </div>
      <p class="text-center text-sm text-muted mt-md">
        <el-button link type="primary">企业SSO登录 →</el-button>
      </p>
    </div>

    <!-- 注册弹窗 -->
    <el-dialog v-model="showRegister" title="注册账号" width="420px" :close-on-click-modal="false">
      <el-form :model="registerForm" label-position="top">
        <el-form-item label="手机号">
          <el-input v-model="registerForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="registerForm.code" placeholder="6位验证码">
            <template #append><el-button size="small" @click="sendRegCode" :disabled="regCountdown>0">{{ regCountdown>0?`${regCountdown}s`:'获取验证码' }}</el-button></template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="registerForm.password" type="password" placeholder="8-20位，含大小写字母+数字+特殊字符" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="registerForm.nickname" placeholder="2-20字符" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="showRegister = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleRegister" :loading="regLoading">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { VideoCamera } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

const loginType = ref('sms')
const loading = ref(false)

// 短信登录
const phone = ref('13800000001')
const smsCode = ref('123456')
const countdown = ref(0)
const activeTimers = []
onUnmounted(() => activeTimers.forEach(clearInterval))

// 密码登录（H2 种子：admin / admin123）
const account = ref('admin')
const password = ref('admin123')

// 注册
const showRegister = ref(false)
const regLoading = ref(false)
const regCountdown = ref(0)
const registerForm = ref({ phone: '', code: '', password: '', nickname: '' })

async function sendSms() {
  if (!phone.value) {
    ElMessage.warning('请输入手机号')
    return
  }
  countdown.value = 60
  const timer = setInterval(() => { countdown.value--; if (countdown.value <= 0) { clearInterval(timer); activeTimers.splice(activeTimers.indexOf(timer), 1) } }, 1000)
  activeTimers.push(timer)
  try {
    await authApi.sendCode(phone.value, 'sms', 'login')
    ElMessage.success('验证码已发送')
  } catch (e) {
    countdown.value = 0
  }
}

async function sendRegCode() {
  if (!registerForm.value.phone) {
    ElMessage.warning('请输入手机号')
    return
  }
  regCountdown.value = 60
  const timer = setInterval(() => { regCountdown.value--; if (regCountdown.value <= 0) { clearInterval(timer); activeTimers.splice(activeTimers.indexOf(timer), 1) } }, 1000)
  activeTimers.push(timer)
  try {
    await authApi.sendCode(registerForm.value.phone, 'sms', 'register')
    ElMessage.success('验证码已发送')
  } catch (e) {
    regCountdown.value = 0
  }
}

async function handleSmsLogin() {
  if (loading.value) return
  if (!phone.value || !smsCode.value) {
    ElMessage.warning('请输入手机号和验证码')
    return
  }
  loading.value = true
  try {
    await authStore.loginBySms(phone.value, smsCode.value)
    ElMessage.success('欢迎回来！')
    router.push('/home')
  } catch (e) {
    // request 拦截器已弹出后端 message，这里不再重复泛化提示
  } finally {
    loading.value = false
  }
}

async function handlePasswordLogin() {
  if (loading.value) return
  if (!account.value || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const accountType = account.value.includes('@') ? 'email' : 'phone'
    await authStore.login({
      account: account.value,
      accountType,
      password: password.value
    })
    ElMessage.success('欢迎回来！')
    router.push('/home')
  } catch (e) {
    // request 拦截器已弹出后端 message
  } finally {
    loading.value = false
  }
}

function handleWechatLogin() { ElMessage.info('微信登录功能开发中') }

async function handleRegister() {
  regLoading.value = true
  try {
    await authStore.register({
      account: registerForm.value.phone,
      accountType: 'phone',
      password: registerForm.value.password,
      verifyCode: registerForm.value.code,
      accountCategory: 'personal',
      nickname: registerForm.value.nickname
    })
    ElMessage.success('注册成功！')
    showRegister.value = false
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error('注册失败，请重试')
  } finally { regLoading.value = false }
}
</script>
