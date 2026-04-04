package app.hydra.manager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object CatalogStateCenter {

    private val _apps = MutableLiveData<List<AppModel>>(emptyList())
    val apps: LiveData<List<AppModel>> = _apps

    fun update(apps: List<AppModel>) {
        _apps.value = apps
    }

    fun currentApps(): List<AppModel> {
        return _apps.value.orEmpty()
    }
}
