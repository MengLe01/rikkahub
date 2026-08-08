#!/bin/bash
set -e

# ============ 配置 ============
GIT_REMOTE=gitee
GIT_BRANCH=master

# ============ 自动推导路径 ============
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
ANDROID_HOME="$SCRIPT_DIR/../android-sdk"
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"

# ============ 构建 ============
cd "$PROJECT_DIR"
export ANDROID_HOME
export JAVA_HOME

TIMESTAMP=$(date +%Y%m%d_%H%M)
LOG_DIR=$PROJECT_DIR/build/log
APK_OUT=$PROJECT_DIR/build/output
mkdir -p "$LOG_DIR" "$APK_OUT"

BUILD_TYPE=${1:-release}
echo "[$(date)] ========== 开始构建 ($BUILD_TYPE) =========="

# 拉取代码
echo "[$(date)] >>> 拉取代码..."
git fetch "$GIT_REMOTE"
git reset --hard "$GIT_REMOTE/$GIT_BRANCH"

# 构建前记录 APK 目录
APK_DIR="app/build/outputs/apk/$BUILD_TYPE"

# 构建
echo "[$(date)] >>> 构建 ${BUILD_TYPE} APK..."
BUILD_EXIT=0
./gradlew "assemble${BUILD_TYPE^}" 2>&1 | tee "$LOG_DIR/build_${TIMESTAMP}.log" || BUILD_EXIT=$?

if [ $BUILD_EXIT -ne 0 ]; then
    echo "[$(date)] !!! 构建失败 (exit code: $BUILD_EXIT)"
    echo "[$(date)] 日志: $LOG_DIR/build_${TIMESTAMP}.log"
    exit $BUILD_EXIT
fi

# 找最新生成的 APK
APK_SRC=$(find "$APK_DIR" -name "*.apk" -type f -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)
if [ -z "$APK_SRC" ]; then
    echo "[$(date)] !!! 构建失败，未找到 APK"
    exit 1
fi

APK_NAME="airhub_${TIMESTAMP}.apk"
cp "$APK_SRC" "$APK_OUT/$APK_NAME"

echo "[$(date)] ========== 构建成功 =========="
echo "[$(date)] APK: $APK_OUT/$APK_NAME"
echo "[$(date)] 日志: $LOG_DIR/build_${TIMESTAMP}.log"
echo "[$(date)] 大小: $(du -h "$APK_OUT/$APK_NAME" | cut -f1)"

# 释放内存
echo "[$(date)] >>> 停止 Gradle Daemon 释放内存..."
./gradlew --stop 2>/dev/null || true
