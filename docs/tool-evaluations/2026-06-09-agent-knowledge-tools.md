# 工具评估：CodeGraph、Understand Anything、Superpowers

评估日期：2026-06-09

评估角色：project-architect

相关工具：

- `colbymchenry/codegraph`
- `Egonex-AI/Understand-Anything`，原链接 `Lum1104/Understand-Anything` 已跳转
- `obra/superpowers`

## 1. 总结结论

当前阶段不建议直接把这三个工具引入主开发流程。

推荐策略：

1. 暂不改变当前主流程：ChatGPT 项目多角色 + GitHub + VSCode + 微信开发者工具 + Codex。
2. 先把三者记录为“后续可试用工具”。
3. 现阶段可以借鉴 Superpowers 的方法论，但不安装它。
4. CodeGraph 可作为后续本地试验的第一候选，但不应现在写入仓库配置或强制所有角色使用。
5. Understand Anything 更适合项目代码量变大后的学习、架构理解和可视化，不适合当前最小骨架阶段。
6. 如果未来试用 CodeGraph 或 Understand Anything，应先更新 `.gitignore`，避免提交本地索引目录。

## 2. 当前项目背景

“食期管家”当前处于 MVP v0.1 阶段，下一步重点是：

- 食品日期计算工具函数。
- 食品数据结构。
- 食品列表。
- 手动录入。
- 筛选、统计和本地保存。

当前仓库代码量仍小，尚未进入复杂架构阶段，因此“代码知识图谱”和“大型 agent 方法论”暂时不是瓶颈。

## 3. colbymchenry/codegraph

### 3.1 主要用途

CodeGraph 是面向 AI coding agent 的本地代码知识图谱工具。它通过预索引代码结构，为 Claude Code、Codex、Cursor、Gemini 等 agent 提供代码搜索、调用关系、影响分析和结构理解能力。

### 3.2 可能收益

- 减少 agent 反复 grep / read 文件的成本。
- 帮助 Codex 更快理解较大代码库。
- 支持影响分析，后续做云函数、页面、服务层联动修改时可能有价值。
- 标称 100% local，不需要外部 API Key。

### 3.3 当前风险

- 它是 MCP / agent 配置类工具，会修改本地 agent 配置。
- 当前项目代码量很小，收益有限。
- 安装命令可能通过 `npx` 或 shell installer 执行，必须由用户明确确认。
- 会生成本地 `.codegraph/` 索引目录，不应提交到仓库。
- 如果自动给 agent 添加权限，需要特别谨慎。

### 3.4 当前建议

阶段：后续评估，不进入当前阶段主流程。

优先级：三者中最高，但仍不建议现在正式引入。

建议试用时机：

- 项目完成多个页面、工具函数、服务层或云函数后。
- Codex 或 code-reviewer 开始频繁需要理解跨文件调用关系时。
- 阶段 3 或阶段 4 之后再试。

建议试用方式：

1. 先只做本地试验，不提交配置。
2. 先运行只打印配置或交互式安装，不自动全局修改所有 agent。
3. 试用前把 `.codegraph/` 加入 `.gitignore`。
4. 让 code-reviewer 检查是否有 `.codegraph/` 或 agent 本地配置误入仓库。

## 4. Egonex-AI/Understand-Anything

### 4.1 主要用途

Understand Anything 用于把代码库、知识库或文档转成可搜索、可提问、可视化的交互式知识图谱。它可以分析文件、函数、类、依赖关系，并生成 dashboard。

### 4.2 可能收益

- 适合学习大型代码库。
- 适合 onboarding 和架构理解。
- 支持中文输出和 dashboard，可帮助用户更直观理解项目结构。
- 后续当项目文档和代码都变多时，可能帮助 learning-coach 或 documentation-ai 解释项目。

### 4.3 当前风险

- 工具较重，对当前小项目收益有限。
- 会生成 `.understand-anything/knowledge-graph.json` 等本地分析结果，不应提交仓库。
- 安装方式可能修改本地插件或 agent 配置。
- 多 agent 分析可能消耗时间和 token。
- 对当前 MVP 阶段的日期工具函数、列表和录入页面帮助不直接。

### 4.4 当前建议

阶段：后续学习和架构理解阶段再评估。

优先级：低于 CodeGraph。

建议试用时机：

- MVP v0.1 接近完成，项目文件变多后。
- 用户希望通过图谱理解页面、工具函数、数据模型、云函数之间关系时。
- 需要生成项目 onboarding 文档时。

建议试用方式：

1. 先只在本地或单独分支试用。
2. 使用中文参数或中文模式。
3. 把 `.understand-anything/` 加入 `.gitignore`。
4. 不让它自动修改项目源码。

## 5. obra/superpowers

### 5.1 主要用途

Superpowers 是一套 agentic skills framework 和软件开发方法论，包含头脑风暴、计划、TDD、代码审查、worktree、分支完成等流程。

### 5.2 可能收益

- 流程意识强，强调先澄清目标再写代码。
- 强调 TDD、YAGNI、DRY、代码审查和验证。
- 可为 Codex 工作流提供方法论参考。
- 包含 writing-skills，可作为后续创建 Skill 的参考。

### 5.3 当前风险

- 与本项目已经建立的多 AI 角色体系高度重叠。
- 可能覆盖或干扰 `$miniapp-food-expiry`、main-brain、codex-task-generator 和 code-reviewer 的职责边界。
- 它会让 agent 进入自己的强流程，不一定符合本项目“project-architect 定版本、main-brain 拆阶段、Codex 执行”的治理模式。
- 如果启用 writing-skills，可能诱导自动创建 Skill；这与本项目“先提案、再授权、再创建”的策略冲突。

### 5.4 当前建议

阶段：暂不安装。

优先级：作为方法论参考，而不是作为插件引入。

推荐做法：

- 借鉴它的 TDD、verification-before-completion、requesting-code-review 等思想。
- 不安装插件。
- 不让它替代现有 AI 协作体系。
- 后续如要引入，必须先由 project-architect 重新评估，并明确它和 `$miniapp-food-expiry` 的优先级关系。

## 6. 推荐排序

当前推荐排序：

1. CodeGraph：后续优先试用候选，但不是现在。
2. Understand Anything：后续学习和架构可视化候选。
3. Superpowers：只借鉴方法论，暂不安装。

## 7. 是否允许 Codex 引入

当前结论：暂不允许 Codex 正式安装这三个工具。

Codex 可以做的事情：

- 创建或更新工具评估文档。
- 在明确授权下更新 `.gitignore`，加入 `.codegraph/` 和 `.understand-anything/`。
- 在明确授权下写本地试用说明文档。
- 在明确授权下创建一个“不修改代码”的试用 PR。

Codex 不能做的事情：

- 自动运行 `curl | sh`、`npx`、插件安装或 marketplace 安装。
- 自动修改用户本地 Codex、Claude、Cursor、Gemini 等 agent 配置。
- 自动启用 MCP server。
- 自动创建或安装新 Skill。
- 自动提交 `.codegraph/` 或 `.understand-anything/` 目录。

## 8. 如果用户决定试用 CodeGraph

建议先由 codex-task-generator 生成一个“准备试用 CodeGraph”的文档任务，不安装工具，只做保护措施：

- 更新 `.gitignore`：加入 `.codegraph/`。
- 创建 `docs/local-tools/CODEGRAPH_TRIAL.md`。
- 写清楚本地安装、初始化、验证和卸载步骤。
- 写清楚不得提交本地索引和 agent 配置。

正式安装应由用户在本地终端执行，而不是让 Codex 在仓库任务中自动执行。

## 9. 如果用户决定试用 Understand Anything

建议先做文档任务：

- 更新 `.gitignore`：加入 `.understand-anything/`。
- 创建 `docs/local-tools/UNDERSTAND_ANYTHING_TRIAL.md`。
- 写清楚本地安装、中文分析、dashboard、卸载和清理步骤。
- 写清楚不得提交生成的知识图谱。

正式安装应由用户在本地终端执行。

## 10. 如果用户决定试用 Superpowers

建议先不安装。

如果用户强烈希望试用，应先做一份冲突评估：

- 它与 `docs/AI_COLLABORATION.md` 是否冲突。
- 它与 `$miniapp-food-expiry` 是否冲突。
- 它是否会让 Codex 越过 main-brain 和 codex-task-generator。
- 它是否会自动创建 Skill 或强制 TDD 流程。
- 它是否适合微信小程序新手学习节奏。

只有评估通过后，才考虑本地插件安装。
