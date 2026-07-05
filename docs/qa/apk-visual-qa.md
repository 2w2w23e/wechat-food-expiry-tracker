# APK 视觉 QA 记录

日期：2026-07-05

## 1. 当前结论

总体结论：PARTIAL

已解除的阻塞：

- 已创建并启动 `Medium_Phone` AVD。
- `adb devices` 可识别 `emulator-5554`。
- 已从干净 PR worktree 构建并安装 `shiqi-android-debug.apk`。

仍未完成：

- 摄像头扫码页在允许权限后会让 `adb screencap` 卡住，本轮只保留入口和权限弹窗证据，真实扫码识别仍需真机或稳定摄像头模拟器复测。
- 编辑食品表单未取得稳定截图证据，本轮不标 PASS。
- 条码图库识别、手动输入、商品查询、确认保存仍需后续真机场景 QA。

说明：本文件只记录视觉检验状态。代码编译、单元逻辑测试和 APK 构建通过不等于视觉 QA 通过。

## 2. 必测路径

| ID | 页面 / 路径 | 必须截图或录屏的状态 | 当前状态 | 证据 |
| --- | --- | --- | --- | --- |
| VIS-001 | 首页列表 | 普通食品卡片、临期 / 今日 / 已过期状态、位置显示、快速消耗 / 补货按钮 | PASS | `docs/qa/screenshots/2026-07-05-vis-001-home.png`, `docs/qa/screenshots/2026-07-05-vis-001-food-card-actions-fixed.png` |
| VIS-002 | 首页筛选 | 状态、分类、位置筛选组合；无结果空态 | PASS | `docs/qa/screenshots/2026-07-05-vis-001-home.png`, `docs/qa/screenshots/2026-07-05-vis-001-food-card-actions-fixed.png` |
| VIS-003 | 新增食品 | 手动到期日模式、生产日期 + 保质期模式、位置字段、开封信息字段 | PASS | `docs/qa/screenshots/2026-07-05-vis-003-add-food.png`, `docs/qa/screenshots/2026-07-05-vis-003-add-food-date-section.png`, `docs/qa/screenshots/2026-07-05-vis-003-date-picker-production.png` |
| VIS-004 | 编辑食品 | 旧数据默认值、位置编辑、开封后保质期编辑、保存后回到列表 | BLOCKED | ADB 在后续截图阶段卡住，未取得稳定编辑截图证据 |
| VIS-005 | 食品详情 | 详情展示、更多操作、剩余归 0、补货、复制食品、标记已用完 | PASS | `docs/qa/screenshots/2026-07-05-vis-005-detail-actions.png`, `docs/qa/screenshots/2026-07-05-vis-005-more-actions.png` |
| VIS-006 | 提醒设置 | 全局提醒开关、提前天数、今日提醒时间段、关闭提醒后的文案 | PASS | `docs/qa/screenshots/2026-07-05-vis-006-reminder-settings.png` |
| VIS-007 | 条码流程 | 相机扫码、图库识别、手动输入、查询结果、用户确认后保存 | PARTIAL | `docs/qa/screenshots/2026-07-05-vis-007-camera-permission.png`; 入口和权限弹窗可见，真实扫码 / 图库 / 手动输入未完成 |
| VIS-008 | 升级回归 | 安装覆盖升级后旧食品数据仍在、旧 JSON 字段兼容、新字段默认值正确 | PARTIAL | 覆盖安装后 `QA_Milk` 仍显示；Gradle 覆盖安装 smoke 见 `docs/qa/screenshots/gradle-build-home-smoke.png`；旧 schema 迁移由 JVM 测试覆盖，未做视觉旧数据注入 |
| VIS-009 | Excel 导出 | 首页导出按钮、系统文件保存器、导出完成提示、导出文件包含 foods / README sheet | PASS | `docs/qa/screenshots/2026-07-05-xlsx-001-home-export-button.png`, `docs/qa/screenshots/2026-07-05-xlsx-001-system-save-picker.png`, `docs/qa/screenshots/2026-07-05-xlsx-001-export-complete.png`; 实际导出的 `.xlsx` 拉到 `.local/qa/` 后确认包含 `QA_Milk`、`expiryDate` header 和 README sheet |

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
