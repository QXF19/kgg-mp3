# kgg-mp3 v1.1.0

Windows 高品质音频转换工具，解压后双击 `kgg-mp3.exe` 即可使用。无需另外安装 Java、FFmpeg 或 SQLite。

- 全新 macOS 风格浅色界面：圆角控件、蓝色操作按钮、清晰的文件队列和输出设置。
- 新增真实旧版 KGM 解密以及新版 KGG v5 解密，自动读取本机酷狗歌曲密钥。
- 原始音频为 MP3 时直接提取，减少重复编码；FLAC 等格式支持高品质 VBR / 320 kbps 输出。
- 新增 `kgg-mp3-cli.exe` 命令行入口。
- GitHub Actions 自动构建 Windows 便携 EXE，并运行真实加密容器回归测试。

新版 KGG v5 需要下载该文件时酷狗保存的对应歌曲密钥。若没有密钥，请在本机酷狗客户端播放该文件并重试；这不是 GPU 或改扩展名可以解决的问题。工具不上传音乐、数据库或密钥。

本版本已在本机使用真实 KGG v5 文件完成 EXE 转换，并通过整首 MP3 解码检查。MP3 编码使用 CPU / LAME；显卡检测不代表 GPU MP3 编码。

下载 `kgg-mp3-windows-x64.zip` 后完整解压，再打开其中的 `kgg-mp3.exe`。请保留 `app` 和 `runtime` 文件夹。
