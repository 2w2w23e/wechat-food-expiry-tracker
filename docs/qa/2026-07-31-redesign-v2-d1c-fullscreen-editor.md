# V2 D1c 全屏录入编辑器验收

日期：2026-07-31

版本：`0.4.6 (36)`

## 结论

`D1c 全屏录入编辑器：PASS`

新增、编辑和补货不再使用嵌套 `AlertDialog`，而是进入同一套全屏批次编辑器。识别结果只预填可编辑字段，用户点击底部主按钮后才写入本地数据。创建、编辑、补货、删除、保存失败回滚、覆盖安装保数和两种屏幕尺寸的视觉验收均通过。

真实手机镜头的对焦、曝光、持续帧率和温升不属于本节点，仍是 `C1b：PARTIAL`。补货视频回填后的日期区域二次截图因 AVD 在高负载回放时掉线未取得，因此这一条视觉证据为 `PARTIAL`，不能外推为真机相机通过。

## 自动化检查

| 检查 | 结果 |
| --- | --- |
| 既有本地业务与 OCR 逻辑 | `219 passed, 0 failed` |
| CameraX 最新帧与对焦策略 | `5 passed, 0 failed` |
| `FoodEditorSessionTest` | `9 passed` |
| Android Lint | `PASS` |
| `:apk:app:assembleDebug` | `PASS` |
| `git diff --check` | `PASS` |

## 用户流程验收

| 流程 | 结果 | 观察 |
| --- | --- | --- |
| 空白新增后直接返回 | PASS | 未修改时不弹出无意义确认框 |
| 修改后返回 | PASS | 弹出“放弃未保存内容？”，继续填写后字段仍保留 |
| 新增商品 | PASS | `2026-07-30 + 10 日` 自动得到 `2026-08-09`，保存后进入库存 |
| 编辑批次 | PASS | 保存后回到原商品详情，商品档案变化时使用新档案 ID |
| 补货新批次 | PASS | 复用商品身份但建立独立数量和日期，原批次不被覆盖 |
| 删除批次 | PASS | 两个 QA 批次均可删除，基线商品仍存在 |
| OCR 结果预填 | PASS | 回填“上好佳薯条”、条码、`2026-01-20`、`9 个月`、`2026-10-20` |
| OCR 不自动入库 | PASS | 未点保存直接放弃后，持久化数据哈希不变 |
| 强制保存失败 | PASS | 编辑器与输入保留，内存和持久化数据回滚到最近成功快照 |
| 补货视频回填 | PASS / PARTIAL | 识别页得到日期并返回同一补货编辑器；回填后日期区域截图因 AVD 掉线缺失 |
| 覆盖安装 | PASS | `adb install -r` 成功，安装前后原始数据哈希一致 |

基线数据 SHA-256：

`c1c2e0c0072f9f37f5bd83e55bf8db901521dd64f66db7cf2d701db1dea317b7`

## APK 交付

- 文件：`artifacts/shiqi-v0.4.6-d1c-debug.apk`
- 大小：`220240688` bytes
- SHA-256：`2e676f7c6437316d3ce4bc3f025cbb4a9baa1ba4a217433be30437b1bbd3765a`
- 签名：APK Signature Scheme v2 `PASS`
- 证书 SHA-256：`eba5350af5a9182067b6bfac2dd1c7b74438b7905cce0b2bc67a12d6ca30be91`
- AVD 实装：`versionName 0.4.6`、`versionCode 36`，`adb install -r` 成功

## 视觉证据

目录：`docs/qa/screenshots/2026-07-30-redesign-v2-d1c/`

| 文件 | 验证内容 |
| --- | --- |
| `01-create-editor-1080x2400.png` | 1080x2400 全屏新增、分组字段与固定操作栏 |
| `02-dirty-back-protection.png` | 未保存内容确认与继续填写 |
| `03-after-create.png` | 新增后库存结果 |
| `04-edit-editor.png` | 全屏批次编辑 |
| `05-replenish-editor.png` | 补货首屏相机入口与独立批次语义 |
| `06-after-replenish.png` | 同商品两个独立批次 |
| `07-create-editor-720x1280.png` | 720x1280 紧凑屏无重叠 |
| `08-create-keyboard-720x1280.png` | 键盘弹出时焦点字段与固定操作栏可见 |
| `09-recognized-editor.png` | OCR 商品名、条码和类别进入编辑器 |
| `10-recognized-dates.png` | OCR 生产日期、保质期和最终日期进入编辑器 |
| `11-replenish-after-video-fill.png` | 视频返回后商品来源、补货模式和相机入口仍保留 |
| 12-v046-post-upgrade-overview.png | 0.4.6 覆盖安装后首页正常启动且基线商品仍存在 |

所有 PNG 均校验为标准 `89504E470D0A1A0A` 文件头，并完成实际画面检查；没有以 UI 树或代码推断代替视觉验收。

## 尚未覆盖

- 真实 Android 手机上的自动对焦、连续曝光、反光包装、持续帧率和温升。
- AVD 高负载视频回放掉线后，补货编辑器日期区的第二张截图。
- Android 进程被系统杀死后恢复未保存编辑器草稿；当前 Activity 固定竖屏，正常返回与外部识别往返已覆盖。
