package com.cyclingcoach.ftp

enum class FtpTestType {
    RAMP_TEST,
    TWENTY_MIN_TEST,
    SIXTY_MIN_TEST,
    /** Detected as an FTP test by name but profile was ambiguous — no FTP update. */
    UNKNOWN,
    /** FTP estimated from previous rides (no structured test performed). */
    ESTIMATED,
}
