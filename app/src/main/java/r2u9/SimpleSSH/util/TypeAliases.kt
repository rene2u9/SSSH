package r2u9.SimpleSSH.util

import r2u9.SimpleSSH.data.model.ActiveSession
import r2u9.SimpleSSH.data.model.SshConnection

/**
 * Type aliases for common callback patterns used throughout the app.
 * Improves code readability and provides semantic meaning to function types.
 */

// Generic callbacks
typealias VoidCallback = () -> Unit
typealias StringCallback = (String) -> Unit
typealias ErrorCallback = (String) -> Unit
typealias FloatCallback = (Float) -> Unit

// Position callbacks
typealias PositionCallback = (x: Float, y: Float) -> Unit

// Terminal callbacks
typealias KeyInputCallback = (String) -> Unit
typealias FontSizeCallback = (Float) -> Unit
typealias SizeChangedCallback = (cols: Int, rows: Int) -> Unit

// Connection callbacks
typealias ConnectionCallback = (SshConnection) -> Unit
typealias SessionCallback = (ActiveSession) -> Unit
