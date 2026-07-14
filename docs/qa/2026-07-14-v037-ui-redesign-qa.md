# APK V0.3.7 界面改版验收

## 目标

在不改变食品数据、到期日计算、Excel、提醒、条码和 OCR 业务逻辑的前提下，降低首页、食品表单和识别页的视觉复杂度，并保证窄屏可用。

## 自动化检查

- `./gradlew.bat testDebugUnitTest assembleDebug`：PASS。
- `./apk/run-local-tests.ps1`：PASS，74 项通过，0 项失败。
- `git diff --check`：PASS。

## 视觉验收

| 场景 | 视口 | 结果 | 证据 |
| --- | --- | --- | --- |
| 首页默认态 | 720 x 1280 | PASS，主次操作、提醒、搜索和空状态无重叠 | `screenshots/2026-07-14-v037-home-720x1280.png` |
| 高级筛选展开 | 720 x 1280 | PASS，状态改为横向紧凑选择，分类和位置按需展开 | `screenshots/2026-07-14-v037-filter-720x1280.png` |
| 新增食品表单 | 720 x 1280 | PASS，基本信息和日期优先，低频字段折叠，无按钮遮挡 | `screenshots/2026-07-14-v037-form-720x1280.png` |
| 食品卡片 | 720 x 1280 | PASS，只保留“减少 1”和“更多操作”两个直接动作 | `screenshots/2026-07-14-v037-food-card-720x1280.png` |
| 智能识别页 | 720 x 1280 | PASS，预览区、结果区、来源切换和填表按钮完整可见 | `screenshots/2026-07-14-v037-scan-720x1280.png` |
| 首页与识别页 | 1080 x 2400 | PASS，常规手机视口无重叠或裁切 | `.local/qa-v037/` 本地证据 |

## 用户流程

在 AVD 上按真实点击流程完成：

1. 手动新增 `QA_MILK`，选择“无过期时间”并保存，首页显示在库 1 件。
2. 从食品详情进入编辑，将名称改为 `QA_Milk_EDIT` 并保存。
3. 从“更多操作”执行删除并确认，首页恢复空状态。
4. 在 V0.3.6 新增 `UpgradeProbe`，使用 `adb install -r` 覆盖升级到 V0.3.7；升级后仍显示在库 1 件和 `UpgradeProbe`。

覆盖升级证据：`screenshots/2026-07-14-v037-upgrade-preserved-720x1280.png`。

## 结论

PASS。改版后的主要页面和完整增改删流程均通过，V0.3.6 到 V0.3.7 的本地食品数据保留。此次只调整界面层级和样式，没有更改 OCR 模型、食品 JSON schema 或存储键。
