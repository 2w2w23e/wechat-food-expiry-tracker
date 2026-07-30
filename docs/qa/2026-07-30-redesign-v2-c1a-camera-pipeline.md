# 2026-07-30 V2 C1a CameraX 采集与最新帧管线验收

## 结论

- CameraX 帧源拆分、最新帧替换、代际隔离和资源释放：`PASS`。
- 连续模糊对焦恢复策略及冷却：`PASS`。
- 四组冻结视频/图片字段结果、核对页到新增表单：`PASS`。
- 1080 x 2400、720 x 1280 实际截图视觉检查：`PASS`。
- `adb install -r` 覆盖升级、稳定签名和本地食品数据保留：`PASS`。
- AVD 实时虚拟相机运行：`BLOCKED_ENVIRONMENT`。进入 CameraX 后 AVD 系统 shell 冻结，无法形成相机预览截图。
- 真实手机光学对焦、曝光、持续帧率、温升：`PARTIAL`，本轮没有真机证据。

因此 C1a 的可自动验证范围通过，但 C1b 真机相机门槛没有完成，不能声称完整相机能力已经通过。

## 环境

- APK：`versionName 0.4.5`，`versionCode 35`
- 包名：`com.shiqi.expirytracker`
- AVD：Android API 29
- 视觉视口：1080 x 2400 / 420 dpi，以及 720 x 1280 / 320 dpi
- 识别：ML Kit 中文/拉丁文字与条码、PP-OCRv6 检测/行识别、OpenCV 低对比增强

## 实现

1. `CameraXFrameSource` 负责 CameraX provider、预览、1920 x 1080 分析目标、旋转、后置相机绑定、手电筒、点按/中央 AF-AE-AWB、自动取消和停止解绑。
2. `ImageAnalysis` 保持 `STRATEGY_KEEP_ONLY_LATEST`。每个不需要的 `ImageProxy` 立即关闭；需要的帧转换为正向 `Bitmap` 后再移交识别线程。
3. `LatestFrameProcessor` 只保留一个尚未开始的最新帧。新帧替换旧待处理帧，旧帧立即释放；会话 generation 改变时拒绝过时帧。
4. 执行器拒绝、识别异常、停止和代际切换都清理帧所有权，不能让识别一直停在 busy。
5. `CameraFocusRecoveryPolicy` 要求连续两次模糊证据，且 1800ms 冷却结束后才允许自动重对焦；已有对焦请求时不叠加。
6. 核对页日期摘要不再先显示“可信度”并挤掉业务字段，生产日期、保质期和有效期最多四行完整展示。

## 自动化

```text
Local logic tests: 219 passed, 0 failed.
Camera frame pipeline tests: 5 passed, 0 failed.
Gradle lintDebug + assembleDebug: BUILD SUCCESSFUL.
git diff --check: PASS.
```

新增管线测试覆盖：

- 多个待处理帧只识别最后一帧。
- 被替换、停止、过时代际和正常处理的帧均只释放一次。
- 执行器拒绝和识别异常后 busy 状态恢复。
- 自动对焦需要连续模糊证据，遵守 in-flight 与冷却约束。
- 单调时钟异常回退不能绕过冷却。

## 冻结样本

| 样本 | 最终字段 | 结论 |
| --- | --- | --- |
| 2026-07-29 录屏 | `复方硫酸软骨素滴眼液`、`6917246211883`；日期保持缺失 | `PASS` |
| 2026-07-19 录屏 | `上好佳薯条`、`6926265313430`、`2026-01-20`、`9个月`、`2026-10-20` | `PASS` |
| 有效期至录屏 | `复方丹参滴丸`、`2025-03-13`、`2029-02-28` | `PASS` |
| 低对比图片 | `2025-09-12`、`2027-03-11` | `PASS` |

最终 AVD 日志中，三段视频完成耗时分别约 22.6s、26.5s 和 41.3s。AVD 负载和模型热状态会造成波动，这些数据只用于同环境回归，不外推到真机。

7 月 29 日样本在核对页点击“使用这些结果”后，商品名与条码确实进入新增表单；“无过期时间”保持未勾选。取消表单后没有写库。

## 视觉检查

- [7 月 29 日核对页](screenshots/2026-07-30-redesign-v2-c1a/01-july29-result.png)
- [识别结果进入新增表单](screenshots/2026-07-30-redesign-v2-c1a/02-july29-prefilled-form.png)
- [生产日期、9 个月保质期和计算到期日](screenshots/2026-07-30-redesign-v2-c1a/03-july19-shelf-life.png)
- [生产日期和包装月级有效期](screenshots/2026-07-30-redesign-v2-c1a/04-valid-until.png)
- [低对比度双日期](screenshots/2026-07-30-redesign-v2-c1a/05-low-contrast.png)
- [720 x 1280 紧凑屏](screenshots/2026-07-30-redesign-v2-c1a/06-low-contrast-720x1280.png)

实际逐张查看确认：

- 日期摘要不再用省略号隐藏保质期或有效期。
- 720 x 1280 下日期说明可换行，字段、证据和固定操作栏不重叠。
- 1080 x 2400 下关键帧、三个字段行和底部操作均保持清晰。
- 无可靠日期的样本只显示冲突/缺失状态，不自动选择“无过期时间”。

## 覆盖安装与签名

```text
adb install -r = Success
食品数据升级前 SHA-256 =
c1c2e0c0072f9f37f5bd83e55bf8db901521dd64f66db7cf2d701db1dea317b7
食品数据升级后及回放取消后 SHA-256 =
c1c2e0c0072f9f37f5bd83e55bf8db901521dd64f66db7cf2d701db1dea317b7
```

最终 APK：

```text
文件大小 = 220240688 bytes
SHA-256 = 4F4D8B32B0AC56D95B98EF8CC5E99B540D8839832E6AE15E745EBCAB30B56726
APK Signature Scheme v2 = true
证书 SHA-256 = eba5350af5a9182067b6bfac2dd1c7b74438b7905cce0b2bc67a12d6ca30be91
```

## AVD 相机阻塞证据

按真实用户路径从总览点击“扫描包装并识别”后，AVD 再次进入系统级冻结：

- `adb devices` 和 `adb get-state` 仍返回 `device`。
- `adb shell echo ok`、`uiautomator dump`、`screencap` 和 `dumpsys activity` 均超时。
- 只能终止残留 emulator/QEMU 进程并冷启动 AVD 才恢复。

同一 AVD 在旧版本也存在该虚拟相机问题。本轮无法读取冻结后的应用日志，因此不能把它归因成已确认的应用崩溃，也不能把实时相机标为通过。

## 资料依据

- [CameraX ImageAnalysis](https://developer.android.com/reference/androidx/camera/core/ImageAnalysis)：非阻塞最新帧策略和及时关闭 `ImageProxy`。
- [CameraX image analysis](https://developer.android.com/media/camera/camerax/analyze)：分析执行器必须快速响应，非阻塞模式避免拖住预览。
- [CameraX FocusMeteringAction](https://developer.android.com/reference/androidx/camera/core/FocusMeteringAction)：AF/AE/AWB 区域、能力降级和自动取消。
- [ML Kit text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)：中文 bundled 模型、字符像素和实时丢旧帧建议。
- [PaddleOCR Android deployment](https://www.paddleocr.ai/latest/en/version3.x/inference_deployment/cross_platform/android_deployment.html)：移动端模型与检测/识别耗时拆分。

## 剩余门槛

1. 在真实 Android 手机上记录首帧、对焦请求/完成、曝光、队列延迟、P50/P95、预览 FPS 和温升。
2. 验证手动点按、连续模糊自动恢复、无 AF 设备降级和手电筒失败反馈。
3. 完成公开集字段级指标、PP-OCRv5/v6 同 ROI A/B 和缺失字段定向补扫。
4. 继续拆分区域调度、识别器和会话状态，降低 `DateOcrScanActivity` 的编排体积。
