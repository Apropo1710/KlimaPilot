package de.klimapilot.app.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Stable installation identity backed by a non-exportable Android Keystore private key.
 *
 * The peer ID is derived from the public key, not from a network address, MAC address,
 * account name, or mutable device label. This class does not pair with or trust any peer.
 *
 * The optional alias exists to let instrumentation tests model two independent devices.
 * Production callers use the stable default alias.
 */
internal class ClimatePeerIdentityStore(
    private val keyAlias: String = KEY_ALIAS
) : ClimatePeerSigner {
    @Synchronized
    override fun getOrCreateIdentity(): ClimatePeerIdentity {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(keyAlias)) {
            val generator = java.security.KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEY_STORE
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }

        val certificate = keyStore.getCertificate(keyAlias)
            ?: throw IllegalStateException("Geräteschlüssel besitzt kein Zertifikat.")
        val encoded = certificate.publicKey.encoded
        val fingerprintBytes = MessageDigest.getInstance("SHA-256").digest(encoded)
        return ClimatePeerIdentity(
            peerId = fingerprintBytes.toHex(),
            publicKeyBase64 = Base64.encodeToString(encoded, Base64.NO_WRAP),
            fingerprint = fingerprintBytes.toHexGrouped()
        )
    }

    @Synchronized
    override fun sign(challenge: ByteArray): ByteArray {
        require(challenge.isNotEmpty() && challenge.size <= MAX_CHALLENGE_BYTES) {
            "Ungültige Länge der Geräte-Challenge."
        }
        getOrCreateIdentity()
        val privateKey = loadKeyStore().getKey(keyAlias, null)
            ?: throw IllegalStateException("Privater Geräteschlüssel fehlt.")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey as java.security.PrivateKey)
            update(challenge)
            sign()
        }
    }

    override fun verify(publicKeyBase64: String, challenge: ByteArray, signatureBytes: ByteArray): Boolean =
        Companion.verify(publicKeyBase64, challenge, signatureBytes)

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "de.klimapilot.peer.identity.v1"
        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val MAX_CHALLENGE_BYTES = 4096

        internal fun verify(
            publicKeyBase64: String,
            challenge: ByteArray,
            signatureBytes: ByteArray
        ): Boolean {
            if (publicKeyBase64.length > MAX_PUBLIC_KEY_BASE64_CHARS ||
                challenge.isEmpty() || challenge.size > MAX_CHALLENGE_BYTES ||
                signatureBytes.isEmpty() || signatureBytes.size > MAX_SIGNATURE_BYTES
            ) return false

            return runCatching {
                val encoded = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
                require(encoded.isNotEmpty() && encoded.size <= MAX_PUBLIC_KEY_BYTES)
                val publicKey = KeyFactory.getInstance("EC")
                    .generatePublic(X509EncodedKeySpec(encoded))
                require(isSupportedPublicKey(publicKey))
                Signature.getInstance(SIGNATURE_ALGORITHM).run {
                    initVerify(publicKey)
                    update(challenge)
                    verify(signatureBytes)
                }
            }.getOrDefault(false)
        }

        internal fun isSupportedPublicKey(publicKey: java.security.PublicKey): Boolean = runCatching {
            val ecKey = publicKey as? ECPublicKey ?: return false
            val parameters = AlgorithmParameters.getInstance("EC").apply {
                init(ECGenParameterSpec(EC_CURVE))
            }.getParameterSpec(ECParameterSpec::class.java)
            val actual = ecKey.params
            actual.curve.field.fieldSize == parameters.curve.field.fieldSize &&
                actual.curve.a == parameters.curve.a &&
                actual.curve.b == parameters.curve.b &&
                actual.generator.affineX == parameters.generator.affineX &&
                actual.generator.affineY == parameters.generator.affineY &&
                actual.order == parameters.order &&
                actual.cofactor == parameters.cofactor
        }.getOrDefault(false)

        private const val MAX_PUBLIC_KEY_BASE64_CHARS = 2048
        private const val MAX_PUBLIC_KEY_BYTES = 1024
        private const val MAX_SIGNATURE_BYTES = 256
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun ByteArray.toHexGrouped(): String =
    toHex().chunked(4).joinToString(":")
