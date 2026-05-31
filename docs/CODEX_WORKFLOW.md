# Codex 工作流

本项目使用 Codex 协助开发，但 Codex 不直接决定产品方向，也不一次性执行大范围重构。

## 推荐工作流

每个 Codex 任务优先使用：

```text
/goal 简短目标，具体细节见 require.txt
/goal 只写一句话，不写详细实现步骤。

详细要求写在 require.txt 中。

# require.txt 基本格式
## Files

允许读取或修改的文件。

## Requirements

具体要求。

## Acceptance criteria

验收标准。

## Tests

测试方式或手动检查方式。

## Out of scope

禁止修改范围。

## PR summary format

要求 Codex 输出的总结格式。
# 示例
/goal 完成食品数据模型文档，具体细节见 require.txt
# 规则
每个任务必须小而清晰。
不要把多个阶段的功能混在一个任务里。
涉及食期管家仓库的任务，默认使用 $miniapp-food-expiry。
涉及小程序代码时，必须写明允许修改的文件。
涉及 OCR、条形码、AI、云函数、密钥时，必须写明不能把密钥放前端，识别结果必须用户确认后保存。
涉及日期计算时，必须检查 expiryDate 是否仍是排序和提醒的核心字段。

