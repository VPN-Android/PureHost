package top.nicelee.purehost.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream

class VpnDataSource {
    private val TAG = "VpnDataSource"
    
    //收到的IP报文Buffer
    private val m_Packet = ByteArray(1024 * 64)

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var vpnInput: FileInputStream? = null
    private var vpnOutput: FileOutputStream? = null
    suspend fun startProcessVpnPacket(vpnService: VpnService, localIP: String) = callbackFlow<Int> {
        withContext(Dispatchers.IO) {
            fileDescriptor = ParcelFileDescriptorHelper.establish(vpnService, localIP)
            fileDescriptor?.use {
                vpnOutput = FileOutputStream(it.fileDescriptor)

                vpnInput = FileInputStream(it.fileDescriptor)
                vpnInput?.use { vi->
                    Log.d(TAG, "开始!!!!!!!!!!!!!!!!!!!!!!!!!")
                    runCatching {
                        var size: Int
                        while ((vi.read(m_Packet).also { read -> size = read }) >= 0) {
                            trySend(size)
                            Log.d(TAG, "读取报文中!!!!!!!!!!!!!!!!!!!!!!!!!")
                        }
                    }
                }
            }
            awaitClose {
                kotlin.runCatching { vpnInput?.close() }
                kotlin.runCatching { vpnOutput?.close() }
                kotlin.runCatching { fileDescriptor?.close() }
            }
        }
    }
}