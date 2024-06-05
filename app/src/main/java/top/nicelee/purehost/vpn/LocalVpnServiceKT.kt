package top.nicelee.purehost.vpn

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader

class LocalVpnServiceKT : CoroutineService() {

    companion object {
        const val TAG = "LocalVpnServiceKT"
    }

    private val viewModel: VpnViewModel by lazy {
        KoinJavaComponent.get(VpnViewModel::class.java)
    }

    private val localServerHelper = LocalServerHelper()
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        localServerHelper.createServer(this)

        serviceScope.launch {
            viewModel.vpnStatusFlow.collectLatest {
                Log.d(TAG, "vpnStatusFlow: $it")
            }
        }

        serviceScope.launch {
            viewModel.vpnSwitchFlow.collect {
                Log.d(TAG, "vpnSwitchFlow: $it")
                if (!it) {
                    localServerHelper.stop()
                    stopSelf()
                }
            }
        }

        serviceScope.launch {
            viewModel.startProcessVpnPacket(localServerHelper, this@LocalVpnServiceKT, localServerHelper.localIP)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        localServerHelper.sendUDPPacket(viewModel.getSendOutput(), ipHeader, udpHeader)
    }
}