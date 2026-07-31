# 食期管家 V2 数据与升级迁移设计

状态：B2 已实现并验收（Room schema v1）
日期：2026-07-31

## 1. 当前基线

当前 APK 使用：

- `FoodStore` 将完整库存 JSON 保存到 `SharedPreferences` 的 `food_expiry_tracker_foods_v0`。
- 当前 schema 版本为 3。
- `FoodItem.id` 已作为唯一批次编号。
- `FoodItem.productProfileId` 已区分商品档案与批次。
- `FoodStoreMigration` 已支持旧数组、商品档案字段、唯一批次 ID、迁移前备份和三份近期备份。
- `BarcodeHistoryStore` 另存用户确认后的条码草稿模板。
- 智能提醒偏移、指纹和计划日期保存在每个 `FoodItem` 中。

这些能力必须保留。V2 不允许因为重新设计页面而重置 storage key、包名、签名或 schema。

## 2. 为什么迁移到 Room

单个 SharedPreferences JSON 已经承担商品、批次、条码、提醒、归档、Excel 和 JSON 同步。继续扩展会带来：

- 任意一项变化都要序列化整个库存。
- 商品、条码和批次关系只能靠约定维护。
- 批量导入难以获得真正的事务回滚。
- 数据量增加后，读取、排序和筛选都依赖完整内存列表。
- 多对多条码关系和一对多批次关系缺少数据库约束。

Android 官方建议复杂数据、部分更新和引用完整性使用 Room，而不是 DataStore。Room 原生支持事务、一对多和多对多关系，并提供 schema 导出和 `MigrationTestHelper`。

V2 采用 Room 保存库存；小型应用设置暂时保留 SharedPreferences。不要同时引入 Room、Proto DataStore 和新的 UI 框架，避免一次迁移过多基础设施。

## 3. Room 实体

### 3.1 `products`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `productId` | text | 主键，沿用 `productProfileId` |
| `name` | text | 非空 |
| `category` | text | 非空 |
| `defaultUnit` | text | 非空 |
| `defaultStorageMethod` | text | 可空 |
| `defaultLocation` | text | 可空 |
| `defaultShelfLifeValue` | integer | 可空 |
| `defaultShelfLifeUnit` | text | 可空 |
| `notes` | text | 可空 |
| `createdAt` | text | ISO 时间 |
| `updatedAt` | text | ISO 时间 |

### 3.2 `batches`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `batchId` | text | 主键，沿用 `FoodItem.id` |
| `productId` | text | 外键指向 `products` |
| `productionDate` | text | 可空，ISO 日期 |
| `shelfLifeValue` | integer | 可空 |
| `shelfLifeUnit` | text | 可空 |
| `expiryDate` | text | 可空，规范最终日期 |
| `dateSource` | text | `calculated/manual/recognized/unknown` |
| `quantity` | real | 非负 |
| `remainingQuantity` | real | 0 到 quantity |
| `unit` | text | 非空 |
| `storageMethod` | text | 非空 |
| `location` | text | 非空 |
| `openedDate` | text | 可空 |
| `afterOpenShelfLifeValue` | integer | 可空 |
| `afterOpenShelfLifeUnit` | text | 可空 |
| `status` | text | `active/finished/archived` |
| `finishedAt` | text | 可空 |
| `notes` | text | 可空 |
| `createdAt` | text | ISO 时间 |
| `updatedAt` | text | ISO 时间 |

`expiryDate` 仍是排序和提醒的唯一最终日期。没有日期时状态是 `unknown`，不得自动写成“永不过期”。

### 3.3 `barcodes`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `barcode` | text | 主键，规范到 GTIN 表达 |
| `format` | text | EAN-13、EAN-8、UPC 等 |
| `createdAt` | text | ISO 时间 |
| `lastConfirmedAt` | text | ISO 时间 |

### 3.4 `product_barcodes`

联合主键：`productId + barcode`。

这是商品与条码的多对多关联表：

- 一个条码可以对应多个商品档案。
- 一个商品档案可以保存多个历史或包装条码。
- 扫码查找返回列表，不能强制一对一。

### 3.5 `batch_reminder_plans`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `batchId` | text | 主键和外键 |
| `mode` | text | `fixed/smart` |
| `offsetsJson` | text | 已计算提醒偏移 |
| `fingerprint` | text | 影响提醒输入的稳定指纹 |
| `plannedDaysLeft` | integer | 计算时剩余天数 |
| `plannedOn` | text | 计算日期 |

提醒只在新增批次或指纹变化时重算。指纹至少包含类别、`expiryDate`、保存方式、开封日期、开封后保质期和提醒模式。

## 4. Repository 边界

UI、OCR、Excel 和 JSON 不直接访问 DAO。统一通过：

```text
InventoryRepository
  - observeDashboard()
  - searchProducts(query, filters)
  - getProductWithBatches(productId)
  - createConfirmedBatch(draft)
  - replenishProduct(productId, batchDraft)
  - decrementBatch(batchId, amount)
  - updateBatch(batchId, changes)
  - archiveBatch(batchId)
  - deleteBatch(batchId)
  - findProductsByBarcode(barcode)
  - importTransaction(payload, policy)
  - exportSnapshot(filters)
```

识别模块只输出 `ConfirmationDraft`。只有确认页调用 `createConfirmedBatch` 或 `replenishProduct`。

## 5. 首次迁移

### 5.1 迁移条件

同时满足：

- Room 库尚未完成初始化。
- 旧 `FoodStore` 存在有效 payload。
- 没有已完成迁移标记。

迁移函数必须幂等，重复执行不会产生重复商品或批次。

### 5.2 迁移步骤

1. 使用现有 `FoodStoreMigration` 把旧数据先标准化到 schema 3 内存模型。
2. 将旧原始 JSON、版本、长度和 SHA-256 写入应用私有迁移备份文件。
3. 校验所有批次 ID 唯一、数量边界、日期格式和 `expiryDate` 规则。
4. 在一个 Room 事务中：
   - 按 `productProfileId` 建立商品档案。
   - 保留每个原 `FoodItem.id` 作为 `batchId`。
   - 建立条码和商品交叉关系。
   - 迁移智能提醒计划。
5. 读回 Room，比较商品数、批次数、总数量、归档数和关键字段摘要。
6. 校验成功后在 Room 元数据表写入 `inventory_initialized = 1` 标记。
7. 至少两个 APK 版本内保留旧 SharedPreferences 和迁移备份为只读恢复源。

禁止调用 `fallbackToDestructiveMigration()`。官方文档明确说明，它会在缺少迁移路径时永久删除用户表。

### 5.3 冲突处理

- 同一 `productProfileId` 下稳定字段一致：合并到一个商品档案。
- 同一 `productProfileId` 下名称或类别明显冲突：确定性拆分新 productId，不能任意覆盖。
- 缺少 `productProfileId`：沿用现有 `FoodStoreMigration` 的稳定生成规则。
- 条码相同但 productId 不同：保留多对多关系。
- 批次 ID 重复：使用现有唯一 ID 修复规则，并记录迁移报告。
- 未知旧字段：原始 JSON 备份保留，不在迁移失败时覆盖。

### 5.4 V0.4.7 实施结果

- 使用 Java 可直接接入的经典 Room `2.8.4`，数据库 schema 为 1，导出的 schema JSON 已纳入版本库。
- `products`、`batches`、`barcodes`、`product_barcodes`、`batch_reminder_plans` 和迁移元数据已规范化落库。
- schema 0、2、3 的 SharedPreferences 数据先由既有迁移器标准化，再在单个 Room 事务中写入和读回校验。
- 迁移前私有备份保存原 payload、原 schema、UTF-8 字节长度和 SHA-256；文件落盘后再次校验内容与散列。
- Room 初始化成功后，旧 SharedPreferences 不再写入，只作为至少两个版本周期内的只读恢复源。
- 同一条码可关联多个商品，同一商品也可保留多个历史条码；同商品的不同批次仍保留独立日期和数量。
- 兼容阶段继续保留同步 `FoodStore` 外观，但所有 Room 工作都在专用单线程执行器运行；后续 Repository 异步化不属于 B2。

## 6. 回滚与恢复

- 首次迁移事务失败：数据库不留下部分记录，旧 SharedPreferences 继续作为权威存储并可在下次启动重试；不允许空列表覆盖旧数据。
- 事务内读回校验失败：抛出异常并整体回滚，`inventory_initialized` 不会写入。
- 已初始化 Room 后发生打开或指纹校验错误：界面只读回退到仍保留的旧数据并拒绝保存，防止错误状态覆盖任一数据源。
- V0.4.7 已生成和验证私有迁移备份，但面向用户的备份选择与主动恢复界面尚未实现，应在设置页迁移节点补齐。
- 后续主动恢复必须展示备份时间、商品数、批次数和覆盖范围，并在单个事务中写入和重新读回校验。

## 7. JSON 与 Excel

### 7.1 JSON 快速同步

V2 的完整规范化快照目标 envelope 如下：

```json
{
  "format": "shiqi-local-transfer",
  "formatVersion": 2,
  "exportedAt": "2026-07-29T09:41:00+08:00",
  "filters": {},
  "products": [],
  "barcodes": [],
  "productBarcodes": [],
  "batches": [],
  "reminderPlans": []
}
```

V0.4.7 的 B2 迁移不改变已经交付给用户的快速换机文件契约。当前应用继续读写 `format = shiqi-food-batch-transfer`、`schemaVersion = 1` 的批次文件，并通过 `batchId` 幂等新增或更新。上面的 `formatVersion = 2` 是未来完整备份格式，不得在没有兼容导入器和升级测试时直接替换现有格式。

导入键：

- 商品：`productId`。
- 条码关系：`productId + barcode`。
- 批次：`batchId`。
- 提醒：`batchId`。

相同批次 ID 默认更新数量、状态和 `updatedAt` 较新的字段；日期冲突必须在预览页明确列出，不静默覆盖。

### 7.2 Excel

- Excel 是批量维护格式，不承担完整备份。
- 导入先解析为内存 staging 数据并完成逐行校验，确认后才打开 Room 事务。
- 错误行显示行号、字段、原值和原因。
- 错误行绝不写入；用户确认时只把预览中可导入的行作为一个事务提交。
- Room 写入或读回校验发生任何错误时，本次所有有效行整批回滚，原库存保持不变。
- 导出后重新导入到空测试库，关键字段必须等价。

## 8. 升级与签名

数据迁移通过还不等于可升级：

- 包名必须保持 `com.shiqi.expirytracker`。
- 所有交付 APK 使用同一长期签名。
- 每次发布递增 `versionCode`。
- 验收必须从至少两个历史 APK 覆盖安装到新 APK。
- 安装前后比较商品数、批次数、条码关系、数量、日期、归档和提醒计划。
- 签名冲突属于发布阻断，不得让用户卸载重装来绕过。

## 9. 测试矩阵

| 场景 | 预期 |
| --- | --- |
| 空安装 | 创建空 Room，无旧迁移标记错误 |
| schema 0 数组 | 全量迁移且 ID 稳定 |
| schema 2 | 补齐唯一批次 ID |
| schema 3 | 商品、条码、批次和提醒完整 |
| 主 payload 损坏 | 从近期备份恢复 |
| 未来 schema | 拒绝覆盖并保留原数据 |
| 迁移中异常 | Room 事务回滚，旧数据可读 |
| 相同条码多个商品 | 查询返回多个档案 |
| 同一商品多个批次 | 日期和数量互不覆盖 |
| JSON 重复导入 | 相同 batchId 幂等更新 |
| Excel 第 N 行错误 | 错误行不写入并显示详情；有效行确认后一次事务提交 |
| 历史 APK 覆盖安装 | 签名一致且数据完全保留 |

Room schema JSON 必须提交到版本库；每次 schema 变化使用 `MigrationTestHelper` 验证所有历史版本到当前版本的路径。

## 10. 参考资料

- [Android DataStore guidance](https://developer.android.com/topic/libraries/architecture/datastore)：复杂数据、部分更新和引用完整性应使用 Room。
- [Room relationships](https://developer.android.com/training/data-storage/room/relationships)：一对多和多对多数据建模。
- [Room many-to-many](https://developer.android.com/training/data-storage/room/relationships/many-to-many)：交叉表与事务查询。
- [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions)：schema 导出、迁移测试和禁止破坏性回退。
