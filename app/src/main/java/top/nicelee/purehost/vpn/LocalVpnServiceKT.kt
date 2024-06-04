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
        val TAG = "LocalVpnServiceKT"
    }

    private val viewModel: VpnViewModel by lazy {
        KoinJavaComponent.get(VpnViewModel::class.java)
    }

    private val localVpnService = LocalVpnService()
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        localVpnService.onCreate(this)

        serviceScope.launch {
            viewModel.startProcessVpnPacket(localVpnService, this@LocalVpnServiceKT, localVpnService.localIP)
            viewModel.startVPN()
        }

        serviceScope.launch {
            viewModel.vpnStatusLiveData.collectLatest {
                if (it == 0) {
                    localVpnService.stopVPN()
                    stopSelf()
                }
            }
        }

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        localVpnService.sendUDPPacket(viewModel.getSendOutput(), ipHeader, udpHeader)
    }
}