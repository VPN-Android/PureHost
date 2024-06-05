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

    private var started = false

    suspend fun establishVpn(vpnService: VpnService, localIP: String): ParcelFileDescriptor? {
        return withContext(Dispatchers.IO) {
            fileDescriptor = ParcelFileDescriptorHelper.establish(vpnService, localIP)
            fileDescriptor
        }
    }

    suspend fun startProcessVpnPacket(byteArray: ByteArray) = callbackFlow {
        withContext(Dispatchers.IO) {
            started = true

            fileDescriptor?.use {

                vpnInput = FileInputStream(it.fileDescriptor)
                vpnInput?.use { vi->
                    Log.d(TAG, "开始!!!!!!!!!!!!!!!!!!!!!!!!!")
                    runCatching {
                        var size = 0
                        while (started && ((vi.read(byteArray).also { read -> size = read }) >= 0)) {
                            if (size == 0) {
                                sleep(10)
                                continue
                            }
                            if (started) {
                                trySend(size)
                            }
                            Log.d(TAG, "读取报文中, size: $size")
                        }
                        channel.close()
                    }
                }
            }
        }

        awaitClose {
            kotlin.runCatching { vpnInput?.close() }
            kotlin.runCatching { fileDescriptor?.close() }
        }
    }

    fun stopProcessVpnPacket() {
        started = false
    }
}