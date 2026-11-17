package de.klimapilot.app

/**
 * Process-wide lock for the two SharedPreferences stores that form the climate-profile
 * persistence layer. All repository/store entry points use this same monitor.
 *
 * The monitor is intentionally reentrant: repository operations may call store operations
 * (and vice versa) while a journal transfer or remote-event application is in progress.
 * Keep network I/O outside this lock.
 */
internal object ClimateProfileStorageLock {
    val monitor: Any = Any()
}
