/**
 * 导演台实用增强包预设。
 * 相机机位、角色动作、灯光、材质。
 */

export const DIRECTOR_PRESETS = Object.freeze({
  camera: [
    { id: 'medium_push_in', label: '中景推进', focalLengthMm: 50, motion: 'DOLLY_IN' },
    { id: 'front_medium', label: '正面中景', focalLengthMm: 50, motion: 'STATIC' },
    { id: 'front_close', label: '正面特写', focalLengthMm: 85, motion: 'STATIC' },
    { id: 'front_wide', label: '正面全景', focalLengthMm: 24, motion: 'STATIC' },
    { id: 'side_follow', label: '侧面跟拍', focalLengthMm: 35, motion: 'TRACK_SIDE' },
    { id: 'side_near', label: '侧面近景', focalLengthMm: 85, motion: 'STATIC' },
    { id: 'overhead', label: '俯拍', focalLengthMm: 35, motion: 'CRANE_DOWN' },
    { id: 'low_angle', label: '仰角', focalLengthMm: 35, motion: 'STATIC' },
    { id: 'dutch_tilt', label: '荷兰角', focalLengthMm: 50, motion: 'STATIC' },
    { id: 'whip_pan', label: '快速甩镜', focalLengthMm: 35, motion: 'WHIP_PAN' },
    { id: 'zoom_in', label: '缓推近', focalLengthMm: 50, motion: 'ZOOM_IN' },
    { id: 'dolly_out', label: '拉远', focalLengthMm: 35, motion: 'DOLLY_OUT' }
  ],

  action: [
    { id: 'stand', clipKey: 'humanoid.stand.v1', label: '站立' },
    { id: 'walk', clipKey: 'humanoid.walk.v1', label: '行走' },
    { id: 'run', clipKey: 'humanoid.run.v1', label: '奔跑' },
    { id: 'sit', clipKey: 'humanoid.sit.v1', label: '坐下' },
    { id: 'turn', clipKey: 'humanoid.turn.v1', label: '转身' },
    { id: 'point', clipKey: 'humanoid.point.v1', label: '指向' },
    { id: 'interact', clipKey: 'humanoid.interact.v1', label: '基础交互' },
    { id: 'idle', clipKey: 'humanoid.idle.v1', label: '呼吸待机' }
  ],

  lighting: [
    { id: 'soft_key', label: '柔光主灯', intensity: 800, colorTemperatureK: 5200, direction: 'front_45' },
    { id: 'hard_key', label: '硬光主灯', intensity: 1500, colorTemperatureK: 5600, direction: 'side_30' },
    { id: 'rim', label: '轮廓光', intensity: 1200, colorTemperatureK: 4500, direction: 'back_45' },
    { id: 'fill', label: '补光', intensity: 300, colorTemperatureK: 5200, direction: 'opposite_front' },
    { id: 'warm_interior', label: '暖室内光', intensity: 600, colorTemperatureK: 3200, direction: 'top_30' },
    { id: 'cool_night', label: '冷夜景光', intensity: 400, colorTemperatureK: 6500, direction: 'top_30' }
  ],

  material: [
    { id: 'matte', label: '哑光', roughness: 0.8, metallic: 0 },
    { id: 'glossy', label: '光泽', roughness: 0.3, metallic: 0 },
    { id: 'metallic', label: '金属', roughness: 0.4, metallic: 0.9 },
    { id: 'skin', label: '皮肤', roughness: 0.6, metallic: 0, subsurface: 0.3 },
    { id: 'fabric', label: '布料', roughness: 0.9, metallic: 0 }
  ]
})
