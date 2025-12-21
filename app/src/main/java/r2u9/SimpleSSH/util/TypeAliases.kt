package r2u9.SimpleSSH.util

import r2u9.SimpleSSH.data.model.ActiveSession
import r2u9.SimpleSSH.data.model.SshConnection

typealias VoidCallback = () -> Unit
typealias StringCallback = (String) -> Unit
typealias ErrorCallback = (String) -> Unit
typealias FloatCallback = (Float) -> Unit

typealias PositionCallback = (x: Float, y: Float) -> Unit

typealias KeyInputCallback = (String) -> Unit
typealias FontSizeCallback = (Float) -> Unit
typealias SizeChangedCallback = (cols: Int, rows: Int) -> Unit

typealias ConnectionCallback = (SshConnection) -> Unit
typealias SessionCallback = (ActiveSession) -> Unit
