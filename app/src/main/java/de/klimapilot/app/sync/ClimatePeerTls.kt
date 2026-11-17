package de.klimapilot.app.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * TLS 1.2 for transport encryption with pinned peer identity.
 *
 * The TLS server presents its existing Android Keystore identity certificate.
 * The client pins that certificate to the public key saved during QR pairing.
 * The client then proves possession of its own paired identity key at application
 * level using a fresh server challenge. No TLS client certificate is requested.
 */
internal object ClimatePeerTls {
    private const val KEY_ALIAS = "de.klimapilot.peer.identity.v1"
    private const val TLS_PROTOCOL = "TLSv1.2"
    private const val CHALLENGE_BYTES = 32
    private const val MAX_FIELD_BYTES = 4096

    fun createServerContext(): SSLContext {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(keyStore.containsAlias(KEY_ALIAS)) { "Lokaler Geräteschlüssel fehlt." }
        val keyManagers = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        ).apply { init(keyStore, null) }.keyManagers
        return SSLContext.getInstance(TLS_PROTOCOL).apply {
            init(keyManagers, null, SecureRandom())
        }
    }

    fun createClientContext(expectedPeerPublicKeyBase64: String): SSLContext {
        require(expectedPeerPublicKeyBase64.isNotBlank() && expectedPeerPublicKeyBase64.length <= 2048)
        val expectedKey = decodePeerKey(expectedPeerPublicKeyBase64)
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                throw java.security.cert.CertificateException("Client-Zertifikate werden nicht verwendet.")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                require(chain.isNotEmpty()) { "Gegenstelle hat kein TLS-Zertifikat gesendet." }
                val presented = chain.first()
                presented.checkValidity()
                require(ClimatePeerIdentityStore.isSupportedPublicKey(presented.publicKey)) {
                    "TLS-Zertifikat verwendet keinen unterstützten Geräteschlüssel."
                }
                require(java.security.MessageDigest.isEqual(
                    expectedKey.encoded,
                    presented.publicKey.encoded
                )) { "TLS-Zertifikat gehört nicht zum gekoppelten Gerät." }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance(TLS_PROTOCOL).apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
    }

    /** Server-side one-time challenge; only a locally trusted peer can answer it. */
    fun authenticateClient(socket: SSLSocket, trustedPeers: ClimateTrustedPeerStore): Boolean {
        val challenge = ByteArray(CHALLENGE_BYTES).also(SecureRandom()::nextBytes)
        val output = DataOutputStream(socket.outputStream)
        val input = DataInputStream(socket.inputStream)
        output.writeInt(challenge.size)
        output.write(challenge)
        output.flush()

        val peerId = readBoundedString(input)
        val publicKey = readBoundedString(input)
        val signature = readBoundedBytes(input)
        if (!trustedPeers.isTrusted(peerId, publicKey)) return false
        return ClimatePeerIdentityStore.verify(publicKey, challenge, signature)
    }

    /** Client-side proof of possession in response to the server's fresh challenge. */
    fun authenticateToServer(socket: SSLSocket, identityStore: ClimatePeerIdentityStore) {
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        val challengeLength = input.readInt()
        require(challengeLength == CHALLENGE_BYTES) { "Ungültige Server-Challenge." }
        val challenge = ByteArray(challengeLength)
        input.readFully(challenge)

        val identity = identityStore.getOrCreateIdentity()
        val signature = identityStore.sign(challenge)
        writeBoundedString(output, identity.peerId)
        writeBoundedString(output, identity.publicKeyBase64)
        writeBoundedBytes(output, signature)
        output.flush()
    }

    private fun decodePeerKey(encoded: String): java.security.PublicKey {
        val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        require(bytes.isNotEmpty() && bytes.size <= 1024)
        val key = java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(bytes))
        require(ClimatePeerIdentityStore.isSupportedPublicKey(key))
        return key
    }

    private fun readBoundedString(input: DataInputStream): String {
        val bytes = readBoundedBytes(input)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readBoundedBytes(input: DataInputStream): ByteArray {
        val length = input.readInt()
        require(length in 1..MAX_FIELD_BYTES) { "Ungültige Länge im Peer-Handshake." }
        return ByteArray(length).also(input::readFully)
    }

    private fun writeBoundedString(output: DataOutputStream, value: String) =
        writeBoundedBytes(output, value.toByteArray(Charsets.UTF_8))

    private fun writeBoundedBytes(output: DataOutputStream, value: ByteArray) {
        require(value.isNotEmpty() && value.size <= MAX_FIELD_BYTES)
        output.writeInt(value.size)
        output.write(value)
    }
}
