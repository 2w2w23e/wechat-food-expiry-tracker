# 食期管家 V2 静态原型视觉 QA

日期：2026-07-29
结论：`PASS`（静态原型）；`PARTIAL`（Android 实现尚未开始）

## 验证范围

- 总览
- 库存
- 自动扫描
- 部分结果确认
- 日期特写
- 完整结果确认
- 商品详情与补货
- 设置、JSON、Excel 和备份入口

## 真实用户流程

Playwright 已完成：

1. 总览点击“扫描添加”。
2. 扫描页先得到商品名和条码，日期保持“查找中”。
3. 进入确认页，缺失日期显示在字段首位，保存按钮禁用。
4. 点击日期字段回到“日期特写”。
5. 日期状态稳定后进入完整确认页。
6. 点击“确认补货”进入商品详情，新批次拥有独立日期。
7. 库存页检查外露的“减少 1”和“补货”。
8. 设置页检查智能提醒、JSON、Excel、备份和离线模型入口。

原型中的日期结果是交互模拟，不是对失败视频 OCR 已通过的声明。

## 视觉检查

检查视口：

- 360 x 640：小屏手机。
- 412 x 915：中等 Android 手机。
- 720 x 1280：高分辨率纵向画布。
- 1080 x 2400：高分辨率长屏画布。
- 1440 x 900：桌面宽屏预览。

自动结构检查：

- 六个主要页面没有未处理的横向溢出。
- 可见按钮和输入触控区不小于 48 x 48 CSS px。
- 扫描底部面板没有越出视口。
- 库存工具栏与滚动列表没有重叠。
- 360px 宽度下长中文商品名使用两行，不被操作按钮遮挡。

## 视觉迭代记录

1. 首版确认页在 360 x 640 下把日期字段排在名称和条码之后，底部操作栏会遮住关键缺失项。
2. 调整为“缺失/冲突字段优先”，日期行首屏完整可见。
3. 日期识别完成后，移除重复的“优先识别日期”按钮。
4. 日期成功后，黄色“补扫日期”证据切换为正常日期关键帧。
5. 筛选、分段控件、减量按钮和日期框统一到至少 48px 触控区。
6. 库存中的长中文名称和批次摘要改为两行显示。

## 截图

- [总览 360x640](screenshots/redesign-v2/home-360x640.png)
- [库存 360x640](screenshots/redesign-v2/inventory-360x640.png)
- [扫描部分结果 360x640](screenshots/redesign-v2/scanner-partial-360x640.png)
- [确认部分结果 360x640](screenshots/redesign-v2/confirm-partial-360x640.png)
- [确认完整结果 360x640](screenshots/redesign-v2/confirm-ready-360x640.png)
- [商品详情 360x640](screenshots/redesign-v2/detail-360x640.png)
- [设置 360x640](screenshots/redesign-v2/settings-360x640.png)
- [总览 412x915](screenshots/redesign-v2/home-412x915.png)
- [扫描 412x915](screenshots/redesign-v2/scanner-partial-412x915.png)
- [总览 720x1280](screenshots/redesign-v2/home-720x1280.png)
- [总览 1080x2400](screenshots/redesign-v2/home-1080x2400.png)
- [总览 1440x900](screenshots/redesign-v2/home-1440x900.png)

## 未覆盖

- 静态原型不能证明 Android View/Activity 实际布局已经完成。
- 视频回放不能证明真实手机连续自动对焦。
- 原型没有执行真实 OCR、数据库迁移、Excel/JSON 文件读写或通知。
- 进入 Android 实施阶段后仍需仪器测试、模拟器截图和真机走查。
