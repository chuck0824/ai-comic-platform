"""
Blender 动画烘焙器。
将 DirectorDocument 的动作片段和关键帧轨迹应用到 Blender 对象。
"""

from coordinate import domain_to_blender

def bake_animation(document):
    """
    将对象变换轨和动作片段应用到场景。

    Returns:
        list[dict]: 动画关键帧摘要
    """
    try:
        import bpy
        return _bake_with_bpy(bpy, document)
    except ImportError:
        return _bake_summary(document)

def _bake_with_bpy(bpy, document):
    """Blender 内动画烘焙"""
    fps = document.get("fps", 24)
    summaries = []

    for obj_def in document.get("objects", []):
        obj_name = obj_def.get("name", "")
        obj = bpy.data.objects.get(obj_name)
        if obj is None:
            continue

        # 清除已有动画
        if obj.animation_data:
            obj.animation_data_clear()

        # 变换轨
        for kf in obj_def.get("keyframes", []):
            frame = int(kf.get("timeMs", 0) / 1000.0 * fps)
            pos = kf.get("position")
            if pos and isinstance(pos, dict):
                bx, by, bz = domain_to_blender(
                    (pos.get("x", 0), pos.get("y", 0), pos.get("z", 0)))
                obj.location = (bx, by, bz)
                obj.keyframe_insert(data_path="location", frame=frame)

            rot = kf.get("rotation")
            if rot and isinstance(rot, dict):
                qx, qy, qz, qw = rot.get("x", 0), rot.get("y", 0), rot.get("z", 0), rot.get("w", 1)
                obj.rotation_mode = 'QUATERNION'
                obj.rotation_quaternion = (qw, qx, -qz, qy)  # Y-up → Z-up quaternion
                obj.keyframe_insert(data_path="rotation_quaternion", frame=frame)

            scale = kf.get("scale")
            if scale and isinstance(scale, dict):
                obj.scale = (scale.get("x", 1), scale.get("y", 1), scale.get("z", 1))
                obj.keyframe_insert(data_path="scale", frame=frame)

        # 设置插值模式
        for fcurve in obj.animation_data.action.fcurves if obj.animation_data and obj.animation_data.action else []:
            for kf in obj_def.get("keyframes", []):
                mode = kf.get("interpolation", "LINEAR")
                if mode == "HOLD":
                    for kp in fcurve.keyframe_points:
                        kp.interpolation = 'CONSTANT'

        summaries.append({
            "object": obj_name,
            "keyframe_count": len(obj_def.get("keyframes", [])),
            "action_count": len(obj_def.get("actions", []))
        })

    return summaries

def _bake_summary(document):
    """非 Blender 环境：返回摘要"""
    result = []
    for obj_def in document.get("objects", []):
        result.append({
            "object": obj_def.get("name", "unknown"),
            "keyframe_count": len(obj_def.get("keyframes", [])),
            "action_count": len(obj_def.get("actions", [])),
            "dry_run": True
        })
    return result
