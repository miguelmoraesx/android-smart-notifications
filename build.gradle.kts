buildscript {
    dependencies {
        // LiteRT-LM 0.17.0 is published with Kotlin 2.4 metadata.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
}

