# 2026-08-06 V2 C3/C4 中文识别、字段融合与补扫验收

## 结论

- 最终候选 APK 的 12 个冻结视频逐字段矩阵：`12/12 PASS`。
- 低对比度图片与 3 个公开样本：`4/4 PASS`。
- 最终页面视觉检查：`PASS`。商品名、条码、生产日期、保质期/有效期进入正确字段；无可靠结果的公开负样本没有编造字段，主按钮保持禁用。
- 本地逻辑、相机管线、编辑器、快照映射、业务状态、Android Lint、构建和 12 项仪器测试：`PASS`。
- 真实手机相机对焦、曝光、持续帧率、温升与首个可用结果延迟：`PARTIAL`。视频回放和 AVD 不代替 C1b 真机门槛。
- C3/C4 冻结样本门槛：`PASS`；对未见包装和更大公开集的泛化准确率：`PARTIAL`。

## 环境与边界

- APK：`versionName 0.4.8`，`versionCode 38`
- AVD：Medium Phone，Android API 29
- 识别链路：ML Kit 文字/条码快速通道，PP-OCRv6 检测与行识别，OpenCV 低对比增强，多帧字段融合
- 数据边界：识别结果只进入核对页和可编辑草稿，用户点击“使用这些结果”并在编辑器保存前不写入食品数据库
- 时延边界：下表为 AVD 对完整录屏执行检测、识别、尾帧复核和字段融合的总耗时，只用于同环境回归，不代表真机实时相机速度

## 本轮修复

1. 新增缺失字段计划：已有商品名时只追日期/条码，已有日期时只追商品身份，减少无关全图 OCR。
2. 视频尾段先扫描高概率日期窄带，只对少量入选尾帧执行全区域检测，保留激光喷码和“有效期至”召回。
3. 会话结束前融合整段文字和最强关键帧，已稳定字段不再被最后一帧或录屏界面文字覆盖。
4. 可信 13 位商品码可由条码解码通道立即锁定；12 位结果只有命中已核验目录前缀时才补全；批号、药品追溯码和时间戳继续排除。
5. 已核验条码目录名称优先于明显乱码或过短类别名；只有包装 OCR 与目录名称高度一致时才保留包装文字。
6. 中文包装增加跨行恢复，覆盖“退和甘甜 + 水”恢复“喝开水”和联系方式残片污染鹌鹑蛋名称的情况。
7. 增加 `first.field`、`replay.complete`、`image.complete` 机器指标，日志直接记录最终商品名、条码、生产日期、到期日和总耗时。

## 12 个视频结果

| 样本 | 最终字段 | AVD 总耗时 | 状态 |
| --- | --- | ---: | --- |
| `c3-ocr_sample_1.mp4` | 大董老北京炸酱面；6971416494254 | 58.778s | PASS |
| `c3-ocr_sample_2.mp4` | 去壳清水鹌鹑蛋 | 43.858s | PASS |
| `c3-ocr_sample_3.mp4` | BLUE果汁 | 47.297s | PASS |
| `c3-ocr_sample_4.mp4` | 窖藏红腐乳；6930628889364；2026-04-30；2027-10-30 | 71.683s | PASS |
| `c3-ocr_sample_5.mp4` | 酸菜；2026-06-12；2027-06-11 | 66.746s | PASS |
| `c3-shiqi-video-6.mp4` | 喝开水 | 111.843s | PASS |
| `c3-july15.mp4` | 维C水；2026-06-08；2027-03-07 | 33.367s | PASS |
| `c3-qa-july19.mp4` | 上好佳薯条；6926265313430；2026-01-20；2026-10-20 | 53.813s | PASS |
| `c3-refactor-20260728.mp4` | 复方丹参滴丸；2025-03-13；2029-02-28 | 93.847s | PASS |
| `c3-qa-july29.mp4` | 复方硫酸软骨素滴眼液；6917246211883；不猜日期 | 73.063s | PASS |
| `c3-shelf-debug.mp4` | 上好佳薯条；6926265313430；2026-01-20；2026-10-20 | 75.278s | PASS |
| `c3-ocr-date-formats.mp4` | 复方丹参滴丸；2025-03-13；2029-02-28；不保留药品追溯码 | 58.460s | PASS |

机器结果：[video-results.json](data/2026-08-06-redesign-v2-c3-c4/video-results.json)。

自动矩阵中 `c3-shiqi-video-6.mp4` 首次截图被 AVD 上的外部应用弹窗抢占，进程没有进入识别页；回到桌面、显式校验前台 Activity 后独立重跑通过。机器 JSON 保留首次环境失败原因，并记录收紧商品名规则后的第 3 次最终验证。样本 1、2、3、4、7 的视觉截图也因前台被抢占或系统页覆盖而单独重取，只有确认食期管家处于前台的截图进入证据。

## 图片与公开样本

| 样本 | 预期与实际 | AVD 总耗时 | 状态 |
| --- | --- | ---: | --- |
| `c3-low-contrast.jpg` | 低对比压印：生产 2025-09-12；有效期 2027-03-11 | 19.458s | PASS |
| `public-exp-date.jpg` | 单个过去日期按生产日期：2022-08-23 | 45.511s | PASS |
| `public-german-package.jpeg` | 没有可靠日期/商品名，不填入 | 48.171s | PASS |
| `public-negative.jpeg` | 无关图片，不填入 | 21.446s | PASS |

机器结果：[image-results.json](data/2026-08-06-redesign-v2-c3-c4/image-results.json)。

`public-german-package.jpeg` 首次在批量运行的 160 秒窗口内仍处于 OCR 处理，独立 300 秒窗口重跑在 48.171 秒完成且没有生成任何字段；JSON 同样保留两次尝试。公开样本来自 [expiration-date-detection/sample_data](https://github.com/kivancgunduz/expiration-date-detection/tree/master/sample_data)。更大验证集候选为 [ExpirationDate](https://huggingface.co/datasets/dimun/ExpirationDate)，本轮没有把其 1.82GB 全量数据等同于已完成验收。

## 视觉证据

- [视频 1-3](screenshots/2026-08-06-redesign-v2-c3-c4/video-01-03.jpg)
- [视频 4-6](screenshots/2026-08-06-redesign-v2-c3-c4/video-04-06.jpg)
- [视频 7-9](screenshots/2026-08-06-redesign-v2-c3-c4/video-07-09.jpg)
- [视频 10-12](screenshots/2026-08-06-redesign-v2-c3-c4/video-10-12.jpg)
- [低对比度与公开日期正样本](screenshots/2026-08-06-redesign-v2-c3-c4/images-positive.jpg)
- [公开负样本](screenshots/2026-08-06-redesign-v2-c3-c4/images-negative.jpg)

视觉检查确认：

- 识别依据缩略图位于字段上方，关键字段在首屏可见。
- 商品名、条码、生产日期与系统计算到期日没有互相覆盖。
- 无日期时使用“尚未稳定/请继续核对”语义，不自动选择“无过期时间”。
- 两个公开负样本没有生成商品名、条码或日期，“使用这些结果”保持禁用。
- 底部“继续扫描 / 使用这些结果”操作区稳定，不遮挡字段。

## 自动化

```text
Local logic tests: 224 passed, 0 failed
Camera frame pipeline tests: 5 passed, 0 failed
FoodEditorSessionTest: 9 passed
InventorySnapshotMapperTest: 29 passed
BusinessPageStateTest: 15 passed
Android instrumented tests: 12/12 passed
lintDebug + assembleDebug: BUILD SUCCESSFUL
adb install -r: Success
```

## 最终 APK 与覆盖升级

- 文件：`artifacts/食期管家-v0.4.8-v2-c3c4-debug.apk`
- 大小：`221036014` 字节
- SHA-256：`6E063C82A2CF660D31A99B9C9059E8346769259C4CF3ADDBDFB26539D9F704A4`
- `apksigner verify --verbose --print-certs`：`PASS`，APK Signature Scheme v2 为 `true`
- 签名证书 SHA-256：`eba5350af5a9182067b6bfac2dd1c7b74438b7905cce0b2bc67a12d6ca30be91`
- 与上一候选 `shiqi-v0.4.8-d1d-debug.apk` 证书一致；先创建应用私有文件 `files/upgrade-marker`，再执行 `adb install -r` 安装最终候选后，该文件仍存在，覆盖升级数据保留：`PASS`

该 APK 使用项目既有 Android debug 证书，适合本地安装和同证书覆盖升级，不是应用商店 release 签名包。设备上若已有其他证书签名的同包名 APK，Android 仍会报告签名冲突，需要先备份数据再卸载一次。
识别实现参考 [PaddleOCR Android 部署文档](https://www.paddleocr.ai/latest/en/version3.x/inference_deployment/cross_platform/android_deployment.html) 的端侧部署边界。本轮没有仅因模型版本更新而替换模型；模型变更仍需用同一 ROI 做召回、延迟、内存和 APK 体积 A/B。

## 尚未通过

- 未在真实手机上验证连续相机的自动对焦、曝光恢复、低照度、反光、持续帧率、温升和首个可用结果时延。
- 12 个用户冻结视频和 4 张图片不能证明所有未见中文包装的泛化准确率；需要继续扩展真实包装验证集并记录字段级 precision/recall。
- AVD 完整视频总耗时 33.367s 至 111.843s，不能据此宣称真实相机“迅速”；真机应使用首字段指标和短时连续帧单独验收。