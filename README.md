# 电视直播 (TV Live)

安卓电视应用：把 NAS 里的电视剧做成一个 **7×24 循环直播** 效果。

## 原理

- 数据源 `durations.json`：每行是一个视频文件及其时长（毫秒）。
- 视频地址**倒数第二级路径**即为电视剧名（如 `.../电视剧/三国演义/01桃园三结义.mkv` → 三国演义）。
- 应用以 **1970-01-01 00:00:00 UTC** 为起点，取 `当前毫秒 % 整部剧总时长` 作为“直播进度”，
  换算出 **第几集 / 第几分 / 第几秒**，并在该集对应偏移处起播。
- 每秒按墙钟重算位置，跨集时自动续播下一集，始终保持“正在直播”的观感。
- 左侧为电视剧频道列表；首次进入播放第一部（三国演义），之后用 `SharedPreferences` 记忆上次频道。

## 构建

```bash
# 需要 Android SDK（compileSdk 34 / build-tools 34.0.0）与 JDK 17
./gradlew assembleDebug
```

## 持续集成

仓库已配置 GitHub Actions（`.github/workflows/build.yml`）：

- 推送 `main` 分支：自动编译并上传 `tv-live-apk` 构建产物。
- 推送 `v*` 标签（如 `v1.0`）：编译并发布 GitHub Release，附带可安装的 debug APK。

```bash
git tag v1.0 && git push origin v1.0
```

> 说明：发布的 APK 为 debug 签名，可直接在电视盒子上通过“未知来源”侧载安装。
