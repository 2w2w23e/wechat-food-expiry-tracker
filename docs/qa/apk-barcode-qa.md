# APK 条码真实场景 QA 清单

日期：2026-07-05

## 1. 范围与边界

本文档用于记录“食期管家”Android 本地 APK 的真机条码扫码 QA。测试对象只包含 APK 本地版现有条码能力：

- 实时相机扫码。
- 图库照片识别。
- 手动输入条码兜底。
- 商品信息查询与用户确认后保存。

明确边界：

- 不写入 API key、OCR key、OpenAI key 或其他密钥。
- 不允许识别结果或查询结果自动入库。
- 不验证云同步，因为当前 APK 本地版没有云数据库、账号体系或多设备同步。
- 不做医疗、营养或绝对食品安全判断。

## 2. 四步判定口径

每个场景都按下面四步分别记录，避免把“能扫到码”和“能查到商品”混在一起：

| 步骤 | 判定内容 | PASS 口径 |
| --- | --- | --- |
| 1. 扫码识别 | 相机预览或图库解码是否识别到可解析的码图形 | 页面能从真实包装或图片中识别出候选码，不崩溃不卡死 |
| 2. 条码提取 | 是否提取出正确商品码、GTIN、EAN-13 或 GS1 参数 | 提取值与包装标注或测试样本预期一致 |
| 3. 商品信息查询 | 是否用提取出的码查询商品名、规格、厂家等信息 | 查询有结果时正确回填；无结果时给出可理解的空结果或失败提示 |
| 4. 用户确认保存 | 查询或手动编辑后的信息是否必须由用户确认后才保存 | 未点击确认不会入库；点击确认后保存为本地食品记录或草稿入口预期状态 |

## 3. 真机场景测试清单

| ID | 场景 | 样品要求 | 操作要点 | 重点验证 |
| --- | --- | --- | --- | --- |
| BAR-REAL-001 | 远距离一维码 | 常见食品包装上的 EAN-13 / UPC 一维码 | 从约 50-80 cm 开始扫码，逐步靠近；必要时使用缩放按钮或双指缩放 | 远距离预览不崩溃；靠近后能完成扫码识别和条码提取 |
| BAR-REAL-002 | 反光包装 | 铝箔袋、亮面塑料袋、罐头贴纸等反光包装 | 在强光、弱光、倾斜角度各扫一次 | 反光导致失败时有兜底路径；成功时提取值准确 |
| BAR-REAL-003 | 弧面包装 | 瓶身、罐身、圆柱形盒身上的条码 | 保持包装弧面完整入框，分别测试正对和轻微旋转角度 | 弧面畸变下不误提取；失败时能重试或改用手动输入 |
| BAR-REAL-004 | 进口 EAN-13 | 进口食品 EAN-13，优先选择非 690-699 开头样品 | 扫码后观察商品信息查询结果；必要时记录无结果 | 条码提取准确；商品查询无命中不影响用户手动确认保存 |
| BAR-REAL-005 | GS1 Digital Link QR | 含 GS1 Digital Link 的 QR Code，或测试 QR 样本 | 扫描 QR，核对 URL 中 GTIN / 商品码参数提取结果 | 能从 QR 中提取商品码或 GTIN；不会把整段 URL 当作食品名直接保存 |
| BAR-REAL-006 | Data Matrix | 含 GS1 AI `(01)` GTIN 的 Data Matrix，或测试样本 | 扫描 Data Matrix，核对 `(01)` 后的 GTIN | 能识别 Data Matrix 并提取商品码；无法查询时提示清楚 |
| BAR-REAL-007 | 图库照片 | 真实包装条码照片、屏幕截图或测试码图片 | 从扫码页右下角图库入口选择图片识别 | 图库权限、选择、解码流程可用；失败时不产生脏数据 |
| BAR-REAL-008 | 手动输入兜底 | 任意已知 EAN-13 / GTIN，含一个故意输错样例 | 扫码失败后手动输入，分别测试有效码和无效码 | 有效码可进入查询和确认；无效码有提示且不自动保存 |

## 4. 结果等级

| 结果 | 含义 |
| --- | --- |
| PASS | 四步均符合预期，且没有崩溃、自动入库、密钥或云同步行为 |
| PARTIAL | 扫码识别和条码提取可用，但商品信息查询无命中、弱网失败或需要用户手动补全；确认保存边界仍正确 |
| BLOCKED | 因缺少真机、相机权限、安装包、测试样本、网络环境或 Android SDK 环境导致无法执行 |
| FAIL | 任一步出现错误提取、崩溃、卡死、误保存、绕过用户确认、写入密钥或触发云同步等问题 |

## 5. 单次 QA 记录模板

```text
测试批次：
测试日期：
测试人：
APK 来源 / 版本：
设备型号：
Android 版本：
网络环境：
相机权限：
图库权限：

总体结论：PASS / PARTIAL / BLOCKED / FAIL
阻塞项：
遗留风险：
```

| ID | 场景 | 样品 / 条码 | 扫码识别 | 条码提取 | 商品信息查询 | 用户确认保存 | 结论 | 证据 / 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BAR-REAL-001 | 远距离一维码 |  | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL |  |  |
| BAR-REAL-002 | 反光包装 |  | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL |  |  |
| BAR-REAL-003 | 弧面包装 |  | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL |  |  |
| BAR-REAL-004 | 进口 EAN-13 |  | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL |  |  |
| BAR-REAL-005 | GS1 Digital Link QR |  | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL |  |  |
| BAR-REAL-006 | Data Matrix |  | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL |  |  |
| BAR-REAL-007 | 图库照片 |  | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL |  |  |
| BAR-REAL-008 | 手动输入兜底 |  | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL | PASS / PARTIAL / BLOCKED / FAIL |  |  |

## 6. 缺陷记录模板

```text
缺陷编号：
关联场景：
实际结果：
预期结果：
复现步骤：
影响步骤：扫码识别 / 条码提取 / 商品信息查询 / 用户确认保存
结果等级：PARTIAL / BLOCKED / FAIL
设备与系统：
截图或录屏：
备注：
```
