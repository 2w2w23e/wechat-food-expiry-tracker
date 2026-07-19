# 食期管家 / Food Expiry Tracker

食期管家是一个面向家庭 / 老年友好的食品库存与保质期管理项目。用户可以记录食品名称、分类、数量、剩余数量、生产日期、保质期、最终可食用日期、保存方式和备注，帮助用户更清楚地查看库存、关注临期食品，并减少遗忘、浪费和误食风险。

当前 Android APK 本地版已支持统一智能识别、商品码识别、商品信息查询、关键帧包装文字 OCR 候选、本地提醒、系统通知和已用完归档。OCR 采用 ML Kit 中文/拉丁文字与条码模型、OpenCV 低对比增强、ONNX Runtime Android 1.27.0 驱动的 `PP-OCRv6_small_det` 文字定位与 `PP-OCRv6_rec_small` 裁剪行复识、最多 3 个关键帧及跨帧候选稳定；所有结果只填入可编辑确认表单，不自动保存。当前仅完成固定代表样本验证，不能外推为全量数据集或真实相机准确率。

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
- APK 本地版支持统一智能识别：同一入口融合 ML Kit 中文/拉丁包装文字与条码、OpenCV 低对比增强、PP-OCRv6 文字检测与裁剪行复识、关键帧区域证据和跨帧候选稳定，结果确认后才进入新增或补货批次表单
- 商品名智能选择：条码商品库命中优先，其次要求明确“产品名称/品名”标签或可信跨帧证据；品牌文字冲突时不强猜，退回画面可见食品品类，说明书、医疗文字、公司名、混合脚本乱码和低分噪声不展示
- APK 本地版支持 Excel `.xlsx` 批量导入、预览校验、覆盖确认、错误行详情和导出，并保留商品档案、条码和独立批次关系
- APK 本地版支持 JSON 快速换机：每个库存批次使用唯一编号，可按入库时间、生产日期和最终日期筛选导出；相同批次只接受更新时间较新的数量和状态，并可通过 Android 系统分享面板发送文件
- 同一条码可关联多个商品档案；扫码命中后由用户选择商品，补货建立独立批次，生产日期、保质期、最终日期和数量不复用旧批次
- 条形码、OCR 或 AI 识别结果必须经用户确认后保存
- 后续优先保证本地 schema 升级、迁移、备份、导出导入和旧数据回归不丢失

## 当前版本状态

当前版本：APK 本地版 V0.3.14 统一关键帧本地 OCR + JSON 快速换机 + 条码商品档案/独立库存批次 + 智能/固定提醒模式 + 已封存的小程序 V0。

当前状态：项目主线暂时转为 APK 开发。微信小程序 V0 核心本地闭环已通过用户人工验收并移入 `miniprogram/` 封存；原计划的小程序 V0.1 食品搜索增强暂停，除非后续明确解封小程序端。

### OCR 当前证据

- 本地 Java 逻辑测试：`194 passed, 0 failed`。
- 510-Date 固定 10 个代表样本：9 个完整日期准确，1 个无年份样本安全拒绝。
- ExpDate 固定 10 个代表样本：9 个完整日期准确，1 个无年份样本安全拒绝。
- Open Food Facts 中文图片只做定性策略抽检：怡宝退回“饮用纯净水”、娃哈哈退回“饮用净水”、农夫山泉退回“天然水”且不再误报 `50日`、蒙牛识别“纯牛奶”。
- 模拟器视频回放中，9 个用户样本全部通过视觉复核；其中激光日期样本正确得到“生产日期 `2026-01-20` + 保质期 `9个月` + 最终日期 `2026-10-20`”，2026-07-15 用户录像正确得到生产日期 `2026-06-08` 和有效期 `2027-03-07`。
- Excel 导出、有效追加、错误行详情、失败回滚和覆盖导入均已实际运行通过；最终 APK 覆盖安装前后本地数据文件散列一致。
- 指定的 2026-07-19 录屏已在模拟器视频回放中识别出“生产日期 `2026-01-20` + 保质期 `9个月` + 最终日期 `2026-10-20`”；JSON 保存、系统分享、同批次更新、重复导入、重启保留及 Excel 回归均完成视觉验收。
- 以上不是全量数据集准确率；本轮没有完成真实手机相机验收。

统一架构、终态样本和限制见 `docs/OCR_VALIDATION_REPORT.md`，最终视觉与回归证据见 `docs/qa/2026-07-18-v0311-final-qa.md`，公开来源及许可边界见 `docs/OCR_PUBLIC_VALIDATION_SOURCES.md`。

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

它用于生成 Android 本地安装包。当前安卓端支持食品列表、新增、编辑、删除、本地存储、`expiryDate` 排序、状态筛选、分类筛选、内置实时商品码扫码、图库条码/二维码图片识别与商品信息查询、一次生成并随食品保存的智能提醒计划、每日简报、系统通知和已用完归档。当前本地版已移除示例数据加载和测试提醒入口。

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

当前 APK 版本不考虑应用商店发布，也不实现云数据库、账号体系、多设备同步、云端 AI 自动抽取、自动入库或云端订阅消息。同一条码允许关联多个本地商品档案；补货始终新建独立批次，不修改旧批次日期和数量。
本地条码与关键帧包装文字 OCR 只生成候选，必须由用户确认后才可填入新增表单，绝不直接保存到食品列表。图库图片先处理 EXIF 方向；日期解析支持多种中英文和紧凑格式，并拦截未来生产日期及无年份猜测。当前证据见 `docs/OCR_VALIDATION_REPORT.md`，日期规则细节见 `docs/APK_OCR_DATE_FORMAT_MATRIX.md`。

V0.3.14 的 `minSdk` 为 24。包内仅保留当前链路使用的 `PP-OCRv6_small_det.onnx` 与 `PP-OCRv6_rec_small.onnx`，没有打入 `.local/` 下载的 PP-OCRv5 server 研究模型。离线效果优先，但可交付 APK 必须小于 1 GB。

## 版本路线

V0：手动食品库存 MVP。

当前优先路线：APK 本地版迭代。

小程序 V0.1：食品搜索增强（已暂缓）。

近期优先：本地数据 schema 版本、迁移、备份、导出导入和升级回归，确保 APK 升级不丢失本地食品数据。

已纳入 APK 本地版：条形码与省力录入、本地提醒、系统通知、已用完归档。

暂缓：云端数据、账号体系、自动云同步、应用商店发布、小程序 V0.1。不同设备之间可使用本地 JSON 文件手动快速迁移。

远期评估：OCR / AI 识别确认流、AI 对话与食谱建议。

不做：医疗、营养或绝对食品安全承诺。

## 技术方向

当前技术方向：

- Android 原生本地版
- Java
- Android SDK 命令行构建
- Android 本地 APK（debug / release 签名模式）
- ML Kit Text Recognition v2 Chinese / Latin 通用包装文字识别与 ML Kit Barcode Scanning
- OpenCV CLAHE / 背景差分低对比喷码增强
- ONNX Runtime Android 1.27.0 + `PP-OCRv6_small_det` 文字定位 + `PP-OCRv6_rec_small` 裁剪行复识
- EXIF 方向校正、日期规则、未来生产日期门禁、商品名冲突回退
- 关键帧质量与字段区域证据、跨帧候选稳定和可编辑确认表单

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
- `docs/APK_OCR_DATE_FORMAT_MATRIX.md`：APK 多模型 OCR 架构、日期写法与推断矩阵
- `docs/OCR_VALIDATION_REPORT.md`：关键帧 OCR 统一架构、终态公开样本证据与真实限制
- `docs/OCR_PUBLIC_VALIDATION_SOURCES.md`：公开验证资源、许可和可报告边界

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
- 云端 OCR / AI 自动抽取和自动入库
- 云端订阅消息或依赖云端的提醒调度
- WorkManager 或复杂后台服务
- 未经样本基准、许可证和体积评估的新依赖或新 Skill 引入

已支持的统一智能识别、商品码识别、商品信息查询、本地通知和已用完归档只在现有 APK 边界内维护，不扩展到云端、账号或发布流程。这些暂缓能力需要在 APK 本地数据稳定或小程序端明确解封后，重新由 RepoMind OS 进行项目级评估。
