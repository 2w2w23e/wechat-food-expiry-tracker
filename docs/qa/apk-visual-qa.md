# APK 视觉 QA 记录

日期：2026-07-05

## 1. 当前结论

总体结论：PARTIAL

已解除的阻塞：

- 已创建并启动 `Medium_Phone` AVD。
- `adb devices` 可识别 `emulator-5554`。
- 已从干净 PR worktree 构建并安装 `shiqi-android-debug.apk`。
- 编辑食品表单已取得稳定截图证据，保存后可回到列表。
- 条码流程已取得手动输入、查询结果、手动新增表单和条码备注预填证据。
- Excel 导入已取得首页入口、系统文件选择器、导入预览、确认追加和导入后食品卡片证据。

仍未完成：

- 真实摄像头扫码识别仍需真机或稳定摄像头模拟器复测；本轮只验证到权限弹窗、拒绝相机权限后的手动输入兜底路径。
- 条码图库识别和完整新增保存仍需后续真机 / 样本图片场景 QA。
- Excel 覆盖导入和错误行视觉预览仍是后续任务；当前已验证追加导入。

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
| VIS-007 | 条码流程 | 相机扫码、图库识别、手动输入、查询结果、用户确认后保存 | PARTIAL | `docs/qa/screenshots/2026-07-05-vis-007-after-scan-tap.png`, `docs/qa/screenshots/2026-07-05-vis-007-camera-denied-manual-available.png`, `docs/qa/screenshots/2026-07-05-vis-007-manual-input-filled.png`, `docs/qa/screenshots/2026-07-05-vis-007-manual-query-result.png`, `docs/qa/screenshots/2026-07-05-vis-007-manual-add-notes-barcode.png`; 手动输入、查询失败兜底、手动新增表单和条码备注预填可见，真实相机扫码 / 图库识别 / 完整保存未完成 |
| VIS-008 | 升级回归 | 安装覆盖升级后旧食品数据仍在、旧 JSON 字段兼容、新字段默认值正确 | PARTIAL | 覆盖安装后 `QA_Milk` 仍显示；Gradle 覆盖安装 smoke 见 `docs/qa/screenshots/gradle-build-home-smoke.png`；旧 schema 迁移由 JVM 测试覆盖，未做视觉旧数据注入 |
| VIS-009 | Excel 导出 | 首页导出按钮、系统文件保存器、导出完成提示、导出文件包含 foods / README sheet | PASS | `docs/qa/screenshots/2026-07-05-xlsx-001-home-export-button.png`, `docs/qa/screenshots/2026-07-05-xlsx-001-system-save-picker.png`, `docs/qa/screenshots/2026-07-05-xlsx-001-export-complete.png`; 实际导出的 `.xlsx` 拉到 `.local/qa/` 后确认包含 `QA_Milk`、`expiryDate` header 和 README sheet |
| VIS-010 | Excel 导入 | 首页导入按钮、系统文件选择器、导入预览、确认前不写入、确认后追加到列表 | PASS | `docs/qa/screenshots/2026-07-05-xlsx-002-home-import-button.png`, `docs/qa/screenshots/2026-07-05-xlsx-002-picker-file-visible.png`, `docs/qa/screenshots/2026-07-05-xlsx-002-import-preview-ascii-confirm.png`, `docs/qa/screenshots/2026-07-05-xlsx-002-import-complete-ascii.png`, `docs/qa/screenshots/2026-07-05-xlsx-002-imported-food-card-ascii.png`; `Import_Banana` 通过预览后追加导入，列表统计从 2 件变 3 件 |

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

- 首页新增“视频识别日期”入口可见，证据：`docs/qa/screenshots/2026-07-05-ocr-002-home-entry-wait.png`。
- OCR 页面可达，顶部标题、返回按钮、识别引导、候选区、原始 OCR 区、手动输入和禁用态“使用候选”按钮可见。
- 相机权限弹窗可见，证据：`docs/qa/screenshots/2026-07-05-ocr-002-permission-dialog.png`。

未完成：

- 点击 Allow 后，本机 `Medium_Phone` AVD 从 adb 断开，重启 adb server 和冷启动 AVD 后仍未重新注册设备；因此没有取得允许权限后的实时预览、动态候选和“使用候选”可点击态截图。
- 真实包装生产日期识别准确率仍需基于 `video/` 样本、真机或稳定摄像头模拟器继续回归。
