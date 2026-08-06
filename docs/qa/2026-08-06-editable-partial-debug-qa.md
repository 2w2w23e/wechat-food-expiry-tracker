# 2026-08-06 可编辑核对、部分结果与调试日志验收

## 结论

| 范围 | 状态 | 结论 |
| --- | --- | --- |
| 商品名修正 | PASS | 核对页可直接修改漏字或错字，保存后结果页同步更新 |
| 缺失日期继续 | PASS | 日期未稳定识别时仍可进入新增表单自主填写，系统不猜测日期、不自动设为无过期 |
| 识别调试模式 | PASS | 可在设置页启停；记录运行状态、原文、字段与耗时；中途定期落盘；关闭时可分享文本日志 |
| 完整业务流程 | PASS | 新增、减一、编辑、补货、删除均在隔离 AVD 上按用户路径走完 |
| Excel/JSON | PASS | 页面视觉、导入导出逻辑、筛选、冲突更新和失败回滚均有验证 |
| 真实手机相机 | PARTIAL | 本轮没有真机镜头证据；需用调试日志继续验收对焦、曝光、帧率和首结果时延 |

## 自动化证据

- APK 本地逻辑测试：224 通过，0 失败。
- CameraX 最新帧管线：5 通过。
- 录入编辑器状态：9 通过。
- 库存快照映射：29 通过。
- 业务页面状态：15 通过。
- Android 仪器测试：16 通过，0 失败。
- Android Lint、应用 APK 和测试 APK：构建成功。
- Excel 覆盖工作簿生成、预览、1904 日期、计算/手动模式往返、无过期往返、无效行、覆盖/追加、档案/条码冲突和事务回滚。
- JSON 覆盖时间区间筛选、中文批次、按更新时间更新数量和状态、保留较新的本地数据、重复批次编号原子拒绝。

## 视觉与用户流程

1. 开启调试模式，确认状态与隐私提示没有重叠：[开启前](screenshots/2026-08-06-editable-partial-debug/02-debug-settings-off.png)、[开启后](screenshots/2026-08-06-editable-partial-debug/03-debug-settings-on.png)。
2. 回放 artifacts/replay-qa/ocr_sample_3.mp4，得到商品名 BLUE果汁，日期保持“已看到日期样文字，尚未稳定”，并显示可用的“继续填写”：[部分结果](screenshots/2026-08-06-editable-partial-debug/05-partial-result.png)。
3. 打开商品名编辑器，确认输入框预填、建议保留且可以保存修正：[商品名编辑](screenshots/2026-08-06-editable-partial-debug/06-product-name-editor.png)。仪器测试实际完成中文“维C”到“维C水”的修改并保存。
4. 点击“继续填写”，新增表单保留商品名、日期为空且可自主选择：[手动补充表单](screenshots/2026-08-06-editable-partial-debug/08-partial-to-manual-form.png)。
5. 依次完成新增 2 件、快捷减一、编辑名称、补货新批次 3 件、删除补货批次、删除最后批次，库存最终回到 0 商品/0 批次。
6. 检查 Excel 和 JSON 页面没有文字截断、按钮重叠或嵌套卡片：[Excel](screenshots/2026-08-06-editable-partial-debug/11-excel-page.png)、[JSON](screenshots/2026-08-06-editable-partial-debug/12-json-page.png)。
7. 关闭调试模式时出现“仅关闭 / 取消 / 关闭并分享”：[关闭确认](screenshots/2026-08-06-editable-partial-debug/09-debug-disable-dialog.png)。选择分享后生成 shiqi-recognition-debug-*.txt 并打开系统分享面板：[分享面板](screenshots/2026-08-06-editable-partial-debug/10-debug-share-chooser.png)。AVD 未安装 QQ/微信，因此仅验证系统分享协议与文件可读；真机安装相应客户端后会由系统列出。

## 调试日志内容

日志包含会话编号、设备/系统/内存/存储、屏幕、权限、CameraX 与模型状态、采集/探测/入选帧数、清晰度、对焦事件、检测/识别/ML Kit 耗时、稳定商品名/日期/条码、OCR 原文和事件时间线。调试开启期间每 8 秒最多落盘一次检查点，强制结束应用后仍能保留最近状态。

## 剩余边界

真实手机光学链路尚未在本轮直接执行，不能从 AVD 视频回放外推出对焦和曝光已经通过。用户应在真机开启识别调试模式，复现后关闭并分享日志；下一轮据此定位采集、清晰度门控、文字检测、文字识别或字段晋升中的具体瓶颈。