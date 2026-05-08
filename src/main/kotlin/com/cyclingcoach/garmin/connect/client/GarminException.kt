package com.cyclingcoach.garmin.connect.client

sealed class GarminException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Credentials were rejected by Garmin SSO. Extends [IllegalArgumentException] for backward compatibility. */
class GarminAuthException(
    message: String,
) : IllegalArgumentException(message)

/** Garmin account has MFA enabled — automated login is not supported. Extends [UnsupportedOperationException] for backward compatibility. */
class GarminMfaRequiredException(
    message: String,
) : UnsupportedOperationException(message)

/** An HTTP call to Garmin returned an unexpected status (not auth-related). */
class GarminApiException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : GarminException(message, cause)

/** A token exchange or refresh operation failed for a non-HTTP reason. */
class GarminTokenException(
    message: String,
    cause: Throwable? = null,
) : GarminException(message, cause)
