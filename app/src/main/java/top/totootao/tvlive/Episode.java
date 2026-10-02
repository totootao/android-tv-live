package top.totootao.tvlive;

/**
 * 单集视频信息。
 */
public class Episode {
    /** 视频播放地址（alist 直链） */
    public final String url;
    /** 时长，单位：毫秒 */
    public final long durationMs;
    /** 展示用标题（去扩展名） */
    public final String title;
    /** 排序序号（文件名开头的数字，用于自然排序） */
    public final int order;

    public Episode(String url, long durationMs, String title, int order) {
        this.url = url;
        this.durationMs = durationMs;
        this.title = title;
        this.order = order;
    }
}
