package com.cyclingcoach.garmin

import java.util.concurrent.CompletableFuture

/**
 * Implemented by each Garmin domain package (activity, weight, …) to provide
 * its own sync logic. The central [GarminSyncService][GarminSyncService]
 * auto-discovers all beans implementing this interface and invokes them.
 */
interface GarminSyncable {
    /** Human-readable label used in log messages. */
    val name: String

    fun sync(): CompletableFuture<Void>
}
