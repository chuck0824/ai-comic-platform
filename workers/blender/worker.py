"""
Blender 4.2 LTS Worker 入口。
消费不可变 DirectorRevision → 构建场景 → 烘焙动画 → 设置相机 → Eevee 渲染 → 输出清单。
在隔离临时目录中运行，不持久化凭证，不记录签名 URL 到日志。
"""

import json
import sys
import os
import hashlib
import time
import traceback
from coordinate import domain_to_blender, blender_to_domain, is_normalized_quat
from manifest import build_manifest, verify_assets
from scene_builder import build_scene
from animation_baker import bake_animation
from camera_builder import build_cameras
from renderer import render_preview


def main():
    """Worker 入口：revision.json → 场景构建 → 渲染 → manifest.json"""
    if len(sys.argv) < 3:
        print("Usage: python worker.py <revision.json> <output_dir> [render_preset]", file=sys.stderr)
        sys.exit(1)

    revision_path = sys.argv[1]
    output_dir = sys.argv[2]
    render_preset = sys.argv[3] if len(sys.argv) > 3 else "standard"

    start_time = time.time()
    print(f"[Worker] 开始处理: revision={revision_path}, output={output_dir}, preset={render_preset}")

    try:
        # 1. 读取 revision
        with open(revision_path) as f:
            revision = json.load(f)

        document = revision.get("document_json") or revision.get("document")
        if isinstance(document, str):
            document = json.loads(document)

        assets = revision.get("assets", [])

        # 2. 验证资产许可
        verify_assets(assets)

        # 3. 构建场景（Y-up → Z-up 转换）
        print(f"[Worker] 构建场景 (Y-up → Z-up)...")
        scene_info = build_scene(document, assets, output_dir)
        print(f"  - 对象: {len(scene_info.get('scene_objects', []))}, "
              f"相机: {scene_info.get('camera_count', 0)}, "
              f"三角形: {scene_info.get('triangle_count', 0)}")

        # 4. 烘焙动画
        print(f"[Worker] 烘焙动画关键帧...")
        anim_info = bake_animation(document)
        print(f"  - 动画对象: {len(anim_info)}")

        # 5. 设置相机
        print(f"[Worker] 设置相机...")
        cam_info = build_cameras(document)
        print(f"  - 相机: {len(cam_info)}")

        # 6. Eevee 渲染
        print(f"[Worker] Eevee 渲染 (preset={render_preset})...")
        render_outputs = render_preview(document, output_dir, render_preset)
        print(f"  - 输出文件: {len(render_outputs)}")

        # 7. 生成清单
        os.makedirs(output_dir, exist_ok=True)

        all_outputs = render_outputs + [{
            "path": f"{output_dir}/manifest.json",
            "type": "manifest"
        }]

        manifest = build_manifest(revision, assets, all_outputs)

        with open(f"{output_dir}/manifest.json", "w") as f:
            json.dump(manifest, f, indent=2, ensure_ascii=False)

        elapsed = time.time() - start_time
        print(f"\n[Worker] ✓ 完成 ({elapsed:.1f}s)")
        print(f"  Output: {output_dir}")
        print(f"  Manifest: {manifest['manifest_checksum']}")

    except Exception as e:
        elapsed = time.time() - start_time
        print(f"\n[Worker] ✗ 失败 ({elapsed:.1f}s): {e}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)

        # 失败时仍输出诊断摘要（脱敏）
        try:
            os.makedirs(output_dir, exist_ok=True)
            with open(f"{output_dir}/error.json", "w") as f:
                json.dump({
                    "error": str(e)[:500],
                    "elapsed_seconds": round(elapsed, 1),
                    "revision_id": revision.get("id", "unknown") if 'revision' in dir() else "unknown"
                }, f, indent=2)
        except:
            pass
        sys.exit(1)


def health_check():
    """存活探针：检查 Blender 是否可用"""
    try:
        import bpy
        return {"status": "healthy", "blender_version": bpy.app.version_string}
    except ImportError:
        return {"status": "degraded", "blender_available": False, "note": "bpy not importable — running in dry-run mode"}
    except Exception as e:
        return {"status": "unhealthy", "error": str(e)[:200]}


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "--health":
        print(json.dumps(health_check(), indent=2))
    else:
        main()
