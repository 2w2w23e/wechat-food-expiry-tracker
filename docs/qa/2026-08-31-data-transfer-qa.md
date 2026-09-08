# 食期管家 V2 Excel / JSON 数据交换 QA

日期：2026-08-31  
结论：`PASS（系统文件选择器、应用自导出回导、预览、覆盖保护和系统分享）`

## Excel 完整旅程

在 `Shiqi_QA` 模拟器实际操作，不以源码推断：

1. 手动新增一个 `dateSource=unknown` 的日期待补充商品。
2. 设置 -> Excel 批量管理 -> 导出 Excel。
3. 在 Android `Downloads` 系统文件选择器保存 `shiqi-foods-20260831-0101.xlsx`。
4. 再从系统文件选择器选择同一文件导入。
5. 首次回导发现应用自导出的 `unknown` 行被错误拒绝，详情为“必须填写最终可食用日期”。
6. 修正导入契约并新增往返测试后，同一原文件预览为总行数 1、可导入 1、错误 0、警告 0。
7. 实际执行追加导入，当前库存从 1 批次变为 2 批次。
8. 再次选择同一文件，执行覆盖导入；确认框明确显示以 1 条覆盖 2 条，并说明先备份、失败不改数据。

证据：

- `docs/qa/screenshots/redesign-v2-android/excel-management-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/excel-export-system-picker-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/excel-self-import-error-detail-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/excel-import-preview-self-export-fixed-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/excel-overwrite-confirm-1080x2400.png`

## JSON 完整旅程

1. 打开筛选导出页，确认入库、生产和最终日期三组闭区间筛选。
2. 留空筛选并执行直接分享，Android 分享面板正确显示 JSON 文件名和可用目标。
3. 保存 JSON 到 `Downloads` 后，从系统文件选择器选择同一文件回导。
4. 同批次编号被识别为内容相同，不重复新增；预览显示文件批次 1、内容相同 1、错误 0。
5. 720x1280 下重建后，JSON 预览对话框仍在当前业务页内且文案、按钮无截断。

证据：

- `docs/qa/screenshots/redesign-v2-android/json-transfer-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/json-filter-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/json-share-chooser-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/json-import-preview-self-export-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/d1-after-recreate-720x1280.png`

## 自动验证

- 本地 Java 逻辑：227 项通过，其中 Excel/JSON 覆盖数字日期、日期来源、重复批次、较新数据更新、较旧冲突保留和整批错误拒绝。
- 导入写入统一经过批量验证和原子保存。
- Android 仪器测试验证覆盖前备份；强制保存失败时库存保持原值。

## 边界

- 模拟器未安装 QQ/微信，因此只能证明标准 Android 分享面板和带读权限的 JSON 附件可达；目标应用自己的接收页面需在装有目标应用的真机确认。
- Excel 是批量管理，不承担 JSON 同步的批次幂等语义；追加导入会建立新的本地独立批次，覆盖导入会替换当前库存。
