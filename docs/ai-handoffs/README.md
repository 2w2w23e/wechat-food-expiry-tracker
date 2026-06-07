# AI 交接协议

本目录用于保存“食期管家”项目中不同 AI 角色之间的阶段交接、任务交接和审核交接。

交接文档用于保存关键长信息，不用于保存普通聊天流水账。

## 1. 什么时候创建交接文件

以下情况应创建交接文件：

1. main-brain 判断一个小阶段结束，需要交给 project-architect 设计下一阶段。
2. project-architect 给 main-brain 下达下一阶段方向。
3. main-brain 给 code-reviewer 交付 PR 审核任务。
4. code-reviewer 完成重要审核，需要把结果交还 main-brain。
5. documentation-ai 完成关键文档更新，需要通知 main-brain 或 project-architect。
6. 出现关键风险、阶段边界变化、数据模型变化或工具/Skill 策略变化。

## 2. 什么时候不创建交接文件

以下情况不需要创建交接文件：

1. 普通简短问答。
2. 临时解释某段代码。
3. 小的文字修正。
4. 用户尚未确认的想法草稿。
5. 不影响阶段推进的聊天记录。

## 3. 文件命名规则

建议格式：

```text
YYYY-MM-DD-from-role-to-role-topic.md
```

示例：

```text
2026-06-07-main-brain-to-project-architect-phase-0-complete.md
2026-06-07-project-architect-to-main-brain-phase-1-start.md
2026-06-07-main-brain-to-code-reviewer-pr-3-review.md
```

## 4. 第一行状态标签

交接文件第一行必须是状态标签。

未读格式：

```text
STATUS: UNREAD | TO: project-architect | FROM: main-brain | TYPE: phase-handoff | CREATED: YYYY-MM-DD
```

已读格式：

```text
STATUS: READ | TO: project-architect | FROM: main-brain | READ_BY: project-architect | READ_AT: YYYY-MM-DD
```

## 5. 角色名称

统一使用以下角色名：

- `user`
- `project-architect`
- `main-brain`
- `documentation-ai`
- `codex-task-generator`
- `codex`
- `code-reviewer`
- `learning-coach`
- `dev-guide`

## 6. 交接文件模板

```md
STATUS: UNREAD | TO: project-architect | FROM: main-brain | TYPE: phase-handoff | CREATED: YYYY-MM-DD

# 交接标题

## 1. 背景

说明为什么创建这个交接。

## 2. 已完成内容

- 

## 3. 关键决策

- 

## 4. 修改过的文件

- 

## 5. 当前风险

- 

## 6. 未完成事项

- 

## 7. 请求接收方处理

- 

## 8. 建议下一步

- 
```

## 7. 读取和更新规则

1. 接收方读取后，应把第一行 `STATUS: UNREAD` 改为 `STATUS: READ`。
2. 接收方应补充 `READ_BY` 和 `READ_AT`。
3. 如果读取后产生新的任务，应创建新的交接文件或更新 `docs/PHASE_STATUS.md`。
4. 不要在同一个交接文件里反复追加长对话。

## 8. 注意事项

- 交接文件应简洁，重点写事实、决策、风险和请求。
- 不要写入 API Key、密钥、账号、token 或其他敏感信息。
- 不要把未确认的 OCR、条形码、AI 识别结果当成正式数据写入文档。
- 如果交接涉及 PR 审核，应写明 PR 编号、分支、修改范围和测试方式。
