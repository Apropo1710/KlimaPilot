package de.klimapilot.app.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.AlgorithmParameters
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory

/**
 * Stable installation identity backed by a non-exportable Android Keystore private key.
 *
 * The peer ID is derived from the public key, not from a network address, MAC address,
 * account name, or mutable device label. This class does not pair with or trust any peer.
 */
internal class ClimatePeerIdentityStore {
    data class Identity(
        val peerId: String,
        val publicKeyBase64: String,
        val fingerprint: String
    )

    @Synchronized
    fun getOrCreateIdentity(): Identity {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEY_STORE
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                    // Application identity signatures use SHA-256withECDSA.
                    // TLS server authentication uses the same standard Keystore key.
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }

        val certificate = keyStore.getCertificate(KEY_ALIAS)
            ?: throw IllegalStateException("Geräteschlüssel besitzt kein Zertifikat.")
        val publicKey = certificate.publicKey
        val encoded = publicKey.encoded
        val fingerprintBytes = MessageDigest.getInstance("SHA-256").digest(encoded)
        val peerId = fingerprintBytes.toHex()
        return Identity(
            peerId = peerId,
            publicKeyBase64 = Base64.encodeToString(encoded, Base64.NO_WRAP),
            fingerprint = fingerprintBytes.toHexGrouped()
        )
    }

    @Synchronized
    fun sign(challenge: ByteArray): ByteArray {
        require(challenge.isNotEmpty() && challenge.size <= MAX_CHALLENGE_BYTES) {
            "Ungültige Länge der Geräte-Challenge."
        }
        getOrCreateIdentity()
        val keyStore = loadKeyStore()
        val privateKey = keyStore.getKey(KEY_ALIAS, null)
            ?: throw IllegalStateException("Privater Geräteschlüssel fehlt.")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey as java.security.PrivateKey)
            update(challenge)
            sign()
        }
    }

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

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun ByteArray.toHexGrouped(): String =
    toHex().chunked(4).joinToString(":")
