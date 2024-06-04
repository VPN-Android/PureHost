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
import java.lang.Thread.sleep

class VpnDataSource {
    private val TAG = "VpnDataSource"
    

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var vpnInput: FileInputStream? = null

    var vpnOutput: FileOutputStream? = null
        private set
        get() {
            if (field == null) {
                throw IllegalStateException("vpnOutput is null")
            }
            return field
        }
    suspend fun startProcessVpnPacket(byteArray: ByteArray, vpnService: VpnService, localIP: String) = callbackFlow {
        withContext(Dispatchers.IO) {
            fileDescriptor = ParcelFileDescriptorHelper.establish(vpnService, localIP)
            fileDescriptor?.use {
                vpnOutput = FileOutputStream(it.fileDescriptor)

                vpnInput = FileInputStream(it.fileDescriptor)
                vpnInput?.use { vi->
                    Log.d(TAG, "开始!!!!!!!!!!!!!!!!!!!!!!!!!")
                    runCatching {
                        var size: Int
                        while ((vi.read(byteArray).also { read -> size = read }) >= 0) {
                            if (size == 0) {
                                sleep(10)
                                Log.d(TAG, "读取报文中!!!!!!!!!!!!!!!!!!!!!!!!!, empty")
                                continue
                            }
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