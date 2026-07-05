"""
Blender 场景构建器。
将 DirectorDocument (Y-up) 转换为 Blender 场景 (Z-up)。
"""

import json
import os
from coordinate import domain_to_blender, domain_quat_to_blender

def build_scene(document, assets, temp_dir):
    """
    在 Blender 中构建场景。
    需要 Blender bpy 模块（仅在 Blender Python 环境中可用）。

    Returns:
        dict: { scene_objects: [...], camera_count: int, triangle_count: int }
    """
    try:
        import bpy
        return _build_with_bpy(bpy, document, assets, temp_dir)
    except ImportError:
        # 非 Blender 环境：返回空场景（用于纯单元测试）
        return _build_dry_run(document, assets)

def _build_with_bpy(bpy, document, assets, temp_dir):
    """使用 Blender Python API 构建场景"""
    # 清空默认场景
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)

    # 设置场景参数
    scene = bpy.context.scene
    scene.render.fps = document.get("fps", 24)
    scene.frame_start = 0
    scene.frame_end = int(document.get("durationMs", 5000) / 1000.0 * scene.render.fps)

    scene_objects = []
    triangle_count = 0

    # 创建相机
    cameras = document.get("cameras", [])
    for cam_def in cameras:
        bpy.ops.object.camera_add()
        cam_obj = bpy.context.object
        cam_obj.name = cam_def.get("name", "Camera")
        cam = cam_obj.data
        cam.lens = cam_def.get("focalLengthMm", 50)
        cam.sensor_width = cam_def.get("sensorWidthMm", 36)

        scene_objects.append({"name": cam_obj.name, "type": "camera", "id": cam_def.get("id")})
        if cam_def.get("id") == document.get("activeCameraId"):
            scene.camera = cam_obj

    # 创建场景对象
    for obj_def in document.get("objects", []):
        pos = obj_def.get("position", {"x": 0, "y": 0, "z": 0})
        bx, by, bz = domain_to_blender((pos.get("x", 0), pos.get("y", 0), pos.get("z", 0)))

        obj_type = obj_def.get("type", "geometry")
        if obj_type == "human":
            bpy.ops.mesh.primitive_uv_sphere_add(radius=0.5, location=(bx, by, bz))
        elif obj_type == "geometry":
            sub = obj_def.get("subType", "cube")
            if sub == "sphere":
                bpy.ops.mesh.primitive_uv_sphere_add(radius=0.5, location=(bx, by, bz))
            else:
                bpy.ops.mesh.primitive_cube_add(size=1, location=(bx, by, bz))
        elif obj_type == "light":
            bpy.ops.object.light_add(type='POINT', location=(bx, by, bz))

        obj = bpy.context.object
        obj.name = obj_def.get("name", f"Object_{obj_def.get('id')}")

        scene_objects.append({"name": obj.name, "type": obj_type, "id": obj_def.get("id")})
        triangle_count += len(obj.data.polygons) if hasattr(obj.data, 'polygons') else 0

    # 导入 GLB 资产
    for asset in assets:
        if asset.get("file_path") and os.path.exists(asset["file_path"]):
            if asset["file_path"].endswith(".glb") or asset["file_path"].endswith(".gltf"):
                bpy.ops.import_scene.gltf(filepath=asset["file_path"])
                for imported in bpy.context.selected_objects:
                    scene_objects.append({"name": imported.name, "type": "asset",
                                           "asset_id": asset.get("asset_id")})

    return {
        "scene_objects": scene_objects,
        "camera_count": len(cameras),
        "triangle_count": triangle_count
    }

def _build_dry_run(document, assets):
    """非 Blender 环境：空运行构建"""
    return {
        "scene_objects": [],
        "camera_count": len(document.get("cameras", [])),
        "triangle_count": 0,
        "dry_run": True
    }
