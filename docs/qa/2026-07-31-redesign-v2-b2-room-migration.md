# V2 B2 Room 数据层与无损迁移验收

日期：2026-07-31

版本：`0.4.7 (37)`

## 结论

`B2 Room 数据层与无损迁移：PASS`

库存主存储已从单个 SharedPreferences JSON 迁移到 AndroidX Room `2.8.4` schema 1。商品、批次、条码、商品与条码多对多关系、批次提醒计划和迁移校验元数据均已规范化落库；schema 0、2、3 的旧数据在一个事务中迁移并读回校验，原 payload 在迁移前写入应用私有备份。迁移成功后旧 SharedPreferences 保持原样且只读。

V0.4.6 覆盖安装到 V0.4.7、完整增改补删、Excel 导入导出、错误行、强制失败回滚、JSON 筛选分享、同批次更新、重复导入、旧版本保护和重启持久化均已实际执行。涉及界面的证据均检查了真实截图，没有用 UI 树或代码推断代替视觉验收。

## 实现边界

- 使用 Java 工程可直接接入的经典 Room `2.8.4`，没有为迁移额外引入 Kotlin、KSP、协程或 Room 3。
- 不调用 `fallbackToDestructiveMigration()`，也不允许主线程直接访问 DAO。
- `FoodItem.id` 保持唯一批次编号，`productProfileId` 保持商品身份。
- 一个条码可关联多个商品，一个商品也可保留多个历史条码；条码或单位变化不会错误拆分商品。
- Room 写入、读回摘要和指纹校验位于同一事务；强制异常会回滚到原库存。
- 当前同步 `FoodStore` 外观只是兼容层，数据库操作已转入专用单线程执行器；页面级异步 Repository 属于后续独立节点。

## 自动化检查

| 检查 | 结果 |
| --- | --- |
| 既有本地业务与 OCR 逻辑 | `221 passed, 0 failed` |
| CameraX 最新帧与对焦策略 | `5 passed, 0 failed` |
| `FoodEditorSessionTest` | `9 passed` |
| `InventorySnapshotMapperTest` | `29 passed` |
| Android Room 仪器测试 | Medium_Phone AVD `5 passed` |
| Android Lint | `PASS` |
| `:apk:app:assembleDebug` | `PASS` |
| 最终 APK 冷启动 | `PASS`，`versionName 0.4.7`、`versionCode 37` |
| APK Signature Scheme v2 | `PASS` |

5 项仪器测试覆盖空安装、schema 0/2/3、幂等加载、事务失败回滚、旧 SharedPreferences 不写回、Room 指纹损坏时只读回退，以及同商品多历史条码的 Room 往返。

## 覆盖升级证据

1. 安装 V0.4.6，完全通过界面新增 `UpgradeMilk`：条码 `4006381333931`、数量 3、剩余 2、生产日期 `2026-07-15`、保质期 30 日、最终日期 `2026-08-14`。
2. 记录旧 SharedPreferences XML SHA-256：`62474ee0216d3faba6667ad19d4a0385eb5e1c169eff44b7374800f30e0c6bfc`。
3. 使用 `adb install -r` 覆盖安装 V0.4.7，商品、条码、数量和日期完整显示。
4. 升级后旧 XML SHA-256 仍为同一值，说明新版本没有重写旧源数据。
5. Room 数据库及私有迁移备份 `files/inventory-migration/room-v1-legacy-0e833339b7549f74.json` 均存在；备份的格式版本、源 schema、UTF-8 长度、payload、内部 SHA-256 和实际 payload 散列逐项一致。

## 用户与数据流程

| 流程 | 结果 | 观察 |
| --- | --- | --- |
| 快捷 `-1` | PASS | `UpgradeMilk` 剩余 2 降到 1，并显示操作反馈 |
| 补货 | PASS | 新建数量 4、生产日期 `2026-07-20`、保质期 20 日、最终日期 `2026-08-09` 的独立批次 |
| 编辑 | PASS | 新批次改为数量 6、剩余 5，原批次仍为 1/3 |
| 新增与删除 | PASS | `TempDelete` 新增后可删除，`UpgradeMilk` 未受影响 |
| Excel 导出 | PASS | 生成有效 `.xlsx`，OpenXML 结构和 3 行商品/条码/批次字段可解析 |
| Excel 有效导入 | PASS | 预览 3 行、可导入 3 行、错误 0 行，确认后恢复独立批次 |
| Excel 错误行 | PASS | 缺少商品名和非法日期分别显示行号、字段、原值与原因；错误行不导入 |
| Excel 强制回滚 | PASS | 强制 Room 事务失败后提示“导入失败，当前本地数据未改动”，库存完全不变 |
| JSON 组合筛选 | PASS | 入库、生产和最终日期过滤后只导出 `TempDelete` 一个批次，筛选条件与唯一批次 ID 正确 |
| JSON 系统分享 | PASS / PARTIAL | Android Sharesheet 正常打开并携带文件；AVD 未安装 QQ/微信，未验证其客户端落点 |
| JSON 较新更新 | PASS | 同一批次只更新数量和状态，不新增重复商品或批次 |
| JSON 重复导入 | PASS | 第二次导入判定内容相同，无新增、无更新 |
| JSON 较旧保护 | PASS | 较旧文件标记“保留本机较新”，不覆盖当前库存 |
| 冷启动重启 | PASS | 两个商品档案、三个批次及各自数量和日期保持不变 |

## APK 交付

- 文件：`artifacts/shiqi-v0.4.7-b2-debug.apk`
- 大小：`220592782` bytes
- SHA-256：`c2489e6bfd69fbc315939edd5001b4a20d0bd0c5e1c9d866e6f65bbd9df6a939`
- 签名：APK Signature Scheme v2 `PASS`
- 证书 SHA-256：`eba5350af5a9182067b6bfac2dd1c7b74438b7905cce0b2bc67a12d6ca30be91`
- AVD 实装：Medium_Phone Android 10，冷启动 `PASS`

## 视觉证据

目录：`docs/qa/screenshots/2026-07-31-redesign-v2-b2-room/`

| 文件 | 验证内容 |
| --- | --- |
| `02-v046-legacy-baseline-stable.png` | V0.4.6 升级前真实库存基线 |
| `03-v047-room-upgrade.png` | V0.4.7 首次启动迁移后商品、日期和数量保留 |
| `04-v047-room-edit-detail.png` | Room 下补货、编辑和两个独立批次详情 |
| `05-json-filter.png` | JSON 入库/生产/最终日期组合筛选 |
| `06-json-share-sheet.png` | Android 系统分享面板与导出文件名 |
| `07-excel-import-preview.png` | Excel 有效行预览和确认入口 |
| `08-json-update-preview.png` | JSON 同批次较新更新预览 |
| `09-excel-error-preview.png` | Excel 可导入/错误/警告数量 |
| `10-excel-error-details.png` | Excel 错误行号、字段和值 |
| `11-excel-import-rollback-toast.png` | 强制失败后的不改动提示与原库存 |
| `12-v047-final-cold-start.png` | 最终构建安装后的空库冷启动画面 |

以上截图已逐张或通过清晰联系表实际查看；标题、数据、按钮和弹窗无重叠、遮挡或截断。最终冷启动画面再次单独查看，空库入口和底部导航完整可见。

## 尚未覆盖

- 真实 Android 手机的相机对焦、曝光、持续帧率、温升和中文低对比包装识别，仍属于 `C1b`，不能用 AVD 或数据迁移结果代替。
- AVD 没有安装 QQ/微信，只验证到标准 Android Sharesheet；目标客户端接收文件仍需真机补证。
- 兼容层每次保存会在事务内重建规范化快照，复杂度为 O(n)；当前 640 项本地逻辑探针和实际库存流程可用，后续异步 Repository 将再做增量写入优化。

## 参考

- [Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Room relationships](https://developer.android.com/training/data-storage/room/relationships)
- [Room many-to-many relationships](https://developer.android.com/training/data-storage/room/relationships/many-to-many)
- [AndroidX Room release notes](https://developer.android.com/jetpack/androidx/releases/room)