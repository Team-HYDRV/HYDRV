package app.hydra.manager

object RuntimeConfig {
    const val githubOwner = "Team-HYDRV"
    const val githubRepo = "HYDRV"

    val githubRepoUrl: String
        get() = "https://github.com/$githubOwner/$githubRepo"

    val githubLatestReleaseApkUrl: String
        get() = "$githubRepoUrl/releases/latest/download/HYDRV.apk"

    val githubRawBaseUrl: String
        get() = "https://raw.githubusercontent.com/$githubOwner/$githubRepo/main"

    val githubApiBaseUrl: String
        get() = "https://api.github.com/repos/$githubOwner/$githubRepo"

    val githubLatestReleaseUrl: String
        get() = "$githubApiBaseUrl/releases/latest"

    val githubChangelogUrl: String
        get() = "$githubRawBaseUrl/CHANGELOG.md"

    val githubContributorsUrl: String
        get() = "$githubApiBaseUrl/contributors?per_page=100"

    val rewardedAdUnitId: String
        get() = "ca-app-pub-3319548483346434/8211608689"

    val rewardedTestAdUnitId: String
        get() = "ca-app-pub-3940256099942544/5224354917"

    val defaultCatalogUrls: List<String>
        get() {
            return listOf("https://api.hydrv.app/catalogue")
        }

    val defaultCatalogUrl: String
        get() = defaultCatalogUrls.first()
}
