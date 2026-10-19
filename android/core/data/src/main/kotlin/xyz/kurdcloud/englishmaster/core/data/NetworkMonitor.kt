package xyz.kurdcloud.englishmaster.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

/** Tracks whether the default network has validated internet access. */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = state.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.isUsable()
            }

            override fun onLost(network: Network) {
                state.value = false
            }
        })
    }

    private fun currentlyOnline(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.isUsable() == true

    private fun NetworkCapabilities.isUsable() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
