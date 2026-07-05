# 食期管家 / Food Expiry Tracker

食期管家是一个面向家庭 / 老年友好的食品库存与保质期管理项目。用户可以记录食品名称、分类、数量、剩余数量、生产日期、保质期、最终可食用日期、保存方式和备注，帮助用户更清楚地查看库存、关注临期食品，并减少遗忘、浪费和误食风险。

当前 Android APK 本地版已支持商品码识别、商品信息查询、本地提醒、系统通知和已用完归档。后续最重要的方向是本地数据升级不丢失，再评估 OCR / AI 信息确认、大模型对话和基于库存的食谱建议等远期能力。

## 当前开发入口

当前仓库暂时转为 Android APK 本地版优先开发，小程序端封存：

- `apk/`：当前 APK 开发目录，包含原生 Android 本地版源码和 APK 构建脚本（默认 debug，可用外部 keystore 构建 release）。
- `miniprogram/`：已验收的微信小程序 V0 源码，当前封存，除非用户明确要求恢复小程序端开发，否则不继续迭代。

## 当前治理状态

本仓库已启用 RepoMind OS。

当前最高治理层是：

```text
.ai-governance/
```

旧的 `docs/AI_COLLABORATION.md`、`docs/ROLE_PROMPTS.md`、`docs/CODEX_WORKFLOW.md`、`docs/DOCUMENT_OWNERSHIP.md`、`docs/TOOL_AND_SKILL_POLICY.md` 等文档仍然保留，用作历史治理文档和导入证据。

如果 `.ai-governance/` 与旧 `docs/*` 治理文档发生冲突，以 `.ai-governance/` 为准。

## 项目目标

本项目计划实现：

- 手动新增、编辑、删除食品信息
- 支持输入“生产日期 + 保质期”自动计算最终可食用日期
- 支持用户直接输入最终可食用日期
- 记录分类、数量、剩余数量、单位、品牌、保存方式和备注
- 按最终可食用日期 `expiryDate` 自动排序
- 显示已过期、今日到期、即将到期、正常等状态
- 支持简单筛选和总览统计
- 面向家庭和老年用户，保持按钮清晰、文字直接、流程简短
- APK 本地版支持条形码扫描、图库识别、商品信息查询、本地提醒、系统通知和已用完归档
- 条形码、OCR 或 AI 识别结果必须经用户确认后保存
- 后续优先保证本地 schema 升级、迁移、备份、导出导入和旧数据回归不丢失

## 当前版本状态

当前版本：APK 本地版 V0.3.1 本地候选 + 已封存的小程序 V0。

当前状态：项目主线暂时转为 APK 开发。微信小程序 V0 核心本地闭环已通过用户人工验收并移入 `miniprogram/` 封存；原计划的小程序 V0.1 食品搜索增强暂停，除非后续明确解封小程序端。

已具备的小程序 V0 本地版能力：

1. 日期计算工具函数。
2. 食品数据结构和本地模拟数据。
3. 首页食品列表。
4. 按 `expiryDate` 排序。
5. 到期状态显示。
6. 手动新增食品。
7. 独立食品详情页。
8. 编辑食品。
9. 删除食品。
10. 状态和分类筛选。
11. 基础统计。
12. 微信小程序本地存储。
13. 页面提示用户当前数据保存在本机小程序内，暂不支持多设备同步。

小程序 V0.1 原计划（当前封存，暂缓）：

- 首页食品搜索入口。
- 至少按食品名称搜索。
- 搜索结果与状态筛选、分类筛选组合工作。
- 搜索后仍按 `expiryDate` 排序。
- 无结果提示清楚。

## APK 本地版

仓库现在包含一个独立的安卓本地版工程，也是当前优先开发入口：

```text
apk/
```

它用于生成 Android 本地安装包。当前安卓端支持食品列表、新增、编辑、删除、本地存储、`expiryDate` 排序、状态筛选、分类筛选、内置实时商品码扫码、图库条码/二维码图片识别与商品信息查询、智能提醒计划、每日简报、系统通知和已用完归档。当前本地版已移除示例数据加载和测试提醒入口。

推荐 Gradle 构建命令：

```powershell
.\gradlew.bat :apk:app:assembleDebug
```

构建产物：

```text
apk/app/build/outputs/apk/debug/app-debug.apk
```

保留 Android SDK 手工构建脚本作为 fallback：

```powershell
powershell -ExecutionPolicy Bypass -File apk/build-apk.ps1
```

构建产物：

```text
apk/build/outputs/apk/shiqi-android-debug.apk
```

构建脚本依赖本机 Android SDK 命令行工具。本仓库文档不等同于已在当前机器完成构建验证；缺少 Android SDK 时，APK 构建需要在补齐环境后验证。

当前 APK 版本不考虑应用商店发布，不实现云数据库、账号体系、多设备同步、OCR、AI 或云端订阅消息。

## 版本路线

V0：手动食品库存 MVP。

当前优先路线：APK 本地版迭代。

小程序 V0.1：食品搜索增强（已暂缓）。

近期优先：本地数据 schema 版本、迁移、备份、导出导入和升级回归，确保 APK 升级不丢失本地食品数据。

已纳入 APK 本地版：条形码与省力录入、本地提醒、系统通知、已用完归档。

暂缓：云端数据、账号体系、多设备同步、应用商店发布、小程序 V0.1。

远期评估：OCR / AI 识别确认流、AI 对话与食谱建议。

不做：医疗、营养或绝对食品安全承诺。

## 技术方向

当前技术方向：

- Android 原生本地版
- Java
- Android SDK 命令行构建
- Android 本地 APK（debug / release 签名模式）

封存的小程序端保留原技术方向：

- 微信小程序原生开发
- WXML
- WXSS
- JavaScript 或 TypeScript
- 微信云开发 / CloudBase
- 云数据库
- 云函数

当前 APK 本地版暂不启用云开发。云数据库、云函数、用户隔离、订阅消息和云端提醒调度应在后续明确恢复云端能力前由 RepoMind OS / Project Governor 重新评估。

## 重要原则

- 最终可食用日期 `expiryDate` 是排序和提醒的核心字段。
- `quantity` 和 `remainingQuantity` 分开保存，用于库存和剩余数量管理。
- 条形码、OCR 和 AI 识别结果不能直接入库，必须经过用户确认。
- API Key、OCR 密钥、OpenAI 密钥、商品库密钥等敏感信息不能写入小程序或 Android 客户端代码。
- 需要密钥、账号、付费接口或绕过访问限制的 OCR、AI、条形码查询、订阅消息和云数据库能力，应放在云函数或服务端处理；当前 APK 只保留不内置密钥的本地和公开查询边界。
- AI 对话和食谱建议不能做医疗、营养诊断或绝对食品安全承诺。

## 项目文档

### RepoMind OS 治理

- `.ai-governance/BOOT.md`：RepoMind OS 启动协议
- `.ai-governance/CONTEXT_INDEX.md`：上下文路由索引
- `.ai-governance/PROJECT_INTAKE.md`：项目导入记录
- `.ai-governance/PROJECT_STATE.md`：当前已验证项目状态
- `.ai-governance/handoff/CURRENT.md`：当前交接状态
- `.ai-governance/decisions/2026-06.md`：2026 年 6 月治理决策记录
- `docs/quickstart.md`：当前仓库的 RepoMind OS 快速开始说明

### 产品与数据

- `docs/FEATURE_SCOPE.md`：新版功能范围和阶段边界
- `docs/DATA_MODEL.md`：食品数据模型说明
- `docs/project-brief.md`：项目简报
- `docs/decision-log.md`：关键决策记录
- `docs/learning-map.md`：学习地图
- `docs/VERSION_ROADMAP.md`：版本路线图
- `docs/PHASE_STATUS.md`：当前阶段状态

### 历史 AI 协作与 Codex 文档

- `AGENTS.md`：给 Codex / AI 编码助手的仓库规则
- `.agents/skills/miniapp-food-expiry/SKILL.md`：食期管家项目专属 Codex Skill
- `docs/CODEX_WORKFLOW.md`：Codex 的 `/goal + require.txt` 工作流
- `docs/AI_COLLABORATION.md`：历史 AI 角色协作体系
- `docs/DOCUMENT_OWNERSHIP.md`：文档与代码修改权限
- `docs/TOOL_AND_SKILL_POLICY.md`：工具和 Skill 引入策略
- `docs/TOOL_RESEARCH.md`：工具与开源项目调研
- `docs/SUPERPOWERS_REFERENCE.md`：Superpowers 方法论参考
- `docs/ROLE_PROMPTS.md`：历史 AI 角色提示词
- `docs/ai-handoffs/README.md`：旧 AI 角色交接协议

## 当前不做

当前不要直接进入以下实现：

- V1 云数据库实现
- 云函数实现
- 用户账号或 openid 隔离实现
- 多设备同步
- 应用商店发布流程
- OCR / AI 信息抽取
- 云端订阅消息或依赖云端的提醒调度
- WorkManager 或复杂后台服务
- 新依赖或新 Skill 引入

已支持的本地商品码识别、商品信息查询、本地通知和已用完归档只在现有 APK 边界内维护，不扩展到云端、账号或发布流程。这些暂缓能力需要在 APK 本地数据稳定或小程序端明确解封后，重新由 RepoMind OS 进行项目级评估。
