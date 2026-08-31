# 食期管家 V2 系统重设计与原型 QA

日期：2026-08-31
结论：`PASS`（仅限系统设计与可点击原型）

## 为什么重做

2026-08-12 的完整手动用户旅程推翻了此前“业务页面已完成”的乐观结论。问题不是单个控件不好看，而是导航、数据规则、状态恢复、识别完成条件和导入承诺彼此不一致。继续在两个超大 Activity 中追加条件只会让用户不断遇到下一层错误，因此本轮先冻结系统行为，再进入 Android 页面替换。

## 已交付

- `docs/redesign-v2/SYSTEM_BLUEPRINT.md`：任务、数据不变量、状态机、识别管线、保留/重写/删除边界。
- `docs/redesign-v2/PRODUCT_REQUIREMENTS.md`：三项导航、手动优先、部分识别可继续、批次和提醒规则。
- `docs/redesign-v2/DESIGN_SYSTEM.md`：页面、组件、尺寸、语义色、相机和无障碍规则。
- `design/redesign-v2/`：覆盖主流程和异常流程的可点击原型。
- `design/redesign-v2/design-qa.md`：视觉对比、交互证据和剩余限制。

## 研究结论如何进入设计

| 原始资料 | 采用方式 |
| --- | --- |
| Android offline-first 与 UI state holder | 本地数据库为唯一事实源；页面不直接拼接持久化、导入和识别状态 |
| CameraX ImageAnalysis | 使用 `KEEP_ONLY_LATEST`，预览和识别解耦，不排队处理所有帧 |
| CameraX FocusMeteringAction | 连续自动对焦加点按对焦；能力缺失时显式降级 |
| ML Kit 中文文字识别 | 保留 bundled Chinese fast path；清晰度和字符像素不足时先请求重拍而非硬猜 |
| OpenCV CLAHE、阈值、形态学 | 只对日期 ROI 建立少量预处理分支，不对整帧暴力跑多套模型 |
| PaddleOCR PP-OCRv5 | 作为候选慢通道，必须经本项目样本 A/B 后才进入 APK |
| 同类库存项目原始仓库 | 保留手动快速录入、批次、导入导出和离线优先；不照搬其视觉样式 |

## 已验证流程

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| 手动新增与三字段校验 | PASS | 同时显示名称、日期、数量错误；聚焦名称 |
| 日期未知仍可保存 | PASS | 保存后回到今日，状态为日期待补语义 |
| 识别部分结果采用 | PASS | 名称/条码可改，日期未识别不阻塞 |
| 依据页往返 | PASS | 修改内容保留，返回原核对与编辑状态 |
| 日期专拍失败恢复 | PASS | 不显示无关字段；可直接返回填写 |
| 条码一对多补货 | PASS | 先选择商品，再创建新批次 |
| 库存筛选 | PASS | 底部面板，无水平截断 |
| 智能/固定提醒 | PASS | 未保存返回保护，保存后回设置 |
| Excel 预览 | PASS | 展示有效行、错误行、回滚承诺；仅原型行为 |

## 视觉证据

- 同屏参考对比：`docs/qa/screenshots/redesign-v2-system/comparison-source-today.png`
- 短屏：`today-360x640.png`、`inventory-360x640.png`、`editor-360x640.png`
- 相机：`scanner-360x640.png`、`scanner-date-360x640.png`、`scanner-360x800.png`
- 核对：`review-360x640.png`、`evidence-360x640.png`
- 数据：`settings-360x640.png`、`excel-preview-360x640.png`

## 自动检查

```text
prototype smoke tests: 8 passed, 0 failed
horizontal overflow: 0 on checked 360x640 screens
independent undersized controls: 0
browser console errors/warnings: 0
git diff --check: pass (CRLF warnings only)
```

## 边界

本结论不代表 Android V2 已实现，不代表 OCR 准确率已达标，也不代表真机相机、Excel 真实写入、JSON 导入、覆盖安装和 APK 签名已通过。这些节点仍必须用 Android 自动测试、视频字段级评测、模拟器/真机视觉证据和覆盖安装数据对比独立验收。
