package de.klimapilot.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import de.klimapilot.app.sync.ClimateProfilePeerDiscovery
import de.klimapilot.app.sync.ClimateTrustedPeerStore
import de.klimapilot.app.sync.UnverifiedClimatePeer
import kotlinx.coroutines.delay

/**
 * Automatic data exchange with paired phones while the app is in the foreground:
 * announces this phone in the local WLAN, finds paired phones, authenticates them and
 * exchanges accounts and profile changes. Runs again every [SYNC_INTERVAL_MS].
 * Unknown or unpaired devices are rejected by the mutual authentication.
 */
@Composable
internal fun ClimatePeerAutoSync(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    var foreground by remember {
        mutableStateOf(lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(enabled, foreground, PeerSyncSignals.pairingRevision) {
        if (!enabled || !foreground) return@LaunchedEffect
        if (ClimateTrustedPeerStore(context).trustedPeers().isEmpty()) return@LaunchedEffect
        val discovery = ClimateProfilePeerDiscovery(context, sessionHandler = ClimatePeerDataSync(context))
        val peers = mutableMapOf<String, UnverifiedClimatePeer>()
        fun syncWith(peer: UnverifiedClimatePeer) {
            discovery.authenticatePeer(peer) { _, message -> PeerSyncSignals.lastStatus = message }
        }
        try {
            while (true) {
                if (!discovery.isRunning) {
                    peers.clear()
                    discovery.start(
                        onPeerFound = { peer -> peers[peer.instanceName] = peer; syncWith(peer) },
                        onPeerLost = { name -> peers.remove(name) },
                        onError = { message -> PeerSyncSignals.lastStatus = message },
                    )
                } else {
                    peers.values.toList().forEach(::syncWith)
                }
                delay(SYNC_INTERVAL_MS)
            }
        } finally {
            discovery.stop()
        }
    }
}

private const val SYNC_INTERVAL_MS = 30_000L
