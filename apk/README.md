# 食期管家 Android APK

这个目录包含“食期管家”的原生 Android 本地版。当前 APK 本地版聚焦手动录入、本地存储、到期排序、提醒规划、系统通知、商品码识别和已用完归档；当前不考虑应用商店发布或云存储。

微信小程序源码已封存在 `../miniprogram/`，除非明确恢复小程序端开发，否则不继续迭代。

## 构建

当前 APK 版本号：`versionName 0.3.1`，`versionCode 4`。每次生成新的可交付 APK 时，都需要同步提升 `AndroidManifest.xml` 和 `apk/app/build.gradle` 里的 `versionName` 和 `versionCode`。

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
- 商品码识别：申请相机权限后进入内置实时扫码页，支持双指缩放和右侧倍率按钮，支持一维商品条码、QR Code / Data Matrix 中的 GS1 Digital Link 或商品码参数，右下角图库入口可选择图片识别，也可手动输入条码后查询商品名、规格、厂家等信息；商品信息查询先走现有 GS1 接口，未命中时对进口商品补查中国商品信息服务平台公开进口商品数据
- 根据商品名和条码分类信息智能建议食品分类，保存前仍需用户确认
- 滚轮式日期选择器
- 无过期时间食品记录
- 一键回到顶部
- 智能应用内提醒计划
- 今日简报
- 今日到期小时提醒展示
- Android 本地系统通知
- 已用完归档和恢复
- Excel `.xlsx` 导出，包含 `foods` 和 `README` 两个工作表
- Android 本地存储

## 本地交付整理

- 已移除示例数据加载入口。
- 已移除内置 mock/sample 食品数据。
- 已移除测试定时系统提醒按钮和测试通知 action。
- APK 包不会写入 API Key、OCR Key、OpenAI Key 或其他密钥。
- 条码、二维码实时预览帧和图库图片解码使用 ZXing core；商品信息查询使用网络请求，不在客户端内置密钥。
- Excel 导出使用项目内置的最小 OOXML writer，不新增三方运行时依赖；导入仍需后续预览、校验和确认流程。

## 当前不做

- 云数据库
- 账号体系
- 多设备同步
- 应用商店发布流程
- OCR
- AI 智能识别
- WorkManager 或复杂后台服务
- API Key 或任何其他密钥内置
