# Codex 工作流

本项目使用 Codex 协助开发，但 Codex 不直接决定产品方向，也不一次性执行大范围重构。

Codex 的作用是根据明确的小任务修改代码或文档。项目方向、功能优先级、阶段边界和关键技术选择，仍然需要由用户确认。

## 推荐工作流

本项目推荐使用：

```text
/goal 简短目标，具体细节见 require.txt
```

`/goal` 只写一句简短目标，不写详细实现步骤。

详细背景、涉及文件、具体要求、验收标准、测试方式和禁止修改范围，都写在 `require.txt` 中。

## 基本使用方式

每次准备让 Codex 执行任务时，优先准备两个部分：

1. `/goal`：一句话说明要完成什么。
2. `require.txt`：写完整任务说明。

示例：

```text
/goal 完成食品数据模型文档，具体细节见 require.txt
```

## require.txt 基本格式

`require.txt` 的第一行必须写：

```text
Use $miniapp-food-expiry.
```

完整模板如下：

```text
Use $miniapp-food-expiry.

## Goal

一句话说明任务目标。

## Context

说明当前背景、为什么做这个任务，以及它和“食期管家”项目的关系。

## Files

Read first:
- README.md
- AGENTS.md
- docs/project-brief.md
- docs/FEATURE_SCOPE.md
- docs/DATA_MODEL.md
- .agents/skills/miniapp-food-expiry/SKILL.md

Allowed to modify:
- 写明允许 Codex 修改的文件

Do not modify:
- 写明禁止 Codex 修改的文件

## Requirements

1. 写清楚具体要求。
2. 每条要求尽量可检查。
3. 不要把多个阶段的功能混在一个任务里。

## Acceptance criteria

- [ ] 写清楚完成后应该满足什么条件。
- [ ] 每条验收标准都应该能被人工检查。
- [ ] 涉及代码时，应能通过运行或手动测试验证。

## Tests

说明测试方式或手动检查方式。

例如：
1. 运行微信开发者工具，确认页面可打开。
2. 检查 `git diff --stat`，确认只修改允许范围内的文件。
3. 手动验证日期计算、列表显示或文档渲染是否正确。

## Out of scope

Do not:
- 写清楚本次任务禁止做什么。
- 不要实现未要求的功能。
- 不要修改无关文件。
- 不要添加 API Key、OCR Key、OpenAI Key 或其他密钥。
- 不要绕过用户确认流程保存 OCR、条形码或 AI 识别结果。

## PR summary format

Please summarize:
1. What changed
2. Files changed
3. Tests or manual checks
4. Risks and edge cases
5. Anything not covered
```

## 任务编写规则

1. 每个任务必须小而清晰。
2. `/goal` 只写一句话，不写实现细节。
3. 具体细节全部写入 `require.txt`。
4. 涉及“食期管家”仓库的任务，默认使用 `$miniapp-food-expiry`。
5. 涉及小程序代码时，必须写明允许修改的文件和禁止修改的文件。
6. 涉及日期计算时，必须检查 `expiryDate` 是否仍是排序和提醒的核心字段。
7. 涉及 OCR、条形码、AI、云函数、数据库权限或密钥时，必须写明：

   * 密钥不能放在小程序前端。
   * OCR、条形码、AI 识别结果不能直接入库。
   * 识别结果必须经过用户确认后才能保存。
8. 不要让 Codex 一次性实现多个大功能。
9. 不要让 Codex 在没有明确验收标准的情况下开始大范围修改。
10. 如果任务范围变大，应先拆成多个小任务。

## 文档任务示例

`/goal`：

```text
/goal 完成食品数据模型文档，具体细节见 require.txt
```

`require.txt` 应说明：

* 只允许修改 `docs/DATA_MODEL.md`
* 不允许修改小程序运行时代码
* 必须说明 `expiryDate`
* 必须说明 `quantity` 和 `remainingQuantity`
* 必须预留 `barcode`、`barcodeLookupStatus`、`productNameSource`
* 必须说明 OCR、条形码、AI 结果需要用户确认后保存

## 代码任务示例

`/goal`：

```text
/goal 实现食品日期计算工具函数，具体细节见 require.txt
```

`require.txt` 应说明：

* 允许修改的工具函数文件
* 不允许修改页面 UI
* 支持生产日期 + 保质期计算 `expiryDate`
* 支持天、月、年三种单位
* 检查闰年、月底、无效日期
* 保持 `expiryDate` 作为排序和提醒核心字段
* 提供手动测试方式或测试用例

## 审查 Codex 结果时要检查

每次 Codex 完成后，至少检查：

1. 是否只修改了允许修改的文件。
2. 是否误改了无关文件。
3. 是否保留 `expiryDate` 作为排序和提醒核心字段。
4. 是否把 OCR、条形码、AI 结果直接保存了。
5. 是否把密钥、API Key 或凭据写进了前端代码。
6. 是否提供了可执行的测试方式。
7. 是否符合老年友好原则，例如按钮清楚、流程简短、关键操作明显。

## 常用检查命令

查看修改了哪些文件：

```bash
git diff --stat
```

查看具体修改内容：

```bash
git diff
```

暂存修改：

```bash
git add .
```

提交修改：

```bash
git commit -m "docs: update Codex workflow"
```

推送到 GitHub：

```bash
git push
```
