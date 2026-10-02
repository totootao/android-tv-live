package top.totootao.tvlive;

import java.util.List;

/**
 * 直播位置计算核心：把“自 1970-01-01 00:00:00 UTC 起经过的毫秒数”映射到
 * 某部电视剧内的“第几集 / 集内偏移”，从而形成 7x24 循环直播效果。
 */
public final class LiveCalculator {

    private LiveCalculator() {
    }

    /**
     * 计算当前时刻在指定剧集中的直播位置。
     *
     * @param eps   已按播放顺序排好序的剧集列表
     * @param nowMs System.currentTimeMillis()，即自 epoch 起的毫秒
     * @return 当前位置（0 基集索引、集内偏移、整剧总时长、整剧内绝对位置）
     */
    public static Position compute(List<Episode> eps, long nowMs) {
        long total = totalDuration(eps);
        if (total <= 0) {
            total = 1;
        }
        long pos = Math.floorMod(nowMs, total);
        long acc = 0;
        for (int i = 0; i < eps.size(); i++) {
            long d = eps.get(i).durationMs;
            if (pos < acc + d) {
                return new Position(i, pos - acc, total, pos);
            }
            acc += d;
        }
        int last = Math.max(0, eps.size() - 1);
        return new Position(last, 0, total, pos);
    }

    public static long totalDuration(List<Episode> eps) {
        long t = 0;
        for (Episode e : eps) {
            t += e.durationMs;
        }
        return t;
    }

    /** 一次位置计算结果。 */
    public static final class Position {
        public final int episodeIndex0; // 0 基集索引
        public final long offsetMs;     // 本集内的偏移（毫秒）
        public final long totalMs;      // 整部剧总时长（毫秒）
        public final long posMs;        // 整部剧内的绝对位置（毫秒）

        public Position(int episodeIndex0, long offsetMs, long totalMs, long posMs) {
            this.episodeIndex0 = episodeIndex0;
            this.offsetMs = offsetMs;
            this.totalMs = totalMs;
            this.posMs = posMs;
        }
    }
}
