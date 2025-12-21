package r2u9.SimpleSSH

import android.app.Application

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
