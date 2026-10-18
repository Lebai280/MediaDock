#!/usr/bin/env bash
# S4 spike 样本生成脚本（本地 FFmpeg 9.x）。
# 产物写入 app/src/androidTest/assets/s4/，总大小约 10 MB。
# 用法：bash tools/spikes/s4/generate_samples.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
OUT="$ROOT/app/src/androidTest/assets/s4"
mkdir -p "$OUT"
cd "$OUT"

HDR10_PARAMS="hdr10=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400:log-level=error"
SIX_CH="aevalsrc=0.1*sin(440*2*PI*t)|0.1*sin(554*2*PI*t)|0.1*sin(659*2*PI*t)|0.1*sin(880*2*PI*t)|0.1*sin(1046*2*PI*t)|0.1*sin(1318*2*PI*t):s=48000:d=5"

# HDR10 / HEVC Main10（MP4 + MKV：对比容器对 HDR 静态元数据的暴露）
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=24:duration=5" \
  -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=5" \
  -c:v libx265 -pix_fmt yuv420p10le -preset ultrafast -b:v 1500k -tag:v hvc1 \
  -x265-params "$HDR10_PARAMS" -c:a aac -b:a 128k -movflags +faststart \
  hdr10_hevc_main10.mp4
ffmpeg -hide_banner -loglevel error -y -i hdr10_hevc_main10.mp4 -c copy hdr10_hevc_main10.mkv

# HLG / HEVC Main10
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=24:duration=5" \
  -c:v libx265 -pix_fmt yuv420p10le -preset ultrafast -b:v 1200k -tag:v hvc1 \
  -x265-params "colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc:log-level=error" \
  -color_primaries bt2020 -color_trc arib-std-b67 -colorspace bt2020nc -movflags +faststart \
  hlg_hevc_main10.mp4

# AV1 Main10（S21 无硬件 AV1，走 dav1d 软解）
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=24:duration=5" \
  -c:v libsvtav1 -pix_fmt yuv420p10le -preset 10 -b:v 900k -movflags +faststart \
  av1_10bit.mp4

# HDR10 / HEVC Main10 4K60
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=3840x2160:rate=60:duration=3" \
  -c:v libx265 -pix_fmt yuv420p10le -preset ultrafast -b:v 12M -tag:v hvc1 \
  -x265-params "$HDR10_PARAMS" -movflags +faststart hdr10_4k60.mp4

# 环绕声：E-AC-3 5.1 / TrueHD 5.1 / DTS 5.1（后两者在 S21 上无解码器）
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "$SIX_CH" -c:a eac3 -b:a 384k -ac 6 eac3_51.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "$SIX_CH" -c:a truehd -strict -2 -ac 6 truehd_51.mka
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "$SIX_CH" -c:a dca -strict -2 -ac 6 dts_51.mka

ls -lh "$OUT"
