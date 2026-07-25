# Guo 的日报

个人使用的 Android 与 iOS AI、科技新闻日报。测试版每天 7:30（中国标准时间）
生成并推送不超过 10 条新闻，候选新闻每 2 小时更新一次。

## 演示视频

[点击查看 Guo 的日报演示视频](demo/guo-daily-demo.mp4)

## 已实现

- 报纸式首页、新闻卡片和新闻详情层级
- 浅色、深色与跟随系统三种外观模式
- 英文主标题下方显示中文翻译
- 影响力/热度评分可视化
- 收藏、分享、不感兴趣、已读状态
- 最近 30 天历史日报与过期清理
- 收藏内容永久保存在本机
- 原文内置浏览器与加载/错误状态
- 每 2 小时后台更新
- 每天 7:30 本地通知与失败后 15 分钟重试
- DeepSeek V4 Pro 筛选、摘要、翻译和入选理由接口

## 新闻来源

- `https://news.learnprompt.pro/data/daily-brief.json`
- `https://duanyytop.github.io/agents-radar/feed.xml`
- `https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml`

NYTimes 科技 RSS 会先进行 AI 关键词筛选。更精确的 NYTimes 文章检索需要
单独申请 NYTimes Developer API Key，当前测试版未要求此密钥。

## DeepSeek

在 App 的“设置 → AI 服务”中填写 API Key。模型固定为
`deepseek-v4-pro`，接口地址为 `https://api.deepseek.com/chat/completions`。

未填写 Key 时，App 仍会抓取新闻，并使用来源已有摘要和本地规则进行去重排序。
API Key 目前保存在设备的应用私有偏好数据中，仅适合个人测试，不建议发布此版本。

## iOS 下载与安装（Windows + iPhone）

iOS 工程位于 [`ios-app`](ios-app)，IPA 由 GitHub Actions 的 macOS 环境自动构建，无需在 Windows 上安装 Xcode。

### 1. 下载 IPA

1. 登录 GitHub，打开 [Build iOS IPA for Sideloadly](https://github.com/Goz1-star/Guo-s-Daily/actions/workflows/ios-unsigned-ipa.yml)。
2. 打开最新一条带绿色对勾的成功构建记录。
3. 在页面底部的 **Artifacts** 区域下载 `guo-daily-ios-unsigned`，不要下载 `guo-daily-ios-build-log`。
4. 解压下载得到的 ZIP，找到 `GuoDaily-ios-unsigned.ipa`。

IPA 构建产物保留 14 天。如果页面中已经没有可下载的产物，请在工作流页面点击 **Run workflow → Run workflow**，等待新任务出现绿色对勾后再下载。

### 2. 准备 Windows 环境

1. 从 [Sideloadly 官网](https://sideloadly.io/)下载并安装 Windows 版。
2. 按照 Sideloadly 的要求安装网页版 iTunes 与 iCloud；不要使用 Microsoft Store 版本。
3. 使用数据线连接 iPhone，解锁设备，并在 iPhone 上轻点“信任此电脑”。

### 3. 安装到 iPhone

1. 打开 Sideloadly，把 `GuoDaily-ios-unsigned.ipa` 拖入窗口。
2. 在设备列表中选择已连接的 iPhone，输入用于个人签名的 Apple ID。
3. 点击 **Start**，根据提示完成登录验证和安装。
4. 如果设备提示需要开发者模式，请前往“设置 → 隐私与安全性 → 开发者模式”，开启后按提示重启设备。
5. 如果首次打开时显示“不受信任的开发者”，请前往“设置 → 通用 → VPN 与设备管理”，点按用于签名的 Apple ID 并选择“信任”。

### 4. 更新与续签

- 免费 Apple ID 签名通常有效 7 天，到期前需要重新签名安装，也可以启用 Sideloadly 自动刷新。
- 更新软件时请继续使用相同的 Apple ID，并保持相同的 Bundle ID，直接覆盖安装即可。
- 不要先删除旧版，否则可能丢失应用内的收藏、历史记录和本机设置。

更多问题可查看 [Sideloadly FAQ](https://sideloadly.io/faq) 和 [Apple 开发者模式说明](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)。

## Android 构建

```powershell
.\gradlew.bat assembleDebug
```

生成的测试安装包位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

最低支持 Android 8.0（API 26）。
