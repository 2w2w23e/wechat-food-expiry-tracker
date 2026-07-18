# 2026-07-18 相机、补货与快捷操作验收

## 结论

- 卡片外层“减少 1”：`PASS`。在库食品直接显示“减少 1 / 补货 / 更多操作”，减少到 0 后出现是否标记已用完的确认提示。
- 补货相机入口：`PASS`。补货新批次表单的日期区直接显示“相机识别本批次日期”。
- 补货日期回填：`PASS`。视频模拟同一识别回调链识别出生产日期 `2026-06-12`、有效期 `2027-06-11`，回到补货表单后只回填新批次日期；商品档案仍为原来的 `SeniorMilk`，“无过期时间”未勾选。
- 实时相机近距离识别：`PARTIAL`。已提高 CameraX 分析分辨率、日期专用模式的检测区域数、低对比增强裁剪数和重识别次数，并增加轻微自动变焦与 8 秒对焦测光；模拟器授权实时相机后截图链仍会阻塞，不能把本轮结论扩大为真机准确率已经通过。

## 自动化

- 本地 Java 逻辑：`185 passed, 0 failed`。
- Android Debug APK：构建成功，v2 签名验证通过。
- 覆盖安装：`adb install -r` 成功。

## 视觉证据

- `screenshots/2026-07-18-camera-replenishment/01-home.png`：卡片外层三项快捷按钮。
- `screenshots/2026-07-18-camera-replenishment/02-decrease-confirm.png`：减少到 0 后的归档确认。
- `screenshots/2026-07-18-camera-replenishment/03-replenish-form.png`：补货表单日期区的相机入口。
- `screenshots/2026-07-18-camera-replenishment/06-replenish-video-result.png`：日期专用识别结果与关键帧证据。
- `screenshots/2026-07-18-camera-replenishment/07-replenish-filled.png`：日期回填到新批次且“无过期时间”未勾选。

识别候选仍需用户点击“核对并填入”，补货记录仍需用户点击“保存”，不会自动写库。
