"""
坐标转换模块。
领域协议：RH_Y_UP_METERS → Blender 原生：Z-up。
往返转换保证数值稳定。
"""

import math

def domain_to_blender(point):
    """Y-up (x, y, z) → Z-up (x, -z, y)"""
    x, y, z = point
    return (x, -z, y)

def blender_to_domain(point):
    """Z-up (x, y, z) → Y-up (x, z, -y)"""
    x, y, z = point
    return (x, z, -y)

def domain_quat_to_blender(q):
    """Y-up Quaternion → Z-up Quaternion"""
    x, y, z, w = q
    return (x, -z, y, w)

def blender_quat_to_domain(q):
    """Z-up Quaternion → Y-up Quaternion"""
    x, y, z, w = q
    return (x, z, -y, w)

def is_normalized_quat(q):
    x, y, z, w = q
    norm = math.sqrt(x*x + y*y + z*z + w*w)
    return abs(norm - 1.0) < 1e-6
