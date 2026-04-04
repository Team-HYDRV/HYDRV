package app.hydra.manager

object RuntimeConfig {

    val rewardedAdUnitId: String
        get() = listOf(
            "ca-app-pub-3319548483346434",
            "5639572396"
        ).joinToString("/")

    val rewardedTestAdUnitId: String
        get() = "ca-app-pub-3940256099942544/5224354917"

    val defaultCatalogUrls: List<String>
        get() {
            return listOf("https://api.hydrv.app/catalogue")
        }

    val defaultCatalogUrl: String
        get() = defaultCatalogUrls.first()
}
