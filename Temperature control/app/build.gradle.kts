plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.flydigicooler"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.flydigicooler"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // 核心强制指定：无论当前文件在哪个子目录下，强制将 src/main 下的所有子目录全量作为源码扫描！
    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf(
                "src/main/java",
                "src/main",
                "src/main/app/src/main/java",
                "src/main/java/com/example/flydigicooler"
            ))
        }
    }
}

dependencies {
    // 零外部依赖
}
