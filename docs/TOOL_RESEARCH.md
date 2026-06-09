# 工具与开源项目调研

本文件记录“食期管家”项目可参考或后续可能引入的 GitHub 开源项目、Codex Skill、UI 组件库、日期库、AI 开发工具和安全工具。

目标：先记录和评估，不盲目引入依赖。任何工具或库进入项目代码前，都必须经过明确评估、用户确认、Codex 任务生成、PR 审查和回退说明。

## 1. 当前结论

当前阶段不建议立即引入新的代码依赖。

优先策略：

1. 继续使用当前主流程：ChatGPT 项目 + GitHub + VSCode + 微信开发者工具 + Codex。
2. Codex 任务继续使用 `/goal + require.txt`。
3. 项目相关 Codex 任务继续默认使用 `$miniapp-food-expiry`。
4. `define-goal` 已安装，可用于把模糊目标变成可验收目标。
5. `create-plan` 暂时安装失败，不作为当前可用 Skill。
6. UI、日期、图表、条形码、AI agent 等工具先记录，等进入对应阶段再评估是否引入。

## 2. Codex Skill 调研

### 2.1 openai/skills

仓库：`openai/skills`

用途：OpenAI 官方 Codex Skills Catalog。

当前判断：推荐作为 Skill 来源，但只安装必要 Skill。

备注：

- 可继续使用官方 curated skills。
- experimental skills 的目录结构可能变化，安装前应让 Codex 或 project-architect 先确认目录存在。
- 不安装来源不明、带脚本、需要密钥或会执行危险命令的 Skill。

### 2.2 define-goal

来源：`openai/skills` curated skill。

用途：把模糊目标转成可验收目标。

当前状态：已安装。

适用场景：

- 用户说“优化首页”“让功能更好用”“适合老年人”这类目标时。
- main-brain 或 codex-task-generator 需要把模糊需求变成具体验收标准时。

建议：可以继续使用。

### 2.3 create-plan

来源：曾计划从 `openai/skills` experimental skill 安装。

当前状态：安装失败。

失败记录：

```text
Skill Installer install https://github.com/openai/skills/tree/main/skills/.experimental/create-plan

Codex 返回：openai/skills on main currently has no skills/.experimental directory；.curated 中只有 define-goal；没有添加 create-plan 文件，临时 checkout 已清理。
```

当前判断：暂不使用。

替代方案：

- 由 main-brain 在任务开始前要求 Codex “只读规划，不修改文件”。
- 在 `require.txt` 中明确写：`Do not edit files. Plan only.`
- 由 codex-task-generator 输出“规划任务”模板，而不是依赖 `$create-plan`。

后续动作：

- 暂不反复安装。
- 后续如果 openai/skills 恢复或移动 create-plan，再重新评估。

## 3. 微信小程序资源与 UI 组件

### 3.1 awesome-wechat-weapp

仓库：`justjavac/awesome-wechat-weapp`

用途：微信小程序开发资源汇总。

适合用途：

- 查官方文档、工具、组件和 Demo。
- 给 learning-coach 和 documentation-ai 作为资料索引。

当前建议：只作为资料索引，不引入代码。

阶段：现在可参考。

风险：资料较杂，具体库仍需单独评估维护状态、许可证和是否适合原生小程序。

### 3.2 WeUI / weui-wxss

仓库：`Tencent/weui-wxss`

用途：微信官方设计团队的基础样式库。

适合用途：

- 老年友好的基础按钮、列表、弹窗、提示。
- 保持接近微信原生体验。

当前建议：进入页面 UI 阶段时优先评估。

阶段：MVP 页面开发前可评估。

风险：如果直接引入样式库，需要确认小程序原生项目结构和样式覆盖方式。

### 3.3 Vant Weapp

仓库：`youzan/vant-weapp`

用途：轻量、可靠的小程序 UI 组件库。

适合用途：

- 表单、按钮、弹窗、日期选择、列表、筛选等组件。
- 提升页面开发效率。

当前建议：作为第二优先级 UI 方案评估。

阶段：MVP 表单页和列表页复杂度上升时再评估。

风险：

- 组件库会增加项目依赖和学习成本。
- UI 可能比 WeUI 更复杂，需要避免影响老年友好原则。

### 3.4 TDesign MiniProgram

仓库：`Tencent/tdesign-miniprogram`

用途：腾讯 TDesign 小程序组件库。

适合用途：

- 现代 UI 组件。
- 后续需要更统一组件规范时评估。

当前建议：暂不引入，仅记录。

阶段：MVP UI 稳定后再评估。

### 3.5 其他小程序组件库

可从 `awesome-wechat-weapp` 中继续查看，例如：

- iView Weapp
- Wux Weapp
- Lin UI
- wx-charts
- wx-calendar

当前建议：不主动引入，避免 UI 体系混乱。

## 4. 日期与数据处理

### 4.1 Day.js

仓库：`iamkun/dayjs`

用途：轻量日期时间库。

适合用途：

- 生产日期 + 保质期计算。
- 日期格式化。
- 月底、闰年、年月日计算。

当前建议：阶段 1 日期计算工具函数开始前评估是否引入。

建议评估问题：

1. 原生 Date 是否足够？
2. Day.js 是否能更稳定处理 month/year 加法？
3. 小程序 npm 引入成本是否可接受？
4. 是否会增加包体积或构建复杂度？
5. 是否需要插件处理时区或 UTC？

当前结论：先不引入。让 Codex 在日期计算任务中评估“原生 Date vs Day.js”。

## 5. 图表与统计

### 5.1 AntV wx-f2

仓库：`antvis/wx-f2`

用途：微信小程序图表。

适合用途：

- 分类库存图表。
- 临期趋势图。
- 食品数量变化图。

当前建议：暂不引入。

原因：当前 MVP 只做简单总览统计，例如食品总数、即将过期数量、已过期数量，不需要图表库。

阶段：复杂统计看板阶段再评估。

## 6. 条形码与商品信息

### 6.1 Open Food Facts

项目：Open Food Facts

用途：开放食品数据库，可作为条形码商品信息查询的参考数据源。

适合用途：

- 第二阶段条形码扫描后的商品信息查询调研。
- 学习商品数据字段设计。

当前建议：仅作为第二阶段调研项，不进入 MVP。

风险：

1. 中国本地商品覆盖可能不足。
2. 查询结果可能不准确。
3. 外部 API 调用必须放云函数或服务端。
4. 查询结果必须用户确认后保存。
5. 不应把商品库信息当作食品安全判断依据。

### 6.2 中国本地商品数据源

当前状态：未选型。

建议：第二阶段由 project-architect 或 main-brain 单独发起调研，不在 MVP 阶段处理。

## 7. AI 编程与 Agent 工具

### 7.1 OpenHands

仓库：`OpenHands/OpenHands`

用途：AI-driven development 平台。

当前建议：暂不引入。

原因：

- 项目当前已经使用 ChatGPT 项目 + Codex + GitHub + VSCode。
- OpenHands 能力较重，会增加权限、配置和审查复杂度。
- 与 Codex 职责重叠。

适合阶段：仅作为后续调研，不进入主流程。

### 7.2 Aider

仓库：`Aider-AI/aider`

用途：终端 AI pair programming。

当前建议：暂不引入。

原因：与 Codex 执行角色重叠，会增加代码修改来源复杂度。

### 7.3 Continue

仓库：`continuedev/continue`

用途：AI checks、IDE 辅助或 PR 检查相关能力。

当前建议：后续有 CI / PR 自动检查需求时再评估。

原因：当前已有 code-reviewer 角色，暂不需要引入额外 AI 审查工具。

### 7.4 Roo Code

仓库：`RooCodeInc/Roo-Code`

用途：VSCode 中的 AI agent 工具。

当前建议：暂不引入。

原因：与 Codex、ChatGPT 多角色体系重叠，可能增加权限和审查复杂度。

## 8. 当前推荐清单

### 8.1 现在推荐使用

- `$miniapp-food-expiry`
- `define-goal`
- `awesome-wechat-weapp` 作为资料索引

### 8.2 下一阶段重点评估

- Day.js：日期计算工具函数阶段评估。
- WeUI：MVP UI 开发前评估。
- Vant Weapp：表单和列表复杂度提高时评估。

### 8.3 第二阶段前评估

- security-threat-model skill
- security-best-practices skill
- Open Food Facts 或其他条形码数据源

### 8.4 后续再评估

- wx-f2
- TDesign MiniProgram
- Continue
- OpenHands
- Aider
- Roo Code

### 8.5 当前不建议引入

- 大型 agent 平台
- MCP server
- 自动部署工具
- 需要额外密钥的第三方服务
- 未知来源的第三方 Skill

## 9. 工具引入流程

任何工具进入项目代码前，必须走以下流程：

1. project-architect 或 main-brain 提出工具评估请求。
2. documentation-ai 更新 `docs/TOOL_RESEARCH.md` 或创建工具评估文档。
3. 用户确认是否进入下一步。
4. codex-task-generator 生成 `/goal + require.txt`。
5. Codex 在明确授权下修改文件、提交 commit、创建 PR。
6. code-reviewer 审查 PR。
7. 用户决定是否合并。

## 10. Codex 引入工具要求

Codex 只有在 `require.txt` 明确授权时，才能引入依赖、工具或 Skill。

`require.txt` 必须说明：

- 工具名称
- 引入原因
- 允许修改文件
- 禁止修改文件
- 安装方式
- 验证方式
- 回退方式
- 安全风险

如果 Codex 在执行过程中发现某个工具可能有帮助，应先写入建议或 Skill proposal，不得自行安装。
