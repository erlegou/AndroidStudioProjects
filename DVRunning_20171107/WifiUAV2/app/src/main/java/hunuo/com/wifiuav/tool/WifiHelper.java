package hunuo.com.wifiuav.tool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;


import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hunuo.com.wifiuav.uity.Dbug;

public class WifiHelper {
    private static String tag = "WifiHelper";
    private static WifiHelper instance = null;
    private WifiManager mWifiManager;
    private WifiManager.WifiLock wifiLock;
    private static String otherWifiSSID = null;

    public static int WIFI_CONNECTING = 0;
    public static int WIFI_CONNECTED = 1;
    public static int WIFI_CONNECT_FAILED = 2;

    private static final String KEY_WPA = "WPA_PSK";
    private static final String KEY_NONE = "NONE";

    public static WifiHelper getInstance(Context context) {
        if (instance == null) {
            instance = new WifiHelper(context);
        }
        return instance;
    }

    private WifiHelper(Context c) {
        if (c != null) {
            mWifiManager = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            this.startScan();
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            Dbug.e(tag, "-isNetworkAvailable- is error, reason : context is empty!");
            return false;
        }

        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                        return anInfo.getType() != ConnectivityManager.TYPE_MOBILE;
                    }
                }
            }
        }
        return false;
    }

    public boolean isOutSideWifi(String tag){
        WifiInfo info = getWifiConnectionInfo();
        if(info != null){
            String ssid = info.getSSID();
            if(!TextUtils.isEmpty(ssid)){
                ssid = formatSSID(ssid);
                if(!TextUtils.isEmpty(ssid) && ssid.startsWith(tag)){
                    return true;
                }
            }
        }
        return false;
    }

    public int isWifiConnected(Context context) {
        if (context == null) {
            Dbug.e(tag, "-isWifiConnected- is error, reason : context is empty!");
            return  -1;
        }
        startScan();
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (wifiNetworkInfo.getDetailedState() == NetworkInfo.DetailedState.OBTAINING_IPADDR
                || wifiNetworkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTING) {
            return WIFI_CONNECTING;
        } else if (wifiNetworkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
            return WIFI_CONNECTED;
        } else {
            Dbug.i(tag, "getDetailedState() == " + wifiNetworkInfo.getDetailedState());
            return WIFI_CONNECT_FAILED;
        }

    }

    public static String interceptChar0Before(String s) {
        if (s == null) {
            return null;
        }
        char[] chars = s.toCharArray();
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            Character ch = c;
            if (0 == ch.hashCode()) {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public boolean isWiFiActive(Context inContext) {
        Context context = inContext.getApplicationContext();
        ConnectivityManager connectivity = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getTypeName().equals("WIFI") && anInfo.isConnected()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isWifi(Context context) {
        if (!isNetworkAvailable(context)) {
            return false;
        }

        ConnectivityManager manager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        int type = networkInfo.getType();

        return (type == ConnectivityManager.TYPE_WIFI);
    }

    /**
     * 查看WIFI当前是否处于打开状态
     *
     * @return true 处于打开状态；false 处于非打开状态(包括UnKnow状态)。
     */
    public boolean isWifiClosed() {
        int wifiState = getWifiState();
        return wifiState == WifiManager.WIFI_STATE_DISABLED
                || wifiState == WifiManager.WIFI_STATE_DISABLING;
    }

    /**
     * 查看WIFI当前是否处于关闭状态
     *
     * @return true 处于关闭状态；false 处于非关闭状态(包括UNKNOWN状态)
     */
    public boolean isWifiOpened() {
        int wifiState = getWifiState();
        return wifiState == WifiManager.WIFI_STATE_ENABLED
                || wifiState == WifiManager.WIFI_STATE_ENABLING;
    }

    /**
     * 如果WIFI当前处于关闭状态，则打开WIFI
     */
    public void openWifi() {
        if (mWifiManager != null && isWifiClosed()) {
            mWifiManager.setWifiEnabled(true);
        }
    }

    /**
     * 如果WIFI当前处于打开状态，则关闭WIFI
     */
    public void closeWifi() {
        if (mWifiManager != null && isWifiOpened()) {
            mWifiManager.setWifiEnabled(false);
        }
    }

    /**
     * 获取当前Wifi的状态编码
     *
     * @return WifiManager.WIFI_STATE_ENABLED，WifiManager.WIFI_STATE_ENABLING，
     * WifiManager.WIFI_STATE_DISABLED，WifiManager.WIFI_STATE_DISABLING，
     * WifiManager.WIFI_STATE_UnKnow 中间的一个
     */
    public int getWifiState() {
        if (mWifiManager != null) {
            return mWifiManager.getWifiState();
        }
        return 0;
    }

    /**
     * 获取已经配置好的Wifi网络
     *
     */
    public List<WifiConfiguration> getSavedWifiConfiguration() {
        if(mWifiManager != null){
            return mWifiManager.getConfiguredNetworks();
        }
        return null;
    }

    /**
     * 获取扫描到的网络的信息
     *
     */
    public List<ScanResult> getWifiScanResult() {
        if(mWifiManager != null){
            return mWifiManager.getScanResults();
        }
        return null;
    }

    /**
     * 执行一次Wifi的扫描
     */
    public synchronized void startScan() {
        if (mWifiManager != null) {
            mWifiManager.startScan();
        }
    }

    /**
     * 通过netWorkId来连接一个已经保存好的Wifi网络
     *
     * @param netWorkId network specific id
     */
    public void connectionConfiguration(int netWorkId) {
//        Dbug.e(tag, " configurationNetWorkIdCheck(netWorkId) : " + configurationNetWorkIdCheck(netWorkId));
        if (mWifiManager != null) {
            mWifiManager.disconnect();
            mWifiManager.enableNetwork(netWorkId, true);
//            mWifiManager.reconnect();
        }
    }

    /**
     * 断开一个指定ID的网络
     */
    public void disconnectionConfiguration(int netWorkId) {
        mWifiManager.disableNetwork(netWorkId);
        mWifiManager.disconnect();
    }

    /**
     * 检测尝试连接某个网络时，查看该网络是否已经在保存的队列中间
     *
     * @param netWorkId network specific id
     * @return true : network save in configuration
     */
    private boolean configurationNetWorkIdCheck(int netWorkId) {
        List<WifiConfiguration> wifiConfigurationList = mWifiManager.getConfiguredNetworks();
        if(null == wifiConfigurationList || wifiConfigurationList.size() == 0){
            return false;
        }
        for (WifiConfiguration temp : wifiConfigurationList) {
            if (temp.networkId == netWorkId) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取Wifi的数据
     *
     */
    public WifiInfo getWifiConnectionInfo() {
        return mWifiManager.getConnectionInfo();
    }

    /**
     * 锁定WIFI，使得在熄屏状态下，仍然可以使用WIFI
     */
    public void acquireWifiLock() {
        if (wifiLock != null) {
            wifiLock.acquire();
        }
    }

    /**
     * 解锁WIFI
     */
    public void releaseWifiLock() {
        if (wifiLock != null) {
            if (wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        }
    }

    /**
     * 创建一个WifiLock
     */
    public void createWifiLock() {
        if (mWifiManager != null) {
            wifiLock = mWifiManager.createWifiLock("wifiLock");
        }
    }

    /**
     * 保存一个新的网络
     *
     * @param _wifiConfiguration
     */
    public int addNetWork(WifiConfiguration _wifiConfiguration) {
        int netWorkId = -255;
        if (_wifiConfiguration != null && mWifiManager != null) {
            netWorkId = mWifiManager.addNetwork(_wifiConfiguration);
//            startScan();
        }
        return netWorkId;
    }

    /**
     * 保存并连接到一个新的网络
     *
     * @param _wifiConfiguration
     */
    public void addNetWorkAndConnect(WifiConfiguration _wifiConfiguration) {
        int netWorkId = addNetWork(_wifiConfiguration);
        if (mWifiManager != null) {
            mWifiManager.enableNetwork(netWorkId, true);
        }else{
            Dbug.e(tag, "mWifiManager is null!");
        }
        Dbug.i(tag, " addNetWorkAndConnect - netWorkId : " + netWorkId);
    }

    /**
     * 获取当前连接状态中的Wifi的信号强度
     *
     */
    public int getConnectedWifiLevel() {
        WifiInfo wifiInfo = getWifiConnectionInfo();
        if (wifiInfo != null) {
            String connectedWifiSSID = wifiInfo.getSSID();
            connectedWifiSSID = formatSSID(connectedWifiSSID);
            List<ScanResult> scanResultList = mWifiManager.getScanResults();
            if (scanResultList != null) {
                for (ScanResult temp : scanResultList) {
                    String tempSSID = formatSSID(temp.SSID);
                    if (!TextUtils.isEmpty(tempSSID) && tempSSID.equals(connectedWifiSSID)) {
                        return temp.level;
                    }
                }
            }
        }
        return 1;
    }

    /**
     * 删除指定SSID的网络
     * @param ssid
     * @return
     */
    public boolean removeSavedNetWork(String ssid){
        if(TextUtils.isEmpty(ssid) || mWifiManager == null){
            return false;
        }
        boolean result = false;
        List<WifiConfiguration> saveWifiConfigList =  mWifiManager.getConfiguredNetworks();
        if(saveWifiConfigList == null || saveWifiConfigList.size() == 0){
            return false;
        }
        ssid = formatSSID(ssid);
        for (WifiConfiguration wifiConfig : saveWifiConfigList){
            if(wifiConfig != null){
                String saveSSID = wifiConfig.SSID;
                saveSSID = formatSSID(saveSSID);
                if(!TextUtils.isEmpty(saveSSID) && saveSSID.equals(ssid)){
                    result = mWifiManager.removeNetwork(wifiConfig.networkId);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 删除一个已经保存的网络
     *
     * @param netWorkId
     */
    public void remoteNetWork(int netWorkId) {
        if (mWifiManager != null) {
            mWifiManager.removeNetwork(netWorkId);
        }
    }

    /**
     * Wifi加密类型的描述类
     */
    public enum WifiCipherType {
        NONE, IEEE8021XEAP, WEP, WPA, WPA2, WPAWPA2
    }

    /**
     * 连接一个WIFI
     *
     * @param ssid  network name
     * @param password network password
     * @param wifiCipherType network type
     */
    public void addNetWorkAndConnect(String ssid, String password, WifiCipherType wifiCipherType) {
        if (mWifiManager != null && wifiCipherType != null) {
            WifiConfiguration temp = isWifiConfigurationSaved(ssid, wifiCipherType);
            if(null != temp){
//                remoteNetWork(temp.networkId);
                Dbug.e(tag, "-addNetWorkAndConnect- step 001");
                connectionConfiguration(temp.networkId);
            }else{
                Dbug.e(tag, "-addNetWorkAndConnect- step 002");
                WifiConfiguration wifiConfig = createWifiConfiguration(ssid, password, wifiCipherType);
//            addNetWorkAndConnect(wifiConfig);
                addNetWork(wifiConfig);
                List<WifiConfiguration> configurationList = getSavedWifiConfiguration();
                if(configurationList != null){
                    for (WifiConfiguration config : configurationList){
                        if(config != null){
                            String tempSSID = config.SSID;
                            tempSSID = formatSSID(tempSSID);
                            ssid = formatSSID(ssid);
                            if(!TextUtils.isEmpty(tempSSID) && tempSSID.equals(ssid)){
                                mWifiManager.disconnect();
                                mWifiManager.enableNetwork(config.networkId, true);
//                                mWifiManager.reconnect();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }


    private WifiConfiguration isWifiConfigurationSaved(String SSID, WifiCipherType wifiCipherType) {
        List<WifiConfiguration> wifiConfigurationList = getSavedWifiConfiguration();
        if (wifiConfigurationList == null) {
            return null;
        }
        String tagetSSID = formatSSID(SSID);
        for (WifiConfiguration temp : wifiConfigurationList) {
            if(temp != null){
                String tempSSID = temp.SSID;
                tempSSID  = formatSSID(tempSSID);
                if(!TextUtils.isEmpty(tempSSID) && tempSSID.equals(tagetSSID)){
                    String keyMgmt = null;
                    for (int k = 0; k < temp.allowedKeyManagement.size(); k++) {
                        if (temp.allowedKeyManagement.get(k)) {
                            if (k < WifiConfiguration.KeyMgmt.strings.length) {
                                keyMgmt = WifiConfiguration.KeyMgmt.strings[k];
                            }
                        }
                    }
                    Dbug.e(tag, "isWifiConfigurationSaved  keyMgmt = " + keyMgmt + " , wifiCipherType : " +wifiCipherType);
                    if((wifiCipherType == WifiCipherType.WPA && KEY_WPA.equals(keyMgmt)) ||
                            (wifiCipherType == WifiCipherType.NONE && KEY_NONE.equals(keyMgmt))){
                        Dbug.e(tag, "isWifiConfigurationSaved return object, network id : " +temp.networkId);
                        return temp;
                    }
                }
            }

        }
        return null;
    }

    private WifiConfiguration createWifiConfiguration(String SSID, String password, WifiCipherType type) {
        WifiConfiguration newWifiConfiguration = new WifiConfiguration();
        newWifiConfiguration.allowedAuthAlgorithms.clear();
        newWifiConfiguration.allowedGroupCiphers.clear();
        newWifiConfiguration.allowedKeyManagement.clear();
        newWifiConfiguration.allowedPairwiseCiphers.clear();
        newWifiConfiguration.allowedProtocols.clear();
        newWifiConfiguration.SSID = "\"" + SSID + "\"";

        switch (type) {
            case NONE:
//                newWifiConfiguration.wepKeys[0] = "";
                newWifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
//                newWifiConfiguration.wepTxKeyIndex = 0;
                break;
            case IEEE8021XEAP:
                break;
            case WEP:
                newWifiConfiguration.hiddenSSID = true;
                newWifiConfiguration.wepKeys[0] = "\"" + password + "\"";
                newWifiConfiguration.wepTxKeyIndex = 0;
                newWifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                newWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                newWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                newWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                newWifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                newWifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                break;
            case WPA:
                newWifiConfiguration.preSharedKey = "\"" + password + "\"";
                newWifiConfiguration.hiddenSSID = true;
                newWifiConfiguration.allowedAuthAlgorithms
                        .set(WifiConfiguration.AuthAlgorithm.OPEN);
                newWifiConfiguration.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.TKIP);
                newWifiConfiguration.allowedKeyManagement
                        .set(WifiConfiguration.KeyMgmt.WPA_PSK);
                newWifiConfiguration.allowedPairwiseCiphers
                        .set(WifiConfiguration.PairwiseCipher.TKIP);
//                newWifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                newWifiConfiguration.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.CCMP);
                newWifiConfiguration.allowedPairwiseCiphers
                        .set(WifiConfiguration.PairwiseCipher.CCMP);
                newWifiConfiguration.status = WifiConfiguration.Status.ENABLED;

                break;
            case WPA2:
                newWifiConfiguration.preSharedKey = "\"" + password + "\"";
                newWifiConfiguration.allowedAuthAlgorithms
                        .set(WifiConfiguration.AuthAlgorithm.OPEN);
                newWifiConfiguration.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.TKIP);
                newWifiConfiguration.allowedGroupCiphers
                        .set(WifiConfiguration.GroupCipher.CCMP);
                newWifiConfiguration.allowedKeyManagement
                        .set(WifiConfiguration.KeyMgmt.WPA_PSK);
                newWifiConfiguration.allowedPairwiseCiphers
                        .set(WifiConfiguration.PairwiseCipher.TKIP);
                newWifiConfiguration.allowedPairwiseCiphers
                        .set(WifiConfiguration.PairwiseCipher.CCMP);
                newWifiConfiguration.allowedProtocols
                        .set(WifiConfiguration.Protocol.RSN);
                newWifiConfiguration.status = WifiConfiguration.Status.ENABLED;
                break;
            default:
                return null;
        }
        return newWifiConfiguration;
    }

    public static int getNetWorkType(Context context) {
        if (!isNetworkAvailable(context)) {
            return 0;
        }

        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        String type = networkInfo.getTypeName();

        if (type.equalsIgnoreCase("WIFI")) {
            return 1;
        } else if (type.equalsIgnoreCase("MOBILE")) {
            TelephonyManager telephonyManager = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            switch (telephonyManager.getNetworkType()) {
                case TelephonyManager.NETWORK_TYPE_EVDO_0:// ~ 400-1000 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_A:// ~ 600-1400 kbps
                case TelephonyManager.NETWORK_TYPE_HSDPA:// ~ 2-14 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPA:// ~ 700-1700 kbps
                case TelephonyManager.NETWORK_TYPE_HSUPA:// ~ 1-23 Mbps
                case TelephonyManager.NETWORK_TYPE_UMTS: // ~ 400-7000 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_B:// ~ 5 Mbps
                    return 1;
                case TelephonyManager.NETWORK_TYPE_1xRTT:// ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_CDMA:// ~ 14-64 kbps
                case TelephonyManager.NETWORK_TYPE_EDGE:// ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_GPRS:// ~ 100 kbps
                case TelephonyManager.NETWORK_TYPE_IDEN:// ~25 kbps
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                default:
                    return 0;
            }
        }

        return 0;
    }

    /**
     * 枚举网络状态
     * NET_NO：没有网络
     * NET_2G:2g网络
     * NET_3G：3g网络
     * NET_4G：4g网络
     * NET_WIFI：wifi
     * NET_UNKNOWN：未知网络
     */
    public enum NetState {
        NET_NO, NET_2G, NET_3G, NET_4G, NET_WIFI, NET_UNKNOWN
    }

    /**
     * 判断当前网络连接类型
     *
     * @param context 上下文
     * @return 状态码
     */
    public static NetState getConnectedType(Context context) {
        NetState stateCode = NetState.NET_NO;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni != null && ni.isConnectedOrConnecting()) {
            switch (ni.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    stateCode = NetState.NET_WIFI;
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    switch (ni.getSubtype()) {
                        case TelephonyManager.NETWORK_TYPE_GPRS: //联通2g
                        case TelephonyManager.NETWORK_TYPE_CDMA: //电信2g
                        case TelephonyManager.NETWORK_TYPE_EDGE: //移动2g
                        case TelephonyManager.NETWORK_TYPE_1xRTT:
                        case TelephonyManager.NETWORK_TYPE_IDEN: //api<8 : replace by 11
                            stateCode = NetState.NET_2G;
                            break;
                        case TelephonyManager.NETWORK_TYPE_EVDO_A: //电信3g
                        case TelephonyManager.NETWORK_TYPE_UMTS:
                        case TelephonyManager.NETWORK_TYPE_EVDO_0:
                        case TelephonyManager.NETWORK_TYPE_HSDPA:
                        case TelephonyManager.NETWORK_TYPE_HSUPA:
                        case TelephonyManager.NETWORK_TYPE_HSPA:
                        case TelephonyManager.NETWORK_TYPE_EVDO_B: //api<9 : replace by 14
                        case TelephonyManager.NETWORK_TYPE_EHRPD:  //api<11 : replace by 12
                        case TelephonyManager.NETWORK_TYPE_HSPAP:  //api<13 : replace by 15
                            stateCode = NetState.NET_3G;
                            break;
                        case TelephonyManager.NETWORK_TYPE_LTE:   //api<11 : replace by 13
                            stateCode = NetState.NET_4G;
                            break;
                        default:
                            // http://baike.baidu.com/item/TD-SCDMA 中国移动 联通 电信 三种3G制式
                            if (ni.getSubtypeName().equalsIgnoreCase("TD-SCDMA") ||
                                    ni.getSubtypeName().equalsIgnoreCase("WCDMA") ||
                                    ni.getSubtypeName().equalsIgnoreCase("CDMA2000")) {
                                stateCode = NetState.NET_3G;
                            }else{
                                stateCode = NetState.NET_UNKNOWN;
                            }

                    }
                    break;
                default:
                    stateCode = NetState.NET_UNKNOWN;
            }

        }
        return stateCode;
    }

    public static String getWIFIIP(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiinfo = wifiManager.getConnectionInfo();
        String ip = formatIpAddress(wifiinfo.getIpAddress());
        if (ip.equals("0.0.0.0")) {
            ip = getLocalIpAddress();
            if (ip.equals("0.0.0.0")) {
                Dbug.e("WIFP_IP", "WIFI IP Error");
            }
        }
        return ip;
    }

    private static String getLocalIpAddress() {
        try {
            String ipv4;
            List<NetworkInterface> nilist = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : nilist) {
                List<InetAddress> ialist = Collections.list(ni.getInetAddresses());
                for (InetAddress address : ialist) {
                    ipv4 = address.getHostAddress();
//                    if (!address.isLoopbackAddress() && InetAddressUtils.isIPv4Address(ipv4)) {
                    if(!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return ipv4;
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return "0.0.0.0";
    }

    private static String formatIpAddress(int ipAddress) {
        return (ipAddress & 0xFF) + "." +
                ((ipAddress >> 8) & 0xFF) + "." +
                ((ipAddress >> 16) & 0xFF) + "." +
                (ipAddress >> 24 & 0xFF);
    }


    /**
     * WIFI 是否打开
     *
     */
    public boolean isWifiOpen() {
        return mWifiManager.isWifiEnabled();
    }

    public List<ScanResult> getSpecifiedSSIDList(final String specified) {
        List<ScanResult> list = new ArrayList<>();
        if (mWifiManager != null) {
            mWifiManager.startScan();
            List<ScanResult> scanResultList = mWifiManager.getScanResults();
            if (scanResultList == null){
                Dbug.e(tag, "scanResultList is null");
                return null;
            }
            for (ScanResult scanResult : scanResultList) {
                Dbug.d(tag, "scanResult.SSID=" + scanResult.SSID+ ", capabilities:" + scanResult.capabilities);
                if (scanResult.SSID.startsWith(specified)) {
                    list.add(scanResult);
                }
            }
        }
        return list;
    }

    public void connectOtherWifi(String exceptSpecified){
        startScan();
        otherWifiSSID = null;
        boolean isConnect = false;
        List<WifiConfiguration> saveWifiList = getSavedWifiConfiguration();
        List<ScanResult> scanResultList = getWifiScanResult();
        if(scanResultList == null || saveWifiList == null){
            Dbug.e(tag, "scanResultList or saveWifiList is null");
            return;
        }

        for (ScanResult scanResult : scanResultList){
            String saveNetWorkName = scanResult.SSID;
            if(TextUtils.isEmpty(saveNetWorkName)){
                continue;
            }
            saveNetWorkName = formatSSID(saveNetWorkName);
            if (TextUtils.isEmpty(saveNetWorkName) || saveNetWorkName.startsWith(exceptSpecified)){
                continue;
            }
//            Dbug.e(tag,"scanResult.SSID-> " + scanResult.SSID);
            for(WifiConfiguration config : saveWifiList){
                String networkName = config.SSID;
                if(TextUtils.isEmpty(networkName)){
                    continue;
                }
                networkName = formatSSID(networkName);
                if (saveNetWorkName.equals(networkName)) {
                    Dbug.e(tag, "Save networkName-> " + saveNetWorkName + " network_id -> " + config.networkId
                            + " networkName : " +networkName);
                    if (mWifiManager != null) {
                        mWifiManager.disconnect();
                        isConnect = mWifiManager.enableNetwork(config.networkId, true);
                    }
                    otherWifiSSID = config.SSID;
                    break;
                }
            }
            if(isConnect){
                break;
            }
        }
    }

    public String getOtherWifiSSID(){
        return otherWifiSSID;
    }

    public void connectWifi(String ssid, String password) {

        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + ssid + "\"";   // Please note the quotes. String should contain ssid in quotes
        conf.preSharedKey = "\"" + password + "\"";
        int netId = mWifiManager.addNetwork(conf);
        Dbug.d(tag, "net id ==:" + netId);

        List<WifiConfiguration> list = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration i : list) {
            if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                Dbug.i(tag, "net id ==:" + i.networkId);
                mWifiManager.disconnect();
                mWifiManager.enableNetwork(i.networkId, true);
                mWifiManager.reconnect();
                break;
            }
        }
    }

    public static String formatSSID(String ssid){
        if(TextUtils.isEmpty(ssid)) return null;
        if(ssid.contains("\"")){
            ssid = ssid.replace("\"", "");
        }
        if(ssid.contains(" ")){
            ssid = ssid.replace(" ", "");
        }
        return ssid;
    }
}
