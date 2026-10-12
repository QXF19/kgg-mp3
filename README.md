# kgg-mp3

[![Windows portable EXE](https://github.com/QXF19/kgg-mp3/actions/workflows/windows-build.yml/badge.svg)](https://github.com/QXF19/kgg-mp3/actions/workflows/windows-build.yml)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Windows 本地高品质 KGG/KGM 音频转 MP3 工具。支持拖拽、批量转换、自动格式探测和多核并行，文件全程保留在本机。

v1.1.0 提供 macOS 风格的浅色界面、圆角控件和蓝色操作按钮，并加入新版 KGG v5 解密。已使用真实 KGG 文件完成转换及整首音频解码验证。新版文件仍需要本机酷狗保存的对应歌曲密钥。

## 下载使用

1. 打开 [Releases](https://github.com/QXF19/kgg-mp3/releases)，下载 `kgg-mp3-windows-x64.zip`。
2. 解压 ZIP。
3. 双击 `kgg-mp3.exe`，无需安装 Java 或 FFmpeg。

命令行使用：

```powershell
./kgg-mp3-cli.exe "D:\Music\song.kgg" "D:\Music\output"
```

## 功能

- 真正解码旧版 KGM/KGMA/VPR 加密容器，包括扩展名为 `.kgg`、`.gkk` 的 KGM 文件。
- 支持新版 KGG/KGM v5：自动解密本机酷狗 `KGMusicV3.db`，读取每首歌的专属密钥。
- 自动识别文件中的 MP3、FLAC、OGG、WAV 音频流。
- 支持 MP3、FLAC、WAV、M4A、AAC、WMA、APE、OPUS 等普通音频。
- 极致 VBR V0、320/256/192 kbps 等音质预设。
- 拖拽文件、批量导入文件夹、多任务并行转换。
- 保留可用的音频元数据，输出重名时自动添加序号。
- 不上传文件、不覆盖源文件、不写入系统目录。

> `.gkk` 不是常见的公开音频标准扩展名，部分场景实际指 `.kgg`。本工具会先检查真实文件头：KGM/VPR 容器会先解码，再转换或无损提取原始 MP3；未知的新格式仍会安全失败，不修改源文件。

> 新版 KGG v5 必须使用下载该歌曲时由酷狗客户端保存的专属密钥。如果提示“本机数据库没有它的专属密钥”，请在同一台电脑的酷狗客户端中播放该文件一次，关闭酷狗后再转换。程序只在本机临时读取数据库，转换完成后不保留明文数据库。

## 关于 GPU

应用会检测 NVIDIA、AMD 或 Intel 显卡并显示硬件状态。MP3/LAME 是 CPU 音频编码器，NVENC、QSV、AMF 等 GPU 编码器主要用于视频，不能直接加速 MP3。工具使用多任务并行充分利用多核 CPU，避免虚假显示 GPU 音频加速。

## 从源码运行

需要 JDK 17 或更高版本。

```powershell
./install-ffmpeg.bat
./install-sqlite.bat
powershell -NoProfile -ExecutionPolicy Bypass -File ./install-ui.ps1
./run.bat
```

## 生成 Windows EXE 包

```powershell
./install-ffmpeg.bat
./install-sqlite.bat
powershell -NoProfile -ExecutionPolicy Bypass -File ./install-ui.ps1
./package-windows.bat
```

发行包会生成到 `dist/kgg-mp3-windows-x64.zip`。GitHub Actions 也会在每次推送后自动构建；推送 `v*` 标签时自动创建 Release。

## 许可证

本项目使用 [MIT License](LICENSE)。KGM 解码算法与掩码数据的来源和 MIT 许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。FFmpeg 由构建脚本单独下载；SQLite 为 Public Domain 软件，详见第三方声明。
