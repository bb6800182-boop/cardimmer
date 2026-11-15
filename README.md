# 車機調暗 Car Dimmer

**English:** Car Dimmer is a screen dimmer for Android head units: when minimum brightness still glares at night, it adds an adjustable dark layer. Big switch and slider, optional schedule. English and Traditional Chinese UI (follows the device language). Free, MIT, no network access. [Intro & download](https://c520.pages.dev/cardimmer/en/)

給安卓車機用的螢幕調暗 App：車機亮度調到最低，晚上還是刺眼時，再蓋一層可以調深淺的暗色。免費、開源（MIT），不連網、沒有廣告。

- 介紹與下載：https://c520.pages.dev/cardimmer/
- 下載 APK：https://c520-dl.bb6800182.workers.dev/cardimmer/android （Android 13 以上；車機、手機、平板都能裝）

## 功能

- 一個大開關、一條大拉桿：暗度每 5% 一格，為車上好點而設計；寬螢幕左右並排，窄螢幕上下排。
- 依時間自動（可關）：像夜燈，開始時間自動變暗、結束時間自動恢復，中途仍可手動開關。
- 開機後照上次的設定自動啟動；調暗中可以從通知列關掉。
- 暗色那一層不攔截觸控，導航、音樂等其他 App 照常使用。
- 介面有繁體中文和英文，跟著裝置的語言（英文名稱 Car Dimmer）。程式裡中英文寫在一起：`L.t("中文", "English")`。

## 限制

- Android 12 起，蓋在其他 App 上的畫面超過一定的不透明度（通常 80%），底下的 App 就收不到觸控，所以暗度上限由系統決定。請先把車機自己的亮度調到最低，再用這個 App 補。
- 為 FYT 系統的副廠車機（Android 13、2000×1200）開發，其他品牌的車機沒有測過。

## 權限

- 「顯示在其他應用程式上層」：用來蓋那一層暗色，使用者在 App 裡按「前往授權」手動打開。
- 前景服務、通知：調暗時維持那一層，並在通知列提供開關。
- 開機完成：開機後恢復上次的設定。

## 自己編譯

需要 JDK 17 和 Android SDK（compileSdk 36）。

```
gradlew.bat assembleDebug
```

正式版要自己的簽章金鑰：在專案資料夾旁邊建 `car-dimmer-signing`，放金鑰檔和 `signing.properties`（`storeFile`＝金鑰檔名、`storePassword`、`keyAlias`、`keyPassword`），再 `gradlew.bat assembleRelease`。金鑰與密碼不要放進版控。

## 授權

[MIT](LICENSE) © 2026 C520。意見、問題歡迎來信：bb6800182@gmail.com；作者的其他作品在 [C520](https://c520.pages.dev/)。
