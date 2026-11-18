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
}

/** Trust decision boundary shared by persistent Android storage and JVM tests. */
internal fun interface ClimatePeerTrust {
    fun isTrusted(peerId: String, publicKeyBase64: String): Boolean
}
