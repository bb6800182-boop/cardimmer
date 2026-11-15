import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.c520.cardimmer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.c520.cardimmer"
        // 車機是 Android 13；從 33 起跳可省掉舊版相容分支
        minSdk = 33
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.1"
    }

    // 簽章放在專案資料夾旁邊的 car-dimmer-signing（不進版控；用 Dropbox 同步，每台電腦都用同一份）：
    // 正式版的金鑰與密碼寫在 signing.properties；開發版用那裡的 debug.keystore。
    // 沒有這個資料夾的電腦照樣能編 debug 版（用 Android 預設的 debug 金鑰），只是編不出簽好章的正式版。
    val signingDir = rootDir.resolveSibling("car-dimmer-signing")
    val signing = Properties().apply { signingDir.resolve("signing.properties").takeIf { it.isFile }?.reader()?.use { load(it) } }
    signingConfigs {
        signingDir.resolve("debug.keystore").takeIf { it.isFile }?.let { getByName("debug").storeFile = it }
        signing.getProperty("storeFile")?.let { store ->
            create("release") {
                storeFile = signingDir.resolve(store)
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    // 正式版不做程式碼縮減：App 很小，而且這樣正式版和測試過的開發版是同一份程式
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        error += listOf(
            "ExportedReceiver", "ExportedService", "UnsafeImplicitIntentLaunch",
            "MutableImplicitPendingIntent", "UnspecifiedRegisterReceiverFlag",
        )
    }
}

kotlin {
    jvmToolchain(17)
}
