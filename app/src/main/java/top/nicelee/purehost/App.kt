package top.nicelee.purehost

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import top.nicelee.purehost.vpn.VpnViewModel

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(module {
                single { VpnViewModel(get()) }
            })
        }
    }
}