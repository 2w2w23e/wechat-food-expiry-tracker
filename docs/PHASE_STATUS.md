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

### V0-2：食品基础数据结构与本地 mock 数据

状态：已完成。

已完成：

- [x] PR #1 `feat: add food mock data foundation` 已合并。
- [x] `utils/food.js`：食品数据结构和基础规范化工具。
- [x] `mock/foods.js`：本地 mock 食品数据。
- [x] `tests/food.test.js`：食品数据结构和 mock 数据测试。
- [x] mock 数据包含 `expiryDate`、`dateSource`、`category`、`quantity`、`remainingQuantity`、`unit`、`storageMethod`、`notes` 等 V0 必需字段。
- [x] mock 数据覆盖正常、即将过期、今日到期、已过期、手动 `expiryDate`、无有效 `expiryDate` 等场景。
- [x] 字段名已统一使用 `notes`，与 `docs/DATA_MODEL.md` 保持一致。
- [x] 已补充 `remainingQuantity <= quantity` 的校验和测试。
- [x] code-reviewer 复审通过，建议合并。
- [x] 未引入页面 UI、新增、编辑、删除、列表展示、本地保存、云数据库、条形码、OCR、AI、提醒调度或新依赖。

### V0-3a：食品排序与到期状态工具函数

状态：已完成。

已完成：

- [x] PR #2 `feat: add food expiry status and sorting utils` 已合并。
- [x] `utils/foodStatus.js`：食品到期状态判断工具函数。
- [x] `utils/foodList.js`：基于 `expiryDate` 的食品排序工具函数。
- [x] `tests/foodStatus.test.js`：到期状态与排序测试。
- [x] 支持 `expired`、`today`、`soon`、`normal`、`unknown` 状态。
- [x] `soon` 默认阈值为 7 天，并标记为 V0 临时策略。
- [x] 排序以 `expiryDate` 为唯一核心字段。
- [x] 无有效 `expiryDate` 的食品稳定排在底部。
- [x] 排序不修改原数组。
- [x] 使用固定参考日期进行测试，避免测试受真实当前日期影响。
- [x] code-reviewer 审核通过，建议合并。
- [x] 未引入页面 UI、新增、编辑、删除、本地保存、云数据库、条形码、OCR、AI、提醒调度或新依赖。

### V0-3b：只读食品列表页面展示

状态：已完成。

已完成：

- [x] PR #3 `feat: show read-only mock food list` 已合并。
- [x] 首页基于 `mockFoods` 展示只读食品列表。
- [x] 页面复用 `sortFoodsByExpiryDate` 排序，没有在页面重复实现排序逻辑。
- [x] 页面复用 `getFoodExpiryStatus` 获取状态，没有在页面重复实现状态判断逻辑。
- [x] 页面展示食品名称、分类、数量 / 剩余数量、单位、保存方式、`expiryDate` 和状态文案。
- [x] 无有效 `expiryDate` 的食品显示“未填写到期日”，并通过排序工具排在底部。
- [x] UI 采用清楚文字、卡片区块和明显状态标签，符合当前 V0 老年友好要求。
- [x] 微信开发者工具实际页面检查通过。
- [x] 未实现新增、编辑、删除、本地保存、云数据库、条形码、OCR、AI 或提醒调度。

## 当前小阶段

### V0-4：手动新增食品

状态：准备开始。

目标：在当前只读列表基础上，增加最小可用的手动新增食品能力，让用户可以通过表单录入食品，并在当前页面列表中看到新增结果。

本阶段优先做最小闭环：

- 在页面上提供“新增食品”入口。
- 提供基础手动录入表单。
- 表单字段优先包含：食品名称、分类、数量、剩余数量、单位、保存方式、生产日期、保质期数值、保质期单位、最终可食用日期、备注。
- 支持两种日期方式：
  - 通过 `productionDate + shelfLifeValue + shelfLifeUnit` 计算 `expiryDate`。
  - 手动填写 `expiryDate`。
- 新增后把食品加入当前页面列表。
- 新增后继续使用 `sortFoodsByExpiryDate` 排序。
- 新增后继续使用 `getFoodExpiryStatus` 显示状态。

本阶段暂不做：

- 编辑食品。
- 删除食品。
- 本地保存。
- 云数据库。
- 条形码、OCR、AI。
- 提醒调度。
- 新依赖引入。
- 复杂表单校验。
- 复杂筛选或统计。

## 当前剩余事项

- [ ] codex-task-generator 为 V0-4 生成 `/goal + require.txt`。
- [ ] Codex 完成 V0-4 PR。
- [ ] code-reviewer 审核 V0-4 PR。
- [ ] main-brain 根据审核结果判断是否进入 V0-5。

## 当前推荐下一步

请让 codex-task-generator 生成 V0-4 的第一个 Codex 任务：

```text
/goal 实现最小可用的手动新增食品表单，具体细节见 require.txt
```

`require.txt` 必须第一行写：

```text
Use $miniapp-food-expiry.
```

任务应限制为：只实现手动新增食品并加入当前页面列表，不实现编辑、删除、本地保存、云数据库、条形码、OCR、AI 或提醒调度。

## 当前阻塞点

暂无明确阻塞。

潜在风险：

1. V0-4 一次性加入编辑、删除、本地保存，导致任务过大。
2. 表单绕过 `calculateExpiryDate` 自行计算 `expiryDate`。
3. 新增后列表绕过 `sortFoodsByExpiryDate` 或 `getFoodExpiryStatus`。
4. `expiryDate` 来源没有通过 `dateSource` 标记为 `calculated` 或 `manual`。
5. getTodayString 使用设备本地日期，后续真实 UI 接入时需要确认是否继续使用本地日期，还是统一按中国时区计算。
6. 过早引入新依赖、云开发、条形码、OCR、AI 或复杂提醒，导致 V0 失焦。

## 需要回到 project-architect 的情况

V0 进行中，main-brain 只在以下情况回到 project-architect：

1. V0 需要改变边界。
2. V0 需要引入新依赖、云开发、工具或 Skill。
3. V0 发现数据模型或安全原则需要调整。
4. V0 被阻塞，需要项目级决策。
5. V0 完成，需要规划 V1。
