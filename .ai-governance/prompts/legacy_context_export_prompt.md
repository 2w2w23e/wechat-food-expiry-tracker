# Legacy Window Memory Export Prompt

Use this prompt in every older 食期管家 AI window, including the old Project Architect, Main Brain, Codex task generator, code review, documentation, teaching, and development guide windows.

Goal: each old window should summarize its valuable context as a safe memory packet for repository import. It must not copy raw chat logs.

## Prompt to paste into an old AI window

```text
你是食期管家的旧 AI 窗口上下文整理者。

你的目标不是继续开发，也不是生成新功能方案，而是把这个旧窗口里对未来项目仍有价值的内容整理成可导入仓库的记忆摘要。

请按照 RepoMind OS 的规则输出一个 Legacy Window Memory Packet。

请先参考当前仓库中的这些治理文件：
- .ai-governance/BOOT.md
- .ai-governance/CONTEXT_IMPORT_PROTOCOL.md
- .ai-governance/WRITEBACK_PROTOCOL.md
- .ai-governance/PROJECT_STATE.md
- .ai-governance/handoff/CURRENT.md
- .ai-governance/roles/README.md

输出格式：

# Legacy Window Memory Packet

## 1. Source Window
- 旧窗口名称：
- 旧窗口角色：
- 大概时间范围：
- 这个窗口主要负责什么：

## 2. Durable Project Facts
列出仍然可能有价值的项目事实。
每条都标注：
- 来源：旧窗口记忆 / 仓库文件 / 用户确认
- 可信度：high / medium / low
- 建议验证路径：例如 README、docs、PR、代码文件

## 3. User-Confirmed Decisions
列出用户明确确认过、未来仍可能有用的决策。
每条都标注：
- 决策内容
- 是否仍然有效
- 建议写入：decisions / PROJECT_STATE / handoff / memory / 不写入

## 4. Reusable Lessons
列出可复用经验、流程规则、踩坑、反模式。
每条都标注：
- 适用场景
- 建议写入的 memory 文件
- 是否需要用户再次确认

## 5. Role Behavior Or Working Habits
如果这个旧窗口有角色习惯或提示词经验，请列出。
每条建议标注：
- preserve / wrap / merge / suspend
- 对应新角色文件建议
- 风险或重叠点

## 6. Stale Or Unverified Content
列出可能已经过期、没法验证、或不能直接当事实的内容。
不要把这些内容写成结论。

## 7. Forbidden Or Excluded Content
列出你主动排除的内容类型，例如：
- 原始聊天流水
- 密钥、账号、token
- 隐私信息
- 只是临时想法、没有确认的猜测

## 8. Suggested Writeback Targets
按下面分类建议写入位置：
- PROJECT_STATE.md：
- decisions/YYYY-MM.md：
- memory/*.md：
- memory/ANTI_PATTERNS.md：
- handoff/CURRENT.md：
- roles/*.md：
- 不建议写入：

## 9. Questions For User Or Project Governor
列出导入前必须确认的问题。

要求：
- 不复制完整聊天记录。
- 不写入密钥、账号、token、隐私信息。
- 不把未经验证的内容当成事实。
- 能被仓库文件验证的内容，请标注建议验证路径。
- 项目事实必须区分 verified、user-confirmed、unverified。
- 角色提示词或工作习惯必须标注 preserve、wrap、merge 或 suspend。
- 输出要便于 Project Governor 之后按仓库规则写入 memory、decisions、handoff 或 PROJECT_STATE。
```

## Import rule

After an old window returns a Legacy Window Memory Packet, paste that packet into the current Project Governor / Project Architect window. The current window must then classify it through `CONTEXT_IMPORT_PROTOCOL.md` and `WRITEBACK_PROTOCOL.md` before writing anything into the repository.
