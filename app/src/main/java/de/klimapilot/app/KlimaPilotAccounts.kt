package de.klimapilot.app

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Gemeinsame KlimaPilot-Konten. Passwörter werden ausschließlich als PBKDF2-Hash gespeichert. */
internal data class KlimaPilotAccount(
    val username: String,
    val displayName: String,
    val role: String,
    val salt: String,
    val passwordHash: String,
    val iterations: Int = PASSWORD_ITERATIONS,
    val createdAt: Long = System.currentTimeMillis(),
    val fontScale: Float = 1.0f
) {
    fun toJson() = JSONObject().apply {
        put("username", username)
        put("displayName", displayName)
        put("role", role)
        put("salt", salt)
        put("passwordHash", passwordHash)
        put("iterations", iterations)
        put("createdAt", createdAt)
        put("fontScale", fontScale.coerceIn(0.85f, 1.5f))
    }

    companion object {
        const val ADMIN = "admin"
        const val USER = "user"
        const val PASSWORD_ITERATIONS = 120_000

        fun create(username: String, displayName: String, password: CharArray, role: String): KlimaPilotAccount {
            val normalized = username.trim()
            require(normalized.matches(Regex("[A-Za-z0-9_.-]{3,32}"))) {
                "Benutzername: 3–32 Zeichen (Buchstaben, Zahlen, . _ -)."
            }
            require(password.size >= 8) { "Das Passwort muss mindestens 8 Zeichen lang sein." }
            require(role == ADMIN || role == USER) { "Ungültige Benutzerrolle." }
            val saltBytes = ByteArray(16).also(SecureRandom()::nextBytes)
            val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
            val hash = derive(password, saltBytes, PASSWORD_ITERATIONS)
            return KlimaPilotAccount(normalized, displayName.trim().ifBlank { normalized }, role, salt, hash)
        }

        fun fromJson(json: JSONObject): KlimaPilotAccount? {
            return try {
            val username = json.getString("username")
            val role = json.getString("role")
            val iterations = json.getInt("iterations")
            if (!username.matches(Regex("[A-Za-z0-9_.-]{3,32}")) ||
                role !in setOf(ADMIN, USER) || iterations !in 50_000..1_000_000) return null
            val salt = json.getString("salt")
            val hash = json.getString("passwordHash")
            if (Base64.decode(salt, Base64.NO_WRAP).size != 16 ||
                Base64.decode(hash, Base64.NO_WRAP).size != 32) return null
            KlimaPilotAccount(username, json.optString("displayName", username), role, salt, hash,
                iterations, json.optLong("createdAt", 0L),
                json.optDouble("fontScale", 1.0).toFloat().coerceIn(0.85f, 1.5f))
        } catch (_: Exception) { null }
        }

        fun verify(account: KlimaPilotAccount, password: CharArray): Boolean = try {
            val candidate = derive(password, Base64.decode(account.salt, Base64.NO_WRAP), account.iterations)
            MessageDigest.isEqual(
                Base64.decode(candidate, Base64.NO_WRAP),
                Base64.decode(account.passwordHash, Base64.NO_WRAP)
            )
        } catch (_: Exception) { false }

        private fun derive(password: CharArray, salt: ByteArray, iterations: Int): String {
            val spec = PBEKeySpec(password, salt, iterations, 256)
            return try {
                val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally { spec.clearPassword() }
        }
    }
}

internal data class KlimaPilotAccountRegistry(
    val revision: Long = 0,
    val accounts: List<KlimaPilotAccount> = emptyList()
) {
    fun toJson() = JSONObject().apply {
        put("schemaVersion", 1)
        put("revision", revision)
        put("accounts", JSONArray().also { array -> accounts.forEach { array.put(it.toJson()) } })
    }

    fun find(username: String): KlimaPilotAccount? =
        accounts.firstOrNull { it.username.equals(username.trim(), ignoreCase = true) }

    fun authenticate(username: String, password: CharArray): KlimaPilotAccount? =
        find(username)?.takeIf { KlimaPilotAccount.verify(it, password) }

    fun createInitialAdmin(username: String, displayName: String, password: CharArray): KlimaPilotAccountRegistry {
        require(accounts.isEmpty()) { "Der Administrator wurde bereits eingerichtet." }
        val admin = KlimaPilotAccount.create(username, displayName, password, KlimaPilotAccount.ADMIN)
        return copy(revision = revision + 1, accounts = listOf(admin))
    }

    fun addAccount(actor: KlimaPilotAccount, username: String, displayName: String, password: CharArray, role: String): KlimaPilotAccountRegistry {
        require(actor.role == KlimaPilotAccount.ADMIN) { "Nur Administratoren dürfen Benutzer verwalten." }
        require(find(username) == null) { "Dieser Benutzername ist bereits vergeben." }
        val account = KlimaPilotAccount.create(username, displayName, password, role)
        return copy(revision = revision + 1, accounts = accounts + account)
    }

    fun resetPassword(actor: KlimaPilotAccount, username: String, password: CharArray): KlimaPilotAccountRegistry {
        require(actor.role == KlimaPilotAccount.ADMIN || actor.username.equals(username, ignoreCase = true)) {
            "Keine Berechtigung, dieses Passwort zu ändern."
        }
        val target = find(username) ?: throw IllegalArgumentException("Benutzer nicht gefunden.")
        val replacement = KlimaPilotAccount.create(target.username, target.displayName, password, target.role)
        return copy(revision = revision + 1, accounts = accounts.map {
            if (it.username.equals(target.username, ignoreCase = true)) replacement.copy(fontScale = target.fontScale) else it
        })
    }

    fun updateFontScale(actor: KlimaPilotAccount, scale: Float): KlimaPilotAccountRegistry {
        require(scale in 0.85f..1.5f) { "Ungültige Schriftgröße." }
        val target = find(actor.username) ?: throw IllegalArgumentException("Benutzer nicht gefunden.")
        return copy(revision = revision + 1, accounts = accounts.map {
            if (it.username.equals(target.username, ignoreCase = true)) it.copy(fontScale = scale) else it
        })
    }

    fun deleteAccount(actor: KlimaPilotAccount, username: String): KlimaPilotAccountRegistry {
        require(actor.role == KlimaPilotAccount.ADMIN) { "Nur Administratoren dürfen Benutzer löschen." }
        val target = find(username) ?: throw IllegalArgumentException("Benutzer nicht gefunden.")
        require(target.role != KlimaPilotAccount.ADMIN || accounts.count { it.role == KlimaPilotAccount.ADMIN } > 1) {
            "Der letzte Administrator kann nicht gelöscht werden."
        }
        require(!actor.username.equals(target.username, ignoreCase = true)) {
            "Das eigene angemeldete Konto kann nicht gelöscht werden."
        }
        return copy(revision = revision + 1, accounts = accounts.filterNot {
            it.username.equals(target.username, ignoreCase = true)
        })
    }

    companion object {
        fun fromJson(json: JSONObject): KlimaPilotAccountRegistry? {
            if (json.optInt("schemaVersion", -1) != 1) return null
            val array = json.optJSONArray("accounts") ?: return null
            val accounts = (0 until array.length()).map { index ->
                KlimaPilotAccount.fromJson(array.optJSONObject(index) ?: return null) ?: return null
            }
            if (accounts.map { it.username.lowercase() }.distinct().size != accounts.size) return null
            return KlimaPilotAccountRegistry(json.optLong("revision", 0L).coerceAtLeast(0L), accounts)
        }
    }
}

internal sealed interface KlimaPilotAccountLoadResult {
    data class Loaded(val registry: KlimaPilotAccountRegistry) : KlimaPilotAccountLoadResult
    data object Missing : KlimaPilotAccountLoadResult
    data class Invalid(val backupSaved: Boolean) : KlimaPilotAccountLoadResult
}

/** Lokaler Kontenspeicher. Die alte ioBroker-Ablage wird ausschließlich einmalig migriert. */
internal class KlimaPilotAccountRepository(context: Context) {
    companion object {
        const val LEGACY_STATE_ID = "0_userdata.0.KlimaPilot.accounts"
        private const val LOCAL_STATE_KEY = "klimapilot_account_registry_v1"
        private const val CORRUPT_BACKUP_PREFIX = "klimapilot_account_registry_corrupt_backup_"
    }

    private val prefs = context.applicationContext.getSharedPreferences("klimapilot_accounts", Context.MODE_PRIVATE)
    private val lock = Any()

    /**
     * Fehlende Daten sind ausschließlich dann "Missing", wenn der Schlüssel wirklich nicht existiert.
     * Vorhandene, aber nicht lesbare Daten werden gesichert und niemals als leere Registry behandelt.
     */
    fun load(): KlimaPilotAccountLoadResult = synchronized(lock) {
        if (!prefs.contains(LOCAL_STATE_KEY)) return@synchronized KlimaPilotAccountLoadResult.Missing

        val stored = prefs.all[LOCAL_STATE_KEY]
        if (stored !is String) {
            return@synchronized KlimaPilotAccountLoadResult.Invalid(backupCorruptValue(stored))
        }

        val registry = runCatching {
            KlimaPilotAccountRegistry.fromJson(JSONObject(stored))
        }.getOrNull()

        if (registry != null) KlimaPilotAccountLoadResult.Loaded(registry)
        else KlimaPilotAccountLoadResult.Invalid(backupCorruptValue(stored))
    }

    /** Sichert den unveränderten Rohtext unter einem inhaltsabhängigen Schlüssel. */
    private fun backupCorruptValue(value: Any?): Boolean {
        val backupValue = if (value is String) value else
            "<ungültiger gespeicherter Wert: ${value?.javaClass?.name ?: "null"}> ${value ?: ""}"
        val digestInput = "${value?.javaClass?.name ?: "null"}:$backupValue"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(digestInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val backupKey = CORRUPT_BACKUP_PREFIX + digest
        if (prefs.contains(backupKey)) return true
        return prefs.edit().putString(backupKey, backupValue).commit()
    }

    /** Initialisierung/Migration darf nur schreiben, wenn lokal tatsächlich noch kein Schlüssel existiert. */
    fun saveInitial(registry: KlimaPilotAccountRegistry): Boolean = synchronized(lock) {
        if (prefs.contains(LOCAL_STATE_KEY)) return@synchronized false
        prefs.edit().putString(LOCAL_STATE_KEY, registry.toJson().toString()).commit()
    }

    /**
     * Übernimmt eine vorhandene ioBroker-Registry nur dann, wenn lokal noch keine
     * Registry existiert oder die lokale Registry gültig und leer ist.
     * Beschädigte oder bereits befüllte lokale Daten bleiben unangetastet.
     */
    fun replaceEmptyWithImported(registry: KlimaPilotAccountRegistry): Boolean = synchronized(lock) {
        if (registry.accounts.isEmpty()) return@synchronized false

        if (!prefs.contains(LOCAL_STATE_KEY)) {
            return@synchronized prefs.edit()
                .putString(LOCAL_STATE_KEY, registry.toJson().toString())
                .commit()
        }

        val stored = prefs.all[LOCAL_STATE_KEY] as? String ?: return@synchronized false
        val local = runCatching {
            KlimaPilotAccountRegistry.fromJson(JSONObject(stored))
        }.getOrNull() ?: return@synchronized false

        if (local.accounts.isNotEmpty()) return@synchronized false

        prefs.edit().putString(LOCAL_STATE_KEY, registry.toJson().toString()).commit()
    }

    fun save(expectedRevision: Long, registry: KlimaPilotAccountRegistry, callback: (Boolean, String?) -> Unit) {
        val result = synchronized(lock) {
            when (val loaded = load()) {
                is KlimaPilotAccountLoadResult.Loaded -> {
                    val current = loaded.registry
                    when {
                        current.revision != expectedRevision ->
                            false to "Die lokale Kontenliste wurde zwischenzeitlich geändert. Bitte erneut laden."
                        registry.revision != expectedRevision + 1 ->
                            false to "Ungültige Versionsnummer der Kontenliste."
                        else -> prefs.edit().putString(LOCAL_STATE_KEY, registry.toJson().toString()).commit() to null
                    }
                }
                KlimaPilotAccountLoadResult.Missing ->
                    false to "Die lokale Kontenliste ist nicht mehr vorhanden. Bitte erneut laden."
                is KlimaPilotAccountLoadResult.Invalid ->
                    false to "Die vorhandenen Kontendaten sind beschädigt. Sie wurden nicht überschrieben."
            }
        }
        callback(result.first, result.second ?: if (result.first) null else "Konten konnten nicht lokal gespeichert werden.")
    }
}

@Composable
internal fun KlimaPilotAccountLoginScreen(
    registry: KlimaPilotAccountRegistry?,
    username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    passwordConfirmation: String, onPasswordConfirmation: (String) -> Unit,
    displayName: String, onDisplayName: (String) -> Unit,
    loading: Boolean, status: String,
    ioBrokerAddress: String, onIoBrokerAddress: (String) -> Unit,
    onLoadExistingAccounts: () -> Unit,
    onReload: () -> Unit, onSubmit: () -> Unit
) {
    val setup = registry?.accounts?.isEmpty() == true
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF1769AA), modifier = Modifier.size(62.dp))
        Spacer(Modifier.height(12.dp))
        Text("KlimaPilot", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                registry == null -> "KlimaPilot-Konten werden geladen …"
                setup -> "Ersteinrichtung · Administrator erstellen"
                else -> "Mit deinem KlimaPilot-Konto anmelden"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        if (registry == null) {
            if (loading) CircularProgressIndicator()
            else Button(onClick = onReload, modifier = Modifier.fillMaxWidth()) { Text("Konten erneut laden") }
        } else {
            OutlinedTextField(username, onUsername, label = { Text("Benutzername") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (setup) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(displayName, onDisplayName, label = { Text("Anzeigename (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                password, onPassword, label = { Text(if (setup) "Passwort (mind. 8 Zeichen)" else "Passwort") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
            )
            if (setup) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    passwordConfirmation, onPasswordConfirmation, label = { Text("Passwort wiederholen") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showConfirmation) VisualTransformation.None else PasswordVisualTransformation()
                )
                if (passwordConfirmation.isNotEmpty() && password != passwordConfirmation) {
                    Text("Die Passwörter stimmen nicht überein.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSubmit,
                enabled = !loading && username.isNotBlank() && password.isNotBlank() &&
                    (!setup || (password.length >= 8 && passwordConfirmation.isNotBlank() && password == passwordConfirmation)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Bitte warten …" else if (setup) "Administrator erstellen" else "Anmelden")
            }
            if (setup) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text("Mit vorhandenem Benutzerkonto anmelden", fontWeight = FontWeight.SemiBold)
                Text(
                    "Gib die WebSocket-Adresse deines ioBroker ein. KlimaPilot lädt die dort bereits gespeicherte Kontenliste. Anschließend kannst du dich mit deinem bisherigen Benutzernamen und Passwort anmelden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
                OutlinedTextField(
                    value = ioBrokerAddress,
                    onValueChange = onIoBrokerAddress,
                    label = { Text("ioBroker-WebSocket-Adresse") },
                    placeholder = { Text("ws://192.168.178.54:8084") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onLoadExistingAccounts,
                    enabled = !loading && ioBrokerAddress.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Vorhandene Konten werden geladen …" else "Vorhandene Konten laden")
                }
            }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(status, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun KlimaPilotAccountSettingsScreen(
    account: KlimaPilotAccount,
    accounts: List<KlimaPilotAccount>,
    status: String,
    onBack: () -> Unit,
    onSwitchAccount: () -> Unit,
    onAddAccount: (String, String, String, String) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirmation by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf(KlimaPilotAccount.USER) }
    var resetTarget by remember { mutableStateOf<KlimaPilotAccount?>(null) }
    var resetPassword by remember { mutableStateOf("") }
    var resetConfirmation by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<KlimaPilotAccount?>(null) }
    var ownPassword by remember { mutableStateOf("") }
    var ownPasswordConfirmation by remember { mutableStateOf("") }
    var showOwnPassword by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück") }
            Column(Modifier.weight(1f)) {
                Text("Benutzerkonto", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Konten und Anmeldung", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(account.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${account.username} · ${if (account.role == KlimaPilotAccount.ADMIN) "Administrator" else "Benutzer"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Dein KlimaPilot-Konto wird lokal auf diesem Gerät gespeichert.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                OutlinedButton(onClick = { ownPassword = ""; ownPasswordConfirmation = ""; showOwnPassword = true },
                    modifier = Modifier.fillMaxWidth()) { Text("Eigenes Passwort ändern") }
                OutlinedButton(onClick = onSwitchAccount, modifier = Modifier.fillMaxWidth()) { Text("Benutzer wechseln") }
            }
        }
        if (account.role == KlimaPilotAccount.ADMIN) {
            Text("Benutzerverwaltung · ${accounts.size} Konten", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            accounts.forEach { item ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.displayName, fontWeight = FontWeight.SemiBold)
                            Text("${item.username} · ${if (item.role == KlimaPilotAccount.ADMIN) "Admin" else "Benutzer"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        TextButton(onClick = { resetTarget = item; resetPassword = ""; resetConfirmation = "" }) { Text("Passwort") }
                        if (!item.username.equals(account.username, ignoreCase = true)) {
                            TextButton(onClick = { deleteTarget = item }) { Text("Löschen") }
                        }
                    }
                }
            }
            Button(onClick = {
                newUsername = ""; newDisplayName = ""; newPassword = ""; newPasswordConfirmation = ""
                newRole = KlimaPilotAccount.USER; showAdd = true
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ManageAccounts, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Benutzer hinzufügen")
            }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    if (showOwnPassword) AlertDialog(
        onDismissRequest = { showOwnPassword = false },
        title = { Text("Eigenes Passwort ändern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ownPassword, { ownPassword = it }, label = { Text("Neues Passwort (mind. 8 Zeichen)") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(ownPasswordConfirmation, { ownPasswordConfirmation = it }, label = { Text("Passwort wiederholen") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = ownPassword.length >= 8 && ownPassword == ownPasswordConfirmation, onClick = {
                onResetPassword(account.username, ownPassword); showOwnPassword = false
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = { showOwnPassword = false }) { Text("Abbrechen") } }
    )

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("KlimaPilot-Benutzer anlegen") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newUsername, { newUsername = it }, label = { Text("Benutzername") }, singleLine = true)
                OutlinedTextField(newDisplayName, { newDisplayName = it }, label = { Text("Anzeigename") }, singleLine = true)
                OutlinedTextField(newPassword, { newPassword = it }, label = { Text("Passwort (mind. 8 Zeichen)") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(newPasswordConfirmation, { newPasswordConfirmation = it }, label = { Text("Passwort wiederholen") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("Rolle")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = newRole == KlimaPilotAccount.USER, onClick = { newRole = KlimaPilotAccount.USER }, label = { Text("Benutzer") })
                    FilterChip(selected = newRole == KlimaPilotAccount.ADMIN, onClick = { newRole = KlimaPilotAccount.ADMIN }, label = { Text("Administrator") })
                }
                if (newPasswordConfirmation.isNotEmpty() && newPassword != newPasswordConfirmation) {
                    Text("Die Passwörter stimmen nicht überein.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = newUsername.isNotBlank() && newPassword.length >= 8 &&
                newPassword == newPasswordConfirmation, onClick = {
                onAddAccount(newUsername, newDisplayName, newPassword, newRole); showAdd = false
            }) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Abbrechen") } }
    )

    resetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            title = { Text("Passwort zurücksetzen") },
            text = {
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Neues Passwort für ${target.username}")
                    OutlinedTextField(resetPassword, { resetPassword = it }, label = { Text("Neues Passwort") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(resetConfirmation, { resetConfirmation = it }, label = { Text("Passwort wiederholen") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = resetPassword.length >= 8 && resetPassword == resetConfirmation, onClick = {
                    onResetPassword(target.username, resetPassword); resetTarget = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { resetTarget = null }) { Text("Abbrechen") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Benutzer wirklich löschen?") },
            text = { Text("Das Konto „${target.username}“ wird dauerhaft aus diesem Gerät entfernt.") },
            confirmButton = { TextButton(onClick = { onDeleteAccount(target.username); deleteTarget = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") } }
        )
    }
}
