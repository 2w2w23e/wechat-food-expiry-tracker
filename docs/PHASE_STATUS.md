# 阶段状态

本文件记录“食期管家”项目当前阶段、阶段目标、完成情况、阻塞点和下一步任务。

本文件由 main-brain 主要维护；project-architect 可在阶段交接后更新大阶段判断。

## 当前阶段

阶段编号：0

阶段名称：项目地基与 AI 协作体系阶段

阶段状态：进行中

最后更新角色：project-architect

最后更新日期：YYYY-MM-DD

## 阶段目标

建立项目开发前的地基，包括：

1. 明确新版产品定位和 MVP 边界。
2. 固定核心数据模型。
3. 建立 Codex 的 `/goal + require.txt` 工作流。
4. 建立 AI 角色协作体系。
5. 定义文档与代码修改权限。
6. 定义工具与 Skill 引入策略。
7. 保存各 AI 角色的开场提示词。
8. 定义阶段交接文档规则。

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

## 已完成地基项目

- [x] `README.md`：项目入口说明。
- [x] `AGENTS.md`：Codex / AI 编码助手仓库规则。
- [x] `.agents/skills/miniapp-food-expiry/SKILL.md`：项目专属 Codex Skill。
- [x] `docs/FEATURE_SCOPE.md`：新版功能范围和阶段边界。
- [x] `docs/DATA_MODEL.md`：食品数据模型说明。
- [x] `docs/CODEX_WORKFLOW.md`：Codex 工作流说明。
- [x] `docs/AI_COLLABORATION.md`：AI 协作体系。
- [x] `docs/DOCUMENT_OWNERSHIP.md`：文档与代码修改权限。
- [x] `docs/TOOL_AND_SKILL_POLICY.md`：工具与 Skill 引入策略。
- [x] `docs/PHASE_STATUS.md`：阶段状态。
- [x] `docs/ROLE_PROMPTS.md`：AI 角色提示词。
- [x] `docs/ai-handoffs/README.md`：AI 交接协议。

## 当前剩余事项

- [ ] 主脑确认阶段 0 是否结束。
- [ ] 主脑选择阶段 1 的第一个最小代码任务。
- [ ] Codex 任务生成器为阶段 1 第一个任务生成 `/goal + require.txt`。
- [ ] Codex 创建第一个功能 PR。
- [ ] code-reviewer 审核第一个功能 PR。

## 当前推荐下一步

建议由 main-brain 读取以下文件后判断是否进入阶段 1：

- `README.md`
- `docs/FEATURE_SCOPE.md`
- `docs/DATA_MODEL.md`
- `docs/CODEX_WORKFLOW.md`
- `docs/AI_COLLABORATION.md`
- `docs/DOCUMENT_OWNERSHIP.md`
- `docs/TOOL_AND_SKILL_POLICY.md`
- `docs/ROLE_PROMPTS.md`

如果 main-brain 判断阶段 0 已结束，下一阶段建议进入：

阶段 1：MVP 核心数据与日期计算基础。

阶段 1 的第一个最小任务建议是：

实现食品日期计算工具函数和基础测试/手动验证说明。

原因：`expiryDate` 是排序、提醒、列表状态、筛选和后续 OCR / 条码确认流程的核心字段，必须先稳定。

## 当前阻塞点

暂无明确阻塞。

潜在风险：

1. 过早实现条形码、AI 对话或智能提醒，导致 MVP 失焦。
2. Codex 任务范围过大，导致一次 PR 修改过多文件。
3. 未经确认的数据进入正式食品记录。
4. 密钥或外部 API 凭据误写入前端或公开仓库。

## 阶段结束交接要求

当 main-brain 判断当前小阶段结束时，应在 `docs/ai-handoffs/` 创建交接文件。

交接文件应包含：

1. 当前阶段完成了什么。
2. 哪些文档或代码发生变化。
3. 当前关键决策。
4. 未完成事项。
5. 建议 project-architect 下一步处理什么。
