# 文档与代码修改权限

本文件定义“食期管家”项目中不同 AI 角色对仓库文件的修改权限。

目标：避免 AI 角色越权修改代码、关键规则、阶段文档或其他角色负责的文档。

## 1. 总原则

1. 用户是最终负责人，拥有所有文件的最终决定权。
2. project-architect 拥有项目地基、角色体系、阶段框架和关键规则文档的最高协调权。
3. Codex 拥有代码修改权，也可以修改文档，但必须严格受 `/goal + require.txt` 的允许范围限制。
4. 其他 AI 角色只能修改自己职责范围内的文档，不能修改运行时代码。
5. 关键保护文档不得被普通任务随意修改，除非 `require.txt` 明确授权，并说明修改原因。
6. 所有修改都应通过 GitHub commit / PR 留痕。
7. Codex 不得自行合并 PR。

## 2. 文件分级

### 2.1 运行时代码文件

包括但不限于：

- `app.js`
- `app.json`
- `app.wxss`
- `project.config.json`
- `pages/**`
- `components/**`
- `utils/**`
- `services/**`
- `cloudfunctions/**`

修改权限：

- 只有 Codex 可以修改。
- 必须有明确的 `/goal + require.txt`。
- `require.txt` 必须写明允许修改的具体文件或目录。
- 代码修改后必须创建 PR，并由 code-reviewer 审核。

### 2.2 关键保护文档

以下文件属于关键保护文档：

- `AGENTS.md`
- `.agents/skills/miniapp-food-expiry/SKILL.md`
- `docs/AI_COLLABORATION.md`
- `docs/DOCUMENT_OWNERSHIP.md`
- `docs/TOOL_AND_SKILL_POLICY.md`
- `docs/CODEX_WORKFLOW.md`
- `docs/FEATURE_SCOPE.md`
- `docs/DATA_MODEL.md`

修改权限：

- project-architect 可以直接修改。
- Codex 可以修改，但必须在 `require.txt` 中明确列入 `Allowed to modify`，并说明修改目的。
- documentation-ai 默认不能修改 `AGENTS.md` 和 `.agents/skills/**/SKILL.md`，除非 project-architect 明确授权。
- main-brain、code-reviewer、learning-coach 不应直接修改这些文件，只能提出修改建议或创建交接文档。

### 2.3 普通项目文档

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

## 3. 角色权限表

| 角色 | 可修改代码 | 可修改普通文档 | 可修改关键保护文档 | 备注 |
| --- | --- | --- | --- | --- |
| user | 是 | 是 | 是 | 最终负责人 |
| project-architect | 否 | 是 | 是 | 最高 AI 协调角色，负责地基和规则 |
| main-brain | 否 | 限定 | 否 | 主要修改 `PHASE_STATUS` 和交接文件 |
| documentation-ai | 否 | 是 | 限定 | 默认不能改 AGENTS 和 Skill |
| codex-task-generator | 否 | 限定 | 否 | 主要生成任务，不做实现 |
| codex | 是 | 是 | 限定 | 只能按 `require.txt` 修改 |
| code-reviewer | 否 | 限定 | 否 | 主要写审核记录 |
| learning-coach | 否 | 限定 | 否 | 主要维护学习文档 |

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

## 5. 文档修改触发条件

以下情况需要更新文档：

- 项目定位、MVP 范围或阶段边界发生变化。
- 数据模型字段发生变化。
- 新增页面、工具函数、云函数、服务层模块。
- 引入条形码、OCR、AI、云数据库权限或外部 API。
- 发现安全风险、权限风险或数据确认流程问题。
- 阶段结束，需要给其他 AI 角色交接。

## 6. 越权处理

如果发现 AI 角色越权修改文件：

1. 不要继续基于该修改开发。
2. 记录越权文件和原因。
3. 交给 code-reviewer 判断风险。
4. 必要时要求 Codex 回滚或创建修复 PR。
5. 如果越权涉及密钥、API Key 或安全信息，应立即视为高风险问题处理。
