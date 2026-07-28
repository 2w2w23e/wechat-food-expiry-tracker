# V0.3.16 相机状态、识别依据与稳定签名 QA

## 结论

- 稳定覆盖签名：`PASS`。
- V0.3.15 覆盖升级到 V0.3.16 且库存数据不变：`PASS`。
- 识别依据关闭后回到原识别结果：`PASS`。
- 继续相机识别保留商品名、条码、日期、保质期和证据帧：`PASS`。
- 视频模拟相机的实时文字区域框与有效帧提示：`PASS`。
- 新增/编辑/补货表单下半部分可滚动访问：`PASS`。
- 减少、补货独立批次、编辑、删除、Excel 往返预览和 JSON 重复导入预览：`PASS`。
- 真实手机相机的自动对焦、曝光、温升、连续帧率和识别准确率：`PARTIAL`，本轮没有连接真实手机，不得由 AVD 或视频外推。

## 环境

- Windows 11
- Android Studio JBR 21
- Medium_Phone AVD，1080 x 2400
- APK：`versionName 0.3.16`、`versionCode 19`、`minSdk 24`
- APK 大小：`220,491,587` 字节
- OCR：ML Kit Chinese / Latin / Barcode、ZXing、OpenCV、ONNX Runtime Android、`PP-OCRv6_small_det`、`PP-OCRv6_rec_small`

## 自动化

- `apk/run-local-tests.ps1`：`198 passed, 0 failed`。
- `.\gradlew.bat :apk:app:assembleDebug --no-daemon`：`BUILD SUCCESSFUL`。
- V0.3.15 与 V0.3.16 签名证书 SHA-256 均为 `eba5350af5a9182067b6bfac2dd1c7b74438b7905cce0b2bc67a12d6ca30be91`。
- `adb install -r app-debug.apk`：`Success`。
- 安装前后 `shared_prefs/shiqi_android_v0.xml` SHA-256 均为 `a52ad52ac626203b161e9c5199f469e674f0f4314e0c8acf59d7fdcf23901cd4`。
- 修复表单滚动后的最终 APK 再次覆盖安装，数据文件 SHA-256 在安装前后均为 `fd7d9db2dbe557bbbaeac35ea45473894440bd84dc85ba9995347aae6ba4a163`。
- 完整增减、补货、编辑、删除、Excel/JSON 回归后再次覆盖安装，数据文件 SHA-256 在安装前后均为 `28d12d703d9c96c0ce30e895ace16e4a0da059ffe54882e9b9b40b7c8255218e`。
- 安装后包信息：`versionCode=19`、`versionName=0.3.16`。

## 视觉流程

截图目录：[`screenshots/2026-07-28-v0316`](screenshots/2026-07-28-v0316)。

1. 从 debug-only 视频模拟入口播放既有相机录屏，不手动输入识别结果。
2. Android framebuffer 连续截图中出现绿色与黄色“可能文字区”动态框，证明框来自 PP-OCRv6 当前帧检测结果，不是固定装饰。
3. 完成页显示“上好佳薯条”、条码 `6926265313430`、生产日期 `2026-01-20`、保质期 `9个月`、系统计算到期日 `2026-10-20`。
4. 点击“识别依据”后出现独立弹窗，原图和字段框仍可见，并提供“返回识别结果”。
5. 关闭弹窗后，步骤 3 的商品名、条码和日期结果保持不变。
6. 点击“继续识别”，选择“继续相机识别（保留当前结果）”；相机页仍显示步骤 3 的全部结果。
7. 旧版覆盖升级后首页仍显示测试库存“升级保留测试牛奶”、在库数量和日期，证明没有因签名或升级清空本地数据。
8. 补货表单上滑后，生产日期、无过期时间、保质期和最终日期控件均在真实 Android 帧缓冲截图中可见。

## 完整用户流程回归

1. 首页“减少 1”把原批次从剩余 `2/3` 改为 `1/3`。
2. 补货表单第一次回归发现 ScrollView 子视图被测量为视口高度，生产日期、保质期等下半部分被裁切且无法滚动；修复为 `MATCH_PARENT x WRAP_CONTENT` 后重新构建、覆盖安装并复测。
3. 修复后上滑可见生产日期、无过期时间、保质期和最终日期。新批次填写生产日期 `2026-07-28`、保质期 `10天`，自动计算到期日 `2026-08-07`。
4. 保存后首页同时显示原批次 `1/3`、到期日 `2026-07-31`，以及补货批次 `2/2`、到期日 `2026-08-07`，证明批次日期和数量独立。
5. 编辑原批次数量 `3 -> 4` 后首页显示 `1/4`；删除原批次经二次确认后，只保留补货批次 `2/2` 和 `2026-08-07`。
6. Excel 导出文件 `shiqi-foods-20260728-0747.xlsx` 可在系统文件选择器中立即选回；导入预览为总行数 1、可导入 1、错误 0、警告 0，随后取消避免制造重复数据。
7. JSON 筛选导出页面可选择入库时间、生产日期和最终日期闭区间。导出文件 `shiqi-batches-202607280751.json` 选回后预览为文件批次 1、新增 0、更新 0、内容相同 1、错误 0，证明重复批次不会再次新增。

该回归同时覆盖了用户高频按钮在卡片外可见、补货独立批次、编辑、删除确认、Excel 文件往返和 JSON 同步去重。

## 实现依据

- CameraX `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`：https://developer.android.com/reference/androidx/camera/core/ImageAnalysis
- CameraX 对焦和测光：https://developer.android.com/media/camera/camerax/configuration
- ML Kit Text Recognition v2 的实时识别、分辨率和丢帧建议：https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- PaddleOCR 文字检测与区域框：https://paddlepaddle.github.io/PaddleOCR/v3.0.0/en/version3.x/module_usage/text_detection.html
- PP-OCRv6：https://www.paddleocr.ai/latest/en/version3.x/algorithm/PP-OCRv6/PP-OCRv6.html

## 边界

- 视频模拟证明调度、文字区域框、候选稳定、状态恢复和结果合并链路可运行，但不等于真实手机镜头准确率。
- AVD 虚拟相机启动 CameraX 后会使 ADB shell 或 framebuffer 截图不稳定，因此真实镜头只记录为 `PARTIAL`。
- OCR 仍只产生可编辑候选；用户点击确认和保存前不会写入食品数据库。
- 本轮未引入通用 YOLO。已有 PP-OCRv6 检测模型直接提供文字区域；增加通用物体模型会增加包体和延迟，却不能直接改善喷码字符识别。
