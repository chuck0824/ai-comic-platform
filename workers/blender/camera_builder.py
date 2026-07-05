"""
Blender 相机构建器。
将 DirectorDocument 的相机定义和关键帧动画应用到 Blender 场景。
"""

from coordinate import domain_to_blender, domain_quat_to_blender

def build_cameras(document):
    """
    从 DirectorDocument 构建相机并设置动画关键帧。

    Returns:
        list[dict]: 相机参数摘要
    """
    try:
        import bpy
        return _build_with_bpy(bpy, document)
    except ImportError:
        return _build_summary(document)

def _build_with_bpy(bpy, document):
    """Blender 内相机构建"""
    scene = bpy.context.scene
    fps = document.get("fps", 24)
    duration_ms = document.get("durationMs", 5000)

    summaries = []
    for cam_def in document.get("cameras", []):
        name = cam_def.get("name", "Camera")
        cam_obj = bpy.data.objects.get(name)
        if cam_obj is None:
            continue

        cam = cam_obj.data
        cam.lens = cam_def.get("focalLengthMm", 50)
        cam.sensor_width = cam_def.get("sensorWidthMm", 36)
        cam.dof.aperture_fstop = cam_def.get("aperture", 2.8)

        # 设置安全框
        aspect = cam_def.get("aspectRatioOverride") or document.get("aspectRatio", "16:9")
        if ":" in aspect:
            w, h = aspect.split(":")
            ratio = float(w) / float(h)
            scene.render.resolution_y = 1080
            scene.render.resolution_x = int(1080 * ratio)

        # 动画关键帧
        if cam_obj.animation_data:
            cam_obj.animation_data_clear()

        timeline = document.get("timeline", {})
        for track in timeline.get("tracks", []):
            if track.get("trackType") == "camera":
                for kf in track.get("keyframes", []):
                    frame = int(kf.get("timeMs", 0) / 1000.0 * fps)
                    prop = kf.get("property", "")
                    value = kf.get("value")

                    if prop == "position" and isinstance(value, dict):
                        bx, by, bz = domain_to_blender(
                            (value.get("x", 0), value.get("y", 0), value.get("z", 0)))
                        cam_obj.location = (bx, by, bz)
                        cam_obj.keyframe_insert(data_path="location", frame=frame)

                    elif prop == "focal_length" and isinstance(value, (int, float)):
                        cam.lens = value
                        cam.keyframe_insert(data_path="lens", frame=frame)

        summaries.append({
            "camera": name,
            "focal_mm": cam.lens,
            "fstop": cam.dof.aperture_fstop,
            "position": (cam_obj.location.x, cam_obj.location.y, cam_obj.location.z)
        })

    return summaries

def _build_summary(document):
    """非 Blender 环境：返回摘要"""
    return [{"camera": c.get("name"), "dry_run": True} for c in document.get("cameras", [])]
