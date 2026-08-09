#!/bin/bash
set -euo pipefail

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
LOG_DIR="$PROJECT_DIR/build/log"
APK_OUT="$PROJECT_DIR/build/output"
BUILD_TYPE=${1:-release}
APK_DIR="$PROJECT_DIR/app/build/outputs/apk/$BUILD_TYPE"
LOG_FILE="$LOG_DIR/build_${TIMESTAMP}.log"
mkdir -p "$LOG_DIR" "$APK_OUT"

stop_gradle_daemon() {
    echo "[$(date)] >>> 停止 Gradle Daemon 释放内存..."
    ./gradlew --stop 2>/dev/null || true
}
trap stop_gradle_daemon EXIT

echo "[$(date)] ========== 开始构建 ($BUILD_TYPE) =========="

# 拉取代码
echo "[$(date)] >>> 拉取代码..."
git fetch "$GIT_REMOTE"
git reset --hard "$GIT_REMOTE/$GIT_BRANCH"
COMMIT_HASH=$(git rev-parse --short=7 HEAD)

# 删除旧 APK，避免失败构建复用历史产物。
if [ -d "$APK_DIR" ]; then
    find "$APK_DIR" -name "*.apk" -type f -delete
fi

# 构建，并从管道中读取 Gradle 而不是 tee 的退出码。
echo "[$(date)] >>> 构建 ${BUILD_TYPE} APK..."
set +e
./gradlew "assemble${BUILD_TYPE^}" 2>&1 | tee "$LOG_FILE"
BUILD_EXIT=${PIPESTATUS[0]}
set -e

if [ "$BUILD_EXIT" -ne 0 ]; then
    echo "[$(date)] !!! 构建失败 (exit code: $BUILD_EXIT)"
    echo "[$(date)] 日志: $LOG_FILE"
    exit "$BUILD_EXIT"
fi

if [ ! -d "$APK_DIR" ]; then
    echo "[$(date)] !!! 构建失败，APK 输出目录不存在"
    echo "[$(date)] 日志: $LOG_FILE"
    exit 1
fi

APK_SRC=$(
    find "$APK_DIR" -name "*.apk" -type f -printf '%T@ %p\n' 2>/dev/null |
        sort -rn |
        sed -n '1s/^[^ ]* //p'
)
if [ -z "$APK_SRC" ]; then
    echo "[$(date)] !!! 构建失败，未找到本次生成的 APK"
    echo "[$(date)] 日志: $LOG_FILE"
    exit 1
fi

APK_NAME="airhub_${TIMESTAMP}_${COMMIT_HASH}.apk"
cp "$APK_SRC" "$APK_OUT/$APK_NAME"

echo "[$(date)] ========== 构建成功 =========="
echo "[$(date)] APK: $APK_OUT/$APK_NAME"
echo "[$(date)] 日志: $LOG_FILE"
echo "[$(date)] 大小: $(du -h "$APK_OUT/$APK_NAME" | cut -f1)"
