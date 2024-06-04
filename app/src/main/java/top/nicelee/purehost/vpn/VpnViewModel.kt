package top.nicelee.purehost.vpn

import android.content.Context
import android.net.VpnService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.TCPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VpnViewModel(private val context: Context, private val source: VpnDataSource) : ViewModel() {

    private val _vpnStatusLiveData = MutableStateFlow<Int>(-1)
    val vpnStatusLiveData: StateFlow<Int> = _vpnStatusLiveData

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

    fun stopVPN() {
        source.stopProcessVpnPacket()
        _vpnStatusLiveData.tryEmit(0)
    }

    fun startVPN() {
        _vpnStatusLiveData.tryEmit(1)
    }

    fun getSendOutput(): FileOutputStream? {
        return source.vpnOutput
    }

}