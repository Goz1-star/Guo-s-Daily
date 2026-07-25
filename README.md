# Guo 的日报

个人使用的 Android AI 与科技新闻日报。测试版每天 7:30（中国标准时间）
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

## 构建

```powershell
.\gradlew.bat assembleDebug
```

生成的测试安装包位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

最低支持 Android 8.0（API 26）。
