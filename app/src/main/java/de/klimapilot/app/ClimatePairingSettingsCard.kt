package de.klimapilot.app

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import de.klimapilot.app.sync.ClimatePairingInvitation
import de.klimapilot.app.sync.ClimatePairingResponse
import de.klimapilot.app.sync.ClimatePairingQrCode
import de.klimapilot.app.sync.ClimatePairingSessionController
import de.klimapilot.app.sync.ClimatePeerIdentityStore
import de.klimapilot.app.sync.ClimateTrustedPeerStore
import com.google.zxing.common.BitMatrix

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
    var pending by remember { mutableStateOf<ClimatePairingSessionController.PendingConfirmation?>(null) }
    var pendingIsIncoming by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Geräte können per QR-Code gekoppelt werden. Beide Seiten müssen den Fingerprint bestätigen.") }
    var busy by remember { mutableStateOf(false) }

    fun getController(): ClimatePairingSessionController {
        controller?.let { return it }
        val identity = identityStore.getOrCreateIdentity()
        localFingerprint = identity.fingerprint
        return ClimatePairingSessionController(
            localPeerId = identity.peerId,
            trustedPeerStore = trustedStore,
            createInvitation = { now -> invitationFactory.create(now) },
            createResponse = { invitation, now -> responseFactory.create(invitation, now) }
        ).also { controller = it }
    }

    fun cancelSession(message: String) {
        controller?.cancel()
        qrPayload = null
        pending = null
        pendingIsIncoming = false
        status = message
    }

    fun startScan() {
        val activity = context as? Activity
        if (activity == null) {
            status = "Der QR-Scanner kann in diesem Kontext nicht geöffnet werden."
            return
        }
        busy = true
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        runCatching { GmsBarcodeScanning.getClient(activity, options).startScan() }
            .onSuccess { task ->
                task.addOnSuccessListener { barcode ->
                    busy = false
                    val raw = barcode.rawValue
                    if (raw.isNullOrBlank()) {
                        status = "Der QR-Code enthält keine lesbaren Daten."
                    } else {
                        runCatching {
                            val active = getController()
                            val incoming = active.prepareIncomingInvitation(raw)
                            if (incoming != null) {
                                pending = incoming
                                pendingIsIncoming = true
                                qrPayload = null
                                status = "Einladung geprüft. Bitte Fingerprint vergleichen und bestätigen."
                            } else {
                                val outgoing = active.prepareOutgoingResponse(raw)
                                if (outgoing != null) {
                                    pending = outgoing
                                    pendingIsIncoming = false
                                    qrPayload = null
                                    status = "Antwort geprüft. Bitte Fingerprint vergleichen und bestätigen."
                                } else {
                                    status = "Der QR-Code ist ungültig, abgelaufen oder gehört nicht zur aktiven Sitzung."
                                }
                            }
                        }.onFailure { error ->
                            status = error.message ?: "Pairing-Code konnte nicht geprüft werden."
                        }
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

    val qrBitmap = remember(qrPayload) {
        qrPayload?.let { payload ->
            runCatching {
                val matrix = ClimatePairingQrCode.encode(payload, 640)
                matrix.toBitmap()
            }.getOrNull()
        }
    }

    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Geräte koppeln", color = colors.onSurface, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Einmalige Kopplung mit Fingerprint-Prüfung. Es werden dabei noch keine Klimaprofile übertragen.", color = colors.onSurfaceVariant, fontSize = 11.sp)
            if (localFingerprint != null) {
                Text("Fingerprint dieses Geräts: $localFingerprint", color = colors.onSurfaceVariant, fontSize = 10.sp)
            }
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(48) },
                label = { Text("Name des anderen Geräts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (pending != null) {
                Text("Geräte-Fingerprint", color = colors.onSurface, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(pending!!.fingerprint, color = colors.onSurface, fontSize = 13.sp)
                Text("Vergleiche diesen Fingerprint mit dem anderen Smartphone, bevor du bestätigst.", color = colors.onSurfaceVariant, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching {
                                val active = getController()
                                val confirmation = pending ?: return@runCatching
                                if (pendingIsIncoming) {
                                    qrPayload = active.confirmIncomingInvitation(confirmation, displayName)
                                    pending = null
                                    status = "Bestätigt. Zeige diesen Antwort-QR-Code dem anderen Gerät."
                                } else {
                                    active.confirmOutgoingResponse(confirmation, displayName)
                                    qrPayload = null
                                    pending = null
                                    status = "Gerät erfolgreich gekoppelt. Eine Datenübertragung ist noch nicht eingerichtet."
                                }
                            }.onFailure { error -> status = error.message ?: "Bestätigung fehlgeschlagen." }
                        },
                        enabled = displayName.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Fingerprint bestätigen") }
                    OutlinedButton(onClick = { cancelSession("Pairing abgebrochen.") }) { Text("Abbrechen") }
                }
            } else if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "KlimaPilot-Pairing-QR-Code",
                    modifier = Modifier.size(240.dp).align(Alignment.CenterHorizontally),
                    contentScale = ContentScale.Fit
                )
                Text("Dieser QR-Code ist nur 120 Sekunden gültig.", color = colors.onSurfaceVariant, fontSize = 11.sp)
                OutlinedButton(onClick = { cancelSession("Pairing abgebrochen.") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Pairing abbrechen")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching {
                                qrPayload = getController().createOutgoingInvitation()
                                pending = null
                                status = "Einladung erstellt. Lass das andere Smartphone diesen QR-Code scannen."
                            }.onFailure { error -> status = error.message ?: "Einladung konnte nicht erstellt werden." }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Einladung anzeigen") }
                    OutlinedButton(
                        onClick = { startScan() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (busy) "Scanner öffnet …" else "QR-Code scannen") }
                }
                if (qrPayload != null) {
                    Text("Einladung bereit.", color = colors.onSurface, fontSize = 12.sp)
                }
            }
            Text(status, color = if (status.contains("ungültig") || status.contains("fehlgeschlagen")) MaterialTheme.colorScheme.error else colors.onSurfaceVariant, fontSize = 11.sp)
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
