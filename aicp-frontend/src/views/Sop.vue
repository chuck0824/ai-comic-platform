<template>
  <div class="sop-redirect">
    <el-result icon="info" title="SOP 页面已更新" sub-title="正在跳转到新版生产 SOP 工作台...">
      <template #extra>
        <el-button type="primary" @click="goToSop">前往新版 SOP</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

function goToSop() {
  const projectId = route.params.projectId
  if (projectId && projectId !== '1') {
    router.push(`/content-projects/${projectId}/sop`)
  } else {
    router.push('/sop')
  }
}

// Auto-redirect after short delay
setTimeout(goToSop, 2000)
</script>

<style scoped>
.sop-redirect {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}
</style>
