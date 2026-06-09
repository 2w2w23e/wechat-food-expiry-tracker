STATUS: UNREAD | TO: project-architect | FROM: main-brain | TYPE: phase-handoff | CREATED: 2026-06-09

# 阶段 1 结束交接：MVP 核心数据与日期计算基础

## 1. 背景

main-brain 已组织完成 MVP v0.1 阶段 1：核心数据与日期计算基础。

阶段 1 的目标是建立 `expiryDate` 相关代码基础，为后续录入、列表、排序、提醒和识别确认流程打底。

本阶段严格遵守 `docs/VERSION_ROADMAP.md` 中的阶段 1 边界：只做日期计算工具函数、生产日期 + 保质期计算、天/月/年单位、无效输入、月底/闰年/日期偏移风险和基础验证说明。

## 2. 已完成内容

- 已实现 `utils/date.js`。
- 已实现 `tests/date.test.js`。
- 已支持通过 `productionDate + shelfLifeValue + shelfLifeUnit` 计算 `expiryDate`。
- 已支持 `day`、`month`、`year` 三种单位。
- 已支持复数单位 `days`、`months`、`years` 归一化。
- 已支持手动 `expiryDate` 模式，返回 `dateSource: manual`。
- 已处理无效日期。
- 已处理负数保质期。
- 已处理月底日期，例如 1 月 31 日 + 1 个月。
- 已处理闰年日期，例如 2024-02-29 + 1 年。
- 已使用 UTC 进行天数偏移，降低本地时区跨日风险。
- 已由 code-reviewer 审核，结论为低风险，建议合并/保留，暂不需要必须修复。

## 3. 关键决策

- `expiryDate` 继续作为最终可食用日期，也是排序、提醒、列表状态和后续识别确认流程的核心字段。
- 本阶段不引入新依赖。
- 本阶段不做页面 UI。
- 本阶段不做食品录入页。
- 本阶段不做食品列表页。
- 本阶段不做云数据库。
- 本阶段不做条形码、OCR、AI 或提醒调度。
- 日期计算工具函数保持独立，后续页面或数据结构应调用它，而不是在页面中重复实现日期计算。

## 4. 修改过的文件

阶段 1 代码实现相关文件：

- `utils/date.js`
- `tests/date.test.js`

阶段 1 收尾文档更新：

- `docs/PHASE_STATUS.md`
- `docs/ai-handoffs/2026-06-09-main-brain-to-project-architect-phase-1-complete.md`

## 5. 当前风险

- `calculateExpiryDate` 返回的是结果对象，后续保存食品记录时，调用方必须把返回值中的 `expiryDate` 明确写入食品记录的 `expiryDate` 字段。
- 后续页面或数据结构如果绕过 `utils/date.js` 自行计算日期，可能导致逻辑分散。
- 后续排序、状态判断、提醒逻辑必须统一读取 `expiryDate`，不能使用其他临时日期字段作为标准。
- 阶段 2 如果直接进入 UI 或手动录入页，可能跳过食品基础数据结构和 mock 数据基础。
- 当前没有引入测试框架，`tests/date.test.js` 是基础 Node assert 测试，后续是否建立正式测试脚本可由 project-architect 或 main-brain 后续评估。

## 6. 未完成事项

- project-architect 尚未确认是否进入阶段 2。
- 阶段 2 尚未生成 `/goal + require.txt`。
- 阶段 2 尚未创建 Codex PR。
- 后续需要在食品数据结构中明确使用 `expiryDate`、`dateSource`、`category`、`quantity`、`remainingQuantity`、`unit`、`storageMethod` 等字段。

## 7. 请求接收方处理

请 project-architect 读取本交接后：

- 确认阶段 1 是否正式结束。
- 确认是否进入 MVP v0.1 阶段 2：食品基础数据结构与本地 mock 数据。
- 如认可进入阶段 2，请向 main-brain 下达阶段 2 边界要求。
- 如认为阶段 1 还需补强，请指出是否要补充空输入测试、测试脚本、注释或文档。

## 8. 建议下一步

如果 project-architect 确认进入阶段 2，建议阶段 2 第一个最小任务是：

```text
/goal 建立食品基础数据结构与本地 mock 数据，具体细节见 require.txt
```

阶段 2 应优先建立可复用的数据结构和 mock 数据，不要直接进入手动录入 UI。

阶段 2 应继续保持：

- 不接云数据库。
- 不做条形码、OCR、AI。
- 不做提醒调度。
- 不引入新依赖，除非用户或 project-architect 明确确认。
- 保持 `expiryDate` 为排序、状态判断和后续提醒的核心字段。
