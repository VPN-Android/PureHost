package top.nicelee.purehost.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader
import java.io.FileInputStream
import java.lang.Thread.sleep

class VpnDataSource {
    private val TAG = "VpnDataSource"
    

    private var fileDescriptor: ParcelFileDescriptor? = null

    private var started = false
    private val localServerHelper = LocalServerHelper()

    suspend fun establishVpn(vpnService: LocalVpnServiceKT, localIP: String) :ParcelFileDescriptor? {
        return withContext(Dispatchers.IO) {
            fileDescriptor = ParcelFileDescriptorHelper.establish(vpnService, localIP)
            fileDescriptor
        }
    }

    suspend fun startProcessVpnPacket(vpnService: LocalVpnServiceKT, byteArray: ByteArray) {
        started = true

        withContext(Dispatchers.IO) {
            fileDescriptor?.use {

                localServerHelper.createServer(vpnService, fileDescriptor, byteArray)

                FileInputStream(it.fileDescriptor).use {vpnInput->
                    Log.d(TAG, "开始!!!!!!!!!!!!!!!!!!!!!!!!!")
                    runCatching {
                        while (started) {
                            val size = vpnInput.read(byteArray)
                            if (size > 0) {
                                Log.d(TAG, "读取报文中, size: $size")
                                localServerHelper.onPacketReceived(size)
                            } else {
                                sleep(10)
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopProcessVpnPacket() {
        localServerHelper.stop()
        started = false
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        localServerHelper.sendUDPPacket(ipHeader, udpHeader)
    }
}