# 数据模型说明

## 1. 设计原则

食期管家的数据模型服务于面向家庭 / 老年友好的食品库存与保质期管理应用。当前主线是 Android APK 本地版，`miniprogram/` 为归档源码。MVP 阶段应保持字段清晰、来源明确、便于人工确认和后续扩展。

核心原则：

- `expiryDate` 是最终可食用日期，也是排序和提醒逻辑的标准字段。
- 用户可以通过生产日期 + 保质期计算得到 `expiryDate`，也可以直接手动输入 `expiryDate`。
- 使用 `dateSource` 记录 `expiryDate` 的来源，避免后续编辑时混淆。
- `quantity` 和 `remainingQuantity` 分开保存，支持库存和剩余量管理。
- `barcode`、`barcodeLookupStatus`、`productNameSource`、`reminderPlan` 等字段已服务于 APK 本地版的商品码识别、商品信息确认和本地提醒能力。
- 条码查询、OCR 和 AI 识别结果必须经过用户确认后才能保存。
- API Key、OCR Key、AI Key、云开发密钥或其他敏感凭据不得放入小程序或 Android 客户端代码。
- AI 聊天不得做医疗、营养或绝对食品安全声明。
- 后续最重要的数据演进目标是本地 schema 升级、迁移和备份不丢失旧数据。

## 2. 核心食品字段

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 食品记录唯一标识。早期本地数据可使用本地生成 ID，云端阶段可使用数据库记录 ID。 |
| `productProfileId` | string | 是 | 稳定商品档案 ID。多个补货批次复用同一档案；同一条码允许对应多个不同档案。 |
| `barcode` | string | 否 | 用户确认后的 EAN/UPC/GTIN 商品条码；表单中可查看、修改或清空。 |
| `userId` | string | 后续必需 | 用户标识。MVP 本地阶段可为空或使用占位值；云端阶段必须用于用户数据隔离。 |
| `name` | string | 是 | 用户输入或确认后的食品名称。 |
| `displayName` | string | 否 | 页面展示名称，可由 `name`、品牌或条码查询结果组合生成。 |
| `category` | string | 是 | 食品分类，例如主食、肉蛋、乳制品、零食、调味品、饮品等。 |
| `brand` | string | 否 | 品牌名称。 |
| `notes` | string | 否 | 用户备注。 |
| `createdAt` | string | 是 | 创建时间，建议使用 ISO 8601 字符串。 |
| `updatedAt` | string | 是 | 更新时间，建议使用 ISO 8601 字符串。 |

## 3. 日期相关字段

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `productionDate` | string | 计算模式必需 | 生产日期，建议格式为 `YYYY-MM-DD`。 |
| `shelfLifeValue` | number | 计算模式必需 | 保质期数值。 |
| `shelfLifeUnit` | string | 计算模式必需 | 保质期单位，例如 `day`、`month`、`year`。 |
| `expiryDate` | string | 是 | 最终可食用日期，建议格式为 `YYYY-MM-DD`。这是排序和提醒逻辑的标准字段。 |
| `dateSource` | string | 是 | `expiryDate` 来源，例如 `calculated`、`manual`、`barcodeLookup`、`ocr`、`ai`。MVP 主要使用 `calculated` 和 `manual`。 |

日期规则：

- 通过生产日期 + 保质期得到的结果必须写入 `expiryDate`。
- 用户手动输入最终可食用日期时，也必须写入 `expiryDate`。
- 列表排序、临期判断、过期判断和后续提醒都应读取 `expiryDate`。
- 无有效 `expiryDate` 的记录不能参与正常到期排序，应排在列表底部或单独提示。

## 4. 数量与库存字段

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `quantity` | number | 是 | 登记时的数量，例如 2。 |
| `remainingQuantity` | number | 是 | 当前剩余数量，例如 1。 |
| `unit` | string | 是 | 数量单位，例如袋、瓶、盒、个、克、千克、毫升。 |
| `isFinished` | boolean | 否 | 是否已用完并进入归档视图。旧数据缺失时默认 `false`。 |
| `finishedAt` | string/null | 否 | 标记已用完的时间。未归档时可为 `null`。 |

数量规则：

- `quantity` 表示初始登记数量。
- `remainingQuantity` 表示当前剩余数量。
- `remainingQuantity` 不应大于 `quantity`，除非用户明确执行补货或修正操作。
- APK 本地版已支持已用完归档和恢复；后续快速减少、补货等操作必须兼容旧 JSON。

## 5. 分类与筛选字段

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `category` | string | 是 | 用于食品列表分组和筛选。 |
| `storageMethod` | string | 是 | 保存方式，例如常温、冷藏、冷冻、避光、干燥。 |
| `storageNote` | string | 否 | 保存补充说明，例如开封后冷藏。 |

MVP 筛选方式：

- 全部
- 即将过期
- 已过期
- 按 `category` 筛选

## 6. 商品档案、批次与条码

- 每条 `FoodItem` 都是一条独立库存批次，`id` 不复用。
- 补货只复用 `productProfileId`、条码、名称、分类、单位、保存方式和位置；生产日期、保质期、`expiryDate`、数量、开封状态、完成状态和提醒快照必须重新填写或计算。
- 同一条码可对应多个 `productProfileId`，扫码后由用户选择要补货的商品，也可明确作为新商品建立新档案。
- 旧 schema v1 记录升级到 v2 时生成确定性档案 ID；不会凭名称或条码擅自合并历史批次。

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `barcode` | string | 否 | 食品条码。APK 本地版可由扫码、图库识别或手动输入产生，保存前需要用户确认。 |
| `barcodeLookupStatus` | string | 否 | 条码查询状态，例如 `notStarted`、`pending`、`found`、`notFound`、`failed`、`confirmed`。 |
| `productNameSource` | string | 否 | 商品名称来源，例如 `manual`、`barcodeLookup`、`ocr`、`ai`。 |

条码规则：

- APK 本地版已支持内置实时扫码、图库图片识别、手动输入和不内置密钥的商品信息查询。
- 条码查询结果不得自动保存。
- 查询结果必须展示给用户确认和编辑，用户确认后才能写入食品记录。
- 条码 API Key、商品库访问密钥、账号或绕过限制逻辑不得出现在客户端代码中。

## 7. 提醒字段

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `reminderEnabled` | boolean | 否 | 是否开启提醒。旧数据缺失时默认可为 `false`。 |
| `reminderPlan` | object | 否 | 提醒计划。APK 本地版可用于应用内提醒、今日简报和本地通知。 |

`reminderPlan` 可以使用如下结构：

```json
{
  "offsetDays": [7, 3, 1, 0],
  "strategy": "default",
  "lastScheduledAt": null
}
```

提醒规则：

- 提醒逻辑必须基于 `expiryDate`。
- 当前不实现订阅消息、云端提醒调度或 WorkManager 复杂后台服务。
- 规则化智能提醒可以根据 `category`、`storageMethod`、`remainingQuantity`、`expiryDate` 等字段生成提醒计划。
- AI 生成的提醒建议必须由用户确认后才能保存为 `reminderPlan`。

## 8. 来源与确认字段

来源字段用于区分用户手动输入、条码查询、OCR 或 AI 提取，避免未确认的数据直接进入正式记录。

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `dateSource` | string | 是 | 日期来源，例如 `manual`、`calculated`、`barcodeLookup`、`ocr`、`ai`。 |
| `productNameSource` | string | 否 | 名称来源，例如 `manual`、`barcodeLookup`、`ocr`、`ai`。 |
| `barcodeLookupStatus` | string | 否 | 条码查询状态。 |

确认规则：

- 用户手动输入的数据可以直接保存。
- 条码查询结果必须由用户确认后保存。
- OCR 结果必须由用户确认后保存。
- AI 提取结果必须由用户确认后保存。
- 未确认的识别结果可保存在临时页面状态、草稿或专门的确认流程中，但不得直接写入正式食品记录。

## 9. 示例 JSON 对象

```json
{
  "id": "food_001",
  "userId": "user_001",
  "name": "纯牛奶",
  "displayName": "某品牌纯牛奶",
  "category": "乳制品",
  "brand": "某品牌",
  "barcode": "",
  "barcodeLookupStatus": "notStarted",
  "productNameSource": "manual",
  "quantity": 2,
  "remainingQuantity": 1,
  "unit": "盒",
  "isFinished": false,
  "finishedAt": null,
  "storageMethod": "冷藏",
  "storageNote": "开封后尽快饮用",
  "productionDate": "2026-05-01",
  "shelfLifeValue": 30,
  "shelfLifeUnit": "day",
  "expiryDate": "2026-05-31",
  "dateSource": "calculated",
  "reminderEnabled": false,
  "reminderPlan": {
    "offsetDays": [7, 3, 1, 0],
    "strategy": "default",
    "lastScheduledAt": null
  },
  "notes": "放在冰箱上层",
  "createdAt": "2026-05-31T09:00:00+08:00",
  "updatedAt": "2026-05-31T09:00:00+08:00"
}
```

## 10. 字段演进说明

MVP 字段应优先保证手动录入、保质期计算、最终可食用日期、分类、数量、剩余数量、保存方式、基础筛选、统计、商品码确认、本地提醒和已用完归档可用。

后续可选字段：

| 字段 | 类型 | 阶段 | 说明 |
| --- | --- | --- | --- |
| `imageFileId` | string | 后续 | 食品照片或包装图片文件 ID。 |
| `ocrRawText` | string | 后续 | OCR 原始文本，仅在确认流程或调试中使用。 |
| `ocrConfidence` | number | 后续 | OCR 置信度。 |
| `aiChatVisible` | boolean | 后续 | 是否允许该食品信息进入 AI 聊天上下文。 |
| `recipeTags` | string[] | 后续 | 菜谱规划标签，例如早餐、快手菜、儿童餐等。 |
| `lastConsumedAt` | string | 后续 | 最近一次消耗或更新剩余量的时间。 |

演进原则：

- 新字段应尽量向后兼容，旧数据缺少新字段时也能正常展示。
- 识别类字段应区分“识别结果”和“用户确认后的正式字段”。
- 涉及密钥、账号、付费外部 API、OCR、AI 的逻辑如后续启用，应放在服务端，不放在客户端。
- AI 聊天和菜谱规划属于后续阶段，不应阻塞 MVP 的食品库存和保质期管理能力。
