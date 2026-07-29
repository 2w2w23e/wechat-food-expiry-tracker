# 2026-07-29 Android V2 B1 导航与首页验收

## 范围

本轮只验收 V2 任务 B1：总览、库存、扫描入口、设置四目的地，以及既有本地库存操作在新外壳中的可达性。OCR 模型、实时相机流水线和 Room 迁移不在 B1 代码范围内。

## 自动化与构建

结论：`PASS`

- 本地 Java 逻辑测试：203 项通过，0 项失败。
- Gradle：`:apk:app:assembleDebug` 构建成功。
- Android Lint：`:apk:app:lintDebug` 通过。
- 安装包：`versionName 0.4.0`、`versionCode 30`、220602722 bytes。
- APK SHA-256：`11CF69BE5BA241D742F08D0D6B725773B71E6348D372EC3200F522794F88A8B0`。
- 模拟器实际安装版本：0.4.0 (30)。

## 升级与本地数据

结论：`PASS`

- 从已安装的 0.3.18 (21) 使用 `adb install -r` 覆盖安装 0.4.0 (30)，返回 `Success`。
- 覆盖安装前后主 SharedPreferences 均为 8501 bytes，SHA-256 均为 `47314D9268C685C4F8ED283D0E8A3D01E9245495725A2121940513848BCD57AA`。
- 升级前已有的“升级保留测试牛奶”在新库存页可见。

## 用户流程

结论：`PASS`

按真实用户顺序完成：

1. 手动新增 `QA Milk`，数量 3，最终日期 2026-07-29。
2. 编辑为 `QA Milk Edited`。
3. 在库存卡片外层执行“减少 1”，剩余由 3/3 变为 2/3。
4. 执行“补货”，确认表单存在“相机识别本批次日期”，再保存数量 1 的独立批次。
5. 两个同名批次分别显示 1/1 与 2/3，旧批次没有被覆盖。
6. 标记一个批次已用完，再通过状态筛选找到并“恢复在库”。
7. 搜索 `QA` 返回 2 项，原有牛奶不出现在搜索结果中。
8. 删除两个 QA 批次，最终恢复为 1 个原有在库批次。
9. 在库存页执行竖屏到横屏再回竖屏的 Activity 重建，页面仍恢复到库存目的地。

流程中首次发现筛选后滑动会因已销毁搜索框引用触发 `NullPointerException`。增加空焦点保护后，按同一路径复测，进程保持前台且不再崩溃。

## Excel 与 JSON

结论：`PASS`

- 实际导出 `shiqi-foods-20260729-1311.xlsx` 到系统 Downloads，文件大小 4507 bytes。
- 再从系统文件选择器回读该文件；导入预览显示总行数 1、可导入 1、错误行 0、警告行 0。
- 在确认写入前取消，因此库存数据未被重复导入。
- JSON 快速换机页显示入库时间、生产日期、最终日期三个闭区间筛选。
- “直接分享”实际打开 Android `ChooserActivity`。AVD 未安装 QQ/微信，因此不声称已验证具体接收应用。

## 视觉检查

结论：`PASS`

逐张人工检查，无横向滚动、控件重叠、文字截断或小于 48 dp 的导航触点：

- 额外将系统 `font_scale` 调至 1.3，在 360x800 总览页复查后仍无重叠或按钮文字截断；检查后恢复为 1.0。

- `screenshots/redesign-v2-b1/home-360x640.png`
- `screenshots/redesign-v2-b1/home-412x915.png`
- `screenshots/redesign-v2-b1/home-720x1280.png`
- `screenshots/redesign-v2-b1/home-1080x2400.png`
- `screenshots/redesign-v2-b1/inventory-360x800.png`
- `screenshots/redesign-v2-b1/settings-360x800.png`

总览主扫描按钮的图标与文字改为同一居中文本内容，避免平台 Button 将图标贴到左边。库存统计在 360 dp 宽度下保持单行，“减少 1”“补货”“更多”均直接可见。

## 扫描与 OCR 边界

结论：`PARTIAL`

- 扫描入口仍调用既有 `DateOcrScanActivity`，B1 没有移除或改写识别能力。
- 当前 AVD 在打开 CameraX 虚拟相机后会使 `adb`/截图链路阻塞，无法形成可信的实时预览视觉证据。
- 2026-07-29 失败录屏已复现：旧链路能读到药品名、用途和条码，但商品名被食品语义门控拒绝，日期字段也没有稳定晋升到确认表单。
- 因此 B1 新外壳可通过；完整 V2 目标和 OCR/真实相机识别仍不得标记完成。
