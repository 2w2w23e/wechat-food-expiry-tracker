# 2026-07-14 APK V0.3.9 保质期、相机与 Excel 验收

## 结论

- 新增视频样本：`PASS`。自动识别商品码 `6926265313430`、生产日期 `2026-01-20`、保质期 `9月`，并计算最终可食用日期 `2026-10-20`。
- 无过期时间：`PASS`。日期未识别时保持未知，不再自动勾选；该样本进入新增表单后“无过期时间”未勾选。
- 旧视频回归：`PASS`。5 个 `video/` 样本都保留可核对的核心候选；浅色纸盒样本恢复“酸菜”、生产 `2026-06-12`、最终可食用 `2027-06-11`。
- 用户闭环：`PASS`。完成视频识别、填入表单、保存、改为冷藏、验证智能提醒重算和删除，全程未手工输入识别字段。
- Excel：`PASS`。完成真实 `.xlsx` 导出、重新选择文件、导入预览、问题行详情、覆盖确认和覆盖完成；覆盖前后均为 3 条数据。
- 真实相机：`PARTIAL`。CameraX 分析已统一到视频的 OCR 和低对比增强管线，并增加 1280x960 分析、点按对焦和任务生命周期保护；AVD 没有可用真实后摄像头，仍需真机复验成像和对焦。

## 根因与修复

新样本的绿色包装把 `保质期 9个月` 识别成 `保廣明9个` 或 `保唐呀9个1`。解析器现在只在同一标签行满足“保 + 两到三个 OCR 损坏字 + 数字 + 个”时容错为月份，不会把普通数量或营养数字当作保质期。

日期文字和商品名使用不同证据区。右下标签区提供生产日期和保质期；全帧 CLAHE 仅补充有效日期对，不允许补充保质期，因此浅色纸盒的激光日期可以恢复，同时避免营养表 `204mg` 再变成 `204天`。

没有稳定日期时，识别草稿保持 `unknown`。只有用户在新增或编辑表单明确勾选“无过期时间”，才写入 `dateSource=none`。

## 自动化测试

- `apk/run-local-tests.ps1`：`101 passed, 0 failed`。
- `./gradlew.bat :apk:app:assembleDebug`：`BUILD SUCCESSFUL`。
- 覆盖场景包括批次尾码、OCR 损坏月份、裁掉首位的双日期、营养表隔离、稳定投票、无日期不自动无过期、Excel 往返和本地数据回滚。

## 视频结果

| 样本 | 可核对结果 | 结论 |
| --- | --- | --- |
| 新增样本 `f378c2d070127470b26f8bc34aa11c70.mp4` | `6926265313430`；`2026-01-20`；`9月`；`2026-10-20` | PASS |
| video01 | 老北京炸酱面；`6971416494254` | PASS |
| video02 | 鹌鹑蛋包装候选 | PASS |
| video03 | 果汁；茶饮料 | PASS |
| video04 | `6930628889364` | PASS |
| video05 | 酸菜；`2026-06-12`；`2027-06-11` | PASS |

## 视觉证据

- 新视频日期和保质期：`docs/qa/screenshots/2026-07-14-v039-new-video-date-shelf-pass.png`
- 新增表单且未勾选无过期：`docs/qa/screenshots/2026-07-14-v039-form-prefill-no-expiry-unchecked.png`
- 保存与删除：`docs/qa/screenshots/2026-07-14-v039-video-item-saved.png`、`docs/qa/screenshots/2026-07-14-v039-video-item-deleted.png`
- 浅色激光日期回归：`docs/qa/screenshots/2026-07-14-v039-video05-laser-date-pass.png`
- Excel 导出、预览、问题详情和覆盖完成：`docs/qa/screenshots/2026-07-14-v039-excel-export-picker.png`、`docs/qa/screenshots/2026-07-14-v039-excel-import-preview.png`、`docs/qa/screenshots/2026-07-14-v039-excel-import-details.png`、`docs/qa/screenshots/2026-07-14-v039-excel-overwrite-complete.png`

## 剩余风险

- 商品名来自包装文字候选；新样本只拍摄背面，名称候选仍包含营养表或规格噪声。条码、日期和保质期不受该名称噪声影响，保存前仍由用户选择并确认。
- 真实相机效果仍受镜头对焦、曝光、运动模糊和文字像素大小影响。真机应让日期标签占识别框较大面积，并点按标签触发对焦。
