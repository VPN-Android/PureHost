package top.nicelee.purehost.vpn

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader

class LocalVpnServiceKT : CoroutineService() {

    companion object {
        val TAG = "LocalVpnServiceKT"
        var instance: LocalVpnServiceKT? = null
    }

    private val viewModel: VpnViewModel by lazy {
        KoinJavaComponent.get(VpnViewModel::class.java)
    }

    val localVpnService = LocalVpnService()
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        instance = this

        localVpnService.onCreate(this)

        serviceScope.launch {
            viewModel.startProcessVpnPacket(localVpnService, this@LocalVpnServiceKT, localVpnService.localIP)
        }

//        serviceScope.launch {
//            viewModel.udpPacketFlow.collect {
//                Log.d(TAG, "udpPacketFlow: $it")
//                localVpnService.onUDPPacketReceived(viewModel.getSendOutput(), viewModel.m_UDPHeader, viewModel.m_DNSBuffer, viewModel.m_IPHeader, it)
//            }
//        }
//
//        serviceScope.launch {
//            viewModel.tcpPacketFlow.collect {
//                Log.d(TAG, "tcpPacketFlow: $it")
//                localVpnService.onTCPPacketReceived(viewModel.getSendOutput(), viewModel.m_TCPHeader, viewModel.m_IPHeader, it)
//            }
//        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        localVpnService.sendUDPPacket(viewModel.getSendOutput(), ipHeader, udpHeader)
    }

    fun stopVPN() {
        localVpnService.stopVPN()
    }
}