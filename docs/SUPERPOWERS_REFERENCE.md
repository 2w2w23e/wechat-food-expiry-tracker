# Superpowers 方法论参考

本文件记录“食期管家”项目如何参考 `obra/superpowers`，但不直接安装或启用它。

结论：当前阶段只参考方法论，不安装 Superpowers 插件，不替换现有 AI 协作体系。

## 1. 为什么要参考

Superpowers 的价值不在于某一个具体插件，而在于它把 AI 编码流程拆成了较清晰的方法论：

- 先澄清目标，不直接写代码。
- 把需求转成可确认的设计。
- 把设计拆成可执行计划。
- 小步实现。
- 尽量测试先行或至少先定义验证方式。
- 完成前必须验证。
- 请求代码审查。
- 分支完成后再决定 PR、合并或丢弃。

这些思想与本项目当前的多 AI 角色体系兼容，但不能让 Superpowers 直接接管流程。

## 2. 本项目不直接安装的原因

当前不安装 Superpowers，原因如下：

1. 本项目已经有明确角色体系：project-architect、main-brain、codex-task-generator、Codex、code-reviewer、documentation-ai、learning-coach。
2. Superpowers 有自己的强制工作流，可能和本项目的角色边界冲突。
3. Superpowers 支持多个 agent harness，安装后可能修改本地 agent 配置。
4. 当前项目仍处于 MVP 早期，优先问题是微信小程序基础功能，而不是复杂 agent 工作流。
5. 本项目已经要求 Codex 通过 `/goal + require.txt` 执行，不需要再叠加另一套强流程。
6. Superpowers 中的 writing-skills 思路有价值，但本项目已经规定新 Skill 必须先提案、再授权、再创建。

## 3. 可以吸收的部分

### 3.1 brainstorming

Superpowers 强调写代码前先澄清真实目标。

本项目吸收方式：

- 由 project-architect 负责版本级目标澄清。
- 由 main-brain 负责当前阶段目标澄清。
- 当目标模糊时，可以使用 `define-goal` 辅助生成可验收目标。

### 3.2 writing-plans

Superpowers 强调把设计拆成清晰计划。

本项目吸收方式：

- project-architect 维护 `docs/VERSION_ROADMAP.md`。
- main-brain 负责阶段内计划。
- codex-task-generator 负责把小任务写成 `/goal + require.txt`。

### 3.3 test-driven-development

Superpowers 强调红绿重构的 TDD。

本项目吸收方式：

- 对纯逻辑代码，例如日期计算工具函数，优先要求验证用例或测试说明。
- 早期如果没有测试框架，也必须写清楚手动验证用例。
- 后续引入测试框架前，由 project-architect 先评估是否必要。

### 3.4 verification-before-completion

Superpowers 强调完成前必须验证。

本项目吸收方式：

- Codex PR 必须写测试方式或手动检查方式。
- code-reviewer 必须检查是否真的可验证。
- main-brain 不应只凭“代码写完了”判断阶段完成。

### 3.5 requesting-code-review

Superpowers 强调阶段间请求代码审查。

本项目吸收方式：

- Codex 每次代码任务完成后创建 PR。
- main-brain 生成 code-reviewer 审核提示词。
- code-reviewer 输出是否建议合并、要求修改或退回。
- 用户最终决定是否合并。

### 3.6 finishing-a-development-branch

Superpowers 强调开发分支完成后的清理和选择。

本项目吸收方式：

- Codex 完成任务后创建 PR。
- PR 审查通过后由用户决定是否合并。
- 如任务失败，应通过修复 PR 或回滚处理，不在 main 上继续混乱修改。

### 3.7 writing-skills

Superpowers 包含创建 Skill 的方法论。

本项目吸收方式：

- 只作为参考，不允许 Codex 自动创建 Skill。
- 如果 Codex 认为某个 Skill 有价值，先创建 `docs/skill-proposals/*.md`。
- 只有用户或 project-architect 明确授权后，才能创建 `.agents/skills/<skill-name>/SKILL.md`。

## 4. 暂不吸收的部分

### 4.1 subagent-driven-development

当前不采用。

原因：本项目已经通过多个 ChatGPT 角色窗口实现职责分离，不需要再让一个工具内部自动调度多个 subagent。

### 4.2 using-git-worktrees

当前不采用。

原因：项目体量还小，Codex PR 分支足够。等出现多个并行功能分支时再评估。

### 4.3 自动强制流程

当前不采用。

原因：本项目的流程必须服从用户、project-architect 和 main-brain 的角色分工，不让外部插件自动覆盖项目治理规则。

## 5. 映射表

| Superpowers 思路 | 食期管家中的对应机制 | 当前处理 |
| --- | --- | --- |
| brainstorming | project-architect / main-brain 澄清目标 | 吸收 |
| writing-plans | VERSION_ROADMAP + 阶段计划 + require.txt | 吸收 |
| TDD | 日期工具函数优先验证用例 | 部分吸收 |
| verification-before-completion | PR 测试说明 + code-reviewer 审核 | 吸收 |
| requesting-code-review | code-reviewer 角色 | 吸收 |
| finishing-a-development-branch | Codex PR + 用户合并决定 | 吸收 |
| writing-skills | skill proposal 流程 | 部分吸收 |
| subagent-driven-development | 多 AI 角色窗口 | 暂不采用插件实现 |
| using-git-worktrees | 后续并行开发再评估 | 暂不采用 |

## 6. 对当前 MVP v0.1 的影响

从现在开始，main-brain 在推进 MVP v0.1 时应吸收以下规则：

1. 阶段开始前先确认目标和不做什么。
2. 每个 Codex 任务必须小而可审查。
3. 每个 Codex 任务必须有验收标准。
4. 每个代码 PR 必须有测试或手动验证说明。
5. 每个代码 PR 必须进入 code-reviewer 审核。
6. 不因为“方法论工具很强”而引入额外复杂度。

## 7. 当前决策

当前决策：

- 不安装 Superpowers。
- 不把 Superpowers 作为 Codex 默认插件。
- 不让 Superpowers 覆盖 `$miniapp-food-expiry`。
- 只吸收其流程思想。
- 后续如需安装，必须由 project-architect 重新评估并获得用户确认。
