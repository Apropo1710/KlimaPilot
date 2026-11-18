package de.klimapilot.app

import android.app.Activity
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.common.BitMatrix
import de.klimapilot.app.sync.ClimatePairingAcknowledgement
import de.klimapilot.app.sync.ClimatePairingInvitation
import de.klimapilot.app.sync.ClimatePairingQrCode
import de.klimapilot.app.sync.ClimatePairingResponse
import de.klimapilot.app.sync.ClimatePairingSessionController
import de.klimapilot.app.sync.ClimatePairingTcpChannel
import de.klimapilot.app.sync.ClimatePeerIdentityStore
import de.klimapilot.app.sync.ClimateTrustedPeerStore
import de.klimapilot.app.sync.ClimateWifiListenerAddressSelector
import java.net.Inet4Address

@Composable
internal fun ClimatePairingSettingsCard() {
    val context = LocalContext.current
    val identityStore = remember { ClimatePeerIdentityStore() }
    val trustedStore = remember(context) { ClimateTrustedPeerStore(context) }
    val invitationFactory = remember(identityStore) { ClimatePairingInvitation(identityStore) }
    val responseFactory = remember(identityStore) { ClimatePairingResponse(identityStore) }
    var controller by remember { mutableStateOf<ClimatePairingSessionController?>(null) }
    var localFingerprint by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("KlimaPilot-Gerät") }
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var scannedInvitationPayload by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ClimatePairingSessionController.PendingConfirmation?>(null) }
    var listener by remember { mutableStateOf<ClimatePairingTcpChannel.Listener?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ein Gerät zeigt die Einladung. Das zweite Gerät scannt einmal und bestätigt die Kopplung.") }

    fun getController(): ClimatePairingSessionController {
        controller?.let { return it }
        val identity = identityStore.getOrCreateIdentity()
        localFingerprint = identity.fingerprint
        return ClimatePairingSessionController(
            localPeerId = identity.peerId,
            trustedPeerStore = trustedStore,
            createInvitation = { throw IllegalStateException("Der WLAN-Rückkanal muss vor der Einladung gestartet werden.") },
            createResponse = { invitation, now -> responseFactory.create(invitation, now) }
        ).also { controller = it }
    }

    fun closeListener() {
        listener?.close()
        listener = null
    }

    fun cancelSession(message: String) {
        closeListener()
        controller?.cancel()
        qrPayload = null
        scannedInvitationPayload = null
        pending = null
        busy = false
        status = message
    }

    fun findWifi(): Pair<Network, Inet4Address> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: throw IllegalStateException("Android-Netzwerkverwaltung ist nicht verfügbar.")
        val candidates = connectivity.allNetworks.toList().sortedBy { if (it == connectivity.activeNetwork) 0 else 1 }
        for (network in candidates) {
            if (connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) continue
            val props = connectivity.getLinkProperties(network) ?: continue
            val iface = props.interfaceName ?: continue
            val address = ClimateWifiListenerAddressSelector.select(iface, props.linkAddresses.map { it.address })
            if (address is Inet4Address && !address.isLinkLocalAddress && address.isSiteLocalAddress) return network to address
        }
        throw IllegalStateException("Kein privates IPv4-WLAN gefunden. Bitte beide Smartphones mit demselben WLAN verbinden.")
    }

    fun startInvitation() {
        runCatching {
            closeListener()
            val (network, address) = findWifi()
            val channel = ClimatePairingTcpChannel.openListener(address)
            listener = channel
            val active = getController()
            val invitation = invitationFactory.create(channel.host, channel.port)
            qrPayload = active.startOutgoingInvitation(invitation)
            pending = null
            busy = true
            status = "Einladung aktiv. Warte auf die signierte Antwort im lokalen WLAN."
            Thread({
                @Suppress("UNUSED_VARIABLE") val selectedWifiNetwork = network
                try {
                    channel.acceptOnce { responsePayload ->
                        val confirmation = active.prepareOutgoingResponse(responsePayload)
                            ?: throw IllegalArgumentException("Die signierte Antwort ist ungültig oder gehört nicht zu dieser Einladung.")
                        active.confirmOutgoingResponseAndCreateAcknowledgement(
                            confirmation,
                            "KlimaPilot-Gerät"
                        ) { response, now ->
                            ClimatePairingAcknowledgement.create(identityStore, response, now).toPayload()
                        }
                    }
                    busy = false
                    qrPayload = null
                    status = "Gerät erfolgreich gekoppelt. Die signierte Speicherbestätigung wurde übertragen."
                    closeListener()
                } catch (error: Exception) {
                    busy = false
                    qrPayload = null
                    status = error.message ?: "Pairing-Rückkanal fehlgeschlagen."
                    closeListener()
                    active.cancel()
                }
            }, "KlimaPilot-Pairing-Listener").apply { isDaemon = true; start() }
        }.onFailure { error ->
            closeListener()
            busy = false
            status = error.message ?: "Einladung konnte nicht gestartet werden."
        }
    }

    fun startScan() {
        val activity = context as? Activity
        if (activity == null) {
            status = "Der QR-Scanner kann in diesem Kontext nicht geöffnet werden."
            return
        }
        busy = true
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        runCatching { GmsBarcodeScanning.getClient(activity, options).startScan() }
            .onSuccess { task ->
                task.addOnSuccessListener { barcode ->
                    busy = false
                    val raw = barcode.rawValue
                    if (raw.isNullOrBlank()) {
                        status = "Der QR-Code enthält keine lesbaren Daten."
                        return@addOnSuccessListener
                    }
                    scannedInvitationPayload = raw
                    val confirmation = getController().prepareIncomingInvitation(raw)
                    if (confirmation == null) {
                        status = "Der QR-Code ist ungültig, abgelaufen oder gehört zu einer aktiven Sitzung."
                    } else {
                        pending = confirmation
                        qrPayload = null
                        status = "Einladung kryptografisch geprüft. Tippe auf „Koppeln“, um die signierte Antwort direkt über WLAN zu senden."
                    }
                }.addOnCanceledListener {
                    busy = false
                    status = "QR-Scan abgebrochen."
                }.addOnFailureListener { error ->
                    busy = false
                    status = error.message ?: "QR-Scanner konnte nicht gestartet werden."
                }
            }
            .onFailure { error ->
                busy = false
                status = error.message ?: "QR-Scanner ist nicht verfügbar."
            }
    }

    fun confirmPairing() {
        val confirmation = pending ?: return
        busy = true
        status = "Sende signierte Antwort und warte auf die Speicherbestätigung …"
        Thread({
            try {
                val (network, _) = findWifi()
                val active = getController()
                val response = active.confirmIncomingInvitation(confirmation, displayName)
                val invitation = ClimatePairingInvitation.parseAndVerify(
                    scannedInvitationPayload ?: throw IllegalStateException("Die Einladungssitzung ist nicht verfügbar.")
                ) ?: throw IllegalStateException("Die Einladungssitzung ist abgelaufen.")
                val acknowledgement = ClimatePairingTcpChannel.exchange(
                    network.socketFactory, invitation.pairingHost, invitation.pairingPort, response
                )
                active.acknowledgeIncomingInvitation(acknowledgement)
                pending = null
                busy = false
                status = "Gerät erfolgreich gekoppelt. Die signierte Speicherbestätigung wurde geprüft."
            } catch (error: Exception) {
                getController().cancel()
                pending = null
                busy = false
                status = error.message ?: "Kopplung fehlgeschlagen. Es wurde kein Erfolg gespeichert."
            }
        }, "KlimaPilot-Pairing-Client").apply { isDaemon = true; start() }
    }

    DisposableEffect(Unit) {
        onDispose { closeListener(); controller?.cancel() }
    }

    val qrBitmap = remember(qrPayload) {
        qrPayload?.let { payload -> runCatching { ClimatePairingQrCode.encode(payload, 640).toBitmap() }.getOrNull() }
    }
    val colors = MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Geräte koppeln", color = colors.onSurface, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Einmaliger QR-Scan mit signiertem WLAN-Rückkanal. Es werden keine Klimaprofile übertragen.", color = colors.onSurfaceVariant, fontSize = 11.sp)
            if (localFingerprint != null) Text("Fingerprint dieses Geräts: $localFingerprint", color = colors.onSurfaceVariant, fontSize = 10.sp)
            OutlinedTextField(value = displayName, onValueChange = { displayName = it.take(48) }, label = { Text("Name dieses Geräts") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (pending != null) {
                Text("Einladender Fingerprint", color = colors.onSurface, fontSize = 12.sp)
                Text(pending!!.fingerprint, color = colors.onSurface, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { confirmPairing() }, enabled = !busy && displayName.isNotBlank(), modifier = Modifier.weight(1f)) { Text(if (busy) "Kopple …" else "Koppeln") }
                    OutlinedButton(onClick = { cancelSession("Pairing abgebrochen. Es wurde kein Gerät gespeichert.") }) { Text("Abbrechen") }
                }
            } else if (qrBitmap != null) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "KlimaPilot-Pairing-QR-Code", modifier = Modifier.size(240.dp).align(Alignment.CenterHorizontally), contentScale = ContentScale.Fit)
                Text("QR-Code 120 Sekunden gültig. Rückkanal wartet auf die signierte Antwort.", color = colors.onSurfaceVariant, fontSize = 11.sp)
                OutlinedButton(onClick = { cancelSession("Pairing abgebrochen.") }, modifier = Modifier.fillMaxWidth()) { Text("Pairing abbrechen") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startInvitation() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Einladung anzeigen") }
                    OutlinedButton(onClick = { startScan() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("QR-Code scannen") }
                }
            }
            Text(status, color = if (status.contains("ungültig") || status.contains("fehlgeschlagen") || status.contains("abgebrochen")) MaterialTheme.colorScheme.error else colors.onSurfaceVariant, fontSize = 11.sp)
            Text("Gekoppelte Geräte: ${trustedStore.trustedPeers().size}", color = colors.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

private fun BitMatrix.toBitmap(): Bitmap {
    val pixels = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        if (this[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
