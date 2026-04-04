package app.hydra.manager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object InstallStatusCenter {

    data class Event(
        val message: String,
        val indefinite: Boolean = false,
        val refreshInstalledState: Boolean = false,
        val token: Long = System.currentTimeMillis()
    )

    private val _events = MutableLiveData<Event>()
    val events: LiveData<Event> = _events

    fun post(
        message: String,
        indefinite: Boolean = false,
        refreshInstalledState: Boolean = false
    ) {
        _events.postValue(
            Event(
                message = message,
                indefinite = indefinite,
                refreshInstalledState = refreshInstalledState
            )
        )
    }
}
