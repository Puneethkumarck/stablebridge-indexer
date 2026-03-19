rootProject.name = "stablebridge-indexer"

buildCache {
    local {
        isEnabled = true
    }
}

include("stablebridge-indexer-api")
include("stablebridge-indexer-client")
include("stablebridge-indexer")
