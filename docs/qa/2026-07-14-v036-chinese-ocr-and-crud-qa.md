# 2026-07-14 APK V0.3.6 中文识别与 CRUD 验收

## 结论

- 固定视频样本验收：PASS。6 个样本都能自动得到画面中可核对的核心商品信息，不依赖手工输入商品名。
- 激光喷码专项：PASS。video05 自动得到商品名“酸菜”、生产日期 `2026-06-12` 和最终可食用日期 `2027-06-11`。
- 用户完整流程：PASS。已完成“视频识别 -> 候选确认 -> 表单预填 -> 保存 -> 覆盖安装保留 -> 编辑 -> 删除”的模拟器视觉验收。
- 泛化准确率：PASS_WITH_LIMITS。固定样本已经通过，但不能据此保证任意包装、反光、模糊或遮挡条件下都能识别全部字段。

## 自动化与构建

- `apk/run-local-tests.ps1`：74 passed，0 failed。
- `:apk:app:assembleDebug`：BUILD SUCCESSFUL。
- `adb install -r`：成功；安装版本为 `versionName 0.3.6`、`versionCode 9`。
- `git diff --check`：exit 0。

## 视频样本

| 样本 | 可核对的自动结果 | 结果 |
| --- | --- | --- |
| video01 | 商品码 `6971416494254`；“炸酱面”候选，公开商品查询曾返回“大董老北京炸酱面” | PASS |
| video02 | “清水鹌鹑蛋” | PASS |
| video03 | “果汁”“茶饮料” | PASS |
| video04 | 商品码 `6930628889364`；公开商品查询曾返回“窖藏红腐乳” | PASS |
| video05 | “酸菜”；生产 `2026-06-12`；最终可食用 `2027-06-11` | PASS |
| video06 | “喝开水”；商品码 `6920459940310` | PASS |

video01-video06 的整批候选截图和 UI XML 保存在本地 `.local/qa-v036/acceptance-v036/`。最终决策层修复后又对最难的 video05、video06 做了定向回归。

## 视觉证据

- video05 激光日期：`docs/qa/screenshots/2026-07-14-v036-video05-laser-date-pass.png`
- video06 中文名与条码：`docs/qa/screenshots/2026-07-14-v036-video06-name-barcode-pass.png`
- 识别结果预填日期：`docs/qa/screenshots/2026-07-14-v036-crud-prefill-dates.png`
- 新增后商品卡：`docs/qa/screenshots/2026-07-14-v036-crud-added.png`
- 覆盖安装后数据仍在：`docs/qa/screenshots/2026-07-14-v036-upgrade-preserved.png`
- 编辑数量为 2/2：`docs/qa/screenshots/2026-07-14-v036-crud-edited.png`
- 删除确认：`docs/qa/screenshots/2026-07-14-v036-crud-delete-confirm.png`
- 删除后在库 0 件：`docs/qa/screenshots/2026-07-14-v036-crud-deleted.png`

## 已验证规则

- 条码、中文/拉丁包装文字和日期在同一识别流程中合并。
- 最多展示三个稳定候选，完成时优先选择规范食品名，不让营销口号或录屏 UI 抢占导入结果。
- 无提示的单个日期不会被同时伪造为生产日期和到期日。
- `YYYYMMDD/YYYYMMDD` 与漏掉分隔符的连续双日期会按时间顺序映射为生产日期和最终可食用日期。
- OCR 只预填新增表单；必须由用户点击“保存”后才写入食品列表。
- 覆盖安装不修改本地存储键和 JSON schema，`adb install -r` 后已保存商品仍存在。

## 技术选择

- 主链路继续使用离线 bundled ML Kit Chinese + Latin OCR。
- OpenCV CLAHE 与锐化用于浅色纸盒上的低对比激光喷码；短视频加密采样，长录屏采用受控采样和并行识别任务。
- PP-OCRv5 Mobile 已做桌面对比准备，但当前 PaddleOCR 3.4.1 / PaddlePaddle 3.3.1 组合发生 PIR/oneDNN 运行时兼容错误，因此没有把未经 Android 验证的第二套原生推理运行时塞进本版 APK。

## 剩余风险

- video03 的 `BLUE` 品牌字样和 video04 的艺术字体商品名仍不如条码/食品类别稳定。
- video06 是包含旧版 App UI 的屏幕录制，当前清洗规则能保留“喝开水”和条码，但原始相机视频会更适合继续评估喷码日期。
- 公开商品查询可能遇到限流；条码锁定和本地 OCR 不依赖查询成功。
