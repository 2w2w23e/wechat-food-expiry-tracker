# 食期管家 V0.4.8 纯手动输入全流程体验审计

## 1. 结论

**发布判断：FAIL。**

本轮不测试视频识别，也不把代码推断当成通过。审计在全新 Android 10 AVD 上，从空库开始，仅使用应用可见界面完成手动新增、日期计算、库存管理、提醒、Excel、JSON、备份恢复和重启持久化，并逐张检查关键截图。

主要业务链路能够运行，但仍存在会造成库存失真、错误日期进入数据库、数据安全承诺未兑现和操作结果难以理解的问题。在以下 P1 问题修复前，不建议把当前 APK 作为普通用户长期保存真实库存的正式版本。

## 2. 环境与边界

- 候选提交：`0ca455f fix(apk): make OCR review editable and debuggable`
- 应用：食期管家 `0.4.8` / Android 本地版
- 设备：`Shiqi_QA`，Android 10 / API 29，1080 x 2400
- 系统日期：2026-08-12
- 数据起点：清空应用数据后的空库
- 本轮明确排除：视频识别、真实相机、真实 QQ/微信接收端
- 分享只验证到 Android Sharesheet；AVD 仅安装 Drive/Gmail，因此 QQ/微信为 `PARTIAL`
- 截图与 UI 层级证据：`docs/qa/screenshots/2026-08-12-manual-entry-audit/`

## 3. 确定问题

### P1-01 Excel 覆盖导入没有兑现“覆盖前自动备份”承诺

**类型：确定 Bug / 数据安全。**

覆盖确认明确写着“覆盖前会保留最近备份”，但用 4 条 Excel 数据覆盖本机 8 条数据后，备份页仍显示“尚无通过长度、哈希和数据格式校验的备份”。只有随后手动点击“创建当前备份”，才出现第一份可恢复备份。

- 覆盖承诺：[71-excel-overwrite-confirm.png](screenshots/2026-08-12-manual-entry-audit/71-excel-overwrite-confirm.png)
- 覆盖后仍无备份：[73-backup-page.png](screenshots/2026-08-12-manual-entry-audit/73-backup-page.png)
- 代码路径 `FoodStore.saveFoodsForImport()` 与普通保存相同，没有建立 `RoomMigrationBackup`

**影响：** 用户相信可以回退，但覆盖成功后没有可恢复版本，直接违反“数据不能丢”的核心要求。

### P1-02 手动新增允许未来生产日期，并允许 0 总数量以“正常在库”保存

**类型：确定 Bug / 数据完整性。**

以系统日 2026-08-12 新增 `ZeroFuture`：总数量 0、剩余 0、生产日期 2027-08-12、保质期 1 天。应用成功保存，并把它显示为“正常”、1 个在库批次、最早到期 2027-08-13。

- 保存前字段：[86-future-zero-ready.png](screenshots/2026-08-12-manual-entry-audit/86-future-zero-ready.png)
- 保存后总览：[87-future-zero-saved.png](screenshots/2026-08-12-manual-entry-audit/87-future-zero-saved.png)
- 库存状态：[88-inventory-future-zero.png](screenshots/2026-08-12-manual-entry-audit/88-inventory-future-zero.png)

**期望：** 生产日期默认不得晚于今天；如确需录入未来批次，必须明确二次确认。新增商品总数量应大于 0，0 数量不能成为“正常在库”。

### P1-03 手动最终日期可早于生产日期

**类型：确定 Bug / 日期完整性。**

新增 `BadOrder`，生产日期选择 2026-08-12，再把最终可食用日期手动改为 2026-08-11。应用没有提示日期先后关系错误，直接保存为“已过期”在库商品。

- 保存前：[120-bad-order-ready.png](screenshots/2026-08-12-manual-entry-audit/120-bad-order-ready.png)
- 保存后：[121-bad-order-result.png](screenshots/2026-08-12-manual-entry-audit/121-bad-order-result.png)

**期望：** 只要同时存在生产日期和最终日期，就必须满足最终日期不早于生产日期；开封日期也应检查不早于生产日期、不过度晚于当前日期。

### P1-04 高频“减 1”到 0 后仍保留为在库，且总览与提醒结论矛盾

**类型：确定 Bug / 状态一致性。**

`Bread-C` 从 1 减到 0 后仍显示“在库批次 1”和“今日到期”。总览统计显示“今天到期 1”并把它列入优先处理，但提醒摘要又显示“今天没有需要优先处理的商品”。详情减量后也没有出现“标记已用完”的下一步提示。

- 减量后详情：[21-decrement.png](screenshots/2026-08-12-manual-entry-audit/21-decrement.png)
- 重启后的矛盾总览：[79c-restart-late.png](screenshots/2026-08-12-manual-entry-audit/79c-restart-late.png)

**影响：** 用户无法判断 0 库存商品究竟还在不在库、是否仍参与到期统计和提醒。

### P1-05 修改一个批次的商品名会把该批次拆成新的商品卡片

**类型：确定 Bug / 数据模型与用户预期不一致。**

`Bread-C` 原来聚合显示 3 个批次。编辑最近批次，把名称修正为 `Bread-CX` 后，库存变为 `Bread-C` 和 `Bread-CX` 两个商品，而不是修改原商品名称。用户只是修正少一个字，却得到两个商品档案。

- 修改前同一商品 3 批次：[26-replenish-saved.png](screenshots/2026-08-12-manual-entry-audit/26-replenish-saved.png)
- 修改后被拆成两个商品：[35-inventory-restored.png](screenshots/2026-08-12-manual-entry-audit/35-inventory-restored.png)
- 名称和条码本身已成功修改：[29b-edit-saved.png](screenshots/2026-08-12-manual-entry-audit/29b-edit-saved.png)

**期望：** 明确提供“重命名整个商品”和“仅将此批次移到新商品”两个动作，不能把后者隐藏在普通“编辑”里。

### P1-06 “复制当前批次”无确认，且会把剩余量恢复为总量

**类型：高风险交互。**

批次菜单点“复制当前批次”后立即写入数据库，没有预览或二次确认。原批次剩余 0/1，复制品直接变为 1/1。新批次编号能够正确生成，但一次误触会凭空增加库存。

- 操作菜单：[22b-batch-more.png](screenshots/2026-08-12-manual-entry-audit/22b-batch-more.png)
- 复制结果：[23-copy-result.png](screenshots/2026-08-12-manual-entry-audit/23-copy-result.png)

### P1-07 详情页不展示备注，也不展示开封后建议处理日

**类型：确定功能缺口。**

表单可计算“开封后建议处理日 2026-08-19”，备注 `coldtest` 也确实保存并进入 JSON/Excel；但商品详情只显示开封日期，不显示建议处理日，也完全没有备注区域。用户平时只能重新进入编辑页找回这些信息。

- 表单计算结果：[14-opened-preview.xml](screenshots/2026-08-12-manual-entry-audit/14-opened-preview.xml)
- 详情缺少建议日和备注：[20-detail-bread.png](screenshots/2026-08-12-manual-entry-audit/20-detail-bread.png)
- 导出文件中备注确实存在：`exported-shiqi-batches.json`

### P1-08 状态筛选最后一项“暂无到期日”被严重裁切

**类型：确定视觉与可操作性 Bug。**

状态按钮被强行排在同一行，最后的“暂无到期日”只剩约 52 px，文字无法完整显示，触控区域也明显小于其他选项。

- 证据：[18-filter-clipped.png](screenshots/2026-08-12-manual-entry-audit/18-filter-clipped.png)

### P1-09 冷启动先出现约 7.4 秒空白页，没有加载反馈

**类型：性能与可感知状态问题。**

强制停止后冷启动，首张截图是纯空白应用窗口；系统日志记录 `Displayed com.shiqi.expirytracker/.MainActivity: +7s448ms`，之后页面才完整出现。启动过程在 `onCreate()` 中同步加载数据库、计算提醒计划并可能保存，再构建首屏。

- 空白阶段：[79b-restart-persistence.png](screenshots/2026-08-12-manual-entry-audit/79b-restart-persistence.png)
- 完成后：[79c-restart-late.png](screenshots/2026-08-12-manual-entry-audit/79c-restart-late.png)

### P2-01 表单错误只用短 Toast，字段不标红、不聚焦、不自动滚动

剩余数量大于总数量、错误条码和提醒格式错误都只显示底部 Toast。Toast 还会覆盖固定操作栏；长表单中用户看不到是哪个字段错了。

- 数量错误：[04-invalid-remaining.png](screenshots/2026-08-12-manual-entry-audit/04-invalid-remaining.png)
- 条码错误：[28-invalid-barcode.png](screenshots/2026-08-12-manual-entry-audit/28-invalid-barcode.png)
- 提醒格式错误：[52-reminder-invalid.png](screenshots/2026-08-12-manual-entry-audit/52-reminder-invalid.png)

### P2-02 手动最终日期的模式切换容易误触并立即清空日期

“改用自动计算”和“更多信息”是相邻的两个整行按钮。误点前者会立即清空手动最终日期，没有确认、撤销或保留原值。

- 两个相邻入口位置：[11-manual-more-position.xml](screenshots/2026-08-12-manual-entry-audit/11-manual-more-position.xml)

### P2-03 详情页操作重复，主要动作层级不清楚

同一详情页同时存在标题区“补货”、底部“补货新批次”、菜单“补货”；编辑也同时存在批次“编辑”和底部“编辑最近批次”。高频动作和危险动作散落在三处。

- 详情页：[20-detail-bread.png](screenshots/2026-08-12-manual-entry-audit/20-detail-bread.png)
- 批次菜单：[22b-batch-more.png](screenshots/2026-08-12-manual-entry-audit/22b-batch-more.png)

### P2-04 备份恢复在多份同规模备份下难以辨认目标

恢复前自动快照会成为最新且默认选中的候选。两份备份如果都是“4 商品 / 4 批次 / 3.0 KB”，确认对话框只重复数量，不显示目标时间、来源或校验摘要。用户很容易恢复错版本。

- 两份近似备份：[99-backup-catalog.png](screenshots/2026-08-12-manual-entry-audit/99-backup-catalog.png)
- 确认框缺少目标时间：[100-selected-1141-confirm.png](screenshots/2026-08-12-manual-entry-audit/100-selected-1141-confirm.png)

### P2-05 应用自己导出的 Excel，原样导回仍产生警告

原样导回应用生成的工作簿时，`Milk-A` 因“无过期时间且未填写生产日期”被标记为警告行。数据可导入，但用户会怀疑自己的备份有问题。

- 导入预览：[65-excel-preview.png](screenshots/2026-08-12-manual-entry-audit/65-excel-preview.png)
- 警告详情：[66-excel-warning-details.png](screenshots/2026-08-12-manual-entry-audit/66-excel-warning-details.png)

### P2-06 空库首页过度强调识别，手动新增权重过低

空库首屏同时出现“扫描包装并识别”“扫码查找”“开始智能识别”三个识别入口，手动新增只有一个次级按钮。对明确想直接输入的用户，第一步不够直接。

- 证据：[01-first-launch.png](screenshots/2026-08-12-manual-entry-audit/01-first-launch.png)

## 4. 全流程结果

| 场景 | 实际结果 | 状态 |
| --- | --- | --- |
| 空库与首次启动 | 页面可用；识别入口过多 | `PASS_WITH_ISSUES` |
| 无过期时间新增 | `Milk-A` 成功保存 | `PASS` |
| 生产日期 + 30 天 | 自动得到 2026-09-11 | `PASS` |
| 直接输入最终日期 | `Bread-C` 今日到期 | `PASS` |
| 最终日期早于生产日期 | 错误数据可直接保存 | `FAIL` |
| 未保存返回保护 | 可继续填写或放弃返回 | `PASS` |
| 数量和条码校验 | 逻辑拦截正确，错误定位差 | `PASS_WITH_ISSUES` |
| 搜索名称与条码 | 名称、EAN-13 均可找到商品 | `PASS` |
| 状态筛选 | 逻辑可用；最后一项严重裁切 | `FAIL_VISUAL` |
| 分类 + 位置组合筛选 | 乳制品 + TestShelf 只返回 Yogurt-B | `PASS` |
| 详情、减 1 | 数量变化成功；到 0 后状态矛盾 | `FAIL` |
| 商品名和条码编辑 | 字段可改；名称修改会拆商品 | `FAIL` |
| 补货新批次 | 名称复用，日期独立，自动计算正确 | `PASS` |
| 复制批次 | UUID 独立；无确认并增加库存 | `FAIL_UX` |
| 标记已用完与恢复在库 | 确认文案清楚，状态可恢复 | `PASS` |
| 单批次删除 | 只删除目标批次，其他批次保留 | `PASS` |
| 固定/智能提醒 | 可切换、校验、保存，未保存保护有效 | `PASS` |
| 测试提醒 | Android 通知栏出现“今日食期简报” | `PASS` |
| JSON 全量导出 | 4 批次、UUID 和字段完整 | `PASS` |
| JSON 日期筛选 | 生产日期 2026-08-12 筛出 2 批次 | `PASS` |
| JSON 分享 | Sharesheet 可见文件名及 Drive/Gmail | `PASS`; QQ/微信 `PARTIAL` |
| JSON 重复导入 | 新增 0、更新 0、相同 4 | `PASS` |
| Excel 导出 | 生成 5101 B `.xlsx`，4 行可回读 | `PASS` |
| Excel 追加导入 | 4 批次追加为 8 批次 | `PASS`，但会真实增加库存 |
| Excel 覆盖导入 | 8 批次恢复为 4 批次 | `PASS` |
| Excel 覆盖前自动备份 | 承诺存在但备份不存在 | `FAIL` |
| 手动备份 | 建立带时间、数量、大小和校验的备份 | `PASS` |
| 手动恢复 | 明确选择 11:41 备份后数据恢复 | `PASS_WITH_UX_RISK` |
| 强制停止与重启持久化 | 4 商品/4 批次、提醒和备注仍在 | `PASS` |
| 版本与说明 | 版本、本地数据和卸载风险说明清楚 | `PASS` |
| 识别状态页 | 只读查看，调试开关和日志分享入口存在 | `PASS_UI_ONLY` |

## 5. 通过后的最终数据

审计结束前已删除临时边界样本、清除搜索/筛选并再次重启。最终库存为：

- `Bread-C`：1 批次，剩余 0 件
- `Bread-CX`：1 批次，剩余 1 件
- `Yogurt-B`：1 批次，剩余 4 件
- `Milk-A`：1 批次，剩余 2 件

最终证据：[122-final-clean.png](screenshots/2026-08-12-manual-entry-audit/122-final-clean.png)

## 6. 修复顺序建议

1. 先修 Excel 覆盖自动备份，补充真实恢复验收和故障回滚测试。
2. 统一数量为 0 的状态机，修复总览、库存、提醒三处口径。
3. 禁止普通手动录入未来生产日期和 0 总数量；补齐日期先后关系校验。
4. 将“重命名商品”与“移动/拆分批次”拆成两个明确动作。
5. 让备注、开封后建议处理日进入详情和提醒依据。
6. 重排状态筛选与详情操作，处理裁切、重复入口和危险动作确认。
7. 把启动数据库/提醒计算移出首帧，提供非空白启动反馈。
8. 最后统一表单的行内错误、焦点和自动滚动。

## 7. 本轮未外推为通过的内容

- 未测试任何视频识别或真实相机识别。
- 未测试真实手机的触控、字体缩放、对焦、通知厂商限制。
- 未安装 QQ/微信，因此没有验证它们能否成功接收并打开 JSON/Excel。
- 未重复执行 APK 覆盖升级与签名兼容测试；本轮只验证当前已安装版本的强制重启持久化。
