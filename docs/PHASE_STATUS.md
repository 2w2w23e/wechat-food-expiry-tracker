# 阶段状态

本文件记录“食期管家”项目当前阶段、阶段目标、完成情况、阻塞点和下一步任务。

本文件由 main-brain 主要维护；project-architect 可在阶段交接后更新大阶段判断。

## 当前阶段

阶段编号：1

阶段名称：MVP 核心数据与日期计算基础

阶段状态：已完成，等待 project-architect 确认进入阶段 2

最后更新角色：main-brain

最后更新日期：2026-06-09

## 阶段 1 结论

阶段 1：MVP 核心数据与日期计算基础，已完成。

结束判断：可以结束。

判断依据：

1. 已实现食品日期计算工具函数。
2. 已建立 `expiryDate` 相关代码基础。
3. 已支持生产日期 + 保质期计算 `expiryDate`。
4. 已支持 `day`、`month`、`year` 三种单位。
5. 已处理无效日期、负数保质期、月底日期和闰年日期。
6. 已提供基础测试文件覆盖关键边界。
7. code-reviewer 已给出低风险、建议合并/保留、暂不需要必须修复的结论。
8. 未发现页面 UI、食品录入页、食品列表页、云数据库、条形码、OCR、AI、提醒调度或新依赖。

## 阶段 1 已完成内容

- [x] `utils/date.js`：日期计算工具函数。
- [x] `tests/date.test.js`：日期计算基础测试。
- [x] 支持 `productionDate + shelfLifeValue + shelfLifeUnit` 计算 `expiryDate`。
- [x] 支持手动 `expiryDate` 模式并返回 `dateSource: manual`。
- [x] 支持 `day`、`month`、`year` 单位。
- [x] 支持复数单位 `days`、`months`、`years` 归一化。
- [x] 处理月底日期，例如 1 月 31 日 + 1 个月。
- [x] 处理闰年日期，例如 2024-02-29 + 1 年。
- [x] 对无效日期和无效保质期返回空 `expiryDate`。
- [x] 使用 UTC 进行天数偏移，降低本地时区跨日风险。

## 当前产品定位

食期管家是一个面向家庭 / 老年友好的食品库存与保质期管理微信小程序。

当前 MVP v0.1 重点是先完成稳定、简单、适合老年人使用的基础版本：

- 手动录入食品。
- 记录分类、数量、剩余数量、单位、保存方式和备注。
- 支持生产日期 + 保质期计算 `expiryDate`。
- 支持手动输入 `expiryDate`。
- 按 `expiryDate` 排序。
- 支持简单筛选和总览统计。

条形码扫描、规则化智能提醒、OCR / AI 信息抽取、大模型对话和食谱规划属于后续阶段。

## 阶段 0 已完成地基项目

- [x] `README.md`：项目入口说明。
- [x] `AGENTS.md`：Codex / AI 编码助手仓库规则。
- [x] `.agents/skills/miniapp-food-expiry/SKILL.md`：项目专属 Codex Skill。
- [x] `docs/FEATURE_SCOPE.md`：新版功能范围和阶段边界。
- [x] `docs/DATA_MODEL.md`：食品数据模型说明。
- [x] `docs/CODEX_WORKFLOW.md`：Codex 工作流说明。
- [x] `docs/AI_COLLABORATION.md`：AI 协作体系。
- [x] `docs/DOCUMENT_OWNERSHIP.md`：文档与代码修改权限。
- [x] `docs/TOOL_AND_SKILL_POLICY.md`：工具与 Skill 引入策略。
- [x] `docs/TOOL_RESEARCH.md`：工具与开源项目调研。
- [x] `docs/PHASE_STATUS.md`：阶段状态。
- [x] `docs/ROLE_PROMPTS.md`：AI 角色提示词。
- [x] `docs/ai-handoffs/README.md`：AI 交接协议。

## 当前剩余事项

- [x] codex-task-generator 为阶段 1 第一个任务生成 `/goal + require.txt`。
- [x] Codex 完成阶段 1 日期计算实现。
- [x] code-reviewer 审核阶段 1 日期计算实现。
- [x] main-brain 判断阶段 1 是否结束。
- [x] main-brain 更新 `docs/PHASE_STATUS.md`。
- [x] main-brain 创建阶段 1 结束交接文档给 project-architect。
- [ ] project-architect 读取阶段 1 交接文档，并确认是否进入阶段 2。

## 当前推荐下一步

请 project-architect 读取阶段 1 交接文档：

```text
`docs/ai-handoffs/2026-06-09-main-brain-to-project-architect-phase-1-complete.md`
```

如 project-architect 确认进入阶段 2，建议阶段 2 按 `docs/VERSION_ROADMAP.md` 执行：

阶段 2：食品基础数据结构与本地 mock 数据。

阶段 2 的第一个最小任务建议是：

```text
/goal 建立食品基础数据结构与本地 mock 数据，具体细节见 require.txt
```

但在 project-architect 确认前，main-brain 不直接展开阶段 2 详细规划。

## 当前阻塞点

暂无明确阻塞。

潜在风险：

1. 阶段 2 过早进入页面 UI 或手动录入页，导致跳过数据结构基础。
2. 日期工具函数后续被页面直接耦合，降低复用性。
3. 调用方未把 `calculateExpiryDate` 返回结果正确写入食品记录的 `expiryDate` 字段。
4. 后续排序、提醒、状态判断没有统一读取 `expiryDate`。
5. 过早实现条形码、AI 对话或智能提醒，导致 MVP v0.1 失焦。

## 阶段结束交接要求

当 main-brain 判断当前小阶段结束时，应在 `docs/ai-handoffs/` 创建交接文件。

交接文件应包含：

1. 当前阶段完成了什么。
2. 哪些文档或代码发生变化。
3. 当前关键决策。
4. 未完成事项。
5. 建议 project-architect 下一步处理什么。
