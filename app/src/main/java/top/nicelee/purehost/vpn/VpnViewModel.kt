package top.nicelee.purehost.vpn

import android.content.Context
import android.net.VpnService
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.TCPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VpnViewModel(private val context: Context, private val source: VpnDataSource) : ViewModel() {

    private val _vpnStatusFlow = MutableStateFlow<Int>(-1)
    val vpnStatusFlow: StateFlow<Int> = _vpnStatusFlow

    private val _vpnSwitchFlow = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val vpnSwitchFlow: SharedFlow<Boolean> = _vpnSwitchFlow

    //收到的IP报文Buffer
    private val m_Packet = ByteArray(1024 * 64)

    //方便解析
    val m_IPHeader: IPHeader by lazy {
        IPHeader(m_Packet, 0)
    }
    val m_TCPHeader: TCPHeader by lazy {
        TCPHeader(m_Packet, 20)
    }
    val m_UDPHeader: UDPHeader by lazy {
        UDPHeader(m_Packet, 20)
    }
    val m_DNSBuffer: ByteBuffer by lazy {
        (ByteBuffer.wrap(m_Packet).position(28) as ByteBuffer).slice()
    }

    suspend fun startProcessVpnPacket(localServerHelper: LocalServerHelper, vpnService: VpnService, localIP: String) {
        _vpnSwitchFlow.tryEmit(true)
        _vpnStatusFlow.tryEmit(1)

        source.startProcessVpnPacket(m_Packet, vpnService, localIP).collect {
            if (it > 0) {
                when (m_IPHeader.protocol) {
                    IPHeader.TCP -> {
//                    _tcpPacketFlow.emit(it)
                        localServerHelper.onTCPPacketReceived(source.vpnOutput, m_TCPHeader, m_IPHeader, it)
                    }
                    IPHeader.UDP -> {
//                    _udpPacketFlow.emit(it)
                        localServerHelper.onUDPPacketReceived(source.vpnOutput, m_UDPHeader, m_DNSBuffer, m_IPHeader, it)
                    }
                }
            }
        }
    }

    fun stopVPN() {
        source.stopProcessVpnPacket()
        _vpnStatusFlow.tryEmit(0)
        val result = _vpnSwitchFlow.tryEmit(false)
        Log.d("VpnViewModel", "tryEmit, _vpnSwitchFlow: $result")
    }

    fun getSendOutput(): FileOutputStream? {
        return source.vpnOutput
    }

}