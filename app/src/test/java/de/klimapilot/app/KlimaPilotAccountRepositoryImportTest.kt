package de.klimapilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KlimaPilotAccountRepositoryImportTest {
    private fun repository(): KlimaPilotAccountRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("klimapilot_accounts", Context.MODE_PRIVATE).edit().clear().commit()
        return KlimaPilotAccountRepository(context)
    }

    private fun importedRegistry(username: String = "bestehend"): KlimaPilotAccountRegistry {
        val account = KlimaPilotAccount.create(
            username, username, "sicheresPasswort123".toCharArray(), KlimaPilotAccount.ADMIN
        )
        return KlimaPilotAccountRegistry(revision = 7, accounts = listOf(account))
    }

    @Test
    fun importsWhenLocalRegistryIsMissing() {
        val repository = repository()
        val imported = importedRegistry()

        assertTrue(repository.replaceEmptyWithImported(imported))
        val loaded = repository.load() as KlimaPilotAccountLoadResult.Loaded
        assertEquals("bestehend", loaded.registry.accounts.single().username)
        assertEquals(7L, loaded.registry.revision)
    }

    @Test
    fun replacesValidEmptyLocalRegistry() {
        val repository = repository()
        assertTrue(repository.saveInitial(KlimaPilotAccountRegistry()))

        assertTrue(repository.replaceEmptyWithImported(importedRegistry()))
        val loaded = repository.load() as KlimaPilotAccountLoadResult.Loaded
        assertEquals("bestehend", loaded.registry.accounts.single().username)
    }

    @Test
    fun doesNotReplaceNonEmptyLocalRegistry() {
        val repository = repository()
        val local = importedRegistry("lokal")
        assertTrue(repository.saveInitial(local))

        assertFalse(repository.replaceEmptyWithImported(importedRegistry("importiert")))
        val loaded = repository.load() as KlimaPilotAccountLoadResult.Loaded
        assertEquals("lokal", loaded.registry.accounts.single().username)
    }

    @Test
    fun doesNotReplaceCorruptLocalRegistry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("klimapilot_accounts", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("klimapilot_account_registry_v1", "{defekt").commit()
        val repository = KlimaPilotAccountRepository(context)

        assertFalse(repository.replaceEmptyWithImported(importedRegistry()))
        assertEquals("{defekt", prefs.getString("klimapilot_account_registry_v1", null))
        assertTrue(repository.load() is KlimaPilotAccountLoadResult.Invalid)
    }
}
