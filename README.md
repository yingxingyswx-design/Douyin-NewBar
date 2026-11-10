# Douyin-NewBar

抖音底部导航栏替换模块（LSPosed），Material 3 Expressive 风格，支持纯色与毛玻璃、自定义配色、栏体高度/悬浮间距/宽度。

> 仅修改本机界面，不收集、不上传任何数据。

## 功能

- 纯色 / 毛玻璃两种渲染模式，毛玻璃带雾面强度、玻璃染色开关
- 深浅两套状态配色：首页/朋友 深色，消息/我 明亮
- 自定义栏体高度（26–46dp）、悬浮间距（0–16dp）、栏体宽度（88% 居中）
- 可选隐藏系统导航栏（默认开启）；关闭后恢复系统手势条/三键导航
- 点击 Tab 派发真实触摸事件切换视图
- App 内亮色/深色主题、强制停止抖音、依赖提示

## 环境要求

- Android 13+
- 已 Root，安装 LSPosed / Zygisk
- LSPosed 作用域勾选 `com.ss.android.ugc.aweme`
- 适配抖音 39.x / 40.x

## 安装

1. 安装 `Douyin-NewBar-v0.2.8.1.apk`
2. 在 LSPosed 中启用模块并勾选抖音
3. 重启抖音（必要时重启手机）
4. 打开模块 App 调整颜色、高度、间距

## 说明

- 毛玻璃的视频画面实时采样在部分 ROM/视频层上不可用（SurfaceView/受保护层），会回退为静态毛玻璃；纯色模式零采集、最稳定。
- 修改设置后如未生效，重启抖音或使用 App 内“强制停止抖音”。

## 构建

```bash
./gradlew :app:assembleRelease
```

需要 JDK 21、Android SDK 36。

## License

MIT
