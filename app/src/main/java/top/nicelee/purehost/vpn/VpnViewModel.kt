package top.nicelee.purehost.vpn

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader

class VpnViewModel(private val source: VpnDataSource) : ViewModel() {

    private val _vpnStatusFlow = MutableStateFlow(-1)
    val vpnStatusFlow: StateFlow<Int> = _vpnStatusFlow

    private val _vpnSwitchFlow = MutableSharedFlow<Boolean>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val vpnSwitchFlow: SharedFlow<Boolean> = _vpnSwitchFlow


    //收到的IP报文Buffer
    private val packetBuffer = ByteArray(1024 * 64)

    suspend fun startProcessVpnPacket(vpnService: LocalVpnServiceKT, localIP: String) {

        source.establishVpn(vpnService, localIP)?.let {

            tryEmitStatus(true)

            source.startProcessVpnPacket(vpnService, packetBuffer)

        } ?: kotlin.run {
            tryEmitStatus(false)
        }
    }

    private fun tryEmitStatus(value: Boolean) {
        _vpnSwitchFlow.tryEmit(value)
        _vpnStatusFlow.tryEmit(if (value) 1 else 0)
    }

    fun stopVPN() {
        source.stopProcessVpnPacket()
        _vpnStatusFlow.tryEmit(0)
        val result = _vpnSwitchFlow.tryEmit(false)
        Log.d("VpnViewModel", "tryEmit, _vpnSwitchFlow: $result")
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        source.sendUDPPacket(ipHeader, udpHeader)
    }

}