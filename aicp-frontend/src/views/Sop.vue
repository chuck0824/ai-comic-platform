<template>
  <div>
    <h2 class="text-xl font-bold mb-lg">生产 SOP — 霸道总裁的替身新娘 · EP01</h2>
    <div class="grid2 gap-lg">
      <!-- 准入检查 -->
      <div class="card">
        <h3 class="font-bold mb-md">生产准入检查（13项）</h3>
        <div class="table-wrap"><table><thead><tr><th>#</th><th>检查项</th><th>结果</th></tr></thead><tbody>
          <tr v-for="(check, i) in checks" :key="i">
            <td>{{ i + 1 }}</td><td>{{ check.name }}</td>
            <td>
              <span :class="['badge', check.result === 'pass' ? 'badge-success' : check.result === 'warning' ? 'badge-warning' : 'badge-danger']">
                <el-icon v-if="check.result === 'pass'"><Check /></el-icon>
                <el-icon v-else-if="check.result === 'warning'"><Warning /></el-icon>
                <el-icon v-else><Close /></el-icon>
                {{ check.result === 'pass' ? '通过' : check.result === 'warning' ? '警告' : '不通过' }}
              </span>
            </td>
          </tr>
        </tbody></table></div>
        <div class="card mt-md" style="background:#fffbeb;border:1px solid #fcd34d">
          <strong>⚠️ 黄灯：2项未通过</strong> — 可进入生产但需标记风险
        </div>
      </div>

      <!-- 审计返工 -->
      <div class="card">
        <h3 class="font-bold mb-md">审计返工表</h3>
        <div class="table-wrap"><table><thead><tr><th>镜号</th><th>问题</th><th>等级</th><th>状态</th></tr></thead><tbody>
          <tr>
            <td><code>SH003</code></td><td>Prompt超500字符</td>
            <td><span class="badge badge-danger">P1</span></td>
            <td><span class="badge badge-warning">修复中</span></td>
          </tr>
          <tr>
            <td><code>SH008</code></td><td>场景连续性断裂</td>
            <td><span class="badge badge-danger">P0</span></td>
            <td><span class="badge badge-danger">未修复</span></td>
          </tr>
          <tr>
            <td><code>SH005</code></td><td>道具颜色不一致</td>
            <td><span class="badge badge-warning">P2</span></td>
            <td><span class="badge badge-success">已修复</span></td>
          </tr>
        </tbody></table></div>
        <div class="mt-lg">
          <h3 class="font-bold mb-sm">AI 失败恢复状态</h3>
          <div class="card" style="background:#fafafa;border:none;padding:16px">
            <div class="text-sm">最近失败：镜头 <code>EP01_SC05_SH012</code></div>
            <div class="progress mt-sm"><div class="progress-fill" style="width:60%;background:var(--warning)"></div></div>
            <div class="text-sm text-muted mt-sm">3次失败 · 当前策略：检查资产参考图 → 自动重试</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Check, Close, Warning } from '@element-plus/icons-vue'

const checks = [
  { name: '剧情事实无偏移', result: 'pass' }, { name: '场景目标明确', result: 'pass' },
  { name: 'Beat完整', result: 'pass' }, { name: '人物关系变化明确', result: 'pass' },
  { name: '关键对白已锁定', result: 'pass' }, { name: '资产ID完整', result: 'warning' },
  { name: '高风险镜头已标记', result: 'pass' }, { name: 'AI提示词不过长', result: 'fail' },
  { name: 'D/E级镜头已拆分', result: 'pass' }, { name: '抽卡表/视频表已区分', result: 'warning' },
  { name: 'Voice ID明确', result: 'pass' }, { name: '配音字幕表就绪', result: 'pass' },
  { name: '上一章状态已继承', result: 'pass' }
]
</script>
