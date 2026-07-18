# 食期管家 Android APK

这个目录包含“食期管家”的原生 Android 本地版。当前 APK 本地版聚焦手动录入、本地存储、到期排序、提醒规划、系统通知、商品码识别和已用完归档；当前不考虑应用商店发布或云存储。

微信小程序源码已封存在 `../miniprogram/`，除非明确恢复小程序端开发，否则不继续迭代。

## 构建

当前 APK 版本号：`versionName 0.3.11`，`versionCode 14`。每次生成新的可交付 APK 时，都需要同步提升 `apk/app/build.gradle` 里的 `versionName` 和 `versionCode`。

当前最低 Android 版本为 API 24（Android 7.0）。当前 debug APK 实测约 220 MB；离线模型只保留当前链路需要的关键文件，可交付 APK 的硬上限为 1 GB。

构建脚本依赖本机 Android SDK 命令行工具。若当前机器缺少 Android SDK，APK 构建应标记为环境阻塞，待补齐环境后再验证。

推荐 Gradle 构建本地调试安装包，便于后续接入 CameraX、ML Kit、Fastexcel 等 Maven 依赖：

```powershell
.\gradlew.bat :apk:app:assembleDebug
```

输出：

```text
apk/app/build/outputs/apk/debug/app-debug.apk
```

保留原 Android SDK 手工构建脚本作为 fallback：

```powershell
powershell -ExecutionPolicy Bypass -File apk/build-apk.ps1
```

输出：

```text
apk/build/outputs/apk/shiqi-android-debug.apk
```

构建正式签名包时，不要把 keystore 或密码放进仓库。先在本机设置环境变量，再运行：

```powershell
$env:SHIQI_RELEASE_KEYSTORE="D:\keys\shiqi-release.jks"
$env:SHIQI_RELEASE_KEY_ALIAS="shiqi"
$env:SHIQI_RELEASE_STORE_PASSWORD="..."
$env:SHIQI_RELEASE_KEY_PASSWORD="..."
powershell -ExecutionPolicy Bypass -File apk/build-apk.ps1 -Variant release
```

输出：

```text
apk/build/outputs/apk/shiqi-android-release.apk
```

## 当前支持

- 食品列表展示
- 新增、编辑、删除食品
- 生产日期 + 保质期计算 `expiryDate`
- 直接手动填写 `expiryDate`
- 按 `expiryDate` 排序
- 到期状态展示
- 状态多选筛选和分类多选筛选
- 食品搜索，支持中文、拼音和首字母匹配
- 统一智能识别：同一页面支持相机、视频模拟和图片，自动融合商品码、中文/拉丁包装文字、生产日期、保质期和最终日期；候选只会填入新增表单，仍需用户确认保存
- 视频识别增强：视频帧会使用全图、放大裁剪、中英文双 OCR、PP-OCRv6 日期行补充识别、多帧候选排序和 OpenCV CLAHE / 背景差分低对比增强；日期按多模型与多帧证据稳定后进入表单
- 商品码识别：支持一维商品条码、QR Code / Data Matrix 中的 GS1 Digital Link 或商品码参数；商品信息查询先走现有 GS1 接口，未命中时对进口商品补查中国商品信息服务平台公开进口商品数据
- 根据商品名和条码分类信息智能建议食品分类，保存前仍需用户确认
- 商品名智能选择：条码商品库命中优先；包装文字必须有明确“产品名称/商品名称/食品名称/品名”标签，或同时满足食品语义、文字结构、分数与跨帧稳定要求；最多展示 1 个最佳高可信名称，说明书、医疗内容、厂家/公司名、混合脚本乱码和低分内容不显示
- 包装文字 OCR：ML Kit Text Recognition v2 Chinese / Latin 负责中文和混合包装文字通用召回；ML Kit Barcode Scanning 与 ZXing 负责条码；OpenCV 负责低对比增强；ONNX Runtime Android 1.27.0 运行 `PP-OCRv6_small_det` 定位文字区域并运行 `PP-OCRv6_rec_small` 复识裁剪文字行；日期规则与跨帧投票负责归一化、冲突处理和候选稳定
- 日期语义：优先使用“生产日期 / 包装日期 / 制造日期 / MFG / MFD”“保质期 / 货架期”“有效期至 / 限用日期 / EXP / BBE”等标签；无标签时仅按受控规则生成待确认候选，完整矩阵见 `../docs/APK_OCR_DATE_FORMAT_MATRIX.md`
- 滚轮式日期选择器
- 无过期时间食品记录
- 一键回到顶部
- 智能应用内提醒计划
- 今日简报
- 今日到期小时提醒展示
- Android 本地系统通知
- 已用完归档和恢复
- Excel `.xlsx` 批量导入、预览校验、覆盖确认、错误行详情和导出；导出文件包含 `foods` 和 `README` 两个工作表，并保留商品档案 ID、条码和独立批次关系
- 条码商品档案与库存批次：同一条码可对应多个商品档案；扫码命中本机档案时选择商品并建立补货新批次，复用名称、分类、单位和保存位置，但生产日期、保质期、最终日期、数量、开封和提醒独立填写，不覆盖旧批次
- Android 本地存储

## 本地交付整理

- 已移除示例数据加载入口。
- 已移除内置 mock/sample 食品数据。
- 已移除测试定时系统提醒按钮和测试通知 action。
- APK 包不会写入 API Key、OCR Key、OpenAI Key 或其他密钥。
- ML Kit、OpenCV、ONNX Runtime 和 PP-OCRv6 检测/识别均在设备本地运行，不上传用户图片或视频。ONNX Runtime 使用 MIT License，PaddleOCR/PP-OCRv6 模型使用 Apache License 2.0。
- 当前不再使用 RapidOCR Android AAR 或 Kotlin 标准库。仅保留 `PP-OCRv6_small_det.onnx` 与 `PP-OCRv6_rec_small.onnx` 两个当前链路需要的 PaddleOCR 模型，不打包方向分类、旧模型或本地研究用 server 模型；即使用户不限制应用大小，APK 仍必须小于 1 GB。
- 条码、二维码实时预览帧和图库图片解码使用 ZXing core；商品信息查询使用网络请求，不在客户端内置密钥。
- Excel 导入导出使用项目内置的最小 OOXML reader/writer，不新增三方运行时依赖；导入必须先预览校验并由用户确认，失败时不覆盖原数据。

## 当前不做

- 云数据库
- 账号体系
- 多设备同步
- 应用商店发布流程
- 需要密钥或云端服务的 OCR / AI 自动抽取
- 识别结果自动保存到食品列表
- WorkManager 或复杂后台服务
- API Key 或任何其他密钥内置

## 多模型 OCR 验收边界

- OCR 输出只能预填确认页；用户可核对和修改，只有点击保存后才写入食品列表。
- 月级有效期（例如 `有效期至 2029.02`）归一化为该月最后一天的候选，并明确提示包装只标到月。
- 低对比、反光、弧面、运动模糊和字符残缺仍可能导致误识别；真实样本必须通过截图或录屏做视觉 QA，不能只以单元测试或模型原文作为 PASS。
- 当前多模型变更的规则、支持格式、样本状态和剩余风险见 `../docs/APK_OCR_DATE_FORMAT_MATRIX.md`。
- V0.3.11 最终视频、条码、Excel、CRUD、升级保数和视觉证据见 `../docs/qa/2026-07-18-v0311-final-qa.md`。
