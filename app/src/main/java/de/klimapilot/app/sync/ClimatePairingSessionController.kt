package de.klimapilot.app.sync

/**
 * Coordinates the user-confirmed QR pairing flow.
 *
 * Cryptographic parsing/signing is delegated to the existing invitation/response
 * classes. This controller only stores a peer after the local confirmation step.
 * Profile transfer is deliberately outside this class.
 */
internal class ClimatePairingSessionController(
    private val localPeerId: String,
    private val trustedPeerStore: ClimateTrustedPeerStore,
    private val createInvitation: (Long) -> ClimatePairingInvitation.Invitation,
    private val createResponse: (ClimatePairingInvitation.Invitation, Long) -> ClimatePairingResponse.Response
) {
    class PendingConfirmation internal constructor(
        val peerId: String,
        val fingerprint: String,
        val sessionId: String
    )

    private var outgoingInvitation: ClimatePairingInvitation.Invitation? = null
    private var outgoingConfirmation: PendingConfirmation? = null
    private var pendingResponsePeer: ClimatePairingResponse.Response? = null
    private var incomingInvitation: ClimatePairingInvitation.Invitation? = null
    private var incomingConfirmation: PendingConfirmation? = null
    private var pendingIncomingResponse: ClimatePairingResponse.Response? = null
    private var pendingIncomingDisplayName: String? = null
    private val consumedIncomingSessions = mutableMapOf<String, Long>()

    /** Starts an outgoing session with an invitation whose WLAN listener is already bound. */
    @Synchronized
    fun startOutgoingInvitation(invitation: ClimatePairingInvitation.Invitation): String {
        check(incomingInvitation == null && outgoingInvitation == null) {
            "Es läuft bereits eine Pairing-Sitzung."
        }
        check(invitation.peerId == localPeerId) {
            "Die Einladung gehört nicht zu dieser Geräteidentität."
        }
        check(ClimatePairingEndpoint.isAllowed(invitation.pairingHost, invitation.pairingPort)) {
            "Der WLAN-Rückkanal ist ungültig."
        }
        outgoingInvitation = invitation
        outgoingConfirmation = null
        pendingResponsePeer = null
        return invitation.toQrPayload()
    }

    @Synchronized
    fun createOutgoingInvitation(nowMillis: Long = System.currentTimeMillis()): String {
        check(incomingInvitation == null && outgoingInvitation == null) {
            "Es läuft bereits eine Pairing-Sitzung."
        }
        val invitation = createInvitation(nowMillis)
        check(invitation.peerId == localPeerId) { "Die Einladung gehört nicht zu dieser Geräteidentität." }
        outgoingInvitation = invitation
        outgoingConfirmation = null
        pendingResponsePeer = null
        return invitation.toQrPayload()
    }

    /** Validates an invitation and exposes its fingerprint without trusting the peer. */
    @Synchronized
    fun prepareIncomingInvitation(
        payload: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PendingConfirmation? {
        if (outgoingInvitation != null || incomingInvitation != null) return null
        consumedIncomingSessions.entries.removeAll { it.value < nowMillis }
        val invitation = ClimatePairingInvitation.parseAndVerify(payload, nowMillis) ?: return null
        if (invitation.peerId == localPeerId) return null
        if (consumedIncomingSessions.containsKey(invitation.sessionId)) return null

        val confirmation = PendingConfirmation(
            peerId = invitation.peerId,
            fingerprint = invitation.fingerprint,
            sessionId = invitation.sessionId
        )
        incomingInvitation = invitation
        incomingConfirmation = confirmation
        return confirmation
    }

    /**
     * Prepares the responder's signed answer after local consent. No trust is
     * persisted here: the inviter must first acknowledge that it has durably stored
     * this responder. The caller must deliver the returned payload and invoke
     * acknowledgeIncomingInvitation only after receiving the matching signed ACK.
     */
    @Synchronized
    fun confirmIncomingInvitation(
        confirmation: PendingConfirmation,
        displayName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val invitation = incomingInvitation
            ?: throw IllegalStateException("Keine aktive Einladungsprüfung.")
        check(incomingConfirmation === confirmation) { "Die Bestätigung gehört nicht zur aktiven Sitzung." }
        check(nowMillis <= invitation.expiresAtMillis) { "Die Pairing-Einladung ist abgelaufen." }

        val response = createResponse(invitation, nowMillis)
        check(response.responderPeerId == localPeerId &&
            response.sessionId == invitation.sessionId &&
            response.invitationPeerId == invitation.peerId &&
            response.invitationNonceBase64 == invitation.nonceBase64
        ) { "Die Pairing-Antwort passt nicht zur aktiven Geräteidentität oder Sitzung." }
        pendingIncomingResponse = response
        pendingIncomingDisplayName = displayName.trim()
        return response.toPayload()
    }

    /**
     * Finalizes responder-side trust only after the inviter confirms durable storage.
     * A mismatched or stale ACK leaves the session pending and stores nothing.
     */
    @Synchronized
    fun acknowledgeIncomingInvitation(
        acknowledgementPayload: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ClimateTrustedPeerStore.TrustedPeer {
        val invitation = incomingInvitation
            ?: throw IllegalStateException("Keine aktive eingehende Pairing-Sitzung.")
        val response = pendingIncomingResponse
            ?: throw IllegalStateException("Es liegt keine bestätigte Pairing-Antwort vor.")
        val acknowledgement = ClimatePairingAcknowledgement.parseAndVerify(
            acknowledgementPayload, invitation, response, nowMillis
        ) ?: throw IllegalArgumentException("Die signierte Speicherbestätigung ist ungültig oder abgelaufen.")
        check(acknowledgement.inviterPeerId == invitation.peerId &&
            acknowledgement.responderPeerId == localPeerId
        ) { "Die Speicherbestätigung gehört nicht zu diesen Geräten." }
        val name = pendingIncomingDisplayName
            ?: throw IllegalStateException("Der Gerätename für die Speicherung fehlt.")
        val trusted = trustedPeerStore.trustPeer(invitation.publicKeyBase64, name)
        consumedIncomingSessions[invitation.sessionId] = invitation.expiresAtMillis
        incomingInvitation = null
        incomingConfirmation = null
        pendingIncomingResponse = null
        pendingIncomingDisplayName = null
        return trusted
    }

    /** Validates the response and exposes the responder fingerprint without trusting it. */
    @Synchronized
    fun prepareOutgoingResponse(
        payload: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PendingConfirmation? {
        val invitation = outgoingInvitation ?: return null
        if (incomingInvitation != null || outgoingConfirmation != null) return null
        val response = ClimatePairingResponse.parseAndVerify(payload, invitation, nowMillis) ?: return null
        if (response.responderPeerId == localPeerId) return null

        pendingResponsePeer = response
        return PendingConfirmation(
            peerId = response.responderPeerId,
            fingerprint = response.responderFingerprint,
            sessionId = response.sessionId
        ).also { outgoingConfirmation = it }
    }

    /** Stores the responder only after the local user has confirmed its fingerprint. */
    @Synchronized
    fun confirmOutgoingResponse(
        confirmation: PendingConfirmation,
        displayName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ClimateTrustedPeerStore.TrustedPeer {
        val invitation = outgoingInvitation
            ?: throw IllegalStateException("Keine aktive ausgehende Pairing-Sitzung.")
        check(outgoingConfirmation === confirmation) { "Die Bestätigung gehört nicht zur aktiven Sitzung." }
        check(nowMillis <= invitation.expiresAtMillis) { "Die Pairing-Einladung ist abgelaufen." }
        val response = pendingResponsePeer
            ?: throw IllegalStateException("Die geprüfte Geräteidentität fehlt.")

        val trusted = trustedPeerStore.trustPeer(response.responderPublicKeyBase64, displayName)
        outgoingInvitation = null
        outgoingConfirmation = null
        pendingResponsePeer = null
        return trusted
    }

    /**
     * Stores the responder and creates the signed ACK that must be returned over the
     * invitation's one-shot transport channel. ACK creation is completed before the
     * local trust write so a signing failure cannot leave a partial local operation.
     */
    @Synchronized
    fun confirmOutgoingResponseAndCreateAcknowledgement(
        confirmation: PendingConfirmation,
        displayName: String,
        createAcknowledgement: (ClimatePairingResponse.Response, Long) -> String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val invitation = outgoingInvitation
            ?: throw IllegalStateException("Keine aktive ausgehende Pairing-Sitzung.")
        check(outgoingConfirmation === confirmation) { "Die Bestätigung gehört nicht zur aktiven Sitzung." }
        check(nowMillis <= invitation.expiresAtMillis) { "Die Pairing-Einladung ist abgelaufen." }
        val response = pendingResponsePeer
            ?: throw IllegalStateException("Die geprüfte Geräteidentität fehlt.")
        val acknowledgementPayload = createAcknowledgement(response, nowMillis)
        trustedPeerStore.trustPeer(response.responderPublicKeyBase64, displayName)
        outgoingInvitation = null
        outgoingConfirmation = null
        pendingResponsePeer = null
        return acknowledgementPayload
    }

    /** Cancels the current local pairing attempt without changing existing trust. */
    @Synchronized
    fun cancel() {
        incomingInvitation?.let { consumedIncomingSessions[it.sessionId] = it.expiresAtMillis }
        outgoingInvitation = null
        outgoingConfirmation = null
        pendingResponsePeer = null
        incomingInvitation = null
        incomingConfirmation = null
        pendingIncomingResponse = null
        pendingIncomingDisplayName = null
    }
}
