# APK Excel 批量导入导出与视频生产日期识别方案

日期：2026-07-05

## 1. 新增需求

新增两条 APK 本地主线需求：

1. Excel 批量导入 / 导出。
   - 用户可导出当前本地食品数据为 `.xlsx`。
   - 用户可选择 `.xlsx` 批量导入食品。
   - 导入前必须预览、校验、确认；不能直接覆盖当前数据。
   - 导入前必须触发本地备份，避免版本更新或误导入导致数据丢失。

2. 实时视频识别生产日期。
   - 用户通过相机视频流扫描食品包装上的生产日期。
   - 识别结果只作为候选字段，必须进入确认 / 编辑流程。
   - 生产日期和保质期都不能自动入库。
   - 识别困难时给出清晰提示，允许用户手动输入。

当前仍保持本地 APK 边界：

- 不引入云数据库。
- 不引入账号体系。
- 不上传用户视频、图片或食品数据到云端。
- 不在客户端写入 API key、OCR key、OpenAI key 或其他密钥。
- 不做医疗、营养或绝对食品安全判断。

## 2. 参考资料结论

2026-07-05 复核官方资料后，技术路线保持不变：

- ML Kit Text Recognition v2 官方 Android 文档支持中文脚本识别器 `ChineseTextRecognizerOptions`，并支持从 CameraX `ImageAnalysis.Analyzer` 的 `media.Image` 构造 `InputImage`。
- ML Kit 官方性能建议包括：实时场景丢弃积压帧、完成后关闭 `ImageProxy`、保证文字在图像中有足够像素、让文字尽量占据画面有效区域。
- CameraX ImageAnalysis 官方文档说明非阻塞模式会保留最新帧并丢弃积压帧；本项目应显式使用 `STRATEGY_KEEP_ONLY_LATEST`。
- PaddleOCR Mobile / Paddle Lite 官方路径需要 Android arm 库、模型优化和 native/Java 接入，适合做第二阶段对比实验，不适合在首个 APK POC 中抢先引入。

参考链接：

- ML Kit Text Recognition v2 Android: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- CameraX ImageAnalysis: https://developer.android.com/media/camera/camerax/analyze
- PaddleOCR Paddle-Lite mobile deployment: https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/lite/readme.md

### 2.1 Excel

候选方案：

| 方案 | 结论 |
| --- | --- |
| Apache POI | 功能最全，但依赖重、方法数和内存压力大，不适合作为当前手工 APK 构建体系的第一选择。 |
| Fastexcel | 更适合本项目第一阶段：Apache-2.0，Java 8+，面向 `.xlsx`，强调较低内存和较高性能。 |
| CSV | 可作为紧急兜底，但不满足用户明确提出的 Excel 导入导出体验，不作为主方案。 |

建议第一阶段只支持 `.xlsx`，不支持 `.xls`、公式计算、复杂格式、图片、合并单元格和多 sheet 自由结构。

### 2.2 视频 OCR

候选方案：

| 方案 | 结论 |
| --- | --- |
| CameraX + ML Kit Text Recognition v2 | 第一阶段首选。官方支持 Android 实时视频文本识别，支持中文脚本，适合快速做本地 APK POC。 |
| PaddleOCR Mobile / Paddle Lite | 第二阶段候选。对中文和复杂场景更强，但 Android 接入复杂度、模型体积、native 依赖和构建成本更高。适合在 ML Kit POC 准确率不足后引入。 |
| OpenCV / Tesseract 自行 OCR | 不作为主方案。可以用 OpenCV 做清晰度、裁剪、对比度等预处理，但不要自己实现 OCR 识别核心。 |

## 3. 参考视频初步观察

本地 `video/` 当前有 5 个短视频，均为 540x960、30fps 左右。抽帧观察后，真实识别难点包括：

- 包装文字很小，不是文档级清晰排版。
- 曲面罐身、袋装褶皱、瓶身反光明显。
- 手指经常遮挡有效文字区域。
- 生产日期可能在侧边、底部、封口、喷码区，不一定在正面。
- 生产日期和保质期可能分布在不同位置。
- 单帧容易模糊，必须用多帧选择和投票。

因此不能采用“拍一张图直接 OCR 并保存”的流程。正确方向是：

1. 视频流中持续抽帧。
2. 对帧做清晰度、曝光、运动模糊和文字区域评分。
3. 只对高质量帧做 OCR。
4. 从 OCR 文本中提取日期候选。
5. 多帧候选投票，给出置信度。
6. 进入用户确认 / 编辑页。
7. 用户确认后才写入食品记录。

## 4. Excel 数据格式建议

第一版 Excel 只提供一个 sheet：`foods`。

列名固定，第一行是表头：

| 列名 | 必填 | 说明 |
| --- | --- | --- |
| name | 是 | 食品名称 |
| category | 否 | 内部分类值或中文分类名 |
| productionDate | 否 | `yyyy-MM-dd` |
| shelfLifeValue | 否 | 正整数 |
| shelfLifeUnit | 否 | `day` / `month` / `year` 或中文别名 |
| expiryDate | 条件必填 | 最终可食用日期；如为空则必须能由生产日期 + 保质期计算 |
| quantity | 否 | 总数量，默认 1 |
| remainingQuantity | 否 | 剩余数量，默认等于 quantity |
| unit | 否 | 盒、瓶、袋等 |
| storageMethod | 否 | 保存方式内部值或中文名 |
| location | 否 | 存放位置内部值或自定义文本 |
| openedDate | 否 | 开封日期 |
| afterOpenShelfLifeValue | 否 | 开封后保质期数值 |
| afterOpenShelfLifeUnit | 否 | `day` / `month` / `year` |
| notes | 否 | 备注 |

导入策略：

- 先解析到临时列表。
- 每行输出校验结果：可导入 / 需修正 / 跳过。
- 用户选择追加导入或覆盖导入。
- 覆盖导入前写入最近备份。
- 导入失败时不修改当前食品数据。
- `expiryDate` 仍是 canonical final edible date。

导出策略：

- 导出当前本地 foods 列表。
- 同时写出 README sheet，解释列名、日期格式、分类枚举、保存方式枚举、位置枚举。
- 不导出内部备份数据。

## 5. 视频识别产品流程建议

入口命名：当前 APK 使用 `识别包装文字`，强调这是包装表面文字候选识别；旧方案中的 `视频识别日期` 仅作为历史备选。

交互流程：

1. 用户进入识别页。
2. 页面提示：对准生产日期 / 喷码 / 包装侧面，缓慢移动和转动包装。
3. 相机实时预览，中央给出识别框。
4. 系统每 300-600ms 处理一帧，忙时丢弃旧帧。
5. UI 展示当前最佳候选：
   - 生产日期候选
   - 保质期候选
   - 原始 OCR 文本片段
   - 置信度
6. 当候选连续多帧一致时，提示用户可以确认。
7. 用户点击确认后进入食品表单，字段预填但仍可编辑。
8. 保存食品仍走现有确认保存逻辑。

识别规则第一版：

- 支持 `生产日期`、`生产批号`、`包装日期`、`制造日期`、`见喷码` 周边文本。
- 支持日期格式：
  - `2026-07-05`
  - `2026/07/05`
  - `20260705`
  - `2026年7月5日`
  - `26.07.05`
- 支持保质期格式：
  - `保质期 7天`
  - `保质期 12个月`
  - `保质期 1年`
  - `冷藏7天`
  - `常温180天`
- 生产日期与保质期同时识别时，计算 `expiryDate`。
- 只有生产日期时，只预填 `productionDate`。
- 只有保质期时，只预填保质期字段。
- 多个日期候选冲突时，不自动选择，要求用户确认。

## 6. 技术落地路径

当前 APK 构建脚本是手工 `javac + d8 + aapt2`，只适合 jar 级依赖。CameraX、ML Kit、Fastexcel 这类现代 Android / Maven 依赖更适合 Gradle 管理。

建议新增基础任务：

### BUILD-002：APK Gradle 构建基线

状态：已完成。PR 当前新增 Gradle Wrapper、Android Gradle Plugin 8.13.0 基线和 `:apk:app` 模块，保留原 `apk/build-apk.ps1` fallback。

- 新增 Android Gradle 项目文件。
- 保留现有 `apk/build-apk.ps1` 作为包装脚本或 fallback。
- 当前 ZXing jar 继续可用。
- 新依赖必须记录在 `apk/THIRD_PARTY_NOTICES.md`。
- 验收：Gradle build、原手工 build、本地 Java 测试均已通过；Gradle APK 已用覆盖安装验证，首页视觉 smoke 见 `docs/qa/screenshots/gradle-build-home-smoke.png`。

### XLSX-001：Excel 模板和导出

状态：已完成。当前导出使用内置最小 OOXML writer 生成 `.xlsx`，不新增第三方运行时依赖；如果后续导入复杂度升高，再评估 Fastexcel。

- 导出 `.xlsx`，包含 `foods` 和 `README` 两个 sheet。
- 本地测试覆盖字段映射、日期格式、中文枚举。
- 视觉 QA 覆盖系统文件保存器和导出完成提示。

### XLSX-002：Excel 导入预览

状态：已完成。当前版本新增本地 `.xlsx` reader，先支持本应用导出的 `foods` sheet 模板和常见 inline/shared string 单元格；解析后只生成预览，用户确认前不写入本地食品数据。

- 当前未引入 Fastexcel reader，避免为第一阶段导入增加运行时依赖；若后续需要兼容复杂 Excel 文件，再评估引入。
- 解析 `.xlsx` 到临时导入结果。
- 显示总行数、可导入行、错误行、警告行。
- 不允许直接写入。
- 本地测试覆盖坏文件、非法日期、缺 `expiryDate` 但可计算、错误行不导入、导出再导入。

### XLSX-003：Excel 确认导入

状态：部分完成。当前已完成追加导入确认：只导入预览中可导入的行，确认后追加到当前本地数据，并复用现有 `FoodStore.saveFoods()` 保存前备份。覆盖导入、错误行详情页和失败回滚视觉用例仍留后续任务。

- 支持追加；覆盖导入留后续。
- 覆盖前写备份。
- 失败不改现有数据。
- 视觉 QA 已覆盖首页导入按钮、系统文件选择器、导入预览、确认追加和导入后列表；错误行视觉预览和覆盖确认还未覆盖。

### OCR-001：视频样本离线评测工具

状态：样本评测已完成第一轮。工具位置为 `tools/ocr_eval/`，已支持 FFmpeg 抽帧和 OpenCV Python 兜底抽帧；本机未安装 Tesseract，因此本轮只完成帧级视觉/质量评估，不声称已完成 OCR 文字识别。

- 不进 APK，先在开发机对 `video/` 抽帧。
- 输出关键帧、清晰度分数、亮度指标、raw OCR 和 `candidateOnly` 候选字段。
- 用户本地 5 个视频已生成 `.local/ocr-eval/OCR-001-samples/report.html` 和 `metrics.csv`；这些输出保持 ignored，不提交真实视频、抽帧或 OCR 结果。
- 样本结论：文字小、曲面/反光/手指遮挡明显，且“最清晰帧”可能是 logo 或条码而不是日期；后续 APK 必须用识别框引导、多帧采样、候选投票和确认页。
- 目标是建立测试集，不直接做产品功能。

### OCR-002：CameraX + ML Kit POC

状态：已完成 APK POC。当前入口为 `识别包装文字`，页面只输出 raw OCR、候选摘要和表单草稿，不直接保存食品。

- 引入 CameraX Preview + ImageAnalysis。
- 引入 ML Kit Text Recognition v2 Chinese。
- 使用 `STRATEGY_KEEP_ONLY_LATEST`，避免积压帧。
- 输出 raw OCR text，不保存食品。
- 视觉 QA 覆盖相机权限、预览、识别框、候选文本。

### OCR-003：生产日期 / 保质期候选提取

状态：纯 Java 解析器已完成，位置为 `apk/app/src/main/java/com/shiqi/expirytracker/DateOcrParser.java`；当前输入 raw OCR text，输出候选字段、来源文本、置信度、冲突标记和 `candidateOnly` 标记，不写入 `FoodItem`。

- 支持中文提示词、英文 `EXP`、`yyyy-MM-dd` / `yyyy/MM/dd` / `yyyy.MM.dd` / `yyyy年M月d日` / `yyyyMMdd` / `yyMMdd`。
- 支持全角数字和常见全角分隔符归一化。
- 支持 `保质期 7 天`、`常温180天`、英文 shelf life 单位归一化。
- 生产日期 + 保质期可计算候选 `calculatedExpiryDate`，但仍只作为候选给确认页使用。
- JVM 测试覆盖候选不自动保存、中文/英文提示、紧凑数字日期、全角字符、多个候选冲突、条码和非法日期不误识别。

### OCR-004：多帧投票和确认页

状态：多帧投票纯 Java 逻辑已完成，位置为 `apk/app/src/main/java/com/shiqi/expirytracker/DateOcrFrameVoter.java`；稳定候选现已进入用户确认流，确认后只预填新增食品表单，表单保存前不写入本地食品数据。

- 对连续帧候选做投票，默认至少 2 帧命中同一候选才算稳定。
- 候选冲突时不进入 ready 状态，必须要求用户人工确认。
- 输出 `productionDate`、`shelfLife`、`expiryDate`、`calculatedExpiryDate` 的稳定候选，但仍全部标记为 `candidateOnly`。
- JVM 测试覆盖重复候选进入确认、单帧不足不确认、重复冲突不确认。
- 当前 UI 节点：只在候选稳定后允许“使用候选”；候选返回后先弹窗确认，再进入食品表单预填；用户在表单保存前不写食品数据。

### OCR-005：PaddleOCR 对比实验

- 如果 ML Kit 在视频样本上准确率不足，再引入 PaddleOCR Mobile / Paddle Lite POC。
- 与 ML Kit 在同一组视频帧上比较：
  - 日期召回率
  - 错识率
  - 延迟
  - APK 体积
  - 低端机发热 / 卡顿
- 未通过对比前不直接替换主 OCR 方案。

## 7. 验收标准

Excel：

- 导出文件能被 Excel / WPS 打开。
- 导入前有预览，错误行不入库；当前已完成追加导入，覆盖导入仍待实现。
- 覆盖导入前有备份。
- 导入后 `expiryDate` 排序和提醒仍正确。
- 旧数据不会因为导入失败丢失。

视频 OCR：

- 不自动保存。
- 原始 OCR 文本或候选来源可供用户确认。
- 识别生产日期必须给出候选来源片段。
- 多候选冲突必须要求用户选择。
- 低置信度必须提示手动输入。
- 视频参考样本必须进入回归集。

## 8. 当前不做

- 云端 OCR。
- 使用 API key 的商业 OCR。
- 用大模型直接判断食品安全或保质期。
- 在客户端内置任何密钥。
- 跳过用户确认直接保存 OCR 结果。

## 9. 2026-07-05 OCR-002 落地更新

本轮已把 `OCR-002` 从方案推进到 APK POC：

- 新增 `DateOcrScanActivity`，使用 CameraX `Preview` + `ImageAnalysis`，并显式使用 `STRATEGY_KEEP_ONLY_LATEST`，避免实时识别积压帧。
- 新增 ML Kit Text Recognition v2 Chinese 本地识别依赖，使用 `InputImage.fromMediaImage(..., imageProxy.getImageInfo().getRotationDegrees())` 处理 CameraX 帧。
- 新增 `DateOcrResultPayload`，把稳定候选转换为可编辑 `FoodItem` 草稿；不写入 `FoodStore`，不写入 notes，不保存 raw OCR。
- 主页面新增“识别包装文字”入口；稳定候选返回后先弹出用户确认，再进入新增表单预填，最终保存仍复用原表单“保存”按钮。
- `apk/build-apk.ps1` 的 debug 路径改为委托 Gradle 构建并复制到原输出路径，因为 CameraX / ML Kit 是 AAR/Maven 依赖，不适合继续由手工 `javac + d8` 打包。

仍未声明完成的内容：

- 当前视觉 QA 已覆盖首页“识别包装文字”入口和无可用相机时的 OCR 页面降级态；允许权限后的实时预览、动态候选和“使用候选”可点击态仍需要真机或稳定摄像头模拟器继续回归。
- 当前 POC 不做自动选择冲突候选，不做云端 OCR，不引入 API key，不上传视频或图片。
