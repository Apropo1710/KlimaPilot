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
    private var incomingInvitation: ClimatePairingInvitation.Invitation? = null
    private var incomingConfirmation: PendingConfirmation? = null
    private val consumedIncomingSessions = mutableMapOf<String, Long>()

    @Synchronized
    fun createOutgoingInvitation(nowMillis: Long = System.currentTimeMillis()): String {
        check(incomingInvitation == null && outgoingInvitation == null) {
            "Es läuft bereits eine Pairing-Sitzung."
        }
        val invitation = createInvitation(nowMillis)
        outgoingInvitation = invitation
        outgoingConfirmation = null
        return invitation.toQrPayload()
    }

    /**
     * Validates an invitation and returns the fingerprint for the local confirmation
     * screen. No peer is trusted at this stage.
     */
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
     * Completes the responder side after the user has confirmed the inviter.
     * The response is created first; trust is persisted only if that succeeds.
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
        trustedPeerStore.trustPeer(invitation.publicKeyBase64, displayName)
        consumedIncomingSessions[invitation.sessionId] = invitation.expiresAtMillis
        incomingInvitation = null
        incomingConfirmation = null
        return response.toPayload()
    }

    /**
     * Validates the response to the locally displayed invitation and exposes the
     * responder fingerprint. Trust is not stored until confirmOutgoingResponse().
     */
    @Synchronized
    fun prepareOutgoingResponse(
        payload: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PendingConfirmation? {
        val invitation = outgoingInvitation ?: return null
        if (incomingInvitation != null || outgoingConfirmation != null) return null
        val response = ClimatePairingResponse.parseAndVerify(payload, invitation, nowMillis) ?: return null
        if (response.responderPeerId == localPeerId) return null

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

        // Re-parse is unnecessary: the confirmation can only be created by this
        // controller after signature and session/nonce validation.
        val responsePeer = pendingResponsePeer
            ?: throw IllegalStateException("Die geprüfte Geräteidentität fehlt.")
        val trusted = trustedPeerStore.trustPeer(responsePeer.publicKeyBase64, displayName)
        outgoingInvitation = null
        outgoingConfirmation = null
        pendingResponsePeer = null
        return trusted
    }

    private var pendingResponsePeer: ClimatePairingResponse.Response? = null

    /** Cancels the current local pairing attempt without changing existing trust. */
    @Synchronized
    fun cancel() {
        outgoingInvitation = null
        outgoingConfirmation = null
        pendingResponsePeer = null
        incomingInvitation = null
        incomingConfirmation = null
    }
}
