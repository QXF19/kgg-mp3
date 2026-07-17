# kgg-mp3

[![Windows portable EXE](https://github.com/QXF19/kgg-mp3/actions/workflows/windows-build.yml/badge.svg)](https://github.com/QXF19/kgg-mp3/actions/workflows/windows-build.yml)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Windows 本地高品质 KGG/KGM 音频转 MP3 工具。支持拖拽、批量转换、自动格式探测和多核并行，文件全程保留在本机。

## 下载使用

1. 打开 [Releases](https://github.com/QXF19/kgg-mp3/releases)，下载 `kgg-mp3-windows-x64.zip`。
2. 解压 ZIP。
3. 双击 `kgg-mp3.exe`，无需安装 Java 或 FFmpeg。

## 功能

- 支持选择 `.kgg`、`.gkk`、`.kgm`、`.kgma`、`.kge`、`.kgtemp`。
- 自动识别文件中的 MP3、FLAC、OGG、WAV 音频流。
- 支持 MP3、FLAC、WAV、M4A、AAC、WMA、APE、OPUS 等普通音频。
- 极致 VBR V0、320/256/192 kbps 等音质预设。
- 拖拽文件、批量导入文件夹、多任务并行转换。
- 保留可用的音频元数据，输出重名时自动添加序号。
- 不上传文件、不覆盖源文件、不写入系统目录。

> `.gkk` 不是常见的公开音频标准扩展名，部分场景实际指 `.kgg`。能否转换取决于文件中是否存在 FFmpeg 可以读取的标准音频流。对不支持的加密或版权保护文件，本工具会安全失败，不修改源文件。

## 关于 GPU

应用会检测 NVIDIA、AMD 或 Intel 显卡并显示硬件状态。MP3/LAME 是 CPU 音频编码器，NVENC、QSV、AMF 等 GPU 编码器主要用于视频，不能直接加速 MP3。工具使用多任务并行充分利用多核 CPU，避免虚假显示 GPU 音频加速。

## 从源码运行

需要 JDK 17 或更高版本。

```powershell
./install-ffmpeg.bat
./run.bat
```

## 生成 Windows EXE 包

```powershell
./install-ffmpeg.bat
./package-windows.bat
```

发行包会生成到 `dist/kgg-mp3-windows-x64.zip`。GitHub Actions 也会在每次推送后自动构建；推送 `v*` 标签时自动创建 Release。

## 许可证

本项目使用 [MIT License](LICENSE)。FFmpeg 由安装与构建脚本单独下载，其许可证归 FFmpeg 项目及对应分发方所有。
