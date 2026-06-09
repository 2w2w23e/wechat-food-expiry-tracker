STATUS: READ | TO: project-architect | FROM: main-brain | READ_BY: project-architect | READ_AT: 2026-06-09

# 阶段 0 结束交接：项目地基与 AI 协作体系阶段

## 1. 背景

main-brain 已按要求读取并检查以下文件：

- `README.md`
- `docs/PHASE_STATUS.md`
- `docs/FEATURE_SCOPE.md`
- `docs/DATA_MODEL.md`
- `docs/AI_COLLABORATION.md`
- `docs/DOCUMENT_OWNERSHIP.md`
- `docs/TOOL_AND_SKILL_POLICY.md`
- `docs/TOOL_RESEARCH.md`
- `docs/ROLE_PROMPTS.md`
- `docs/ai-handoffs/README.md`

判断结果：阶段 0 可以结束，项目可以进入阶段 1：MVP 核心数据与日期计算基础。

## 2. 已完成内容

阶段 0 已完成以下地基工作：

- 明确新版产品定位：面向家庭 / 老年友好的食品库存与保质期管理微信小程序。
- 明确 MVP 范围：手动录入、分类、数量、剩余数量、日期计算、`expiryDate` 排序、简单筛选和总览统计。
- 明确第二阶段和后续阶段边界：条形码、商品查询、规则化智能提醒、OCR / AI、大模型对话和食谱规划暂不进入 MVP 第一批代码任务。
- 固定核心数据模型，明确 `expiryDate` 是排序和提醒的标准字段。
- 固定 `quantity` 和 `remainingQuantity` 分离保存。
- 明确条形码、OCR、AI 识别结果必须经过用户确认后才能保存。
- 明确 API Key、OCR Key、AI Key、商品库密钥、云开发密钥不得写入小程序前端或公开仓库。
- 建立 Codex 的 `/goal + require.txt` 工作流，并要求 `require.txt` 第一行写 `Use $miniapp-food-expiry.`。
- 建立 AI 角色协作体系、文档与代码修改权限、工具与 Skill 引入策略、工具调研记录和交接协议。

## 3. 关键决策

- 阶段 1 不先做页面 UI，而是先做食品日期计算工具函数。
- 原因：`expiryDate` 是后续手动录入、列表排序、到期状态、筛选、提醒和识别确认流程的核心基础。
- 阶段 1 第一个任务必须小而可审查，只允许实现日期计算工具函数和基础验证。
- 阶段 1 第一个任务暂不实现：食品录入页面、食品列表页面、云数据库、条形码扫描、OCR / AI、大模型对话、真实提醒调度。
- 当前不建议引入新依赖。日期计算任务中可让 Codex 评估原生 Date 是否足够，但不得自行引入 Day.js 或其他依赖，除非任务明确授权。

## 4. 修改过的文件

本次阶段结束由 main-brain 修改：

- `docs/PHASE_STATUS.md`
- `docs/ai-handoffs/2026-06-09-main-brain-to-project-architect-phase-0-complete.md`

阶段 0 已存在并作为依据的关键文档包括：

- `README.md`
- `AGENTS.md`
- `.agents/skills/miniapp-food-expiry/SKILL.md`
- `docs/FEATURE_SCOPE.md`
- `docs/DATA_MODEL.md`
- `docs/CODEX_WORKFLOW.md`
- `docs/AI_COLLABORATION.md`
- `docs/DOCUMENT_OWNERSHIP.md`
- `docs/TOOL_AND_SKILL_POLICY.md`
- `docs/TOOL_RESEARCH.md`
- `docs/ROLE_PROMPTS.md`
- `docs/ai-handoffs/README.md`

## 5. 当前风险

- 日期计算可能在闰年、月底日期、无效日期和时区处理上出现边界错误。
- Codex 任务如果范围过大，可能顺手修改页面 UI、数据模型或引入依赖。
- 过早实现条形码、AI 对话或智能提醒，可能导致 MVP 失焦。
- 外部 API、密钥或商品库凭据如果误入前端或公开仓库，会造成安全风险。
- 未确认的条形码、OCR 或 AI 识别结果如果直接进入正式食品记录，会破坏项目核心安全边界。

## 6. 未完成事项

- codex-task-generator 尚未生成阶段 1 第一个 `/goal + require.txt`。
- Codex 尚未创建阶段 1 第一个功能 PR。
- code-reviewer 尚未审核阶段 1 第一个功能 PR。
- 阶段 1 完成后，需要根据代码实现情况同步更新文档或创建新的交接文件。

## 7. 请求接收方处理

请 project-architect 读取本交接后：

- 确认阶段 0 结束判断是否合理。
- 如认可，请设计或批准阶段 1 的阶段边界。
- 如需要，可给 main-brain 下达阶段 1 的进一步边界要求。
- 如认为阶段 0 地基仍缺失，请指出必须补齐的文档或规则。

## 8. 建议下一步

建议进入阶段 1 的第一个最小任务：

```text
/goal 实现食品日期计算工具函数，具体细节见 require.txt
```

请 main-brain 要求 codex-task-generator 生成完整 `require.txt`，并确保：

- 第一行是 `Use $miniapp-food-expiry.`
- 只允许修改日期计算工具函数相关文件和必要的验证说明。
- 不允许修改页面 UI。
- 不允许实现录入、列表、云数据库、条形码、OCR、AI 或提醒调度。
- 不允许引入新依赖，除非用户或 project-architect 明确授权。
- 必须覆盖天、月、年、闰年、月底、无效日期等日期计算边界。
- 必须保持 `expiryDate` 作为统一最终可食用日期字段。
