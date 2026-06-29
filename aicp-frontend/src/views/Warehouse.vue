<template>
  <div>
    <div class="flex items-center justify-between mb-lg">
      <h2 class="text-xl font-bold">我的剧本仓库</h2>
      <div class="flex gap-sm">
        <el-button size="small" type="primary" @click="$router.push('/content-projects')">
          前往内容项目
        </el-button>
        <el-button size="small" @click="$router.push('/script-gen')">
          旧版剧本创作
        </el-button>
      </div>
    </div>
    <div class="card p-lg text-center">
      <p class="text-muted mb-md">剧本仓库已升级为内容项目管理。</p>
      <p class="text-sm text-muted mb-md">旧版剧本仍可通过搜索查看，新创作建议使用项目模式。</p>
      <el-button type="primary" @click="$router.push('/content-projects')">马上体验</el-button>
    </div>
  </div>
</template>
