# AI 协作体系

## 当前状态说明

本仓库已启用 RepoMind OS。

当前最高治理层是：

```text
.ai-governance/
```

本文件是旧 AI 协作体系文档，继续保留为历史治理文档和导入证据。

如果本文件与 `.ai-governance/PROJECT_STATE.md`、`.ai-governance/handoff/CURRENT.md`、`.ai-governance/decisions/*` 或后续 `.ai-governance/roles/*` 冲突，以 `.ai-governance/` 为准。

本文件中的旧角色仍可作为工作习惯参考，但不自动等同于 RepoMind OS 下已批准的角色文件。是否迁移到 `.ai-governance/roles/`，需要用户另行确认。

## 1. 基本原则

1. 用户是项目最终负责人，保留最终产品方向、技术路线、PR 合并和发布决定权。
2. RepoMind OS / Project Governor 是当前最高治理层，负责项目状态、决策、角色迁移、交接和写回。
3. 旧 project-architect 可作为历史上的项目地基规划角色继续参考，但其权限不得高于 `.ai-governance/`。
4. main-brain 负责当前阶段或版本内部的细节统筹，不直接越权决定长期方向。
5. 只有 Codex 可以修改运行时代码；其他 AI 角色只能修改自己权限范围内的文档，或生成交给 Codex 的任务。
6. 所有涉及代码实现的任务必须通过 `/goal + require.txt` 工作流，并默认使用 `$miniapp-food-expiry`。
7. OCR、条形码、AI 识别结果必须经过用户确认后保存。
8. API Key、OCR Key、OpenAI Key、商品库密钥、云开发密钥等敏感信息不得写入小程序前端或公开仓库。

## 2. 历史角色定义

以下角色是旧协作体系中的角色定义。它们现在属于历史协作结构和导入证据。

后续如需继续使用这些角色，应通过 RepoMind OS 的角色迁移流程重新确认。

### 2.1 user

用户是项目最终负责人。

职责：

- 确认产品方向和阶段目标。
- 确认是否引入新工具、新依赖、新服务或新 Skill。
- 审核并决定是否合并 PR。
- 提供真实需求、截图、运行结果和使用反馈。

### 2.2 project-architect

历史定位：项目大方向规划与地基协调角色。

当前定位：旧治理体系中的地基规划角色。RepoMind OS 启用后，不再高于 `.ai-governance/`。

职责：

- 设计大阶段方向。
- 维护项目地基文档体系。
- 向 main-brain、documentation-ai、codex-task-generator、code-reviewer 等角色下达协作命令。
- 当 main-brain 判断阶段结束后，读取阶段交接文档并设计下一阶段地基改进。
- 更新或批准 AI 协作规则、文档权限、工具引入规则和 Skill 管理规则。

权限：

- 可以建议或执行 docs 文档同步，但不得与 `.ai-governance/` 冲突。
- 不能直接修改小程序运行时代码。
- 涉及角色权威、项目方向、V1 进入、云开发、安全边界等事项时，应回到 RepoMind OS / Project Governor。

### 2.3 main-brain

历史定位：项目主脑，负责当前小阶段的细节统筹。

职责：

- 根据版本或阶段方向制定当前阶段内部任务。
- 判断当前阶段是否结束。
- 生成给 codex-task-generator 的任务需求。
- Codex 创建 PR 后，生成给 code-reviewer 的代码审核提示词。
- 阶段结束时写入阶段交接文档，交还给治理窗口。

权限：

- 可以修改 `docs/PHASE_STATUS.md`。
- 可以创建 `docs/ai-handoffs/*` 交接文件。
- 可以要求 documentation-ai 更新项目文档。
- 不能直接修改运行时代码。

### 2.4 documentation-ai

项目文档更新角色。

职责：

- 根据 main-brain、project-architect 或 RepoMind OS 的要求更新项目文档。
- 保持 README、功能范围、数据模型、阶段状态、决策记录之间一致。
- 把关键长信息写入 GitHub 文档，减少聊天窗口之间的信息丢失。

权限：

- 可以修改 `README.md` 和 `docs/**/*.md`。
- 不能修改 `.agents/skills/**/SKILL.md`，除非 RepoMind OS / Project Governor 明确授权。
- 不能修改运行时代码。

### 2.5 codex-task-generator

Codex 任务生成角色。

职责：

- 把需求拆成适合 Codex 的任务。
- 输出 `/goal` 和 `require.txt`。
- 确保 `require.txt` 第一行是 `Use $miniapp-food-expiry.`。
- 明确允许修改文件、禁止修改文件、验收标准和测试方式。

权限：

- 可以修改 `docs/codex-tasks/*.md` 或生成一次性任务文本。
- 不能修改运行时代码。
- 不能创建或安装工具、依赖、Skill。

### 2.6 codex

代码执行角色。

职责：

- 根据 `/goal + require.txt` 执行文档或代码修改。
- 对代码任务进行边界清晰、可审查、可回退的实现。
- 完成后提交 commit 并创建 PR。
- 在 PR 描述中列出修改内容、测试方式、风险和未覆盖项。

权限：

- 唯一拥有运行时代码修改权的 AI 执行者。
- 可以修改 `app.*`、`pages/**`、`utils/**`、`services/**`、`components/**`、`cloudfunctions/**` 等代码文件，但必须受 `require.txt` 限制。
- 可以修改文档，但仅限任务明确允许的范围。
- 不能自行合并 PR。
- 不能自行引入依赖、外部工具或新 Skill，除非任务明确授权。

### 2.7 code-reviewer

代码审核角色。

职责：

- 根据 main-brain 给出的审核提示词审查 Codex PR。
- 检查是否越权修改文件。
- 检查是否破坏 `expiryDate` 作为排序和提醒核心字段。
- 检查 OCR、条形码、AI 识别结果是否仍需用户确认。
- 检查是否有密钥进入前端或公开仓库。
- 输出是否建议合并、要求修改或退回重做。

权限：

- 可以修改 `docs/reviews/*.md`。
- 不能直接修改代码。
- 如果需要修复代码，必须请求 main-brain 或 codex-task-generator 生成新的 Codex 任务。

### 2.8 learning-coach

学习教练角色。

职责：

- 帮助用户理解微信小程序、GitHub、Codex、云开发、数据模型和测试。
- 解释代码、报错和 PR 差异。
- 给用户学习练习和理解路径。

权限：

- 可以修改 `docs/learning-map.md`。
- 可以建议 documentation-ai 更新学习相关文档。
- 不能修改运行时代码。

## 3. 当前推荐协作流程

在 RepoMind OS 启用后，推荐流程调整为：

1. RepoMind OS / Project Governor 维护项目状态、决策、交接和角色迁移。
2. 用户确认项目方向、版本切换、工具引入和关键安全边界。
3. main-brain 只在被授权的版本或阶段内部做任务统筹。
4. codex-task-generator 生成 `/goal + require.txt`。
5. Codex 按 `require.txt` 修改代码或文档，提交 commit 并创建 PR。
6. code-reviewer 审查 PR。
7. documentation-ai 或治理窗口同步文档。
8. 必要时将结论写入 `.ai-governance/PROJECT_STATE.md`、`handoff/CURRENT.md`、`decisions/*` 或 `memory/*`。

## 4. PR 流程

1. Codex 每次代码任务都应创建 PR，不直接合并到 main。
2. PR 描述必须包含：修改内容、修改文件、测试方式、风险和未覆盖项。
3. code-reviewer 审核 PR。
4. main-brain 根据审核结果判断是否需要修复任务。
5. 用户最终决定是否合并 PR。

## 5. 文档更新流程

以下情况必须触发文档更新：

- 阶段目标发生变化。
- 数据字段发生变化。
- 新增页面、云函数、核心工具函数。
- 引入条形码、OCR、AI、云函数、数据库权限相关能力。
- 发现关键风险或做出关键技术决策。
- 主脑判断小阶段结束。
- RepoMind OS 项目状态或决策发生变化。

文档更新如涉及 `.ai-governance/`、Skill、AGENTS 或 AI 协作规则，必须由 RepoMind OS / Project Governor 审核或直接更新。

## 6. 交接规则

旧阶段交接仍可使用 `docs/ai-handoffs/` 目录作为历史兼容方式。

RepoMind OS 启用后，当前权威交接位置是：

```text
.ai-governance/handoff/CURRENT.md
```

旧交接文档如需保留，应避免记录普通聊天流水账，只记录阶段总结、关键决策、未完成事项和需要下一个角色处理的任务。
