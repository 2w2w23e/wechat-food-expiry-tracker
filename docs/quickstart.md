# RepoMind OS 快速开始

这是在“食期管家”仓库中启动 RepoMind OS 的简短流程。

RepoMind OS 默认从 minimal governance（最小治理）开始。它不要求固定角色集合；只有当项目和用户确实需要时，才创建自定义角色。

## 1. 启动第一个 GPT 窗口

打开一个新的 GPT 网页窗口，粘贴这段启动提示：

```text
你是这个仓库的 Project Governor Bootstrap Window（项目总管启动窗口）。

请先读取 `.ai-governance/BOOT.md`，然后使用 `.ai-governance/CONTEXT_INDEX.md` 选择最小必要上下文。
如果这是该项目的第一个 GPT 窗口，请继续遵守 `.ai-governance/FIRST_WINDOW_PROTOCOL.md`。

不要写实现代码。
不要让 Codex 修改文件。
完成读取后，不要停在总结阶段；请继续进入 bootstrap 流程并提出第一批问题。
```

第一个窗口应该成为 Project Governor Bootstrap Window。它不应该马上创建角色、让 Codex 写代码，或把旧聊天总结当成项目事实。

## 2. 每个窗口的基础读取顺序

每个 RepoMind OS 相关窗口都应先读取：

```text
.ai-governance/BOOT.md
.ai-governance/CONTEXT_INDEX.md
```

如果窗口正在扮演某个命名角色，还应读取对应角色文件，例如：

```text
.ai-governance/roles/PROJECT_GOVERNOR.md
.ai-governance/roles/REPO_GOVERNOR.md
.ai-governance/roles/CODEX.md
.ai-governance/roles/PROMPT_ARCHITECT.md
```

不要只依赖隐藏聊天记忆。涉及项目状态、方向、角色、Codex 任务或写回时，必须读取或刷新相关仓库文件。

## 3. 完成启动流程

Project Governor 应该：

- 判断当前是 first-window 还是 existing-session。
- 询问用户是否已有角色、prompt、上下文或偏好需要导入和评估。
- 判断项目目的、成熟度、约束、用户和当前目标。
- 将可用上下文分类为已验证、未验证、过期或缺失。
- 只提出仓库和用户意图确实需要的角色。
- 在长期角色变更前，使用 Repo Governor 审计或进行有限仓库审计。
- 在创建角色、修改项目状态或做重大方向决策前请求用户批准。

minimal setup（最小设置）表示：第一个窗口只使用核心协议和必要角色，让治理保持轻量。

custom roles（自定义角色）可以在评估后导入、重写、拆分、合并或生成。

## 4. 打开专家角色窗口

当 Project Governor 需要另一个角色协作时，它应使用：

```text
.ai-governance/prompts/role_task_packet.md
```

然后打开新的 GPT 窗口，给目标角色粘贴类似提示：

```text
你是这个仓库的 <ROLE NAME>。

请读取 `.ai-governance/BOOT.md`、`.ai-governance/CONTEXT_INDEX.md`、你在 `.ai-governance/roles/` 下的角色文件，以及下面的 Role Task Packet。

不要假设隐藏聊天历史。
不要从这个角色窗口直接修改仓库文件。
请使用 `.ai-governance/prompts/role_result_packet.md` 返回 Role Result Packet。

<在这里粘贴 Role Task Packet>
```

用户再把 Role Result Packet 复制回 Project Governor / Main Brain 窗口。

如果某个角色还不存在，不要在专家窗口中临时编造角色。应回到 Project Governor，遵守 Role Creation Protocol，并在创建或启用该角色前获得用户批准。

## 5. 运行 Codex 任务

只有在任务已批准且边界明确时才使用 Codex。

对于文件修改任务，Project Governor、Main Brain 或 Prompt Architect 应该准备包含以下内容的 Codex prompt：

- purpose and task（目的和任务）；
- files to read first（先读取文件）；
- allowed files（允许修改文件）；
- forbidden files（禁止修改文件）；
- validation commands（验证命令）；
- done-when criteria（完成条件）；
- commit, push, and PR rules（提交、推送和 PR 规则）；
- final report format（最终报告格式）；
- writeback check（回写检查）。

如果 prompt 缺少清晰文件边界、验证要求或用户批准，Codex 应停止。

食期管家相关 Codex 任务仍应遵守旧项目规则：

```text
Use $miniapp-food-expiry.
```

## 6. 需要时使用 Writeback（写回）

当临时聊天输出需要成为长期仓库状态时，使用：

```text
.ai-governance/WRITEBACK_PROTOCOL.md
```

通常需要写回的内容包括：

- 已批准的项目方向或范围变化；
- 长期决策；
- 角色创建或角色权限变化；
- 项目状态更新；
- 下一个窗口需要知道的 handoff（交接）状态；
- 可复用经验、反模式或已确认用户偏好。

不是每条聊天消息都需要写回。长期更新应保持小、可验证，并且不包含 secrets、原始私密聊天记录或不必要个人信息。

## 7. 当前食期管家项目边界

当前项目状态以 `.ai-governance/PROJECT_STATE.md` 和 `.ai-governance/handoff/CURRENT.md` 为准。

当前不要直接进入：

- V1 云数据库实现；
- 云函数实现；
- 用户账号或 openid 隔离实现；
- 多设备同步；
- 条形码扫描；
- 商品信息查询；
- OCR / AI 信息抽取；
- 提醒调度；
- 订阅消息；
- 新依赖或新 Skill 引入。

这些能力需要在 RepoMind OS / Project Governor 完成项目级评估，并获得用户明确确认后再进入。
