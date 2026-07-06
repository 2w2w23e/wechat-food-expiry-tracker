# APK 统一识别重设计方案

日期：2026-07-06

## 1. 背景

当前 APK 已经有两个入口：

- `扫码识别`：识别条码后查询商品信息。
- `识别包装文字`：用视频/相机帧 OCR 提取生产日期和保质期候选。

这个拆分不符合真实使用场景。用户拿起包装时，通常不知道先扫条码还是找日期；有些包装条码清楚但日期难找，有些包装没有可用条码但日期清楚。识别页还存在三个明显问题：

- 结果按帧变化，候选不稳定，`使用候选` 经常不可用。
- 条码、商品名、包装文字没有合并为同一个识别依据。
- UI 把相机/视频入口放得过重，结果区和预览区层级不清。

因此后续不再继续强化两个独立页面，而是改成一个统一的“智能识别”流程。

## 2. 产品目标

统一入口命名建议：`智能识别` 或 `识别包装`。

一次识别会同时尝试：

1. 自动寻找条码。
2. 自动识别包装文字。
3. 用条码查询到的商品信息辅助生成商品名、备注和识别依据。
4. 用包装文字提取生产日期、保质期和最终日期候选。
5. 输出一个可编辑的识别结果面板。

关键边界保持不变：

- 条码、OCR 和商品查询结果都不能自动保存。
- 用户必须确认或编辑后，才能填入新增食品表单并最终保存。
- 不做医疗、营养或绝对食品安全判断。
- 当前仍是本地 APK，不上传图片、视频或食品数据到云端。

## 3. 网上资料结论

优先采用成熟本地能力，不从零写 OCR 或条码识别核心。

### 3.1 ML Kit Barcode Scanning

官方文档显示 ML Kit Barcode Scanning 支持 Android 本地条码识别、可配置格式、可选自动缩放，并有 bundled / unbundled 两种模型交付方式：

- https://developers.google.com/ml-kit/vision/barcode-scanning/android
- https://developers.google.com/ml-kit/tips/installation-paths

结论：

- 第一阶段应把实时识别主链路迁移到 CameraX + ML Kit Barcode Scanning。
- 继续保留现有 ZXing 作为图库、视频帧或模型不可用时的 fallback。
- 商品条码格式先限制在 EAN-13、EAN-8、UPC-A、UPC-E、ITF、Code 128、QR Code、Data Matrix，减少无关格式误识别。
- 模型交付建议先用 bundled，保证离线安装 APK 后立即可用；如果 APK 体积压力明显，再评估 unbundled。

### 3.2 ML Kit Text Recognition v2

ML Kit Text Recognition v2 官方文档强调：文字需要足够像素，模糊会显著降低准确率；实时识别应限制图像尺寸、让文字尽量占据有效区域，并丢弃积压帧：

- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://developer.android.com/media/camera/camerax/analyze

结论：

- 继续使用 ML Kit Text Recognition v2 Chinese 作为第一阶段 OCR。
- 必须增加画面质量门禁、识别框引导、ROI 裁剪和候选锁定；单纯提高解析正则不能解决真实包装文字小、反光、弯曲和抖动问题。
- CameraX `ImageAnalysis` 必须使用 `STRATEGY_KEEP_ONLY_LATEST`，分析完成后及时关闭 `ImageProxy`。

### 3.3 PaddleOCR / PP-OCRv5

PaddleOCR 官方资料显示 PP-OCRv5 覆盖简体中文、英文等多语言场景，并提供移动端/边缘部署模型；Android demo 依赖 PaddleLite 和模型文件：

- https://paddlepaddle.github.io/PaddleOCR/main/en/version3.x/algorithm/PP-OCRv5/PP-OCRv5.html
- https://paddlepaddle.github.io/PaddleOCR/v3.0.0/en/version2.x/legacy/android_demo.html
- https://paddlepaddle.github.io/PaddleX/3.4/en/pipeline_usage/tutorials/information_extraction_pipelines/document_scene_information_extraction_v4.html

结论：

- PaddleOCR 更适合第二阶段对比实验，不适合立刻替换当前 APK 主链路。
- 引入前必须用同一批 `video/` 样本评估：日期召回率、误识别率、首个稳定候选耗时、APK 体积、低端机发热和卡顿。
- 如果 ML Kit 在真实包装视频上持续达不到可用准确率，再做 PaddleOCR Mobile POC。

## 4. 统一识别架构

建议新增或重命名为 `UnifiedRecognitionActivity`。

输入来源统一为：

- 相机实时流。
- 本地视频模拟相机。
- 图片/图库。
- 手动输入兜底。

每一帧走同一条管线：

```text
Frame
  -> FrameQualityGate
  -> BarcodeRecognizer
  -> TextRecognizer
  -> ProductLookupEnricher
  -> DateAndShelfLifeExtractor
  -> CandidateStabilizer
  -> UnifiedRecognitionResult
```

### 4.1 FrameQualityGate

先判断帧是否值得识别：

- 清晰度分数：过滤明显模糊帧。
- 亮度/曝光：过滤过暗、过曝、强反光帧。
- 有效区域：优先中央识别框和用户框选区域。
- 运动抖动：视频模拟和实时相机都应避免连续处理抖动帧。

低质量帧不进入 OCR 主流程，只更新轻量提示，例如“靠近一点”“保持稳定”“反光太强”。

### 4.2 BarcodeRecognizer

条码识别应优先运行，因为条码一旦稳定，可以立刻提供商品名、品牌、规格或备注依据。

规则：

- 同一条码连续 2 帧一致，或图库单图高置信命中，即视为稳定条码。
- 稳定条码锁定后，不再被空结果或短时误识别覆盖。
- 条码查询在后台执行，不阻塞 OCR。
- 查询结果只作为候选和辅助信息，不直接保存。

### 4.3 TextRecognizer

OCR 不再把全帧原文直接作为主要 UI 结果，而是输出结构化线段：

- 原始文字。
- 行/块位置。
- 来源帧质量。
- 是否位于 ROI。
- 是否靠近日期关键词、喷码区域或条码区域。

原始 OCR 文本保留在“查看原文”里，用于排查和人工确认，不在主界面持续跳动。

### 4.4 ProductLookupEnricher

如果条码稳定，复用现有商品信息查询：

- 商品名优先来自条码查询结果。
- 商品品牌/规格进入备注或候选详情。
- 查询失败时保留条码本身，允许用户手动补充。

注意：商品查询结果只能辅助“识别依据”和表单预填，不能绕过用户确认。

### 4.5 DateAndShelfLifeExtractor

继续复用并增强 `DateOcrParser`，但输入要从“整段 raw OCR”升级为“带来源权重的文本线段”。

候选加权建议：

- 日期关键词附近更高权重：生产日期、包装日期、制造日期、见喷码、EXP、MFG。
- 保质期关键词附近更高权重：保质期、常温、冷藏、shelf life。
- 位于 ROI 或喷码区域的文本更高权重。
- 来自低质量帧、边缘裁切、明显 OCR 混淆的文本降权。

冲突时不强行选一个，应显示“多个候选，请选择”。

## 5. 候选稳定模型

当前最大问题是结果一直变化。后续必须把“每帧识别结果”和“用户可用候选”分开。

### 5.1 滚动窗口

维护最近 8 到 12 个有效帧，UI 主结果最多每 700 到 1000ms 更新一次。

每个字段单独投票：

- `barcode`
- `productName`
- `productionDate`
- `shelfLife`
- `expiryDate`

### 5.2 评分公式

建议第一版采用可测试的规则评分：

```text
score =
  sameValueVotes
  + keywordWeight
  + roiWeight
  + frameQualityWeight
  + barcodeContextWeight
  - conflictPenalty
```

其中：

- `sameValueVotes`：同一标准化值出现次数。
- `keywordWeight`：靠近生产日期/保质期关键词。
- `roiWeight`：位于识别框、喷码区或用户框选区。
- `frameQualityWeight`：清晰、亮度正常、稳定。
- `barcodeContextWeight`：条码商品信息能辅助商品名或类别。
- `conflictPenalty`：同一字段出现多个强候选。

### 5.3 锁定和防抖

锁定规则：

- 条码：同值 2 次稳定锁定。
- 日期/保质期：同值 3 个加权有效投票，或 2 个高质量关键词命中投票。
- 图库单图：允许生成候选，但标记“待确认”，不自动锁死。
- 用户手动点选或编辑后，该字段进入手动锁定，后台识别不得覆盖。

防抖规则：

- 当前 UI 候选不被轻易替换。
- 新候选分数必须比当前候选高至少 2 分，或当前候选超过有效期，才允许替换。
- 候选冲突时显示选择列表，而不是禁用所有操作。

这样可以解决“结果一直变化，无法使用候选”的核心问题。

## 6. UI 重设计

页面结构建议：

```text
顶部栏：返回 + 智能识别 + 状态标签
预览区：相机/视频/图片画面 + 单一识别框
底部结果面板：商品码、商品名、生产日期、保质期、最终日期
底部工具栏：相机 / 视频 / 图片 / 手动
```

### 6.1 预览区

- 相机和视频不应是页面顶部的主按钮，而是底部输入来源切换。
- 预览区占页面主要空间，识别框固定、清晰、稳定。
- 视频模拟相机时显示“视频样本”状态，但 UI 布局与相机一致。

### 6.2 结果面板

主面板只显示可操作结果：

- 商品码：已锁定 / 待确认。
- 商品名：来自条码 / 来自 OCR / 手动填写。
- 生产日期：候选值 + 来源提示。
- 保质期：候选值 + 来源提示。
- 最终日期：由生产日期 + 保质期计算，或待用户填写。

按钮：

- `重新识别`
- `手动修正`
- `填入新增表单`

`填入新增表单` 不应只在完整日期稳定时可用；只要存在条码、商品名或任一日期字段，就可以进入确认/编辑流程。

### 6.3 原文与调试

- raw OCR 不作为主视觉内容滚动刷新。
- 低置信度或用户需要核对时，打开“查看原文”。
- 原文里标记来自哪一帧、哪一块区域，方便后续 QA。

## 7. 任务拆分

```text
REC-001 统一识别设计文档
REC-002 引入 ML Kit Barcode Scanning 并更新第三方声明
REC-003 新建统一识别结果模型和候选稳定器
REC-004 统一识别 UI：预览区 + 底部结果面板 + 来源工具栏
REC-005 CameraX/视频/图片复用同一识别管线
REC-006 条码查询结果与 OCR 字段合并成统一结果
REC-007 video/ 样本视觉 QA 和指标报告
REC-008 APK 构建、安装、截图验收和 PR 上传
```

依赖关系：

```text
REC-001 -> REC-002
REC-001 -> REC-003
REC-001 -> REC-004
REC-002 -> REC-005
REC-003 -> REC-005
REC-004 -> REC-005
REC-005 -> REC-006
REC-006 -> REC-007
REC-007 -> REC-008
```

调度方式继续使用 Kahn 拓扑排序：

1. 所有入度为 0 的节点进入任务池。
2. 任务池内按关键路径长度优先调度。
3. UI、识别模型、依赖接入可以并行，但同一个 Java 文件不能多智能体同时写。
4. `REC-007` 必须包含视频模拟视觉检验，不能只靠代码推断。
5. `REC-008` 必须包含测试跑通、APK 构建、覆盖安装或安装验证、截图证据。

## 8. 验收标准

统一识别完成后，至少满足：

- 首页只有一个主要识别入口，不再让用户先选择扫码还是 OCR。
- 同一识别页能自动发现条码和包装文字。
- 有条码时，商品码和商品查询信息进入同一个结果面板。
- 无条码时，仍可仅用 OCR 候选进入确认/编辑。
- 主结果不会按帧持续闪动；稳定候选可点击、可锁定、可手动修正。
- raw OCR 原文可查看，但不会占据主界面。
- 所有识别结果都必须经用户确认后才保存。
- 用 `video/` 样本完成视觉 QA，并产出截图或录屏证据。

## 9. 暂不做

- 不做云端 OCR。
- 不上传用户图片或视频。
- 不把 OCR/AI 结果自动写入食品数据库。
- 不引入 API key 或商业 OCR key 到 APK 客户端。
- 不直接切换到 PaddleOCR 主链路，先完成同样样本的可量化对比。
