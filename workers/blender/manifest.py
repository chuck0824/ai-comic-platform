"""
输出清单构建。
确定性序列化 — 相同输入产生相同输出。
"""

import json
import hashlib

def build_manifest(revision, assets, outputs):
    """构建输出清单。相同输入保证稳定哈希。"""
    manifest = {
        "revision_id": revision.get("id"),
        "revision_hash": revision.get("document_hash"),
        "assets": sorted(assets, key=lambda a: a.get("asset_id", "")),
        "outputs": sorted(outputs, key=lambda o: o.get("path", "")),
    }
    serialized = json.dumps(manifest, sort_keys=True, ensure_ascii=False)
    checksum = hashlib.sha256(serialized.encode()).hexdigest()
    manifest["manifest_checksum"] = checksum
    return manifest

def verify_assets(assets):
    """验证资产许可和授权状态。未授权资产抛出 ValueError。"""
    for asset in assets:
        if not asset.get("license_verified"):
            raise ValueError(f"资产 {asset.get('asset_id')} 未验证许可")
        if asset.get("type") == "portrait" and not asset.get("portrait_authorized"):
            raise ValueError(f"真人肖像 {asset.get('asset_id')} 未授权")
