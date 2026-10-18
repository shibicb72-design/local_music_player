package com.shibi.musicplayer.domain

/** Why an item did not reach the system trash. */
enum class TrashFailReason { Removable, NoPermission, NotTrashed }

/** What a MediaStore query said about one item after the system reported OK. */
data class ItemCheck(
    val found: Boolean,
    val trashed: Boolean = false,
    val volumeName: String? = null,
    val securityError: Boolean = false,
)

data class TrashFailure(val reason: TrashFailReason, val items: List<TrashItem>)

data class TrashOutcome(val trashed: List<TrashItem>, val failures: List<TrashFailure>) {
    val failedCount: Int get() = failures.sumOf { it.items.size }
}

/** Pure verification of a trash request against per-item MediaStore checks. */
object TrashResult {
    const val PRIMARY_VOLUME = "external_primary"

    /** False only while the row is found, not trashed and had no security error: it may still be in flight. */
    fun isSettled(check: ItemCheck?): Boolean =
        check == null || !check.found || check.trashed || check.securityError

    fun classify(items: List<TrashItem>, checks: Map<String, ItemCheck>): TrashOutcome {
        val trashed = mutableListOf<TrashItem>()
        val failed = mutableMapOf<TrashFailReason, MutableList<TrashItem>>()
        for (item in items) {
            val check = checks[item.uri]
            val reason = when {
                check == null -> null
                check.securityError -> TrashFailReason.NoPermission
                !check.found || check.trashed -> null
                check.volumeName != null && check.volumeName != PRIMARY_VOLUME -> TrashFailReason.Removable
                else -> TrashFailReason.NotTrashed
            }
            if (reason == null) trashed += item else failed.getOrPut(reason) { mutableListOf() } += item
        }
        val failures = TrashFailReason.entries.mapNotNull { reason ->
            failed[reason]?.let { TrashFailure(reason, it) }
        }
        return TrashOutcome(trashed, failures)
    }
}
