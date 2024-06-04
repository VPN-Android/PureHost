package top.nicelee.purehost.vpn

import android.content.Context
import android.net.VpnService
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.TCPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader
import java.nio.ByteBuffer

class VpnViewModel(private val context: Context, private val source: VpnDataSource) : ViewModel() {


    private val _tcpPacketFlow = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1024 * 64,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    val tcpPacketFlow: SharedFlow<Int> = _tcpPacketFlow


    private val _udpPacketFlow = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1024 * 64,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    val udpPacketFlow: SharedFlow<Int> = _udpPacketFlow

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

    suspend fun startProcessVpnPacket(localVpnService: LocalVpnService, vpnService: VpnService, localIP: String) {
        source.startProcessVpnPacket(m_Packet, vpnService, localIP).collect {
            if (it > 0) {
                when (m_IPHeader.protocol) {
                    IPHeader.TCP -> {
//                    _tcpPacketFlow.emit(it)
                        localVpnService.onTCPPacketReceived(source.vpnOutput, m_TCPHeader, m_IPHeader, it)
                    }
                    IPHeader.UDP -> {
//                    _udpPacketFlow.emit(it)
                        localVpnService.onUDPPacketReceived(source.vpnOutput, m_UDPHeader, m_DNSBuffer, m_IPHeader, it)
                    }
                }
            }
        }
    }

    fun getSendOutput() = source.vpnOutput

}