package top.nicelee.purehost.vpn

import android.content.Intent
import android.net.VpnService
import android.util.Log
import top.nicelee.purehost.vpn.ip.IPHeader
import top.nicelee.purehost.vpn.ip.UDPHeader

class LocalVpnServiceKT : VpnService(){

    companion object {
        val TAG = "LocalVpnServiceKT"
        var instance: LocalVpnServiceKT? = null
    }

    val localVpnService = LocalVpnService()
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        instance = this

        localVpnService.onCreate(this)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        localVpnService.sendUDPPacket(ipHeader, udpHeader)
    }

    fun stopVPN() {
        localVpnService.stopVPN()
    }
}