"""
Blender Worker 坐标转换单元测试。
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import unittest
from coordinate import domain_to_blender, blender_to_domain, is_normalized_quat
from manifest import build_manifest, verify_assets
import math


class CoordinateTest(unittest.TestCase):

    def test_y_up_to_blender_z_up_round_trip(self):
        """Y-up → Z-up → Y-up 往返转换保持数值一致"""
        point = (1.0, 2.0, 3.0)
        actual = blender_to_domain(domain_to_blender(point))
        for expected_value, actual_value in zip(point, actual):
            self.assertAlmostEqual(expected_value, actual_value)

    def test_origin_unchanged(self):
        """原点在两次转换中都保持不变"""
        self.assertEqual(domain_to_blender((0, 0, 0)), (0, 0, 0))
        self.assertEqual(blender_to_domain((0, 0, 0)), (0, 0, 0))

    def test_normalized_quaternion(self):
        """归一化 Quaternion 通过检测"""
        q = (0.0, 0.0, 0.0, 1.0)
        self.assertTrue(is_normalized_quat(q))

    def test_unnormalized_quaternion(self):
        """非归一化 Quaternion 被检测"""
        q = (1.0, 2.0, 3.0, 4.0)
        self.assertFalse(is_normalized_quat(q))

    def test_near_normalized_quaternion(self):
        """接近归一化的 Quaternion（容差 1e-6）"""
        q = (0.0, 0.0, 0.7071068, 0.7071068)  # ~45° rotation
        self.assertTrue(is_normalized_quat(q))


class ManifestTest(unittest.TestCase):

    def test_manifest_is_stable_for_same_inputs(self):
        """相同输入产生相同清单（确定性序列化）"""
        revision = {"id": "rev-1", "document_hash": "abc123"}
        assets = [{"asset_id": "a1", "license_verified": True, "type": "model"}]
        outputs = [{"path": "/tmp/out/frame_0001.png", "type": "image"}]

        m1 = build_manifest(revision, assets, outputs)
        m2 = build_manifest(revision, assets, outputs)

        self.assertEqual(m1["manifest_checksum"], m2["manifest_checksum"])
        self.assertEqual(len(m1["manifest_checksum"]), 64)

    def test_verify_assets_rejects_unlicensed(self):
        """未验证许可的资产抛出 ValueError"""
        assets = [{"asset_id": "a1", "license_verified": False}]
        with self.assertRaises(ValueError):
            verify_assets(assets)

    def test_verify_assets_rejects_unauthorized_portrait(self):
        """未授权的真人肖像抛出 ValueError"""
        assets = [{"asset_id": "p1", "license_verified": True, "type": "portrait", "portrait_authorized": False}]
        with self.assertRaises(ValueError):
            verify_assets(assets)

    def test_verify_assets_passes_licensed_portrait(self):
        """已授权肖像通过验证"""
        assets = [{"asset_id": "p1", "license_verified": True, "type": "portrait", "portrait_authorized": True}]
        verify_assets(assets)  # 不抛异常


if __name__ == "__main__":
    unittest.main()
