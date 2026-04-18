package app.hydra.manager

object AppRecreationController {

    @Volatile
    private var pendingThemeRefresh = false
    @Volatile
    private var pendingSettingsSection: String? = null

    fun markThemeRefresh() {
        pendingThemeRefresh = true
    }

    fun markSettingsSection(sectionName: String?) {
        pendingSettingsSection = sectionName
    }

    fun consumeSettingsSection(): String? {
        val value = pendingSettingsSection
        pendingSettingsSection = null
        return value
    }

    fun consumeThemeRefresh(): Boolean {
        val value = pendingThemeRefresh
        pendingThemeRefresh = false
        return value
    }
}
