# 📖 AudioBookApp

一款本地有声小说 Android App，支持导入 TXT/EPUB 小说，使用 Edge-TTS 将文字转为语音播放。

## ✨ 功能特性

- 📚 **本地书架管理** - 导入 TXT/EPUB 小说，自动检测章节
- 🎵 **语音朗读** - 使用微软 Edge-TTS 高质量中文语音
- 📝 **句子级播放** - 按句子分割，实时生成音频，流畅播放
- 🔖 **书签收藏** - 长按句子添加书签，快速跳转
- ⏰ **睡眠定时** - 定时暂停或章节结束后自动停止
- 🎨 **阅读自定义** - 字体大小、行间距、深色模式
- 📊 **进度管理** - 自动保存阅读进度，预缓存功能
- 🔔 **后台播放** - 支持后台继续播放
- 💾 **数据备份** - 导出/导入阅读进度和设置

## 📸 截图

| 书架 | 播放器 | 设置 |
|:---:|:---:|:---:|
| 书架截图 | 播放器截图 | 设置截图 |

## 🛠️ 技术栈

- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM + Clean Architecture
- **依赖注入**: Hilt
- **数据库**: Room
- **音频播放**: Media3/ExoPlayer
- **TTS**: Edge-TTS WebSocket 协议（Kotlin 实现）
- **文件解析**: TXT/EPUB（ZIP + Jsoup）

## 📦 安装

### 从源码构建

```bash
# 克隆仓库
git clone https://github.com/peaches-nine/AudioBookApp.git

# 构建 APK
cd AudioBookApp
./gradlew assembleDebug
```

### 直接下载

下载最新的 APK：[Releases](https://github.com/peaches-nine/AudioBookApp/releases)

## 🚀 使用方法

1. **导入小说** - 点击书架右上角 `+` 按钮，选择 TXT 或 EPUB 文件
2. **开始播放** - 点击书籍卡片，进入播放页面
3. **切换配音** - 点击章节标题栏的配音图标，选择喜欢的声音
4. **调整设置** - 播放器内点击设置图标，调整字体、行距、主题

## 🎤 支持的配音

应用内置多款高质量中文语音：

| 配音名称 | 性别 | 特点 |
|---------|------|------|
| XiaoxiaoNeural | 女 | 温柔自然 |
| YunxiNeural | 男 | 年轻活力 |
| YunjianNeural | 男 | 新闻播报 |
| XiaoyiNeural | 女 | 情感丰富 |
| ... | ... | 更多语音可选 |

## ⚠️ Disclaimer

本应用使用微软 Edge 浏览器的免费 TTS 服务：
- 该服务可能仅供个人/非商业用途
- 微软可能随时更改协议或限制访问
- 使用者需自行评估法律风险

## 📄 License

本项目采用 [MIT License](LICENSE) 开源协议。

---

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=peaches-nine/AudioBookApp&type=Date)](https://star-history.com/peaches-nine/AudioBookApp)

---

如果这个项目对你有帮助，欢迎 Star ⭐ 支持！