package de.klimapilot.app.sync

/** Public identity material shared by Android Keystore and JVM test signers. */
internal data class ClimatePeerIdentity(
    val peerId: String,
    val publicKeyBase64: String,
    val fingerprint: String
)

/** Signing boundary: production uses Android Keystore; tests can use software keys. */
internal interface ClimatePeerSigner {
    fun getOrCreateIdentity(): ClimatePeerIdentity
    fun sign(challenge: ByteArray): ByteArray
    fun verify(publicKeyBase64: String, challenge: ByteArray, signatureBytes: ByteArray): Boolean
}

/** Independently verifies the SHA-256 peer ID against its advertised public key. */
internal fun peerIdMatchesPublicKey(peerId: String, publicKeyBase64: String): Boolean = runCatching {
    require(peerId.matches(Regex("[a-f0-9]{64}")))
    val encoded = java.util.Base64.getDecoder().decode(publicKeyBase64)
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(encoded)
    val calculated = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    calculated == peerId
}.getOrDefault(false)

/** Trust decision boundary shared by persistent Android storage and JVM tests. */
internal fun interface ClimatePeerTrust {
    fun isTrusted(peerId: String, publicKeyBase64: String): Boolean
}

/**
 * Runs on a mutually authenticated socket before it is closed (application data exchange).
 * Returns a short status text; exceptions mark the session as failed.
 */
internal fun interface ClimatePeerSessionHandler {
    fun onAuthenticated(socket: java.net.Socket, asServer: Boolean): String?
}
