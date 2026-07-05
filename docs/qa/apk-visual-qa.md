# APK 视觉 QA 记录

日期：2026-07-05

## 1. 当前结论

总体结论：BLOCKED

阻塞原因：

- 当前机器 `adb devices` 未发现 Android 设备。
- `emulator -list-avds` 未发现可启动模拟器。
- Android SDK 当前缺少 `cmdline-tools`、`avdmanager` 和系统镜像，不能在本轮直接创建 AVD。

说明：本文件只记录视觉检验状态。代码编译、单元逻辑测试和 APK 构建通过不等于视觉 QA 通过。

## 2. 必测路径

| ID | 页面 / 路径 | 必须截图或录屏的状态 | 当前状态 | 证据 |
| --- | --- | --- | --- | --- |
| VIS-001 | 首页列表 | 普通食品卡片、临期 / 今日 / 已过期状态、位置显示、快速消耗 / 补货按钮 | BLOCKED | 缺少设备 / 模拟器 |
| VIS-002 | 首页筛选 | 状态、分类、位置筛选组合；无结果空态 | BLOCKED | 缺少设备 / 模拟器 |
| VIS-003 | 新增食品 | 手动到期日模式、生产日期 + 保质期模式、位置字段、开封信息字段 | BLOCKED | 缺少设备 / 模拟器 |
| VIS-004 | 编辑食品 | 旧数据默认值、位置编辑、开封后保质期编辑、保存后回到列表 | BLOCKED | 缺少设备 / 模拟器 |
| VIS-005 | 食品详情 | 详情展示、更多操作、剩余归 0、补货、复制食品、标记已用完 | BLOCKED | 缺少设备 / 模拟器 |
| VIS-006 | 提醒设置 | 全局提醒开关、提前天数、今日提醒时间段、关闭提醒后的文案 | BLOCKED | 缺少设备 / 模拟器 |
| VIS-007 | 条码流程 | 相机扫码、图库识别、手动输入、查询结果、用户确认后保存 | BLOCKED | 缺少设备 / 模拟器 |
| VIS-008 | 升级回归 | 安装覆盖升级后旧食品数据仍在、旧 JSON 字段兼容、新字段默认值正确 | BLOCKED | 缺少设备 / 模拟器 |

## 3. 解除阻塞后的执行命令

```powershell
$env:JAVA_HOME='D:/Program Files/Android/Android Studio/jbr'
$env:Path="$env:JAVA_HOME/bin;C:/Users/xinyu.wang/AppData/Local/Android/Sdk/platform-tools;$env:Path"
$env:ANDROID_HOME='C:/Users/xinyu.wang/AppData/Local/Android/Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME

powershell -ExecutionPolicy Bypass -File apk/build-apk.ps1
adb devices
adb install -r apk/build/outputs/apk/shiqi-android-debug.apk
adb shell am start -n com.shiqi.expirytracker/.MainActivity
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

## 4. 判定口径

| 结果 | 含义 |
| --- | --- |
| PASS | 所有必测路径都有截图或录屏证据，布局无明显遮挡、溢出、错位、乱码或不可点击问题，关键操作按预期保存数据 |
| PARTIAL | 主要路径可用，但部分低频路径、设备型号或权限场景未覆盖；必须列出遗漏 |
| BLOCKED | 缺少设备、模拟器、权限、样本、安装包或截图证据，无法完成视觉检验 |
| FAIL | 发现崩溃、布局不可用、中文乱码、数据丢失、自动保存识别结果、误触发云能力或其他关键缺陷 |
