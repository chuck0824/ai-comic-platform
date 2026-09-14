/**
 * 剧本八阶段事实链 Feature Flag（R2-A）。
 * 默认关闭；开启后工作台应读取服务端 stage-checkpoints 投影。
 *
 * 环境变量：VITE_SCRIPT_STAGE_TRUTH_ENABLED=true
 */

export function scriptStageFeatures(env = import.meta.env) {
  return {
    stageTruthEnabled: env.VITE_SCRIPT_STAGE_TRUTH_ENABLED === 'true'
  }
}

export const DEFAULT_SCRIPT_STAGE_FEATURES = Object.freeze({
  stageTruthEnabled: false
})
