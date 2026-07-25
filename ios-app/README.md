# Guo 的日报 · iOS 客户端

这是 Guo 的日报的 Expo/React Native 客户端，和现有的 Android 原生工程并列保存。

## 本地运行

```powershell
cd ios-app
npm start
```

在 Windows 上可以先用 Expo Go 或 Web 预览界面。iOS 真机安装需要先在 macOS 云端构建 IPA，再使用 Sideloadly 侧载。

## 当前版本

- NYTimes 风格的日报首页层级
- 今日、收藏、历史、设置四个页面
- 新闻详情、英文标题中文翻译、摘要、入选理由
- 影响力与热度可视化
- 分享原文与摘要
- 阅读状态、收藏状态
- 跟随系统、浅色、深色三种外观
- DeepSeek API Key 本机安全存储、删除和连接测试
- 启动及手动刷新时从 Learn Prompt 日报接口取得最新 AI 与科技资讯
- 最新资讯本机缓存；断网或新闻源暂时异常时保留上次成功内容

## 新闻刷新

打开应用时会自动联网更新，首页右上角的“刷新”也会立即重新请求数据。首页状态条会显示新闻数据的生成时间；如果更新失败，应用会显示原因并继续展示上次成功缓存。当前版本优先使用 `news.learnprompt.pro` 已聚合、去重和评估的日报数据，每次最多展示 10 条。

## DeepSeek API Key

在 iPhone 安装版中打开“设置 → AI 服务”，输入新的 DeepSeek API Key 后点击“保存 Key”。Key 使用 iOS Keychain 保存在本机，不写入源码或 GitHub。点击“测试连接”会发送一个极短请求并产生少量 API 用量。

## 构建策略

当前项目不依赖 App Store 发布。后续会在 GitHub Actions 的 macOS runner 上生成 iOS 构建产物，再在 Windows 使用 Sideloadly 安装到个人 iPhone。
