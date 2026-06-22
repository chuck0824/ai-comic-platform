<template>
  <div style="max-width:800px">
    <h2 class="text-xl font-bold mb-lg">个人中心</h2>

    <!-- 加载中 -->
    <div v-if="loading" class="canvas-mock" style="min-height:200px;display:flex;align-items:center;justify-content:center">
      <span class="text-muted">加载中...</span>
    </div>

    <template v-else>
      <div class="grid2 gap-lg">
        <!-- 头像卡片 -->
        <div class="card" style="text-align:center">
          <el-avatar :size="80" style="background:var(--accent);font-size:32px;font-weight:700;margin-bottom:16px">
            {{ displayInitial }}
          </el-avatar>
          <h3 class="font-bold">{{ profile.nickname }}</h3>
          <p class="text-sm text-muted">{{ memberLevelText }}</p>
          <p v-if="membership.expire" class="text-xs text-muted mt-sm">
            会员到期: {{ membership.expire || '永久' }}
          </p>
          <div class="flex gap-sm justify-center mt-md">
            <span class="badge badge-accent">本月生成 {{ stats.scripts }}次</span>
            <span class="badge badge-success">导出 {{ stats.exports }}条</span>
          </div>
          <el-button size="default" class="mt-lg" @click="editProfileVisible = true">编辑资料</el-button>
        </div>

        <!-- 账户信息 -->
        <div class="card">
          <h3 class="font-bold mb-md">账户信息</h3>
          <div class="flex flex-col gap-sm">
            <div class="flex justify-between"><span class="text-muted">昵称</span><span>{{ profile.nickname }}</span></div>
            <div class="flex justify-between"><span class="text-muted">手机号</span><span>{{ profile.phone }}</span></div>
            <div class="flex justify-between"><span class="text-muted">邮箱</span><span>{{ profile.email || '未绑定' }}</span></div>
            <div class="flex justify-between"><span class="text-muted">账户类型</span><span>{{ accountTypeText }}</span></div>
            <div class="flex justify-between"><span class="text-muted">实名状态</span>
              <span :class="['badge', realNameBadge]">{{ realNameText }}</span>
            </div>
            <div class="flex justify-between"><span class="text-muted">会员等级</span>
              <span class="badge badge-accent">{{ memberLevelText }}</span>
            </div>
            <div class="flex justify-between"><span class="text-muted">注册时间</span><span>{{ profile.createdAt || '—' }}</span></div>
          </div>
        </div>
      </div>

      <!-- 使用统计 -->
      <div class="card mt-lg">
        <h3 class="font-bold mb-md">使用统计</h3>
        <div class="grid4">
          <div class="stat-card" style="background:var(--bg-app);border-radius:var(--radius-md);padding:16px">
            <div class="num" style="font-size:20px">{{ stats.scripts }}</div>
            <div class="lbl">本月生成次数</div>
          </div>
          <div class="stat-card" style="background:var(--bg-app);border-radius:var(--radius-md);padding:16px">
            <div class="num" style="font-size:20px">{{ stats.exports }}</div>
            <div class="lbl">导出视频数</div>
          </div>
          <div class="stat-card" style="background:var(--bg-app);border-radius:var(--radius-md);padding:16px">
            <div class="num" style="font-size:20px">{{ stats.warehouse }}</div>
            <div class="lbl">仓库剧本数</div>
          </div>
          <div class="stat-card" style="background:var(--bg-app);border-radius:var(--radius-md);padding:16px">
            <div class="num" style="font-size:20px">{{ stats.storage }}</div>
            <div class="lbl">存储空间</div>
          </div>
        </div>
      </div>

      <!-- 会员权益 -->
      <div class="card mt-lg">
        <h3 class="font-bold mb-md">会员权益</h3>
        <div class="flex flex-col gap-sm">
          <div v-for="(val, key) in membership.benefits" :key="key" class="flex justify-between">
            <span class="text-muted">{{ benefitLabel(key) }}</span>
            <span :class="val ? 'text-success' : 'text-muted'">
              <el-icon v-if="val"><CircleCheck /></el-icon>
              <el-icon v-else><Close /></el-icon>
            </span>
          </div>
        </div>
        <el-button v-if="auth.memberLevel === 'free'" size="default" type="primary" class="mt-md" @click="ElMessage.info('会员升级功能即将上线')">
          升级会员
        </el-button>
      </div>

      <!-- 安全设置 -->
      <div class="card mt-lg">
        <h3 class="font-bold mb-md">安全设置</h3>
        <div class="flex flex-col gap-sm">
          <el-button size="default" @click="ElMessage.info('修改密码功能即将上线')">修改密码</el-button>
          <el-button size="default" @click="ElMessage.info('MFA功能即将上线')">MFA多因素认证</el-button>
          <el-button size="default" @click="ElMessage.info('设备管理功能即将上线')">登录设备管理</el-button>
        </div>
      </div>
    </template>

    <el-button size="default" type="danger" class="mt-lg" @click="auth.logout()">退出登录</el-button>

    <!-- 编辑资料弹窗 -->
    <el-dialog v-model="editProfileVisible" title="编辑资料" width="400px">
      <el-form>
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" />
        </el-form-item>
        <el-form-item label="头像URL">
          <el-input v-model="editForm.avatarUrl" placeholder="https://..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editProfileVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveProfile" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
import { scriptApi } from '@/api/script'

const auth = useAuthStore()
const loading = ref(true)
const saving = ref(false)
const editProfileVisible = ref(false)

// 从后端加载的真实数据
const profile = reactive({
  nickname: '',
  phone: '',
  email: '',
  accountType: '',
  realNameStatus: '',
  createdAt: '',
  avatarUrl: ''
})

const stats = reactive({
  scripts: 0,
  exports: 0,
  warehouse: 0,
  storage: '0MB'
})

const membership = reactive({
  level: '',
  expire: '',
  benefits: {}
})

const editForm = reactive({ nickname: '', avatarUrl: '' })

const displayInitial = computed(() => profile.nickname?.charAt(0) || auth.user?.nickname?.charAt(0) || '用')

const memberLevelText = computed(() => {
  const map = { free: '免费用户', creator: '创作者会员', enterprise: '企业版' }
  return map[membership.level || auth.memberLevel] || '免费用户'
})

const accountTypeText = computed(() => {
  return profile.accountType === 'enterprise' ? '企业账户' : '个人创作者'
})

const realNameText = computed(() => {
  const map = { unverified: '未认证', pending: '审核中', verified: '已认证' }
  return map[profile.realNameStatus] || '未知'
})

const realNameBadge = computed(() => {
  const map = { unverified: 'badge-neutral', pending: 'badge-warning', verified: 'badge-success' }
  return map[profile.realNameStatus] || 'badge-neutral'
})

function benefitLabel(key) {
  const map = {
    dailyGenQuota: '每日生成配额',
    repoCapacity: '仓库容量',
    canListScript: '剧本上架',
    exportNoWatermark: '无水印导出',
    batchGenerate: '批量生成',
    maxResolution: '最高分辨率'
  }
  return map[key] || key
}

// 加载用户数据
async function loadProfile() {
  loading.value = true
  try {
    // 并行获取 profile + membership + scripts count
    const [profileRes, memberRes, scriptsRes] = await Promise.allSettled([
      userApi.getProfile(),
      userApi.getMembership(),
      scriptApi.getScripts({ page: 1, page_size: 1 })
    ])

    if (profileRes.status === 'fulfilled') {
      const data = profileRes.value.data
      Object.assign(profile, {
        nickname: data?.nickname || auth.user?.nickname || '',
        phone: data?.phone || auth.user?.phone || '—',
        email: data?.email || '未绑定',
        accountType: data?.account_type || auth.user?.accountType || 'free_user',
        realNameStatus: data?.real_name_status || 'unverified',
        createdAt: data?.created_at ? new Date(data.created_at).toLocaleDateString('zh-CN') : '—',
        avatarUrl: data?.avatar_url || ''
      })

      // 从 profile 读取统计
      if (data?.stats) {
        stats.scripts = data.stats.scripts_generated || 0
        stats.exports = data.stats.videos_exported || 0
        stats.warehouse = data.stats.scripts_in_repo || 0
        stats.storage = (data.stats.storage_used_mb || 0) + 'MB'
      }
    }

    if (memberRes.status === 'fulfilled') {
      const data = memberRes.value.data
      membership.level = data?.level || 'free'
      membership.expire = data?.expire_at || data?.expireAt || '—'
      membership.benefits = data?.benefits || {}
    }

    // 从脚本列表获取仓库数量
    if (scriptsRes.status === 'fulfilled') {
      const total = scriptsRes.value.data?.pagination?.total
      if (total !== undefined && !stats.warehouse) {
        stats.warehouse = total
      }
    }
  } catch {
    // 降级到 auth store 数据
    profile.nickname = auth.user?.nickname || ''
    ElMessage.warning('部分数据加载失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    await userApi.updateProfile({
      nickname: editForm.nickname,
      avatar_url: editForm.avatarUrl
    })
    profile.nickname = editForm.nickname
    profile.avatarUrl = editForm.avatarUrl
    editProfileVisible.value = false
    ElMessage.success('资料已更新')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}

// 打开编辑弹窗时初始化表单
const originalProfileWatch = computed(() => {
  editForm.nickname = profile.nickname
  editForm.avatarUrl = profile.avatarUrl
  return ''
})
// Fix: use watch instead
import { watch } from 'vue'
watch(editProfileVisible, (val) => {
  if (val) {
    editForm.nickname = profile.nickname
    editForm.avatarUrl = profile.avatarUrl
  }
})

onMounted(loadProfile)
</script>
