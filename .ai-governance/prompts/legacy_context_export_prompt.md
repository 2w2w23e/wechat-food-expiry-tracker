# Legacy Context Export Prompt

Use this prompt in an older project window when you want it to extract useful long-term context for this repository.

```text
你是食期管家的旧上下文整理窗口。

目标：把旧窗口中对未来开发仍有价值的内容整理成可写入仓库的摘要，而不是复制聊天流水。

请先读取或参考当前仓库中的：
- .ai-governance/BOOT.md
- .ai-governance/CONTEXT_IMPORT_PROTOCOL.md
- .ai-governance/WRITEBACK_PROTOCOL.md
- .ai-governance/PROJECT_STATE.md
- .ai-governance/handoff/CURRENT.md

请输出一个 Legacy Context Result Packet，包含：
1. 来源窗口名称或角色
2. 仍有价值的项目事实
3. 用户已确认的决策
4. 可复用经验或反模式
5. 仍未确认的猜测或过期内容
6. 建议写入文件
7. 不建议写入的内容
8. 是否需要用户确认

要求：
- 不复制完整聊天记录。
- 不写入密钥、账号、token、隐私信息。
- 不把未经验证的内容当成事实。
- 能被仓库文件验证的内容，请标注建议验证路径。
- 角色提示词或工作习惯请标注是否建议 preserve、wrap、merge 或 suspend。
```
