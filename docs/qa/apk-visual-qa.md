# APK 视觉 QA 记录

日期：2026-07-05 / 2026-07-06

## 1. 当前结论

总体结论：本轮范围 PASS；全量真实相机范围仍 PARTIAL

已解除的阻塞：

- 已创建并启动 `Medium_Phone` AVD。
- `adb devices` 可识别 `emulator-5554`。
- 已从干净 PR worktree 构建并安装 `shiqi-android-debug.apk`。
- 编辑食品表单已取得稳定截图证据，保存后可回到列表。
- 条码流程已取得手动输入、查询结果、手动新增表单和条码备注预填证据。
- Excel 导入已取得首页入口、系统文件选择器、导入预览、确认追加和导入后食品卡片证据。
- 2026-07-06 已补齐视频模拟相机 OCR 入口、视频选择、抽帧预览、动态 raw OCR 和候选稳定性提示。
- 2026-07-06 已补齐 Excel 覆盖导入、错误行详情、覆盖确认、导入成功结果和 debug 强制失败回滚视觉证据。
- 2026-07-06 已补齐条码图库识别、GS1 QR 提取、无商品信息兜底、手动新增确认保存和保存后卡片视觉证据。
- 2026-07-06 已补齐统一智能识别：首页单一入口、视频模拟预览、图片条码锁定、确认弹窗和新增表单预填视觉证据。

仍未完成 / 本轮不包含：

- 真实摄像头扫码识别和真实摄像头 OCR 实时预览按用户 2026-07-06 最新要求先不做。
- OCR 对真实包装生产日期的准确率仍需更大样本和更强 OCR / 预处理方案继续验证；本轮只证明视频模拟实时识别链路可运行，不能声称准确率已充分达标。

说明：本文件只记录视觉检验状态。代码编译、单元逻辑测试和 APK 构建通过不等于视觉 QA 通过。

## 2. 必测路径

| ID | 页面 / 路径 | 必须截图或录屏的状态 | 当前状态 | 证据 |
| --- | --- | --- | --- | --- |
| VIS-001 | 首页列表 | 普通食品卡片、临期 / 今日 / 已过期状态、位置显示、快速消耗 / 补货按钮 | PASS | `docs/qa/screenshots/2026-07-05-vis-001-home.png`, `docs/qa/screenshots/2026-07-05-vis-001-food-card-actions-fixed.png` |
| VIS-002 | 首页筛选 | 状态、分类、位置筛选组合；无结果空态 | PASS | `docs/qa/screenshots/2026-07-05-vis-001-home.png`, `docs/qa/screenshots/2026-07-05-vis-001-food-card-actions-fixed.png` |
| VIS-003 | 新增食品 | 手动到期日模式、生产日期 + 保质期模式、位置字段、开封信息字段 | PASS | `docs/qa/screenshots/2026-07-05-vis-003-add-food.png`, `docs/qa/screenshots/2026-07-05-vis-003-add-food-date-section.png`, `docs/qa/screenshots/2026-07-05-vis-003-date-picker-production.png` |
| VIS-004 | 编辑食品 | 旧数据默认值、位置编辑、开封后保质期编辑、保存后回到列表 | PASS | `docs/qa/screenshots/2026-07-05-vis-004-edit-food-form.png`, `docs/qa/screenshots/2026-07-05-vis-004-edit-save-return.png` |
| VIS-005 | 食品详情 | 详情展示、更多操作、剩余归 0、补货、复制食品、标记已用完 | PASS | `docs/qa/screenshots/2026-07-05-vis-005-detail-actions.png`, `docs/qa/screenshots/2026-07-05-vis-005-more-actions.png` |
| VIS-006 | 提醒设置 | 全局提醒开关、提前天数、今日提醒时间段、关闭提醒后的文案 | PASS | `docs/qa/screenshots/2026-07-05-vis-006-reminder-settings.png` |
| VIS-007 | 条码流程 | 相机扫码、图库识别、手动输入、查询结果、用户确认后保存 | PASS（图库/手动确认保存）；真实相机 N/A | `docs/qa/screenshots/2026-07-05-vis-007-after-scan-tap.png`, `docs/qa/screenshots/2026-07-05-vis-007-camera-denied-manual-available.png`, `docs/qa/screenshots/2026-07-05-vis-007-manual-input-filled.png`, `docs/qa/screenshots/2026-07-05-vis-007-manual-query-result.png`, `docs/qa/screenshots/2026-07-05-vis-007-manual-add-notes-barcode.png`, `docs/qa/screenshots/2026-07-06-barcode-retry-picker.png`, `docs/qa/screenshots/2026-07-06-barcode-retry-result.png`, `docs/qa/screenshots/2026-07-06-barcode-retry-form-prefill.png`, `docs/qa/screenshots/2026-07-06-barcode-retry-saved-home.png`, `docs/qa/screenshots/2026-07-06-barcode-retry-saved-card.png`; 本轮不做真实相机 |
| VIS-008 | 升级回归 | 安装覆盖升级后旧食品数据仍在、旧 JSON 字段兼容、新字段默认值正确 | PASS_WITH_LIMITS | 覆盖安装后 `QA_Milk` 仍显示；Gradle 覆盖安装 smoke 见 `docs/qa/screenshots/gradle-build-home-smoke.png`；2026-07-06 使用最终 `0.3.2` APK 覆盖安装后，首页仍显示 `在库 2 件` 和既有食品简报，证据：`docs/qa/screenshots/2026-07-06-final-upgrade-home.png`；旧 schema 迁移由 JVM 测试覆盖 |
| VIS-009 | Excel 导出 | 首页导出按钮、系统文件保存器、导出完成提示、导出文件包含 foods / README sheet | PASS | `docs/qa/screenshots/2026-07-05-xlsx-001-home-export-button.png`, `docs/qa/screenshots/2026-07-05-xlsx-001-system-save-picker.png`, `docs/qa/screenshots/2026-07-05-xlsx-001-export-complete.png`; 实际导出的 `.xlsx` 拉到 `.local/qa/` 后确认包含 `QA_Milk`、`expiryDate` header 和 README sheet |
| VIS-010 | Excel 导入 | 首页导入按钮、系统文件选择器、导入预览、确认前不写入、追加/覆盖确认、错误行详情、失败回滚 | PASS | `docs/qa/screenshots/2026-07-05-xlsx-002-home-import-button.png`, `docs/qa/screenshots/2026-07-05-xlsx-002-picker-file-visible.png`, `docs/qa/screenshots/2026-07-05-xlsx-002-import-preview-ascii-confirm.png`, `docs/qa/screenshots/2026-07-05-xlsx-002-import-complete-ascii.png`, `docs/qa/screenshots/2026-07-05-xlsx-002-imported-food-card-ascii.png`, `docs/qa/screenshots/2026-07-06-excel-import-preview-overwrite-errors.png`, `docs/qa/screenshots/2026-07-06-excel-import-error-details.png`, `docs/qa/screenshots/2026-07-06-excel-import-overwrite-confirm.png`, `docs/qa/screenshots/2026-07-06-excel-import-overwrite-result.png`, `docs/qa/screenshots/2026-07-06-excel-forced-failure-toast.png`, `docs/qa/screenshots/2026-07-06-excel-forced-failure-home-after.png`; 失败回滚使用 debug-only ADB 开关强制下一次导入保存失败，首页仍保持 2 件 |
| VIS-011 | 统一智能识别 | 首页单一入口、视频模拟预览、图片条码锁定、确认弹窗、只进入新增表单不自动保存 | PASS_WITH_LIMITS | `docs/qa/screenshots/2026-07-06-unified-recognition-home.png`, `docs/qa/screenshots/2026-07-06-unified-recognition-video-replay.png`, `docs/qa/screenshots/2026-07-06-unified-recognition-image-barcode.png`, `docs/qa/screenshots/2026-07-06-unified-recognition-confirm-dialog.png`, `docs/qa/screenshots/2026-07-06-unified-recognition-form-prefill.png`; 视频样本链路可运行但该样本未形成稳定日期候选，真实包装准确率仍需扩大样本回归 |

## 3. 本轮视觉发现与修复

- 发现：滚动到食品卡片时，右下角圆形 `↑` 浮动按钮遮挡食品卡片右侧操作按钮。
- 修复：禁用该浮动按钮显示，保留正常手动滚动。
- 复测：`docs/qa/screenshots/2026-07-05-vis-001-food-card-actions-fixed.png` 中食品卡片操作区不再被遮挡。

## 4. 执行命令

```powershell
$env:JAVA_HOME='D:/Program Files/Android/Android Studio/jbr'
$env:Path="$env:JAVA_HOME/bin;C:/Users/xinyu.wang/AppData/Local/Android/Sdk/platform-tools;$env:Path"
$env:ANDROID_HOME='C:/Users/xinyu.wang/AppData/Local/Android/Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME

powershell -ExecutionPolicy Bypass -File apk/build-apk.ps1
adb devices
adb install -r apk/build/outputs/apk/shiqi-android-debug.apk
adb shell am start -n com.shiqi.expirytracker/.MainActivity

.\gradlew.bat :apk:app:assembleDebug
adb install -r apk/app/build/outputs/apk/debug/app-debug.apk
adb shell input tap 540 1267
```

截图证据建议保存到 `docs/qa/screenshots/`，文件名使用：

```text
2026-07-05-vis-001-home-list.png
2026-07-05-vis-002-filters.png
2026-07-05-vis-003-add-food.png
2026-07-05-vis-004-edit-food.png
2026-07-05-vis-005-detail-actions.png
2026-07-05-vis-006-reminder-settings.png
2026-07-05-vis-007-barcode-confirm.png
2026-07-05-vis-008-upgrade-data.png
```

## 5. 判定口径

| 结果 | 含义 |
| --- | --- |
| PASS | 所有必测路径都有截图或录屏证据，布局无明显遮挡、溢出、错位、乱码或不可点击问题，关键操作按预期保存数据 |
| PARTIAL | 主要路径可用，但部分低频路径、设备型号或权限场景未覆盖；必须列出遗漏 |
| BLOCKED | 缺少设备、模拟器、权限、样本、安装包或截图证据，无法完成视觉检验 |
| FAIL | 发现崩溃、布局不可用、中文乱码、数据丢失、自动保存识别结果、误触发云能力或其他关键缺陷 |

## 6. OCR-002 视觉记录

日期：2026-07-05

结论：PARTIAL

已取得证据：

- 当时首页新增“识别包装文字”入口可见；该旧入口已在 2026-07-06 并入“智能识别”，历史证据：`docs/qa/screenshots/2026-07-05-ocr-002-home-packaging-entry-current.png`。
- OCR 页面可达；无可用相机时顶部标题、返回按钮、失败提示、候选区、原始 OCR 区、手动输入和禁用态“使用候选”按钮可见，证据：`docs/qa/screenshots/2026-07-05-ocr-002-no-camera-fallback-current.png`。
- 相机权限弹窗可见，证据：`docs/qa/screenshots/2026-07-05-ocr-002-permission-dialog.png`。

未完成：

- 本机 `Medium_Phone` AVD 使用默认摄像头后曾在 Allow 后出现相机/adb 不稳定；改用无摄像头启动后已验证 OCR 降级态，但仍没有取得允许权限后的实时预览、动态候选、“使用候选”可点击态和候选回填表单截图。
- 真实包装生产日期识别准确率仍需基于 `video/` 样本、真机或稳定摄像头模拟器继续回归。

## 7. 2026-07-06 视频模拟 OCR 视觉记录

结论：链路 PASS；识别准确率 PARTIAL

本轮按“先不做真实相机”的范围，用 `video/` 样本模拟相机实时识别：

- 页面进入后不再立即请求相机权限，可选择“视频”或“相机”，证据：`docs/qa/screenshots/2026-07-06-video-ocr-input-choice.png`。
- 系统文件选择器可选择 `/sdcard/Download/ocr_sample_1.mp4`，证据：`docs/qa/screenshots/2026-07-06-video-ocr-picker.png`。
- 视频抽帧会显示在识别框内，并持续刷新 raw OCR，证据：`docs/qa/screenshots/2026-07-06-video-ocr-replay-running.png`、`docs/qa/screenshots/2026-07-06-video-ocr-replay-final.png`。
- 多帧投票没有得到稳定生产日期候选时，“使用候选”保持禁用，页面提示可换更清晰视频或手动输入；没有自动保存 OCR 结果。

边界：

- 本轮证明视频模拟相机实时识别链路可运行，且遵守候选确认规则。
- 当前 ML Kit 对样本 1 的 raw OCR 噪声较大，未形成稳定生产日期候选，因此不能声明真实包装生产日期识别准确率已经充分完成。
- 真实相机实时预览和真实摄像头 OCR 按用户 2026-07-06 最新要求先不做。
