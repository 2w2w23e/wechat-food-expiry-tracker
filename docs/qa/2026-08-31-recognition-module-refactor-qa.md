# C1 相机识别模块拆分 QA

日期：2026-08-31
结论：`PARTIAL PASS`

## 本切片范围

- 将取景框、实时文字框、对焦点和识别依据框从 `DateOcrScanActivity` 拆为独立 `RecognitionGuideOverlay`。
- 将 OCR 图像变体及其来源坐标、质量和回收语义拆为 `RecognitionFrameVariant`。
- 新增 `RecognitionWorkSession`，集中管理识别代次、全量重置、保留证据补扫、确认冻结和 Activity 销毁后的异步回调失效。
- 不更换 OCR 模型、不修改字段投票阈值、不改变库存写入。

## 自动验证

| 检查 | 结果 |
| --- | --- |
| 本地 Java 逻辑 | PASS，345 项断言/测试组，0 失败 |
| 相机帧与会话测试 | PASS，7/7；覆盖仅保留最新帧、资源释放、代次失效、确认冻结和关闭 |
| Android 仪器测试 | PASS，22/22，0 跳过、0 失败 |
| Android lint | PASS，0 error、51 warning |
| Debug APK | PASS，成功 assemble |
| `git diff --check` | PASS，仅工作区既有 CRLF 提示 |

## 视觉验证

- 在 1080x2400 AVD 通过真实入口“新增商品 -> 相机识别日期 -> Allow”进入相机页面。
- 人工查看 `docs/qa/screenshots/redesign-v2-android/c1-overlay-extracted-1080x2400.png`：标题、对焦状态、取景框、日期单字段状态和手动回退按钮均可见，无重叠、截断或空白覆盖。
- 720x1280 的同页面基线仍由 `scanner-date-final-720x1280.png` 覆盖；本切片未改变尺寸、颜色或布局参数，只移动绘制所有权。

## 仍未完成

- `DateOcrScanActivity` 仍有 9115 行，视频回放、全帧 OCR 编排、Paddle 日期慢通道、关键帧和字段融合仍在 Activity 内。
- 本切片证明拆分未破坏 UI 和生命周期，不代表用户视频字段召回率或真机对焦已经通过。
- C1 继续拆出 replay frame source、识别调度和字段草稿/证据控制器后才能标记完成。
