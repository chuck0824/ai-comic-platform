<template>
  <div>
    <div class="flex items-center gap-md mb-lg">
      <h2 class="text-xl font-bold">XX文化传媒有限公司</h2>
      <span class="badge badge-success">企业版 · 已认证</span>
    </div>

    <div class="grid4 mb-lg">
      <div class="card stat-card"><div class="num">12</div><div class="lbl">团队成员</div></div>
      <div class="card stat-card"><div class="num">45</div><div class="lbl">本月生成</div></div>
      <div class="card stat-card"><div class="num" style="color:var(--accent)">¥3,280</div><div class="lbl">本月支出</div></div>
      <div class="card stat-card"><div class="num">1,280</div><div class="lbl">API调用次数</div></div>
    </div>

    <div class="grid2 gap-lg">
      <div class="card">
        <h3 class="font-bold mb-md">待处理审批</h3>
        <div class="card card-hover mb-sm" style="padding:12px;border-left:3px solid var(--warning)">
          <strong>采购申请</strong> — @张三 申请购买《霸道总裁的替身新娘》<span class="badge badge-accent">¥29.9</span>
          <div class="flex gap-sm mt-sm">
            <el-button type="success" size="small"><el-icon><Check /></el-icon> 批准</el-button>
            <el-button type="danger" size="small"><el-icon><Close /></el-icon> 驳回</el-button>
          </div>
        </div>
        <div class="card card-hover" style="padding:12px;border-left:3px solid var(--warning)">
          <strong>成片审核</strong> — @李四 提交《重生之商业帝国》第3集
          <div class="flex gap-sm mt-sm"><el-button size="small"><el-icon><View /></el-icon> 查看</el-button></div>
        </div>
      </div>
      <div class="card">
        <h3 class="font-bold mb-md">团队成员</h3>
        <div class="table-wrap"><table><thead><tr><th>成员</th><th>部门</th><th>角色</th><th>权限</th></tr></thead><tbody>
          <tr><td>张三</td><td>—</td><td>管理员</td><td><span class="badge badge-accent">全部</span></td></tr>
          <tr><td>李四</td><td>内容一部</td><td>部门负责人</td><td><span class="badge badge-success">创作+审批</span></td></tr>
          <tr><td>王五</td><td>内容一部</td><td>编剧</td><td><span class="badge badge-neutral">生成+采购</span></td></tr>
          <tr><td>赵六</td><td>美术组</td><td>AI美术</td><td><span class="badge badge-neutral">画布+导出</span></td></tr>
        </tbody></table></div>
        <el-button size="small" class="mt-md"><el-icon><Plus /></el-icon> 邀请成员</el-button>
      </div>
    </div>

    <div class="card mt-lg" style="padding:16px">
      <h3 class="font-bold mb-md">快捷操作</h3>
      <div class="flex gap-sm">
        <el-button size="small">企业资产库</el-button>
        <el-button size="small">采购预算管理</el-button>
        <el-button size="small">API Key 管理</el-button>
        <el-button size="small">数据报表</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Check, Close, View, Plus } from '@element-plus/icons-vue'
</script>
