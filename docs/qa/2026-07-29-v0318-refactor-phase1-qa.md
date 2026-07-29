# V0.3.18 界面与识别内核重构第一阶段验收

## 结论

| 范围 | 结论 | 证据 |
| --- | --- | --- |
| 详细改造计划与任务图 | PASS | `docs/APK_REFACTOR_PLAN_2026-07-29.md` |
| 本地逻辑回归 | PASS | 既有 `202 passed, 0 failed`，新增调度策略 `4 passed, 0 failed` |
| Android 构建 | PASS | `:apk:app:assembleDebug`，`BUILD SUCCESSFUL` |
| 覆盖安装与版本 | PASS | `versionName=0.3.18`、`versionCode=21` |
| 覆盖安装数据保留 | PASS | 安装前后 `shiqi_android_v0.xml` SHA-256 均为 `28d12d703d9c96c0ce30e895ace16e4a0da059ffe54882e9b9b40b7c8255218e` |
| 7 月 28 日录屏日期识别 | PASS | 生产日期 `2025-03-13`，有效期 `2029-02-28`，只进入核对表单 |
| 首页与数据管理视觉 | PASS | 1080×2400、720×1280 均无重叠；Excel/JSON 入口仍可达 |
| 识别结果视觉 | PASS | 日期优先显示，720×1280 与 1080×2400 均无文字截断 |
| 原文返回与确认边界 | PASS | 打开原文后返回结果不丢失；取消表单后库存 SHA-256 不变 |
| 真实手机相机 | PARTIAL | 本轮不把 AVD/录屏结论外推为真机光学对焦、曝光、温升和帧率 |

## 自动化验证

执行：

```powershell
$env:JAVA_HOME='D:\Program Files\Android\Android Studio\jbr'
powershell -ExecutionPolicy Bypass -File apk/run-local-tests.ps1
```

结果：

- APK 本地逻辑：`202 passed, 0 failed`
- 识别调度策略：`4 passed, 0 failed`
- 已覆盖日期解析、未来生产日期拒绝、日期投票、商品名融合、批次、补货、提醒、Excel 往返、JSON 去重更新和存储迁移。

构建：

```powershell
.\gradlew.bat --no-daemon :apk:app:assembleDebug
```

结果为 `BUILD SUCCESSFUL`。现存 Java 8、Gradle 9 兼容性和 AndroidManifest namespace 提示不影响本次构建，但应列入后续工程治理。

## 视频识别回归

输入：

`C:\Users\xinyu.wang\Downloads\Record_2026-07-28-16-46-03_e7c84e4b72b45d7acf770bbb7219e5ff.mp4`

稳定结果：

- 批号文本：`L250321`
- 生产日期：`2025-03-13`
- 月级有效期：`2029-02`，确认值为 `2029-02-28`
- 本样本没有形成可用商品名和条码，界面明确显示“正在寻找中文商品名 / 暂未识别”，没有伪造低置信候选。

性能证据：

- 改造前基线：`109068ms`，只完成 4 个精选帧。
- 最终候选重复回放：约 `57.6s`、`64.6s`、`75.3s`、`87.8s`，均得到同一日期结果；波动主要来自 AVD 模型首次加载和视频抽帧。
- PP-OCR 检测长边固定为 `960`，单次检测约 `0.3-1.2s`。
- 行识别从 `11/18/32` 个裁剪收敛为固定 `8` 个裁剪，单次约 `0.65-1.8s`。
- AVD 仍记录 ML Kit 首次任务超时，后续阶段需要把模型生命周期与 Activity 进一步解耦。

## 视觉检查

- 首页大屏：`docs/qa/screenshots/refactor-v1/home-1080x2400.png`
- 首页小屏：`docs/qa/screenshots/refactor-v1/home-720x1280.png`
- 数据管理：`docs/qa/screenshots/refactor-v1/data-management-1080x2400.png`
- 运行中文字框：`docs/qa/screenshots/refactor-v1/video-recognition-running-1080x2400.png`
- 大屏最终结果：`docs/qa/screenshots/refactor-v1/video-recognition-result-optimized-1080x2400.png`
- 小屏最终结果：`docs/qa/screenshots/refactor-v1/video-recognition-result-720x1280.png`
- 原文弹窗：`docs/qa/screenshots/refactor-v1/recognition-raw-dialog-1080x2400.png`
- 返回后结果：`docs/qa/screenshots/refactor-v1/recognition-result-after-raw-back-1080x2400.png`
- 表单日期回填：`docs/qa/screenshots/refactor-v1/recognition-prefill-dates-1080x2400.png`

## 安装包

- 文件：`apk/shiqi-food-expiry-v0.3.18-refactor-debug.apk`（本地构建产物，不提交 Git）
- 大小：`220199310` 字节
- SHA-256：`9B5412214314A92C4DA9BAF7052B5AC81251D32926AF3C25F5E7F4304CCD87EB`
- APK Signature Scheme v2：PASS
- 证书 SHA-256：`eba5350af5a9182067b6bfac2dd1c7b74438b7905cce0b2bc67a12d6ca30be91`

## 剩余风险

1. 真实手机相机仍需验证连续自动对焦、低照度曝光、反光包装、温升和持续帧率。
2. 中文商品名、条码和日期应继续按冻结验证集分别统计字段准确率、整单可用率和首个可用结果时间，不能只看单条视频。
3. `DateOcrScanActivity` 仍然过大；下一阶段按计划继续提取会话状态、取帧、OCR 编排和结果展示模块。
