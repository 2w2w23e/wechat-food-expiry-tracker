# APK V0.3.14 指定视频与 JSON 快速换机 QA

日期：2026-07-19

## 结论

- PASS：指定录屏可识别生产日期、保质期和自动计算的最终日期。
- PASS：JSON 筛选导出、文件保存、系统分享、预览、同批次更新、重复导入和重启持久化。
- PASS：Excel 导出后重新导入预览，1 行可导入、0 错误、0 警告。
- PASS：新增、编辑、减少 1、补货独立批次、删除完整用户路径。
- PARTIAL：AVD 未安装 QQ/微信，只验证到 Android 系统分享面板和可读 JSON 附件；真实手机相机未覆盖。

## 指定视频

样本：`Record_2026-07-19-13-00-08_e7c84e4b72b45d7acf770bbb7219e5ff.mp4`

- 时长约 36.9 秒，720 x 1584，约 39.6 fps。
- 视觉逐帧检查在约 27 秒处清楚看到“保质期：9个月”。
- 根因是聚焦 OCR 只合并了补充包装区域的日期候选，没有合并同一区域的保质期候选。
- 修复后视频回放处理 6 个 OCR 关键帧，耗时 85,826 ms。
- 最终确认页显示：商品码 `6926265313430`、生产日期 `2026-01-20`、保质期 `9个月`、最终日期 `2026-10-20`。
- 点击“核对并填入”后，以上字段进入可编辑新增表单；没有绕过用户确认自动保存。

视觉证据：

- `.local/qa-20260719/video-contact-sheet.jpg`
- `.local/qa-20260719/video-final-complete-visual.png`
- `.local/qa-20260719/video-form-date.png`

## JSON 快速换机

- 导出筛选页同时提供入库时间、生产日期、最终日期的开始和结束日期，边界包含当天，留空不限。
- 无筛选导出 1 个批次，文件包含格式名、schema 版本、导出时间、筛选条件和完整批次字段。
- 系统分享面板显示 JSON 文件附件；AVD 中可选 Drive、Gmail。QQ/微信是否接收该 MIME 类型仍需在安装对应应用的真机上确认。
- 文件成功保存到 Downloads：`shiqi-batches-202607190619.json`。
- 以相同批次 ID、较新 `updatedAt` 导入数量 5、剩余 0、已用完状态，预览显示“更新数量/状态：1”，确认后首页显示在库 0、已用完 1。
- 强制停止并重新启动应用后，已用完批次仍为 0/5，证明更新已持久化。
- 再次导入相同文件，预览显示“内容相同：1”，没有重复新增。
- 视觉测试发现 Android `JSONObject` 会把 JSON null 的开封日期读成字符串 `null`；修复后合法空日期可导入，并增加回归测试。

视觉证据：

- `.local/qa-20260719/json-filter.png`
- `.local/qa-20260719/json-share.png`
- `.local/qa-20260719/json-import-preview-fixed.png`
- `.local/qa-20260719/json-import-applied.png`
- `.local/qa-20260719/json-reimport-preview.png`
- `.local/qa-20260719/finished-filter.png`

## 用户路径回归

- Excel：导出 `shiqi-foods-20260719-0634.xlsx` 后重新选择导入，预览为总行数 1、可导入 1、错误 0、警告 0；在预览处取消，没有制造测试重复数据。
- 新增：手动新增 `QA_Food`，生产日期 2026-07-19、保质期 30 日，自动计算最终日期 2026-08-18。
- 减少：首页外露“减少 1”把剩余数量从 3 改为 2。
- 补货：补货表单复用商品身份，但日期和数量留给新批次；“相机识别本批次日期”入口可见。保存 2 件、10 日保质期的新批次，最终日期为 2026-07-29。
- 编辑：把新补货批次数量和剩余数量从 2 改为 4。
- 删除：删除新补货批次，原批次仍保留为 2/3，证明不同批次相互独立。

视觉证据：

- `.local/qa-20260719/excel-import-preview-current.png`
- `.local/qa-20260719/crud-added-active.png`
- `.local/qa-20260719/crud-reduced.png`
- `.local/qa-20260719/replenish-form-current.png`
- `.local/qa-20260719/replenish-saved.png`
- `.local/qa-20260719/crud-edited.png`
- `.local/qa-20260719/crud-deleted.png`

## 自动化与构建

- 本地 Java 逻辑测试：194 passed，0 failed。
- Gradle：`:apk:app:assembleDebug` BUILD SUCCESSFUL。
- 覆盖安装：`adb install -r` Success，应用数据保留。
- `git diff --check`：退出码 0；Windows 仅提示未来 LF/CRLF 转换。

## 剩余边界

- 录屏模拟处理仍慢于视频实时播放，不能据此声称真实手机相机达到同等速度或准确率。
- QQ/微信实际接收与打开附件需要在安装对应应用的真机上补做一次手测。
- JSON 是用户主动发起的本地文件迁移，不是账号或自动云同步。
