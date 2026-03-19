plugins {
    id("stablebridge-indexer.service")
}

stablebridge {
    jibImageName.set("stablebridge/indexer")
}

dependencies {
    implementation(project(":stablebridge-indexer-api"))

    // Bloom filter (Guava fallback for local dev without RedisBloom)
    implementation("com.google.guava:guava:33.4.8-jre")
}
