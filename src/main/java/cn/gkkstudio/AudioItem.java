package cn.gkkstudio;

import java.nio.file.Path;

final class AudioItem {
    enum State { WAITING, ANALYZING, CONVERTING, DONE, FAILED, CANCELLED }

    final Path source;
    volatile State state = State.WAITING;
    volatile int progress;
    volatile String detail = "等待转换";
    volatile Path output;
    volatile Process process;

    AudioItem(Path source) {
        this.source = source;
    }

    String stateText() {
        return switch (state) {
            case WAITING -> "等待中";
            case ANALYZING -> "正在分析";
            case CONVERTING -> "转换中";
            case DONE -> "已完成";
            case FAILED -> "失败";
            case CANCELLED -> "已取消";
        };
    }
}
