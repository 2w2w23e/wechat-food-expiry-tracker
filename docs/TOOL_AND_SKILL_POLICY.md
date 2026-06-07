# 工具与 Skill 引入策略

本文件定义“食期管家”项目中外部工具、开发依赖、Codex Skill 和仓库内 Skill 的引入规则。

目标：让项目可以利用好工具提升开发效率，同时避免工具过多、权限过大、来源不明或引入安全风险。

## 1. 基本原则

1. 用户是最终审批人。
2. project-architect 负责工具和 Skill 引入策略的设计、审核和更新。
3. Codex 可以根据任务引入工具、依赖或 Skill，但必须得到明确授权，并写入 `/goal + require.txt`。
4. Codex 不能在普通功能任务中自行安装工具、添加依赖、创建 Skill 或修改关键规则文件。
5. 所有工具、依赖和 Skill 的引入都必须说明用途、收益、风险、替代方案和回退方式。
6. 不为了追新而引入工具。优先使用当前最小可行工作流。
7. 任何涉及密钥、云函数、OCR、条形码、AI、数据库权限的工具，都必须遵守安全原则：密钥不进前端，识别结果不自动入库，用户确认后保存。

## 2. 工具分类

### 2.1 允许优先使用的基础工具

这些是当前项目默认工作流的一部分：

- GitHub：代码、文档、Issue、PR、审查和版本记录。
- VSCode：本地编辑代码和文档。
- 微信开发者工具：运行和调试小程序。
- ChatGPT 项目：多角色协作、学习、指导和文档讨论。
- Codex：执行明确的小范围代码或文档任务。

### 2.2 可评估引入的辅助工具

这些工具或能力可以由 project-architect 或 main-brain 提出评估，但不得直接进入主流程：

- OpenAI 官方 Skills，例如 `define-goal`、`create-plan`、`security-threat-model`。
- GitHub Actions，用于后续自动检查、测试、lint 或文档格式检查。
- 安全检查工具，用于云函数、API、密钥和数据库权限审查。
- 代码格式化或 lint 工具，用于后续项目规模变大后的质量控制。

### 2.3 谨慎引入的工具

以下工具需要更严格评估：

- 会执行 shell 命令或自动修改大量文件的 agent 工具。
- 第三方未验证 Codex Skill。
- 会访问外部 API 或上传项目代码的工具。
- 需要密钥、token、个人账号登录态或云服务权限的工具。
- 会自动生成、合并或发布代码的工具。

## 3. Codex 引入工具规则

Codex 可以帮助引入工具，但必须满足：

1. `require.txt` 明确写出允许引入的工具名称和目的。
2. `require.txt` 明确允许修改的文件，例如配置文件、文档或 CI 文件。
3. PR 描述必须说明：
   - 为什么引入该工具
   - 它解决什么问题
   - 可能带来的风险
   - 如何运行或验证
   - 如何回退
4. 如果工具需要密钥，Codex 只能写文档或云函数环境变量说明，不能把密钥写进仓库。
5. Codex 不能在普通功能任务中顺手引入依赖或工具。
6. Codex 不能自行启用自动合并、自动部署或自动发布流程。

## 4. Skill 使用规则

### 4.1 默认项目 Skill

本项目默认 Skill 是：

```text
$miniapp-food-expiry
```

所有与“食期管家”仓库相关的 Codex 任务，`require.txt` 第一行应写：

```text
Use $miniapp-food-expiry.
```

### 4.2 可使用的外部 Skill

可评估使用的 Skill 类型：

- `define-goal`：把模糊目标转成可验收目标。
- `create-plan`：在复杂任务前只读规划，不直接改文件。
- `security-threat-model`：接入云函数、OCR、条形码、AI 前做威胁建模。
- `security-best-practices`：审查云函数、数据库权限和密钥相关实现。
- `gh-fix-ci`：后续存在 GitHub Actions 后修复 CI 失败。
- `gh-address-comments`：后续处理 PR review comments。
- `openai-docs`：后续接入 OpenAI API 前查官方文档。

引入外部 Skill 前，必须先阅读对应 `SKILL.md`，确认其来源、用途、脚本和风险。

### 4.3 安装 Skill 的审批规则

1. project-architect 可以建议安装 Skill。
2. main-brain 可以请求评估某个 Skill 是否适合当前阶段。
3. Codex 可以在任务中使用已安装 Skill。
4. Codex 不得自行安装新 Skill，除非 `require.txt` 明确授权。
5. 未知来源、包含脚本、需要网络访问、需要密钥或会执行命令的 Skill，必须先由 project-architect 审查。
6. 用户最终确认是否安装或保留该 Skill。

## 5. Codex 是否可以创建 Skill

结论：可以，但不能默认自动启用。

Codex 在任务过程中如果发现某个 Skill 很有价值，应遵循以下流程：

### 5.1 默认流程：先提出 Skill 提案

Codex 应优先创建 Skill 提案文档，而不是直接创建可启用 Skill。

建议路径：

```text
docs/skill-proposals/YYYY-MM-DD-skill-name.md
```

提案必须包含：

1. Skill 名称
2. 适用场景
3. 触发条件
4. 能解决的问题
5. 可能风险
6. 是否包含脚本
7. 需要读取或修改哪些文件
8. 与 `$miniapp-food-expiry` 的关系
9. 是否建议正式创建 `.agents/skills/<skill-name>/SKILL.md`

### 5.2 正式创建 Skill 的条件

只有满足以下条件，Codex 才可以正式创建 Skill：

1. 用户或 project-architect 明确授权。
2. `require.txt` 明确允许创建 `.agents/skills/<skill-name>/SKILL.md`。
3. Skill 不得包含不必要脚本。
4. Skill 的 `description` 必须写清楚触发条件，避免误触发。
5. Skill 不得绕过项目安全原则。
6. Skill 不得与 `$miniapp-food-expiry` 的核心规则冲突。

### 5.3 禁止行为

Codex 不得：

- 在普通代码任务中顺手创建 Skill。
- 创建会自动保存 OCR、条形码或 AI 结果的 Skill。
- 创建要求把密钥放进前端的 Skill。
- 创建鼓励大范围重构的 Skill。
- 创建来源不明或复制未知第三方脚本的 Skill。
- 未经授权安装第三方 Skill。

## 6. 工具引入评估模板

当考虑引入新工具或新 Skill 时，先填写以下模板：

```md
# 工具 / Skill 评估

## 名称

## 来源

## 解决的问题

## 当前是否必须

## 替代方案

## 需要修改的文件

## 是否需要密钥或外部账号

## 安全风险

## 回退方式

## 建议结论

- [ ] 现在引入
- [ ] 后续再评估
- [ ] 不建议引入
```

## 7. 当前建议

当前阶段建议保持工具链简单：

- 必须使用：`$miniapp-food-expiry`
- 可评估使用：`define-goal`、`create-plan`
- 第二阶段前评估：`security-threat-model`、`security-best-practices`
- 有 PR / CI 后再评估：`gh-address-comments`、`gh-fix-ci`
- 接入 OpenAI API 前再评估：`openai-docs`

现阶段不建议引入复杂 agent 平台、自动部署工具或需要额外密钥的外部服务。
