# V2 D1a 商品库存与批次详情验收

日期：2026-07-30

## 结论

- D1a 商品分组库存与全屏批次详情：`PASS`
- Android V2 整体：`PARTIAL`

D1a 已将库存从“一批次一卡片”改为“商品档案一行、批次在详情中独立管理”。同一条码不会作为分组键；同条码不同商品档案保持两行。真实手机相机、Room 迁移、完整公开集指标和其余业务页面迁移不在本节点内，因此不能据此宣称 V2 全部完成。

## 自动化门禁

- 纯 Java 逻辑测试：`217 passed, 0 failed`
- Android Lint：`PASS`
- `:apk:app:assembleDebug`：`PASS`
- 覆盖安装：`adb install -r` 成功
- 版本：`versionName 0.4.3`、`versionCode 33`

新增测试覆盖：

- 只按 `productProfileId` 分组
- 同条码不同商品档案不合并
- 有效批次按 `expiryDate` 升序、未知日期靠后、已用完批次最后
- 商品数量与在库/已用完批次数聚合
- 减量选择最早到期且仍有库存的批次
- 旧数据缺少档案 ID 时不会相互误合并
- 长批次 ID 显示可区分的末尾 8 位
- 明确无过期时间与日期缺失在商品摘要中保持不同状态

## 完整用户流程

| 流程 | 实际结果 | 状态 |
| --- | --- | --- |
| 搜索批次 `later` | 只显示“原生鲜牛乳”1 个商品、1 个匹配批次 | PASS |
| 搜索结果进入详情再返回 | 查询词、结果和库存目的地保持；数据哈希不变 | PASS |
| 筛选“已用完” | 只显示 1 个商品、1 个已用完批次；减量按钮禁用 | PASS |
| 商品外层 `-1` | `qa-milk-early` 从 2 减至 1，较晚批次保持 3 | PASS |
| 撤销减量 | 最早批次恢复为 2，较晚批次仍为 3 | PASS |
| 打开补货 | 相机识别本批次日期位于 360x640 与 360x800 首屏 | PASS |
| 取消补货 | 原商品和全部批次不变，数据哈希不变 | PASS |
| 新增补货批次 | 生成独立 `food_*` UUID，复用原 `productProfileId` | PASS |
| 修改新增批次 | 行内编辑将总量/剩余量从 1/1 改为 2/2 | PASS |
| 删除新增批次 | 二次确认后只删除目标批次，原 6 个批次保留 | PASS |
| 三批次详情 | 2 个在库批次和 1 个已用完批次均可独立编辑/更多操作 | PASS |

测试发现并修复了两个不能靠代码推测发现的问题：

1. 正式批次 UUID 都有相同 `food_` 前缀，截取前 8 位会显示成相同编号。现在长编号显示末尾 8 位。
2. 补货相机按钮原本在基本信息下方，首屏看不到。现在“本批次日期”识别区置于补货表单顶部，识别结果仍只回填新批次。

## 视觉检查

库存页与商品详情逐张检查了以下视口：

- 360x640 @ 160dpi
- 360x800 @ 160dpi
- 412x915 @ 160dpi
- 720x1280 @ 320dpi
- 1080x2400 @ 420dpi

检查结果：

- 文字、商品行、减量和补货按钮没有重叠或越界。
- 48dp 高频触控区保持稳定。
- 详情页隐藏底部导航，系统返回恢复库存页。
- 三批次在窄屏中可滚动查看，编号、日期、数量、状态和操作可区分。
- 360x640 补货表单首屏仍能看到相机按钮和固定的取消/保存按钮。

证据目录：`docs/qa/screenshots/2026-07-30-redesign-v2-d1a/`

代表截图：

- [1080x2400 商品库存](screenshots/2026-07-30-redesign-v2-d1a/09-inventory-1080x2400.png)
- [三批次详情顶部](screenshots/2026-07-30-redesign-v2-d1a/11-multibatch-detail-top.png)
- [三批次详情底部](screenshots/2026-07-30-redesign-v2-d1a/12-multibatch-detail-bottom.png)
- [360x640 补货相机首屏](screenshots/2026-07-30-redesign-v2-d1a/14-replenishment-camera-first-screen-360x640.png)
- [减量撤销](screenshots/2026-07-30-redesign-v2-d1a/15-decrement-undo-360x800.png)
- [明确无过期时间的库存](screenshots/2026-07-30-redesign-v2-d1a/16-explicit-no-expiry-inventory.png)
- [明确无过期时间的详情](screenshots/2026-07-30-redesign-v2-d1a/17-explicit-no-expiry-detail.png)
- [恢复后的原始库存](screenshots/2026-07-30-redesign-v2-d1a/18-restored-original-inventory.png)

## 数据与升级

- 第一次覆盖安装前后原有数据文件 SHA-256 均为 `e2024440fbab904849eadf075605b1a190a67fc79e27f193f206d67571805194`。
- QA 的减量、增改删只在已备份的模拟数据上执行。
- QA 结束后已恢复原牛奶批次：剩余 2、原 `updatedAt` 和三份安全恢复副本均保留。
- 恢复后的数据文件 SHA-256 为 `c1c2e0c0072f9f37f5bd83e55bf8db901521dd64f66db7cf2d701db1dea317b7`。

SharedPreferences 会为每次显式写入旋转恢复副本，因此“执行真实增改删后整个 XML 哈希仍与测试前一致”不是合理要求；验收分别检查了覆盖安装不写数据、取消不写数据，以及确认增改删后主批次字段按预期变化。

## APK 产物

- 路径：`artifacts/shiqi-v0.4.3-d1a-debug.apk`
- 大小：`220561247` 字节（210.34 MiB）
- SHA-256：`8ff89ed5a2dcacfd490957b92c528d1794d4b370d851f62d91af355fbdb537c4`
- APK Signature Scheme v2：`true`
- 签名证书 SHA-256：`eba5350af5a9182067b6bfac2dd1c7b74438b7905cce0b2bc67a12d6ca30be91`
## 未覆盖与剩余风险

- AVD 不能证明真实手机镜头的自动对焦、曝光、持续帧率和温升。
- 本节点没有改动 OCR 模型或相机调度。
- Room 数据层、其余 V2 业务页面、公开集字段统计和发布候选 APK 仍未完成。
- 最终发布门槛仍要求真实手机完整流程与相机视觉验收。
