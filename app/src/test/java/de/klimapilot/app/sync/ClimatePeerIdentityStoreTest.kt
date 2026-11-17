package de.klimapilot.app.sync

import android.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class ClimatePeerIdentityStoreTest {
    @Test
    fun verifiesSignatureOnlyForOriginalChallenge() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val challenge = ByteArray(32) { (it * 7).toByte() }
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(challenge)
            sign()
        }
        val publicKey = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)

        assertTrue(ClimatePeerIdentityStore.verify(publicKey, challenge, signature))
        assertFalse(ClimatePeerIdentityStore.verify(publicKey, "different".toByteArray(), signature))
    }

    @Test
    fun rejectsMalformedAndOversizedPeerKeyMaterial() {
        assertFalse(ClimatePeerIdentityStore.verify("not-a-key", byteArrayOf(1), byteArrayOf(1)))
        assertFalse(ClimatePeerIdentityStore.verify("A".repeat(3000), byteArrayOf(1), byteArrayOf(1)))
        assertFalse(ClimatePeerIdentityStore.verify("", ByteArray(0), byteArrayOf(1)))
    }
}
