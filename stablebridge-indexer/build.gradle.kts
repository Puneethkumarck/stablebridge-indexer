plugins {
    id("stablebridge-indexer.service")
}

stablebridge {
    jibImageName.set("stablebridge/indexer")
}

dependencies {
    implementation(project(":stablebridge-indexer-api"))

    // Configuration properties metadata generation
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Bloom filter (Guava fallback for local dev without RedisBloom)
    implementation("com.google.guava:guava:33.4.8-jre")
}
