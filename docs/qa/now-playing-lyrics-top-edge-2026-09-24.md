# 正在播放歌词顶部过渡

问题：歌词行已有模糊，但歌词视口顶部直接裁切，文字到达边界时仍出现硬切线。

修改：桌面 LyricsSurface 启用 64dp 顶部透明度渐变，在已有行模糊之后用 DstIn 遮罩消除硬边。遮罩作用于歌词离屏层，不覆盖动态专辑背景；最终层继续使用 Plus 混合，保留歌词与背景的加色关系。默认渐隐高度为零，其他调用方不变。未调整焦点位置或字体。

验证：core:lyrics-ui 25 项、service:playback:presentation 50 项测试通过；createDistributable 成功；git diff --check 通过。重启后在 Run 的 1:05 暂停位置及手动滚动时检查，顶部文字渐隐，焦点歌词保持清晰。启动时沿用现有安全模式入口，点击尝试正常启动后恢复。

截图：/Users/shine/.codex/visualizations/2026/09/23/01a0cecb-d7f4-7683-ac4d-653d01f9c139/now-playing/tide-lyric-edge-after.png

范围：此次修复顶部硬裁切；64dp 为视觉调校值，不是 Apple 官方参数，未证明与 Apple Music 逐像素一致。
