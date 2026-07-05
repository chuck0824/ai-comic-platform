# Blender 4.2 LTS Worker — 消费 DirectorRevision 产出 Eevee 预演
# 构建: docker build -t canvas-blender-worker:4.2 .
# 运行: docker run --rm --gpus all -v /data/blender-jobs:/jobs canvas-blender-worker:4.2

FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive \
    BLENDER_VERSION=4.2 \
    BLENDER_MAJOR=4.2 \
    PYTHONUNBUFFERED=1

# 系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates xz-utils \
    libgl1-mesa-glx libglu1-mesa libxi6 libxrender1 libxkbcommon0 \
    libsm6 libfontconfig1 libfreetype6 \
    ffmpeg \
    python3 python3-pip python3-venv \
    && rm -rf /var/lib/apt/lists/*

# 下载并安装 Blender 4.2 LTS
RUN curl -fsSL "https://download.blender.org/release/Blender4.2/blender-4.2.0-linux-x64.tar.xz" \
    -o /tmp/blender.tar.xz \
    && tar -xJf /tmp/blender.tar.xz -C /opt/ \
    && rm /tmp/blender.tar.xz \
    && ln -s /opt/blender-4.2.0-linux-x64/blender /usr/local/bin/blender

# Worker 代码
WORKDIR /app/worker
COPY requirements.txt .
RUN pip3 install --break-system-packages -r requirements.txt

COPY coordinate.py manifest.py worker.py scene_builder.py animation_baker.py camera_builder.py renderer.py ./

# 运行用户（非 root）
RUN useradd -m -s /bin/bash blender && chown -R blender:blender /app
USER blender

# 临时目录和输出目录
ENV BLENDER_TEMP=/tmp/blender-jobs \
    BLENDER_OUTPUT=/app/output

RUN mkdir -p $BLENDER_TEMP $BLENDER_OUTPUT

ENTRYPOINT ["python3", "/app/worker/worker.py"]
