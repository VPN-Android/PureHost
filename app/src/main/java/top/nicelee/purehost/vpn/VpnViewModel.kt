package top.nicelee.purehost.vpn

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader

class VpnViewModel(private val context: Context, private val source: VpnDataSource) : ViewModel() {

    private val _vpnStatusFlow = MutableStateFlow(-1)
    val vpnStatusFlow: StateFlow<Int> = _vpnStatusFlow

    private val _vpnSwitchFlow = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val vpnSwitchFlow: SharedFlow<Boolean> = _vpnSwitchFlow

    private val localServerHelper = LocalServerHelper()

    //收到的IP报文Buffer
    private val m_Packet = ByteArray(1024 * 64)

    suspend fun startProcessVpnPacket(vpnService: LocalVpnServiceKT, localIP: String) {
        _vpnSwitchFlow.tryEmit(true)
        _vpnStatusFlow.tryEmit(1)

        this.localServerHelper.createServer(vpnService, m_Packet)

        source.startProcessVpnPacket(m_Packet, vpnService, localIP).collect {
            if (it > 0) {
                when (m_Packet[9]) { // IPHeader: m_Packet[m_Offset + offset_proto]
                    IPHeader.TCP -> {
                        localServerHelper.onTCPPacketReceived(source.vpnOutput, it)
                    }
                    IPHeader.UDP -> {
                        localServerHelper.onUDPPacketReceived(source.vpnOutput, it)
                    }
                }
            }
        }
    }

    fun stopVPN() {
        this.localServerHelper.stop()

        source.stopProcessVpnPacket()
        _vpnStatusFlow.tryEmit(0)
        val result = _vpnSwitchFlow.tryEmit(false)
        Log.d("VpnViewModel", "tryEmit, _vpnSwitchFlow: $result")
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        this.localServerHelper.sendUDPPacket(source.vpnOutput, ipHeader, udpHeader)
    }

}