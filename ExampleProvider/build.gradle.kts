dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
}

// Use an integer for version numbers

version = 15

cloudstream {
    language = "th"
    description = "037-HDD ดูหนังออนไลน์ ซีรีส์ อนิเมะ"
    authors = listOf("popo")

    /**
     * Status int:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=037-hdd.com&sz=%size%"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

// The plugin compiler uses the app API as compileOnly; fallback tests exercise those API types.
configurations.getByName("testImplementation").extendsFrom(configurations.getByName("compileOnly"))
