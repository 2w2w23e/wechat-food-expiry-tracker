# 食期管家 V2 Android 壳与手动编辑器 QA

日期：2026-08-31  
结论：`PASS（限 Android 壳、手动编辑器和日期专用相机入口）`

## 本轮范围

- 一级导航从四项收敛为“今日 / 库存 / 设置”，扫描只作为任务内能力。
- 今日页保留一个全局“新增”，移除重复的扫描和手动新增入口。
- 新增/编辑/补货表单重排为“商品 -> 日期 -> 数量与保存 -> 更多信息”。
- 新增和补货默认数量为 1；编辑时才同时显示入库数量和当前剩余。
- “日期暂时不清楚”与“明确无过期时间”拆成两个互斥状态。
- 新增、编辑、补货、减量、用完、恢复和删除接入统一库存命令服务。
- 日期专用相机只显示日期进度；没有识别结果时可直接返回手填。
- 录入草稿在相机返回和 Activity 重建后恢复，不再退回首页或清空字段。

## 自动验证

```text
prototype smoke: 8 passed
local logic: 226 passed
camera frame pipeline: 5 passed
food editor session: 9 passed
inventory snapshot mapper: 29 passed
business page state: 15 passed
inventory command service: 58 passed
Android connected tests: 22 passed
Android lint: 0 errors, 51 warnings
```

Android 测试包含一条完整模拟用户路径：新增日期待补充商品、修改名称、减少 1 至用完、删除批次，并校验每一步持久化结果。新增的恢复测试还会填写名称和条码、重建 Activity，再核对所有草稿值和“日期待补充”状态。

## 视觉验证

已在 `Shiqi_QA` 1080x2400 模拟器实际安装和点击，不以源码推断：

- `docs/qa/screenshots/redesign-v2-android/home-latest-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/editor-latest-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/editor-validation-errors-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/manual-unknown-saved-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/settings-current-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/home-final-720x1280.png`
- `docs/qa/screenshots/redesign-v2-android/editor-final-720x1280.png`
- `docs/qa/screenshots/redesign-v2-android/scanner-date-final-1080x2400.png`
- `docs/qa/screenshots/redesign-v2-android/scanner-date-final-720x1280.png`
- `docs/qa/screenshots/redesign-v2-android/editor-restored-after-camera-and-recreate-final-1080x2400.png`

确认结果：

1. 商品、日期和保存动作在首屏可理解，低频字段不再阻挡日期填写。
2. 名称、生产日期和保质期错误同时显示，不再一次只暴露一个错误。
3. 日期未知可保存，首页显示“日期待补充”，不会自动写成无过期时间。
4. 底部固定操作区不覆盖当前正在编辑的关键字段；页面仍可滚动到数量与更多信息。
5. 日期专用相机不再显示“自动 / 日期特写 / 条码 / 名称”等无关控件，短屏仍保留大面积预览和手填退路。
6. 实际填写 `keep_me`、进入相机、返回并切换密度触发 Activity 重建后，仍停留在新增页且原值可见，副标题也恢复为普通手动录入文案。

## 未通过边界

- Excel、JSON、备份恢复虽有既有自动测试，尚未在新版 Android 导航下完成完整视觉旅程。
- 相机/OCR 仍沿用旧大 Activity，未达到 V2 识别架构完成门槛。
- 没有真实手机相机对焦、曝光、帧率和温升证据。

因此本轮范围通过，但只能证明 Android 壳、手动编辑器、日期专用入口和核心 CRUD 垂直切片，不能宣称 V2、完整识别系统或最终 APK 完成。
