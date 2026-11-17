package de.klimapilot.app

import org.json.JSONObject

/**
 * Transport-neutral profile event application.
 *
 * Both ioBroker and paired-phone transports must pass received events through this
 * engine so validation, ordering, conflict resolution and durable version recording
 * stay identical.
 */
internal class ClimateProfileSyncEngine {
    /**
     * Validation failures are isolated to their individual event. Storage and persistence
     * failures deliberately escape so the sync round stops without pretending it succeeded.
     */
    internal fun applyRemoteEvents(
        events: List<JSONObject>,
        deviceMap: Map<String, String>,
        store: ClimateProfileSyncStore,
        repository: ClimateProfileRepository
    ): ClimateProfileSyncClient.RemoteApplySummary {
        var applied = 0
        var skipped = 0
        var rejected = 0
        var firstRejectedReason: String? = null
        val sorted = events.sortedWith(compareBy<JSONObject> { it.optLong("logicalClock", 0L) }
            .thenBy { it.optString("installationId") }.thenBy { it.optLong("sequence", 0L) })
        for (event in sorted) {
            val deviceId = deviceMap[event.optString("deviceKey")]
            if (deviceId == null) {
                skipped++
                continue
            }
            try {
                if (applyRemoteEventIfNewer(event, deviceId, store, repository)) applied++ else skipped++
            } catch (error: InvalidRemoteProfileEventException) {
                rejected++
                if (firstRejectedReason == null) firstRejectedReason = error.message ?: "Ungültige Remote-Daten."
            }
        }
        return ClimateProfileSyncClient.RemoteApplySummary(applied, skipped, rejected, firstRejectedReason)
    }

    /** Applies one remote event only after local journal entries have received comparable versions. */
    internal fun applyRemoteEventIfNewer(
        event: JSONObject,
        deviceId: String,
        store: ClimateProfileSyncStore,
        repository: ClimateProfileRepository
    ): Boolean = synchronized(ClimateProfileStorageLock.monitor) {
        // This must happen for every event, not just once at sync start: local edits may
        // be committed while upload/read network operations are in flight.
        repository.flushPendingSyncJournal(store)
        if (!store.isRemoteEventNewer(event)) {
            false
        } else {
            try {
                when (event.optString("type")) {
                    "selection" -> {
                        val payload = event.optJSONObject("profile")
                            ?: throw IllegalArgumentException("Aktive Profilauswahl enthält keine Daten.")
                        val activeId = if (payload.isNull("activeProfileId")) null
                            else payload.optString("activeProfileId").takeIf { it.isNotBlank() }
                        check(store.saveActiveProfileId(deviceId, activeId)) {
                            "Aktive Profilauswahl konnte nicht lokal gespeichert werden."
                        }
                    }
                    "individual" -> {
                        val payload = event.optJSONObject("profile")
                            ?: throw IllegalArgumentException("Individuelle Einstellungen enthalten keine Daten.")
                        check(store.applyRemoteIndividualProfile(deviceId, payload)) {
                            "Individuelle Einstellungen konnten nicht lokal gespeichert werden."
                        }
                    }
                    else -> {
                        repository.applyRemoteEvent(event, deviceId)
                        if (event.optString("type") == "delete" &&
                            store.activeProfileId(deviceId) == event.optString("profileId")) {
                            check(store.saveActiveProfileId(deviceId, null)) {
                                "Gelöschte aktive Profilauswahl konnte nicht zurückgesetzt werden."
                            }
                        }
                    }
                }
            } catch (error: IllegalArgumentException) {
                // Only validation performed while interpreting the remote payload is isolated.
                // Journal/store failures happen before this block or use IllegalStateException.
                throw InvalidRemoteProfileEventException(error.message ?: "Ungültige Remote-Daten.", error)
            }
            check(store.recordRemoteEvent(event)) { "Versionsstand konnte nicht lokal gespeichert werden." }
            true
        }
    }


}
