"""
Blender Eevee 渲染器。
按黄金场景预设参数输出预览帧和 MP4。
"""

import os
import json
import hashlib

def render_preview(document, output_dir, preset="standard"):
    """
    使用 Eevee 渲染预览。
    preset: "draft" (16 samples) | "standard" (32) | "high" (64)

    Returns:
        list[dict]: 输出文件清单
    """
    try:
        import bpy
        return _render_with_bpy(bpy, document, output_dir, preset)
    except ImportError:
        return _render_dry_run(output_dir)

def _render_with_bpy(bpy, document, output_dir, preset):
    """Blender 内渲染"""
    scene = bpy.context.scene
    scene.render.engine = 'BLENDER_EEVEE'

    samples = {"draft": 16, "standard": 32, "high": 64}.get(preset, 32)
    scene.eevee.taa_render_samples = samples

    fps = document.get("fps", 24)
    duration_ms = document.get("durationMs", 5000)
    frame_end = int(duration_ms / 1000.0 * fps)

    scene.render.fps = fps
    scene.frame_start = 0
    scene.frame_end = frame_end

    # 输出设置
    scene.render.resolution_x = 1920
    scene.render.resolution_y = 1080
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'FFMPEG'
    scene.render.image_settings.color_mode = 'RGB'

    os.makedirs(output_dir, exist_ok=True)

    outputs = []

    # 渲染首帧、尾帧
    for label, frame in [("first_frame", 0), ("last_frame", frame_end)]:
        scene.frame_set(frame)
        path = os.path.join(output_dir, f"{label}.png")
        scene.render.filepath = path
        bpy.ops.render.render(write_still=True)
        outputs.append({"path": path, "type": "image", "label": label, "frame": frame})

    # 渲染关键节拍帧（每 1/4 时长）
    for i in range(1, 4):
        beat_frame = int(frame_end * i / 4)
        scene.frame_set(beat_frame)
        path = os.path.join(output_dir, f"beat_{i}.png")
        scene.render.filepath = path
        bpy.ops.render.render(write_still=True)
        outputs.append({"path": path, "type": "image", "label": f"beat_{i}", "frame": beat_frame})

    # 渲染 MP4 预览
    mp4_path = os.path.join(output_dir, "preview.mp4")
    scene.render.filepath = mp4_path
    scene.render.image_settings.file_format = 'FFMPEG'
    scene.render.ffmpeg.format = 'MPEG4'
    scene.render.ffmpeg.codec = 'H264'
    bpy.ops.render.render(animation=True)
    outputs.append({"path": mp4_path, "type": "video", "format": "mp4"})

    return outputs

def _render_dry_run(output_dir):
    """非 Blender 环境：生成占位文件"""
    os.makedirs(output_dir, exist_ok=True)
    outputs = []
    for name in ["first_frame.png", "last_frame.png", "beat_1.png", "beat_2.png", "beat_3.png"]:
        path = os.path.join(output_dir, name)
        with open(path, "w") as f:
            f.write("dry-run placeholder\n")
        outputs.append({"path": path, "type": "image"})
    return outputs
