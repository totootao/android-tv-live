package top.totootao.tvlive;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安卓电视“直播”应用：
 * <ul>
 *   <li>左侧为电视剧频道列表，右侧为播放器。</li>
 *   <li>根据“自 1970-01-01 00:00:00 起经过的毫秒数”计算当前应播放的集/分/秒，循环直播。</li>
 *   <li>首次进入播放第一部剧；之后记忆上次播放的频道。</li>
 * </ul>
 */
public class MainActivity extends Activity {

    private static final String TAG = "TVLive";
    private static final String DURATIONS_URL =
            "https://www.totootao.top/alist/d/3-90/mnt/nas/%E8%A7%86%E9%A2%91/durations.json";
    private static final String PREFS = "tv_live_prefs";
    private static final String KEY_LAST = "last_series";

    /** 电视剧名 -> 已排序的剧集列表（保留 JSON 中的插入顺序，即“第一部”为三国演义） */
    private final Map<String, List<Episode>> seriesMap = new LinkedHashMap<>();
    private final List<String> seriesNames = new ArrayList<>();

    private ExoPlayer player;
    private PlayerView playerView;
    private RecyclerView listView;
    private SeriesAdapter adapter;
    private TextView tvSeriesTitle;
    private TextView tvLiveInfo;
    private TextView tvEpisodeTitle;

    private String currentSeries = null;
    private List<Episode> currentEps = null;
    private int currentEpisodeIndex = -1;

    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            refreshLive();
            tick.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.playerView);
        listView = findViewById(R.id.seriesList);
        tvSeriesTitle = findViewById(R.id.seriesTitle);
        tvLiveInfo = findViewById(R.id.liveInfo);
        tvEpisodeTitle = findViewById(R.id.episodeTitle);

        listView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SeriesAdapter(seriesNames, this::onSeriesSelected);
        listView.setAdapter(adapter);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "playback error: " + error.getMessage(), error);
            }
        });

        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            String json = fetch(DURATIONS_URL);
            if (json == null) {
                try {
                    InputStream is = getAssets().open("durations.json");
                    json = readStream(is);
                } catch (Exception e) {
                    Log.e(TAG, "assets fallback failed", e);
                }
            }
            if (json != null) {
                try {
                    parse(json);
                } catch (Exception e) {
                    Log.e(TAG, "parse failed", e);
                }
            }
            runOnUiThread(this::onDataReady);
        }).start();
    }

    private void onDataReady() {
        if (seriesNames.isEmpty()) {
            tvSeriesTitle.setText("加载失败：无法获取剧集数据");
            return;
        }
        adapter.notifyDataSetChanged();

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String last = sp.getString(KEY_LAST, null);
        int idx = 0;
        if (last != null && seriesMap.containsKey(last)) {
            idx = seriesNames.indexOf(last);
        }
        if (idx < 0) {
            idx = 0;
        }
        adapter.setSelected(idx);
        onSeriesSelected(idx);
        tick.postDelayed(tickTask, 1000);
    }

    private void onSeriesSelected(int position) {
        if (position < 0 || position >= seriesNames.size()) {
            return;
        }
        String name = seriesNames.get(position);
        if (name.equals(currentSeries)) {
            return; // 同一频道不重复加载
        }
        currentSeries = name;
        currentEps = seriesMap.get(name);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST, name).apply();
        adapter.setSelected(position);
        playCurrent();
    }

    /** 初次进入某频道：按墙钟定位到“直播点”并起播。 */
    private void playCurrent() {
        if (currentEps == null || currentEps.isEmpty()) {
            return;
        }
        LiveCalculator.Position p = LiveCalculator.compute(currentEps, System.currentTimeMillis());
        currentEpisodeIndex = p.episodeIndex0;
        Episode ep = currentEps.get(p.episodeIndex0);
        player.setMediaItem(MediaItem.fromUri(ep.url), p.offsetMs);
        player.prepare();
        player.play();
        updateOverlay(p, ep);
    }

    /** 每秒据墙钟刷新：跨集时自动续播下一集，保证“直播”不脱节。 */
    private void refreshLive() {
        if (currentEps == null || currentEps.isEmpty()) {
            return;
        }
        LiveCalculator.Position p = LiveCalculator.compute(currentEps, System.currentTimeMillis());
        if (p.episodeIndex0 != currentEpisodeIndex) {
            currentEpisodeIndex = p.episodeIndex0;
            Episode ep = currentEps.get(p.episodeIndex0);
            player.setMediaItem(MediaItem.fromUri(ep.url), p.offsetMs);
            player.prepare();
            player.play();
        }
        updateOverlay(p, currentEps.get(p.episodeIndex0));
    }

    private void updateOverlay(LiveCalculator.Position p, Episode ep) {
        if (currentSeries != null) {
            tvSeriesTitle.setText(currentSeries);
        }
        int epNum = p.episodeIndex0 + 1;
        long totalSec = p.offsetMs / 1000;
        long hh = totalSec / 3600;
        long mm = (totalSec / 60) % 60;
        long ss = totalSec % 60;
        String pos;
        if (hh > 0) {
            pos = String.format("第 %d 集 · %02d:%02d:%02d", epNum, hh, mm, ss);
        } else {
            pos = String.format("第 %d 集 · %02d:%02d", epNum, mm, ss);
        }
        tvLiveInfo.setText(pos);
        tvEpisodeTitle.setText(ep == null ? "" : ep.title);
    }

    private void parse(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        LinkedHashMap<String, List<Episode>> tmp = new LinkedHashMap<>();
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String rawUrl = it.next();
            long dur = obj.getLong(rawUrl);
            String decoded = java.net.URLDecoder.decode(rawUrl, "UTF-8");
            String[] parts = decoded.split("/");
            if (parts.length < 2) {
                continue;
            }
            String series = parts[parts.length - 2]; // 倒数第二级 = 电视剧名
            String file = parts[parts.length - 1];
            Episode ep = new Episode(rawUrl, dur, prettyTitle(file), extractOrder(file));
            tmp.computeIfAbsent(series, k -> new ArrayList<>()).add(ep);
        }
        for (Map.Entry<String, List<Episode>> e : tmp.entrySet()) {
            List<Episode> eps = e.getValue();
            Collections.sort(eps, (a, b) -> {
                if (a.order != b.order) {
                    return Integer.compare(a.order, b.order);
                }
                return a.title.compareTo(b.title);
            });
            seriesMap.put(e.getKey(), eps);
        }
        seriesNames.addAll(seriesMap.keySet());
    }

    /** 取文件名开头的连续数字作为排序序号。 */
    private static int extractOrder(String file) {
        int i = 0;
        while (i < file.length() && Character.isDigit(file.charAt(i))) {
            i++;
        }
        if (i > 0) {
            try {
                return Integer.parseInt(file.substring(0, i));
            } catch (Exception ignore) {
                // 忽略
            }
        }
        return Integer.MAX_VALUE;
    }

    /** 去掉扩展名作为展示标题。 */
    private static String prettyTitle(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    private static String fetch(String urlStr) {
        try {
            URL u = new URL(urlStr);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "TVLive/1.0");
            int code = c.getResponseCode();
            if (code != 200) {
                c.disconnect();
                return null;
            }
            String s = readStream(c.getInputStream());
            c.disconnect();
            return s;
        } catch (Exception e) {
            Log.e(TAG, "fetch failed: " + urlStr, e);
            return null;
        }
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tick.removeCallbacks(tickTask);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
