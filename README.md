<p align="center">
  <img src="https://raw.githubusercontent.com/peaches-nine/ListenBook/master/logo_standard.svg" alt="ListenBook" width="120">
</p>

<h1 align="center">ListenBook</h1>

<p align="center">
  本地有声书 Android App | 导入小说，语音朗读
</p>

<p align="center">
  <a href="https://github.com/peaches-nine/ListenBook/releases"><img src="https://img.shields.io/github/v/release/peaches-nine/ListenBook?include_prereleases" alt="Release"></a>
  <a href="https://github.com/peaches-nine/ListenBook/blob/master/LICENSE"><img src="https://img.shields.io/github/license/peaches-nine/ListenBook" alt="License"></a>
  <a href="https://github.com/peaches-nine/ListenBook"><img src="https://img.shields.io/github/stars/peaches-nine/ListenBook?style=social" alt="Stars"></a>
</p>

---

## 功能

- **本地书架** - 导入 TXT/EPUB，自动检测章节
- **语音朗读** - Microsoft Edge-TTS 高质量中文语音
- **句子级播放** - 按句分割，实时生成，流畅播放
- **书签收藏** - 长按添加书签，快速跳转
- **睡眠定时** - 定时暂停或章末自动停止
- **阅读自定义** - 字体、行距、深色模式
- **后台播放** - 支持后台继续播放
- **数据备份** - 导出/导入进度和设置

## 安装

**下载 APK**: [Releases](https://github.com/peaches-nine/ListenBook/releases)

**源码构建**:
```bash
git clone https://github.com/peaches-nine/ListenBook.git
cd ListenBook
./gradlew assembleDebug
```

## 技术栈

| 类型 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| DI | Hilt |
| 数据库 | Room |
| 音频 | Media3/ExoPlayer |
| TTS | Edge-TTS WebSocket（Kotlin实现） |

## Disclaimer

本应用使用 Microsoft Edge 免费 TTS 服务，可能仅供个人/非商业用途，使用者需自行评估法律风险。

## License

[MIT](LICENSE)

---

<p align="center">
  <a href="https://star-history.com/peaches-nine/ListenBook"><img src="https://api.star-history.com/svg?repos=peaches-nine/ListenBook&type=Date" alt="Star History" width="500"></a>
</p>