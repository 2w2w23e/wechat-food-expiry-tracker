# C1 相机识别模块拆分 QA

日期：2026-08-31
结论：`PARTIAL PASS`

本记录为 8 月切片基线；9 月恢复后的当前代码和测试结果见 `2026-09-08-c1-resume-checkpoint.md`，不得把本表旧设备结果直接归到后续修改。

## 本切片范围

- 将取景框、实时文字框、对焦点和识别依据框从 `DateOcrScanActivity` 拆为独立 `RecognitionGuideOverlay`。
- 将 OCR 图像变体及其来源坐标、质量和回收语义拆为 `RecognitionFrameVariant`。
- 新增 `RecognitionWorkSession`，集中管理识别代次、全量重置、保留证据补扫、确认冻结和 Activity 销毁后的异步回调失效。
- 新增 `VideoReplayFrameSource`，集中持有 `MediaMetadataRetriever`、视频元数据、缩放取帧、原始复核帧和幂等释放；Activity 不再直接管理解码器。
- 将识别页顶部状态文案归并到纯状态映射 `ScanUiPresentation.recognitionStatus`，同一识别状态不会再由多个 Activity 分支产生矛盾提示。
- 将 Paddle OCR 文本、置信度与归一化坐标封装为独立 `PaddleDateEvidence`，并以纯逻辑测试固定多行包装文本到字段观察值的转换语义。
- 不更换 OCR 模型、不修改字段投票阈值、不改变库存写入。

## 自动验证

| 检查 | 结果 |
| --- | --- |
| 本地 Java 逻辑 | PASS，347 项断言/测试组，0 失败 |
| 相机帧与会话测试 | PASS，7/7；覆盖仅保留最新帧、资源释放、代次失效、确认冻结和关闭 |
| Android 仪器测试 | PASS，22/22，0 跳过、0 失败 |
| Android lint | PASS，0 error、51 warning |
| Debug APK | PASS，成功 assemble |
| `git diff --check` | PASS，仅工作区既有 CRLF 提示 |

## 视觉验证

- 在 1080x2400 AVD 通过真实入口“新增商品 -> 相机识别日期 -> Allow”进入相机页面。
- 人工查看 `docs/qa/screenshots/redesign-v2-android/c1-overlay-extracted-1080x2400.png`：标题、对焦状态、取景框、日期单字段状态和手动回退按钮均可见，无重叠、截断或空白覆盖。
- 720x1280 的同页面基线仍由 `scanner-date-final-720x1280.png` 覆盖；本切片未改变尺寸、颜色或布局参数，只移动绘制所有权。

## 用户视频回放

- 使用 `Record_2026-07-29-18-13-34_e7c84e4b72b45d7acf770bbb7219e5ff.mp4` 通过调试入口运行新的 `VideoReplayFrameSource`。
- 5 个选择窗口共接受 5 帧，34.514 秒完成，无解码异常或 Activity 崩溃。
- 字段结果：商品名“复方硫酸软骨素滴眼液”和条码 `6917246211883` 稳定；日期出现冲突且没有静默预选，用户仍可继续扫描或继续填写。
- 视觉证据：`docs/qa/screenshots/redesign-v2-android/c1-video-july29-after-frame-source-1080x2400.png`。

## 仍未完成

- `DateOcrScanActivity` 仍有 8922 行，视频循环调度、全帧 OCR 编排、Paddle 日期慢通道、关键帧和字段融合仍在 Activity 内。
- 本切片证明拆分未破坏 UI 和生命周期，不代表用户视频字段召回率或真机对焦已经通过。
- C1 继续拆出 replay frame source、识别调度和字段草稿/证据控制器后才能标记完成。
