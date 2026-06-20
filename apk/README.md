# 食期管家 Android APK

这个目录包含“食期管家”的原生 Android 本地版，是当前暂时优先开发的 APK 方向。微信小程序源码已封存在 `../miniprogram/`，除非用户明确要求恢复小程序端，否则不继续迭代。

## 构建

在仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File apk/build-apk.ps1
```

构建完成后，debug APK（调试安装包）会输出到：

```text
apk/build/outputs/apk/shiqi-android-debug.apk
```

## 当前支持

- 食品列表展示
- 新增、编辑、删除食品
- 生产日期 + 保质期计算 `expiryDate`（最终可食用日期字段）
- 直接手动填写 `expiryDate`
- 按 `expiryDate` 排序
- 到期状态显示
- 状态筛选和分类筛选
- 主动加载示例数据
- Android 本地存储

## 当前不做

- 应用商店发布
- release 签名证书
- 云数据库
- 账号体系
- 多设备同步
- 条形码
- OCR（文字识别）
- AI（智能识别）
- 提醒调度
- API Key（接口密钥）或任何其他密钥
