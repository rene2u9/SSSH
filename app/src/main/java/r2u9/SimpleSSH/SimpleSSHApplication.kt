package r2u9.SimpleSSH

import android.app.Application
import com.google.android.material.color.DynamicColors
import r2u9.SimpleSSH.util.AppPreferences

class SimpleSSHApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: SimpleSSHApplication
            private set
    }
}
