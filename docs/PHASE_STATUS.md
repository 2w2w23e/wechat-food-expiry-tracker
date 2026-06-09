# 阶段状态

本文件记录“食期管家”项目当前版本、阶段目标、完成情况、阻塞点和下一步任务。

本文件由 main-brain 主要维护；project-architect 可在版本边界、工具引入、地基缺失、阻塞或 V0 完成后更新大阶段判断。

## 当前版本

版本编号：V0

版本名称：手动食品库存 MVP

版本状态：进行中

最后更新角色：main-brain

最后更新日期：2026-06-09

## 当前 V0 任务边界

V0 的目标是完成一个可实际试用的手动版“食期管家”。用户可以手动录入食品，查看库存，看到临期 / 过期状态，按最终可食用日期排序，并完成基础筛选、统计和保存。

V0 必须坚持：

- 不做条形码扫描。
- 不做商品信息查询。
- 不做 OCR / AI 信息抽取。
- 不做大模型对话。
- 不做菜谱规划。
- 不做复杂智能提醒。
- 不做复杂数据看板。
- 不做家庭成员权限系统。
- 不做自动保存识别结果。
- 不做前端密钥存储。
- 不引入新依赖、云开发、工具或 Skill，除非用户或 project-architect 明确确认。

## V0 内部开发顺序

main-brain 在 V0 内部按以下顺序推进小阶段：

1. 核心数据与日期计算基础。
2. 食品基础数据结构与本地 mock 数据。
3. 食品列表展示、`expiryDate` 排序和到期状态显示。
4. 手动新增食品。
5. 编辑和删除食品。
6. 简单筛选与总览统计。
7. 基础本地保存能力。
8. V0 手动测试清单、文档同步和收尾。

说明：此顺序服务于 V0 手动闭环，不展开 V1/V2/V3。

## 已完成内容

### V0-1：核心数据与日期计算基础

状态：已完成。

已完成：

- [x] `utils/date.js`：日期计算工具函数。
- [x] `tests/date.test.js`：日期计算基础测试。
- [x] 支持 `productionDate + shelfLifeValue + shelfLifeUnit` 计算 `expiryDate`。
- [x] 支持手动 `expiryDate` 模式并返回 `dateSource: manual`。
- [x] 支持 `day`、`month`、`year` 单位。
- [x] 支持复数单位 `days`、`months`、`years` 归一化。
- [x] 处理月底日期，例如 1 月 31 日 + 1 个月。
- [x] 处理闰年日期，例如 2024-02-29 + 1 年。
- [x] 对无效日期和无效保质期返回空 `expiryDate`。
- [x] 使用 UTC 进行天数偏移，降低本地时区跨日风险。
- [x] code-reviewer 已给出低风险、建议合并/保留、暂不需要必须修复的结论。

## 当前小阶段

### V0-2：食品基础数据结构与本地 mock 数据

状态：准备开始。

目标：根据 `docs/DATA_MODEL.md` 建立前端可复用的食品数据结构和本地 mock 数据，为后续列表展示、排序、筛选、录入和本地保存打基础。

本阶段只做：

- 定义前端食品记录数据结构。
- 准备少量 mock 食品数据。
- 确保 mock 数据包含 `expiryDate`、`dateSource`、`category`、`quantity`、`remainingQuantity`、`unit`、`storageMethod` 等 V0 必需字段。
- 确保 mock 数据覆盖正常、即将过期、今日到期、已过期、无有效 `expiryDate` 等后续列表状态需要的场景。
- 复用或兼容已有 `utils/date.js` 日期计算工具。

本阶段不做：

- 页面 UI。
- 食品新增表单。
- 编辑和删除。
- 列表页面展示。
- 本地保存。
- 云数据库。
- 条形码、OCR、AI。
- 提醒调度。
- 新依赖引入。

## 当前剩余事项

- [ ] codex-task-generator 为 V0-2 生成 `/goal + require.txt`。
- [ ] Codex 完成 V0-2 PR。
- [ ] code-reviewer 审核 V0-2 PR。
- [ ] main-brain 根据审核结果判断是否进入 V0-3。

## 当前推荐下一步

请让 codex-task-generator 生成 V0-2 的第一个 Codex 任务：

```text
/goal 建立食品基础数据结构与本地 mock 数据，具体细节见 require.txt
```

`require.txt` 必须第一行写：

```text
Use $miniapp-food-expiry.
```

任务应限制为：只建立食品数据结构和 mock 数据，不修改页面 UI，不实现新增、编辑、删除、列表展示、本地保存、云数据库、条形码、OCR、AI 或提醒调度。

## 当前阻塞点

暂无明确阻塞。

潜在风险：

1. V0-2 过早进入页面 UI 或手动录入页，导致数据基础不稳。
2. mock 数据字段与 `docs/DATA_MODEL.md` 不一致。
3. 后续排序、提醒、状态判断没有统一读取 `expiryDate`。
4. 调用方未把 `calculateExpiryDate` 返回结果正确写入食品记录的 `expiryDate` 字段。
5. 过早引入新依赖、云开发、条形码、OCR、AI 或复杂提醒，导致 V0 失焦。

## 需要回到 project-architect 的情况

V0 进行中，main-brain 只在以下情况回到 project-architect：

1. V0 需要改变边界。
2. V0 需要引入新依赖、云开发、工具或 Skill。
3. V0 发现数据模型或安全原则需要调整。
4. V0 被阻塞，需要项目级决策。
5. V0 完成，需要规划 V1。
