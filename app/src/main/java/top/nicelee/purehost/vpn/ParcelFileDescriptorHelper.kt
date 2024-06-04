package top.nicelee.purehost.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import top.nicelee.purehost.vpn.config.ConfigReader

object ParcelFileDescriptorHelper {

    private const val TAG = "ParcelFileDescriptorHelper"

    fun establish(vpnService: VpnService, localIP: String) : ParcelFileDescriptor? {
        val builder: VpnService.Builder = vpnService.Builder()

        //builder.setMtu(...);
        builder.addAddress(localIP, 24)

        //builder.addRoute("0.0.0.0", 0);
        builder.setSession("PureHost")

        //builder.addDnsServer("222.66.251.8");
        if (ConfigReader.dnsList.isEmpty()) {
            ConfigReader.initDNS(vpnService.applicationContext)
            Log.d(TAG, "根据系统默认DNS初始化...")
        } else {
            Log.d(TAG, "根据提供的DNS初始化...")
        }
        for (dns in ConfigReader.dnsList) {
            builder.addDnsServer(dns!!)
            builder.addRoute(dns!!, 32)
        }
        ConfigReader.dnsList.clear()

        //        try{
//            Log.d(TAG,"\n\n\n\n\n\n\n创建Server!!!!!!!!!!!!!!!!!!!!!!!!!\n\n\n\n\n\n\n\n\n\n" + this.getPackageName());
//            //将本程序加入VPN列表
//            builder.addAllowedApplication(this.getPackageName());
//
//            PackageManager packageManager= this.getPackageManager();
//            List<PackageInfo> list=packageManager.getInstalledPackages(0);
//            for(PackageInfo pkg :  list){
//                Log.d(TAG,pkg.packageName + "加入列表");
//                builder.addAllowedApplication(pkg.packageName);
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//            Log.d(TAG,"\n\n\n\n\n\n\n创建Server出现错误!!!!!!!!!!!!!!!!!!!!!!!!!\n\n\n\n\n\n\n\n\n\n" + this.getPackageName());
//        }


        //builder.addDnsServer(...);
        //builder.addSearchDomain(...);
        //builder.setConfigureIntent(...);

        return builder.establish()
    }
}