package r2u9.SimpleSSH.util

/**
 * Application-wide constants for SimpleSSH.
 * Centralizes magic numbers and configuration values for easier maintenance.
 */
object Constants {

    /**
     * Time-related constants in milliseconds.
     */
    object Time {
        /** Interval for updating session duration display */
        const val SESSION_UPDATE_INTERVAL_MS = 1000L

        /** Timeout for TCP socket connection checks */
        const val HOST_REACHABILITY_TIMEOUT_MS = 3000

        /** Delay after sending WOL packet before attempting connection */
        const val WOL_BOOT_DELAY_MS = 3000L

        /** Cursor blink interval */
        const val CURSOR_BLINK_INTERVAL_MS = 530L

        /** Milliseconds per second (for duration calculations) */
        const val MS_PER_SECOND = 1000L
        const val MS_PER_MINUTE = 60_000L
        const val MS_PER_HOUR = 3_600_000L
    }

    /**
     * Terminal display configuration.
     */
    object Terminal {
        /** Minimum font size in sp */
        const val MIN_FONT_SIZE_SP = 8f

        /** Maximum font size in sp */
        const val MAX_FONT_SIZE_SP = 32f

        /** Default font size in sp */
        const val DEFAULT_FONT_SIZE_SP = 14f

        /** Default scrollback buffer lines */
        const val DEFAULT_SCROLLBACK_LINES = 2000

        /** Maximum scrollback buffer lines */
        const val MAX_SCROLLBACK_LINES = 10000

        /** Default PTY columns */
        const val DEFAULT_PTY_COLUMNS = 80

        /** Default PTY rows */
        const val DEFAULT_PTY_ROWS = 24

        /** Terminal type for PTY allocation */
        const val PTY_TERM_TYPE = "xterm-256color"

        /** Buffer size for reading from SSH channel */
        const val READ_BUFFER_SIZE = 8192

        /** Maximum escape sequence buffer length */
        const val MAX_ESCAPE_BUFFER_LENGTH = 4096

        /** Tab stop interval */
        const val TAB_STOP_INTERVAL = 8

        /** Maximum tab stop position */
        const val MAX_TAB_STOP = 320

        /** Fling velocity divisor for scroll calculation */
        const val FLING_VELOCITY_DIVISOR = 500

        /** Maximum lines to scroll per fling */
        const val MAX_FLING_SCROLL_LINES = 20
    }

    /**
     * Network configuration.
     */
    object Network {
        /** Default SSH port */
        const val DEFAULT_SSH_PORT = 22

        /** Default WOL port */
        const val DEFAULT_WOL_PORT = 9

        /** Default broadcast address for WOL */
        const val DEFAULT_BROADCAST_ADDRESS = "255.255.255.255"

        /** Magic packet repetitions (MAC address is repeated 16 times) */
        const val WOL_MAC_REPETITIONS = 16

        /** Magic packet header bytes (6 x 0xFF) */
        const val WOL_HEADER_BYTES = 6
    }

    /**
     * Notification constants.
     */
    object Notification {
        /** Base ID for session notifications */
        const val SESSION_NOTIFICATION_ID_BASE = 100

        /** Channel ID for SSH session notifications */
        const val CHANNEL_ID_SSH_SESSION = "ssh_session"
    }

    /**
     * UI-related constants.
     */
    object UI {
        /** Selection highlight alpha (0-255) */
        const val SELECTION_ALPHA = 100

        /** Selection highlight RGB components */
        const val SELECTION_RED = 100
        const val SELECTION_GREEN = 150
        const val SELECTION_BLUE = 255
    }

    /**
     * ANSI/VT100 escape sequences.
     */
    object EscapeSequences {
        const val ESC = "\u001b"
        const val DEL = "\u007f"

        // Cursor movement
        const val CURSOR_UP = "\u001b[A"
        const val CURSOR_DOWN = "\u001b[B"
        const val CURSOR_RIGHT = "\u001b[C"
        const val CURSOR_LEFT = "\u001b[D"
        const val CURSOR_HOME = "\u001b[H"
        const val CURSOR_END = "\u001b[F"

        // Page navigation
        const val PAGE_UP = "\u001b[5~"
        const val PAGE_DOWN = "\u001b[6~"
        const val INSERT = "\u001b[2~"
        const val DELETE = "\u001b[3~"

        // Function keys (F1-F4 use SS3 format)
        const val F1 = "\u001bOP"
        const val F2 = "\u001bOQ"
        const val F3 = "\u001bOR"
        const val F4 = "\u001bOS"

        // Function keys (F5-F12 use CSI format)
        const val F5 = "\u001b[15~"
        const val F6 = "\u001b[17~"
        const val F7 = "\u001b[18~"
        const val F8 = "\u001b[19~"
        const val F9 = "\u001b[20~"
        const val F10 = "\u001b[21~"
        const val F11 = "\u001b[23~"
        const val F12 = "\u001b[24~"
    }
}
