package org.zimmob.zimlx.launcherclient

import android.content.Context

class LauncherClientProxyConnection(
        private val context: Context,
        private val proxyPackage: String = "org.zimmob.zimlx.launcherclientproxy",
        private val overlayEnabled: Boolean) {


    companion object {
        const val SERVICE_DISCONNECTED = 0
        const val SERVICE_CONNECTED = 1
        const val SERVICE_CONNECTING = 2
    }
}