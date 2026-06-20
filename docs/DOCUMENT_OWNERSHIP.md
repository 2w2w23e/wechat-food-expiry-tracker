# 文档与代码修改权限

本文件定义“食期管家”项目中不同 AI 角色对仓库文件的修改权限。

## 当前状态说明

本仓库已启用 RepoMind OS。

当前最高治理层是：

```text
.ai-governance/
```

本文件属于旧 `docs/` 地基文档，继续保留为权限说明和历史导入证据。

如果本文件与 `.ai-governance/PROJECT_STATE.md`、`.ai-governance/handoff/CURRENT.md`、`.ai-governance/decisions/*` 或后续 `.ai-governance/roles/*` 冲突，以 `.ai-governance/` 为准。

## 1. 总原则

1. 用户是最终负责人，拥有所有文件的最终决定权。
2. `.ai-governance/` 是最高治理文件区。
3. RepoMind OS / Project Governor 负责项目状态、决策、角色迁移、交接和写回。
4. Codex 拥有代码修改权，也可以修改文档，但必须严格受 `/goal + require.txt` 的允许范围限制。
5. 其他 AI 角色只能修改自己职责范围内的文档，不能修改运行时代码。
6. 关键保护文档不得被普通任务随意修改，除非任务明确授权，并说明修改原因。
7. 所有修改都应通过 GitHub commit / PR 留痕。
8. Codex 不得自行合并 PR。

## 2. 文件分级

### 2.1 最高治理文件

包括：

- `.ai-governance/BOOT.md`
- `.ai-governance/CONTEXT_INDEX.md`
- `.ai-governance/FIRST_WINDOW_PROTOCOL.md`
- `.ai-governance/PROJECT_INTAKE.md`
- `.ai-governance/PROJECT_STATE.md`
- `.ai-governance/handoff/**`
- `.ai-governance/decisions/**`
- `.ai-governance/roles/**`
- `.ai-governance/memory/**`
- `.ai-governance/user_preferences/**`
- `.ai-governance/checklists/**`
- `.ai-governance/prompts/**`

修改权限：

- 用户可以修改。
- RepoMind OS / Project Governor 可以在用户授权或协议允许范围内修改。
- 其他角色不能自行修改 `.ai-governance/**`。
- Codex 只有在 `require.txt` 明确授权时才能修改 `.ai-governance/**`。
- 涉及项目方向、角色权限、安全边界、用户偏好或 durable memory 的写回，应遵守 `.ai-governance/WRITEBACK_PROTOCOL.md`。

### 2.2 运行时代码文件

包括但不限于：

- `apk/**`
- `miniprogram/app.js`
- `miniprogram/app.json`
- `miniprogram/app.wxss`
- `miniprogram/project.config.json`
- `miniprogram/pages/**`
- `miniprogram/components/**`
- `miniprogram/utils/**`
- `miniprogram/services/**`
- `cloudfunctions/**`

修改权限：

- 只有 Codex 可以修改。
- 必须有明确的 `/goal + require.txt`。
- `require.txt` 必须写明允许修改的具体文件或目录。
- 代码修改后必须创建 PR，并由 code-reviewer 审核。
- 当前 APK 开发期间，默认只修改 `apk/**`；`miniprogram/**` 已封存，除非用户明确要求恢复小程序端，否则不应修改。

### 2.3 关键保护文档

以下文件属于关键保护文档：

- `AGENTS.md`
- `.agents/skills/miniapp-food-expiry/SKILL.md`
- `docs/AI_COLLABORATION.md`
- `docs/DOCUMENT_OWNERSHIP.md`
- `docs/TOOL_AND_SKILL_POLICY.md`
- `docs/CODEX_WORKFLOW.md`
- `docs/FEATURE_SCOPE.md`
- `docs/DATA_MODEL.md`
- `docs/VERSION_ROADMAP.md`

修改权限：

- 用户可以修改。
- RepoMind OS / Project Governor 可以修改或授权修改。
- Codex 可以修改，但必须在 `require.txt` 中明确列入 `Allowed to modify`，并说明修改目的。
- documentation-ai 默认不能修改 `AGENTS.md`、`.agents/skills/**/SKILL.md` 或 `.ai-governance/**`，除非 RepoMind OS / Project Governor 明确授权。
- main-brain、code-reviewer、learning-coach 不应直接修改这些文件，只能提出修改建议或创建交接文档。

### 2.4 普通项目文档

包括但不限于：

- `README.md`
- `docs/project-brief.md`
- `docs/learning-map.md`
- `docs/decision-log.md`
- `docs/PHASE_STATUS.md`
- `docs/ROLE_PROMPTS.md`
- `docs/ai-handoffs/**`
- `docs/reviews/**`
- `docs/codex-tasks/**`

修改权限：

- documentation-ai 可以修改通用项目文档。
- main-brain 可以修改阶段状态和交接文件。
- code-reviewer 可以修改审核记录。
- learning-coach 可以修改学习相关文档。
- Codex 可以修改文档，但必须受 `require.txt` 限制。
- 如果普通文档内容会改变项目方向、角色权限、安全边界或版本决策，应回到 RepoMind OS / Project Governor。

## 3. 角色权限表

| 角色 | 可修改代码 | 可修改普通文档 | 可修改关键保护文档 | 可修改 `.ai-governance/**` | 备注 |
| --- | --- | --- | --- | --- | --- |
| user | 是 | 是 | 是 | 是 | 最终负责人 |
| RepoMind OS / Project Governor | 否 | 是 | 是 | 是 | 当前最高治理层 |
| project-architect | 否 | 是 | 建议或被授权 | 否，除非授权 | 旧地基规划角色，不能高于 RepoMind OS |
| main-brain | 否 | 限定 | 否 | 否 | 主要修改 `PHASE_STATUS` 和交接文件 |
| documentation-ai | 否 | 是 | 限定 | 否 | 默认不能改 AGENTS、Skill 和 governance 文件 |
| codex-task-generator | 否 | 限定 | 否 | 否 | 主要生成任务，不做实现 |
| codex | 是 | 是 | 限定 | 限定 | 只能按 `require.txt` 修改 |
| code-reviewer | 否 | 限定 | 否 | 否 | 主要写审核记录 |
| learning-coach | 否 | 限定 | 否 | 否 | 主要维护学习文档 |

## 4. Codex 修改规则

Codex 可以修改代码和文档，但必须满足：

1. 任务必须使用 `/goal + require.txt`。
2. `require.txt` 第一行必须写 `Use $miniapp-food-expiry.`。
3. `require.txt` 必须明确：
   - Read first
   - Allowed to modify
   - Do not modify
   - Requirements
   - Acceptance criteria
   - Tests
   - Out of scope
4. 如果没有把某个文件列入 `Allowed to modify`，Codex 不应修改它。
5. 如果 Codex 发现必须修改未授权文件，应停止并说明原因，而不是自行修改。
6. Codex 完成后应提交 commit 并创建 PR，但不得自行 merge。
7. Codex 不得自行引入依赖、外部服务、工具或 Skill，除非任务明确授权。
8. Codex 不得把任何密钥写入小程序前端或公开仓库。

## 5. 文档修改触发条件

以下情况需要更新文档：

- 项目定位、MVP 范围或阶段边界发生变化。
- 数据模型字段发生变化。
- 新增页面、工具函数、云函数、服务层模块。
- 引入条形码、OCR、AI、云数据库权限或外部 API。
- 发现安全风险、权限风险或数据确认流程问题。
- 阶段结束，需要给其他 AI 角色交接。
- RepoMind OS 项目状态、决策、角色权限或写回规则发生变化。

## 6. 越权处理

如果发现 AI 角色越权修改文件：

1. 不要继续基于该修改开发。
2. 记录越权文件和原因。
3. 交给 code-reviewer 判断风险。
4. 必要时要求 Codex 回滚或创建修复 PR。
5. 如果越权涉及密钥、API Key 或安全信息，应立即视为高风险问题处理。
6. 如果越权涉及 `.ai-governance/**`，应回到 RepoMind OS / Project Governor 判断是否保留、修正或回滚。
