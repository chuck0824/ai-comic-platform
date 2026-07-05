"""
Blender Worker 入口。
消费不可变 DirectorRevision，产出预览渲染和清单。
在隔离临时目录中运行，不持久化凭证。
"""

import json
import sys
import os
from coordinate import domain_to_blender, blender_to_domain, is_normalized_quat
from manifest import build_manifest, verify_assets

def main():
    """Worker 入口：接收 revision JSON 路径，产出输出目录。"""
    if len(sys.argv) < 3:
        print("Usage: python worker.py <revision.json> <output_dir>", file=sys.stderr)
        sys.exit(1)

    revision_path = sys.argv[1]
    output_dir = sys.argv[2]

    with open(revision_path) as f:
        revision = json.load(f)

    document = revision.get("document_json") or revision.get("document")
    if isinstance(document, str):
        document = json.loads(document)

    assets = revision.get("assets", [])
    verify_assets(assets)

    # 坐标转换
    for obj in document.get("objects", []):
        pos = obj.get("position", {})
        if pos:
            obj["position"] = {
                "x": pos.get("x", 0),
                "y": -pos.get("z", 0),
                "z": pos.get("y", 0)
            }

    os.makedirs(output_dir, exist_ok=True)

    outputs = [{
        "path": f"{output_dir}/manifest.json",
        "type": "manifest",
        "checksum": ""
    }]

    manifest = build_manifest(revision, assets, outputs)

    with open(f"{output_dir}/manifest.json", "w") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print(f"Worker 完成: {output_dir}")
    print(f"Manifest checksum: {manifest['manifest_checksum']}")

if __name__ == "__main__":
    main()
