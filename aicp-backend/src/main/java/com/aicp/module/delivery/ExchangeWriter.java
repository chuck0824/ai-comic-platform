package com.aicp.module.delivery;

/** 外部后期交换文件写入器 */
public interface ExchangeWriter {
    String fileName();
    byte[] write(DeliveryManifestService.DeliveryManifestView manifest);
}
