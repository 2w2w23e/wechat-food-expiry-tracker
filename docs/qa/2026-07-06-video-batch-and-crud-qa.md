# 2026-07-06 APK 视频批量识别与 CRUD 视觉验收

## 结论

- 结论：PASS_WITH_LIMITS。
- 通过范围：所有提供的视频样本均已完成“视频模拟识别 -> 候选确认 -> 新增表单 -> 保存到首页列表”的闭环，不手动输入商品文字。
- 已完成用户化流程：新增、修改、删除食品均按真实点击路径完成视觉验收。
- 限制：本轮证明视频-only 新增链路可用；部分样本的商品名仍有 OCR 噪声，生产日期 / 保质期未达到可声明高准确率的程度，因此保存时按“无过期时间”候选处理。

## 视频样本结果

| ID | 视频 | 最终结果 | 自动候选 |
| --- | --- | --- | --- |
| video01 | `D:\home\wechat-food-expiry-tracker\video\飞书20260705-183820.mp4` | PASS | `大董老北京 炸酱面` |
| video02 | `D:\home\wechat-food-expiry-tracker\video\飞书20260705-183848.mp4` | PASS | `料简单1蛋白爽滑1蛋黃绵密 震小象超市自有品牌` |
| video03 | `D:\home\wechat-food-expiry-tracker\video\飞书20260705-183904.mp4` | PASS_WITH_NOISY_NAME | `养分表` |
| video04 | `D:\home\wechat-food-expiry-tracker\video\飞书20260705-183915.mp4` | PASS_WITH_NOISY_NAME | `大米小 米 三氧化` |
| video05 | `D:\home\wechat-food-expiry-tracker\video\飞书20260705-183929.mp4` | PASS_WITH_NOISY_NAME | `配除都好吃 酸酸爽爽` |
| video06 | `C:\Users\xinyu.wang\Downloads\Record_2026-07-06-15-46-06_e7c84e4b72b45d7acf770bbb7219e5ff.mp4` | PASS_WITH_NOISY_NAME | `更们量 熟水饮用水` |

## 视觉证据

- 视频候选锁定：`docs/qa/screenshots/2026-07-06-video-batch-video03-candidate.png`
- 视频确认弹窗：`docs/qa/screenshots/2026-07-06-video-batch-video06-confirm.png`
- 新增填写：`docs/qa/screenshots/2026-07-06-crud-add-filled.png`
- 新增后首页：`docs/qa/screenshots/2026-07-06-crud-after-add.png`
- 编辑填写：`docs/qa/screenshots/2026-07-06-crud-edit-filled.png`
- 编辑后首页：`docs/qa/screenshots/2026-07-06-crud-after-edit.png`
- 删除确认：`docs/qa/screenshots/2026-07-06-crud-delete-confirm.png`
- 删除后首页：`docs/qa/screenshots/2026-07-06-crud-after-delete.png`

## 复测证据来源

- video01：`.local/video-batch-v035-fix01/batch-results.json`
- video02：`.local/video-batch-v035-fix0102/batch-results.json`
- video03-video05：`.local/video-batch-v035-fix0305/batch-results.json`
- video06：`.local/video-batch-v035-final/batch-results.json`

## 已验证规则

- 识别结果不会直接保存到食品列表，必须先进入确认弹窗。
- 确认后只填入新增表单，保存动作由用户点击完成。
- 视频未稳定识别出生产日期 / 保质期时，不生成错误到期日，表单自动使用“无过期时间”。
- 新增、编辑、删除均经过视觉截图确认。
