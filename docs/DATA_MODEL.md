# 数据模型说明

## 1. 设计原则

食期管家的数据模型服务于面向家庭 / 老年友好的食品库存与保质期管理小程序。MVP 阶段应保持字段清晰、来源明确、便于人工确认和后续扩展。

核心原则：

- `expiryDate` 是最终可食用日期，也是排序和提醒逻辑的标准字段。
- 用户可以通过生产日期 + 保质期计算得到 `expiryDate`，也可以直接手动输入 `expiryDate`。
- 使用 `dateSource` 记录 `expiryDate` 的来源，避免后续编辑时混淆。
- `quantity` 和 `remainingQuantity` 分开保存，支持库存和剩余量管理。
- `barcode`、`barcodeLookupStatus`、`productNameSource`、`reminderPlan` 等字段为后续阶段预留，但 MVP 不实现真实条码查询或智能提醒。
- 条码查询、OCR 和 AI 识别结果必须经过用户确认后才能保存。
- API Key、OCR Key、AI Key、云开发密钥或其他敏感凭据不得放入小程序前端代码。
- AI 聊天不得做医疗、营养或绝对食品安全声明。

## 2. 核心食品字段

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 食品记录唯一标识。早期本地数据可使用本地生成 ID，云端阶段可使用数据库记录 ID。 |
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

数量规则：

- `quantity` 表示初始登记数量。
- `remainingQuantity` 表示当前剩余数量。
- `remainingQuantity` 不应大于 `quantity`，除非用户明确执行补货或修正操作。
- 第二阶段可增加快速减少、吃完、补货等操作，但 MVP 只定义字段。

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

## 6. 条码预留字段

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `barcode` | string | 否 | 食品条码。MVP 只预留字段，不实现扫码和查询。 |
| `barcodeLookupStatus` | string | 否 | 条码查询状态，例如 `notStarted`、`pending`、`found`、`notFound`、`failed`、`confirmed`。 |
| `productNameSource` | string | 否 | 商品名称来源，例如 `manual`、`barcodeLookup`、`ocr`、`ai`。 |

条码规则：

- MVP 不接入真实条码 API。
- 条码查询结果不得自动保存。
- 第二阶段实现条码查询时，查询结果必须展示给用户确认和编辑，用户确认后才能写入食品记录。
- 条码 API Key 或商品库访问密钥不得出现在小程序前端代码中。

## 7. 提醒字段

| 字段 | 类型 | MVP 必需 | 说明 |
| --- | --- | --- | --- |
| `reminderEnabled` | boolean | 否 | 是否开启提醒。MVP 可预留，默认可为 `false`。 |
| `reminderPlan` | object | 否 | 提醒计划，为后续规则化智能提醒预留。 |

`reminderPlan` 可以预留如下结构：

```json
{
  "offsetDays": [7, 3, 1, 0],
  "strategy": "default",
  "lastScheduledAt": null
}
```

提醒规则：

- 提醒逻辑必须基于 `expiryDate`。
- MVP 不实现订阅消息或真实提醒调度。
- 后续规则化智能提醒可以根据 `category`、`storageMethod`、`remainingQuantity`、`expiryDate` 等字段生成提醒计划。
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

MVP 字段应优先保证手动录入、保质期计算、最终可食用日期、分类、数量、剩余数量、保存方式、基础筛选和统计可用。

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
- 涉及密钥、外部 API、OCR、AI 的逻辑应放在云函数或服务端，不放在小程序前端。
- AI 聊天和菜谱规划属于后续阶段，不应阻塞 MVP 的食品库存和保质期管理能力。
