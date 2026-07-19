# V0.3.15 相机对焦与低对比中文日期 QA

## 范围

- CameraX 中央测光与点按 AF/AE/AWB。
- 取景框内画面、焦点、清晰度、曝光和中文 OCR 进度反馈。
- 首帧 ML Kit 与 PP-OCR 模型预热解耦。
- 黑色低对比瓶底日期恢复和错误片段门禁。
- 既有日期、条码、商品档案、补货、Excel 和 JSON 逻辑回归。

## 自动化

- `apk/run-local-tests.ps1`：`197 passed, 0 failed`。
- `:apk:app:compileDebugJavaWithJavac`：PASS。
- `:apk:app:assembleDebug`：PASS。
- APK 大小约 220 MB，小于 1 GB。

## 低对比视觉证据

输入为用户提供的黑色瓶底图片。肉眼可读内容为两行激光/压印日期：`20250912`、`20270311`。

- 原流程曾把残片 `202509` 错当成月级生产日期，也曾产生单次低置信 `20220311` 误读。
- 新门禁拒绝无标签 `yyyyMM` 残片，并要求完整中央日期达到高置信或由多个预处理结果一致支持。
- 最终确认页显示生产日期 `2025-09-12`、有效期 `2027-03-11`，与图片肉眼内容一致。
- 页面无相机 HUD 残留，结果区按钮和日期文字没有重叠。

截图：`docs/qa/screenshots/2026-07-19-v0315/low-contrast-date-pair.png`。

## 旧视频回归

重新运行 `Record_2026-07-19-13-00-08_e7c84e4b72b45d7acf770bbb7219e5ff.mp4` 时，完整转录虽然已经包含“保期：9个月”和多次 `20260120S7420`，但原完成阶段会被一次未来噪声 `20360120` 干扰，导致首轮只显示生产日期。完成阶段现在仅在同时存在保质期时启用保守恢复：过去十年内的完整八位日期必须至少重复两次，并且票数必须唯一领先，才可补为生产日期；并列或仅出现一次时仍拒绝。

- 最终确认结果：商品码 `6926265313430`、生产日期 `2026-01-20`、保质期 `9个月`、系统计算到期日 `2026-10-20`。
- 页面视觉复核：结果区无重叠，生产日期与保质期可见；系统计算到期日在 Android 页面语义树中存在。

截图：`docs/qa/screenshots/2026-07-19-v0315/video-shelf-life-pass.png`。

## 相机边界

代码层确认 CameraX 使用 `1920x1080` 目标、`STRATEGY_KEEP_ONLY_LATEST`、中央延迟测光、点按测光、3 秒自动取消和独立模型预热。模拟器虚拟相机一旦启动会阻塞本机 `adb screencap`，因此无法把虚拟相机截图当成真机对焦证据。

结论：低对比图片识别、指定旧视频回归与页面视觉为 `PASS`；真实手机自动对焦、连续曝光、帧率和中文包装实时速度为 `PARTIAL`。本轮还尝试改用桌面窗口截图检查虚拟相机，但 AVD 在 CameraX 启动后直接退出，因此仍不能把虚拟相机外推成真机证据。

## 官方资料

- Android CameraX FocusMeteringAction：https://developer.android.com/reference/androidx/camera/core/FocusMeteringAction
- Android CameraX Image analysis：https://developer.android.com/media/camera/camerax/analyze
- ML Kit Text Recognition v2：https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- OpenCV CLAHE：https://docs.opencv.org/4.x/d5/daf/tutorial_py_histogram_equalization.html
- OpenCV morphology：https://docs.opencv.org/4.x/d9/d61/tutorial_py_morphological_ops.html
