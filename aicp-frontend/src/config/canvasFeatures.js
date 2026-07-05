/**
 * Canvas 生产内核 Feature Flag。
 * 所有 Flag 默认关闭，通过环境变量逐步开启。
 *
 * 环境变量映射：
 *   VITE_CANVAS_KERNEL_V2       → kernelV2
 *   VITE_TYPED_PORTS            → typedPorts
 *   VITE_DIRECTOR_V2            → directorV2
 *   VITE_MODEL_ADAPTER_V2       → modelAdapterV2
 *   VITE_QUALITY_DELIVERY_V2    → qualityDeliveryV2
 */

export function canvasFeatures(env = import.meta.env) {
  const enabled = (key) => env[key] === 'true'
  return {
    /** R1: 新生产内核（探索/正式双模式、ShotWorkUnit、迁移） */
    kernelV2: enabled('VITE_CANVAS_KERNEL_V2'),
    /** R1: 类型化端口和角色约束连线 */
    typedPorts: enabled('VITE_TYPED_PORTS'),
    /** R2: Three.js 导演台 */
    directorV2: enabled('VITE_DIRECTOR_V2'),
    /** R3: 模型适配器预览和确认 */
    modelAdapterV2: enabled('VITE_MODEL_ADAPTER_V2'),
    /** R4: 质量报告和交付清单 */
    qualityDeliveryV2: enabled('VITE_QUALITY_DELIVERY_V2')
  }
}

/** 默认配置：所有 Flag 关闭 */
export const DEFAULT_FEATURES = Object.freeze({
  kernelV2: false,
  typedPorts: false,
  directorV2: false,
  modelAdapterV2: false,
  qualityDeliveryV2: false
})
