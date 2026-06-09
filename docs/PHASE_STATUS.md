# 阶段状态

本文件记录“食期管家”项目当前阶段、阶段目标、完成情况、阻塞点和下一步任务。

本文件由 main-brain 主要维护；project-architect 可在阶段交接后更新大阶段判断。

## 当前阶段

阶段编号：1

阶段名称：MVP 核心数据与日期计算基础

阶段状态：准备开始

最后更新角色：main-brain

最后更新日期：2026-06-09

## 阶段 0 结论

阶段 0：项目地基与 AI 协作体系阶段，已结束。

结束判断：可以结束。

判断依据：

1. 新版产品定位和 MVP 边界已经写入 `README.md` 和 `docs/FEATURE_SCOPE.md`。
2. 核心食品数据模型已经写入 `docs/DATA_MODEL.md`。
3. Codex 的 `/goal + require.txt` 工作流已经写入 `docs/CODEX_WORKFLOW.md`。
4. AI 角色协作体系已经写入 `docs/AI_COLLABORATION.md` 和 `docs/ROLE_PROMPTS.md`。
5. 文档与代码修改权限已经写入 `docs/DOCUMENT_OWNERSHIP.md`。
6. 工具与 Skill 引入策略已经写入 `docs/TOOL_AND_SKILL_POLICY.md` 和 `docs/TOOL_RESEARCH.md`。
7. 阶段交接规则已经写入 `docs/ai-handoffs/README.md`。
8. 当前无明确阻塞点，可以进入阶段 1 的第一个代码实现任务。

## 当前产品定位

食期管家是一个面向家庭 / 老年友好的食品库存与保质期管理微信小程序。

当前重点是先完成稳定、简单、适合老年人使用的 MVP：

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

## 阶段 1 当前目标

阶段 1 目标是开始 MVP 核心代码实现，但仍保持任务足够小、可审查、可回退。

当前阶段优先建立：

1. 食品日期计算工具函数。
2. `expiryDate` 作为统一最终可食用日期字段的代码基础。
3. 日期计算的基础测试或手动验证说明。

暂不实现：

- 食品录入页面。
- 食品列表页面。
- 云数据库。
- 条形码扫描。
- OCR / AI 信息抽取。
- 大模型对话。
- 真实提醒调度。

## 当前剩余事项

- [x] main-brain 确认阶段 0 是否结束。
- [x] main-brain 更新 `docs/PHASE_STATUS.md` 的阶段状态。
- [x] main-brain 创建阶段 0 结束交接文档给 project-architect。
- [ ] codex-task-generator 为阶段 1 第一个任务生成 `/goal + require.txt`。
- [ ] Codex 创建阶段 1 第一个功能 PR。
- [ ] code-reviewer 审核阶段 1 第一个功能 PR。

## 当前推荐下一步

请让 codex-task-generator 生成阶段 1 第一个 Codex 任务：

```text
/goal 实现食品日期计算工具函数，具体细节见 require.txt
```

`require.txt` 必须第一行写：

```text
Use $miniapp-food-expiry.
```

任务应限制为：只实现日期计算工具函数和基础验证，不修改页面 UI，不实现录入、列表、云数据库、条形码、OCR、AI 或提醒调度。

## 当前阻塞点

暂无明确阻塞。

潜在风险：

1. 过早实现条形码、AI 对话或智能提醒，导致 MVP 失焦。
2. Codex 任务范围过大，导致一次 PR 修改过多文件。
3. 未经确认的数据进入正式食品记录。
4. 密钥或外部 API 凭据误写入前端或公开仓库。
5. 日期计算在闰年、月底、无效日期、时区处理上出现边界错误。

## 阶段结束交接要求

当 main-brain 判断当前小阶段结束时，应在 `docs/ai-handoffs/` 创建交接文件。

交接文件应包含：

1. 当前阶段完成了什么。
2. 哪些文档或代码发生变化。
3. 当前关键决策。
4. 未完成事项。
5. 建议 project-architect 下一步处理什么。
