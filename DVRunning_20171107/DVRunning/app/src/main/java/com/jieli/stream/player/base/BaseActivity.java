package com.jieli.stream.player.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.widget.Toast;

import com.jieli.lib.stream.beans.DeviceVersionInfo;
import com.jieli.lib.stream.beans.StateInfo;
import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.tools.ParseHelper;
import com.jieli.lib.stream.util.ICommon;
import com.jieli.stream.player.MainApplication;
import com.jieli.stream.player.R;
import com.jieli.stream.player.tool.ActivityStack;
import com.jieli.stream.player.tool.WifiHelper;
import com.jieli.stream.player.ui.activity.BrowseFileActivity;
import com.jieli.stream.player.ui.activity.LauncherActivity;
import com.jieli.stream.player.ui.activity.MainActivity;
import com.jieli.stream.player.ui.activity.TimelineActivity;
import com.jieli.stream.player.ui.dialog.DeviceListDialog;
import com.jieli.stream.player.ui.dialog.NotifyDialog;
import com.jieli.stream.player.ui.service.CommunicationService;
import com.jieli.stream.player.util.AppUtil;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IAction;
import com.jieli.stream.player.util.IConstant;
import com.jieli.stream.player.util.PreferencesHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * class name: BaseActivity
 * function : All activity parent classes
 * @author JL
 * create time : 2015-10-21 14:30
 * version : v1.0
 * ///////////////////////////////////////////////////////////////////////////
 *
 */
public class BaseActivity extends FragmentActivity implements ICommon, IAction, IConstant {
    private final String tag = getClass().getSimpleName();
    private Toast mToastShort, mToastLong;
    public MainApplication mApplication;
    private NotifyDialog mConnectionErrorNotify, mDeviceCloseWifiDialog;
    private NotifyDialog mNotifyDialog, mPasswordErrorDialog;
    private NotifyDialog mUsbModeDialog, readDataErrDialog, devRejectDialog;
    private static boolean isReadyToConnect = false;
    private static boolean isNeedReconnection = false;
    private WifiHelper mWifiHelper;
    private static String mConnectingSSID;
    private boolean isSocketConnecting = false;
    private int connectDevWifiFailureCount = 0;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()){
                case ACTION_DEVICE_WIFI_DISABLED:
                    if(mDeviceCloseWifiDialog == null){
                        mDeviceCloseWifiDialog = new NotifyDialog(R.string.dialog_tip, R.string.device_is_disabled_wifi, R.string.confirm, new NotifyDialog.OnConfirmClickListener() {
                            @Override
                            public void onClick() {
                                if(mDeviceCloseWifiDialog != null && mDeviceCloseWifiDialog.isShowing()){
                                    mDeviceCloseWifiDialog.dismiss();
                                }
                                release();
                            }
                        });
                        mDeviceCloseWifiDialog.setCancelable(false);
                    }
                    if(!mDeviceCloseWifiDialog.isShowing()){
                        mDeviceCloseWifiDialog.show(getFragmentManager(), "device_close_wifi");
                    }
                    break;
                case ACTION_SDCARD_STATE:
                    StateInfo stateInfo = (StateInfo) intent.getSerializableExtra(KEY_SDCARD_STATE);
                    String mountState = ARGS_NULL;
                    String msg = null;
                    if(stateInfo != null) {
                        if (stateInfo.getParam().length >= 2) {
                            mountState = stateInfo.getParam()[0];
                            msg = stateInfo.getParam()[1];
                        } else {
                            if (stateInfo.getParam().length == 1) {
                                mountState = stateInfo.getParam()[0];
                            }
                        }
                    }
                    onMountState(mountState, msg);
                    break;
                case ACTION_DEVICE_CONNECTION_ERROR:

                    int errorType = intent.getIntExtra(KEY_DEVICE_CONNECTION_ERROR, -1);

                    isSocketConnecting = false;
                    isReadyToConnect = false;
                    Dbug.i(tag, "==errorType==" + errorType );
                    switch (errorType){
                        case CommandHub.ERROR_CONNECTION_EXCEPTION:
                            mConnectionErrorNotify.setContent(R.string.connect_failed);
                            break;
                        case CommandHub.ERROR_CONNECTION_TIMEOUT:
                            mConnectionErrorNotify.setContent(R.string.connection_timeout);
                            break;
                    }
                    if (mConnectionErrorNotify != null && mConnectionErrorNotify.isShowing()){
                        mConnectionErrorNotify.dismiss();
                    }
                    if(mConnectionErrorNotify != null && !mConnectionErrorNotify.isShowing()){
                        handler.removeCallbacks(connectDeviceWiFi);
                        mConnectionErrorNotify.show(getFragmentManager(), "ConnectionErrorNotify");
                    }
                    break;
                case ACTION_REJECT_CONNECTION:
                    if (mNotifyDialog != null && mNotifyDialog.isShowing()){
                        mNotifyDialog.dismiss();
                    }
                    String rejection = intent.getStringExtra(KEY_REJECT_CONNECTION);
                    switch (rejection){
                        case ARGS_DEVICE_HAS_TAKEN:
                            if(devRejectDialog != null && !devRejectDialog.isShowing()){
                                devRejectDialog.show(getFragmentManager(), "device_reject_connection");
                            }
                            break;
                        case ARGS_DEVICE_IN_USB_MODE:
                            if(mUsbModeDialog != null && !mUsbModeDialog.isShowing()){
                                mUsbModeDialog.show(getFragmentManager(), "in_usb_mode");
                            }
                            break;
                    }
                    break;

                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    boolean isSocketConnect = CommandHub.getInstance().isActive();
                    boolean isWifiConnect;
                    if(networkInfo == null){
                        Dbug.e(tag, "networkInfo is null");
                        WifiHelper.getInstance(getApplicationContext()).startScan();
                       return;
                    }
                    isWifiConnect = (networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED);
                    if(networkInfo.getType() == ConnectivityManager.TYPE_MOBILE){
                        Dbug.e(tag, "networkType is TYPE_MOBILE");
                        WifiHelper.getInstance(getApplicationContext()).startScan();
                        return;
                    }
                    if (wifiInfo == null || TextUtils.isEmpty(wifiInfo.getSSID())){
                        Dbug.e(tag, "wifiInfois null or SSID is empty!");
                        WifiHelper.getInstance(getApplicationContext()).startScan();
                        if(!(BaseActivity.this instanceof LauncherActivity)){
                            handler.removeCallbacks(limitTimeConnect);
                            handler.postDelayed(limitTimeConnect, 60000);
                        }
                        return;
                    }

                    handler.removeCallbacks(limitTimeConnect);
                    SharedPreferences preferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                    String mSSID = preferences.getString(CURRENT_SSID, null);
                    String prefixSSID = wifiInfo.getSSID().replace("\"", "");
                    prefixSSID = prefixSSID.replace(" ", "");
                    if(!TextUtils.isEmpty(mSSID)){
                        mSSID = mSSID.replace("\"", "");
                        mSSID = mSSID.replace(" ", "");
                    }
                    Dbug.i(tag, "Current prefixSSID=====" + prefixSSID + ", getNetworkId==" + wifiInfo.getNetworkId() +" isSocketConnect : " +isSocketConnect);
                    Dbug.e(tag, "Is connected: " + isWifiConnect + ", isNeedReconnection=" + isNeedReconnection + ",isReadyToConnect=" + isReadyToConnect);
                    if (isReadyToConnect) {
                        if (isWifiConnect) {
                            if (prefixSSID.equals(mSSID)) {
                                handler.removeCallbacks(connectDeviceWiFi);
                                Dbug.e(tag, " == initSocket == 001");
                                initSocket();
                            } else {
                                handler.removeCallbacks(connectDeviceWiFi);
                                handler.post(connectDeviceWiFi);
                            }
                        }
                    } else {
                        if (isWifiConnect && (prefixSSID.equals(mSSID)) && ((BaseActivity.this instanceof MainActivity) ||
                                (BaseActivity.this instanceof  LauncherActivity)) && !isSocketConnect && !isNeedReconnection) {
                            handler.removeCallbacks(connectDeviceWiFi);
                            Dbug.e(tag, " == initSocket == 002");
                            initSocket();
                        }
                    }
                    break;
                case WifiManager.SUPPLICANT_STATE_CHANGED_ACTION:
                    SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    int supplicantError = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
                    Dbug.d(tag, "supplicantError=" + supplicantError + ", state=" + state);
                    if (SupplicantState.DISCONNECTED.equals(state) && supplicantError == WifiManager.ERROR_AUTHENTICATING && isReadyToConnect ){

                        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                        String saveDevPWD = sharedPreferences.getString(CURRENT_PWD, null);
                        PreferencesHelper.remove(MainApplication.getApplication(), mConnectingSSID);

                        if(!TextUtils.isEmpty(saveDevPWD) && saveDevPWD.length() >= 8){
                            Dbug.w(tag, "-mPasswordErrorDialog is showing- saveDevPWD : " +saveDevPWD);
                            if (mNotifyDialog != null && mNotifyDialog.isShowing()){
                                mNotifyDialog.dismiss();
                            }
                            isReadyToConnect = false;
                            if (mPasswordErrorDialog == null){
                                mPasswordErrorDialog = new NotifyDialog(R.string.dialog_tip, R.string.pwd_incorrect, R.string.confirm, new NotifyDialog.OnConfirmClickListener() {
                                    @Override
                                    public void onClick() {
                                        mPasswordErrorDialog.dismiss();

                                        if(mWifiHelper != null){
                                            if(mWifiHelper.removeSavedNetWork(mConnectingSSID)){
                                                mConnectingSSID = null;
                                                Dbug.e(tag, "mPasswordErrorDialog removeSavedNetWork ok!");
                                            }
                                        }

                                        searchDevice();
                                    }
                                });
                            }
                            if(!mPasswordErrorDialog.isShowing()){
                                mPasswordErrorDialog.show(getFragmentManager(), "mPasswordErrorDialog");
                            }
                        }
                    }
                    break;
                case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                    break;
                case ACTION_REQUEST_UI_DESCRIPTION:
                    stateInfo = (StateInfo) intent.getSerializableExtra(IAction.KEY_REQUEST_UI_DESCRIPTION);
                    requestData(stateInfo);
                    break;
                case ACTION_QUIT_APP:
                    release();
                    break;
                case ACTION_RESET_DEVICE:
//                    showToast(R.string.reset_success);
                    /** device will be restart when app send restart cmd to device*/
                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_RESTART_DEVICE, ARGS_RESTART_DEVICE);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            release();
                        }
                    }, 2000L);
                    break;
                case ACTION_FORMAT_SDCARD:
                    showToast(R.string.format_success);
                    cleanCache();
                    break;
                case ACTION_ENTER_OFFLINE_MODE:
                    enterOfflineMode();
                    break;
                case Intent.ACTION_USER_PRESENT:
                    if(BaseActivity.this.isFinishing()){
                        return;
                    }
                    SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                    String saveSSID = sharedPreferences.getString(CURRENT_SSID, null);
                    String currentPWD = sharedPreferences.getString(CURRENT_PWD, null);
                    mWifiHelper.startScan();
                    WifiInfo mWifiInfo = mWifiHelper.getWifiConnectionInfo();
                    if(mWifiInfo == null || TextUtils.isEmpty(mWifiInfo.getSSID())){
                        Dbug.e(tag, " ACTION_USER_PRESENT mWifiInfo is null!");
                        return;
                    }
                    String currentSSID = mWifiInfo.getSSID().trim();
                    boolean isSocketAlive = CommandHub.getInstance().isActive();
                    Dbug.e(tag, " currentSSID : " +currentSSID + " saveSSID : " +saveSSID + " Socket Alive : " +isSocketAlive );
                    if(!TextUtils.isEmpty(currentSSID)){
                        currentSSID = currentSSID.replace("\"", "");
                        currentSSID = currentSSID.replace(" ", "");
                    }
                    if(!TextUtils.isEmpty(saveSSID)){
                        saveSSID = saveSSID.replace("\"", "");
                        saveSSID = saveSSID.replace(" ", "");
                    }
                    if(!TextUtils.isEmpty(currentSSID)){
                        if(!isSocketAlive){
                            if(currentSSID.equals(saveSSID)){
                                if (mConnectionErrorNotify != null && mConnectionErrorNotify.isShowing()){
                                    mConnectionErrorNotify.dismiss();
                                }
                                initSocket();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if((BaseActivity.this instanceof  BrowseFileActivity) || (BaseActivity.this instanceof TimelineActivity)){
                                            /**Request device for enter browsing mode*/
                                            CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_ENTER_BROWSING_MODE, ARGS_ENTER_BROWSING_MODE);
                                            Dbug.e(tag, "-BaseActivity- CMD_ENTER_BROWSING_MODE cmd sent!001");
                                        }
                                    }
                                }, 2000L);
                            }else{
                                if((BaseActivity.this instanceof MainActivity) && !isNeedReconnection && !isReadyToConnect){
                                    newConnectDevice(currentSSID, currentPWD);
                                }
                            }
                        }
                    }
                    break;
                case ACTION_DEVICE_IN_USB_MODE:
                    if (mUsbModeDialog != null && !mUsbModeDialog.isShowing()){
                        mUsbModeDialog.show(getFragmentManager(), "mUsbModeDialog");
                    }
                    break;
                case ACTION_CLOSE_DEV_WIFI:
                    StateInfo closeDevWifi = (StateInfo) intent.getSerializableExtra(CLOSE_DEV_WIFI);
                    if(closeDevWifi != null && closeDevWifi.getParam().length > 0){
                        String param1 = closeDevWifi.getParam()[0];
                        if(param1.equals(ARGS_DISABLE_DEVICE_WIFI_SUCCESS)){
                            handler.removeCallbacks(closeSocket);
                            handler.postDelayed(closeSocket, 250L);
                        }
                    }
                    break;
                case ACTION_INIT_CTP_SOCKET_SUCCESS:
                    Dbug.w(tag, "==ACTION_INIT_CTP_SOCKET_SUCCESS==");
                    if (mDeviceCloseWifiDialog != null && mDeviceCloseWifiDialog.isShowing()){
                        mDeviceCloseWifiDialog.dismiss();
                    }
                    if(mConnectionErrorNotify != null && mConnectionErrorNotify.isShowing()){
                        mConnectionErrorNotify.dismiss();
                    }
                    if(mNotifyDialog != null && mNotifyDialog.isShowing()){
                        mNotifyDialog.dismiss();
                    }
                    if(mPasswordErrorDialog != null && mPasswordErrorDialog.isShowing()){
                        mPasswordErrorDialog.dismiss();
                    }
                    if(readDataErrDialog != null && readDataErrDialog.isShowing()){
                        readDataErrDialog.dismiss();
                    }
                    if(devRejectDialog != null && devRejectDialog.isShowing()){
                        devRejectDialog.dismiss();
                    }
                    if (mUsbModeDialog != null && mUsbModeDialog.isShowing()) {
                        mUsbModeDialog.dismiss();
                    }
                    handler.removeCallbacks(closeSocket);
                    sendCommandToService(SERVICE_CMD_CLEAR_DEVICE_STATUS);
                    mApplication.setIsOffLineMode(false);
                    mApplication.setIsFirstReadData(true);
                    isSocketConnecting = false;
                    isReadyToConnect = false;
                    break;
                case ACTION_REAR_CAMERA_PLUG_STATE:
                    StateInfo plugStateInfo = (StateInfo) intent.getSerializableExtra(KEY_REAR_CAMERA_PLUG_STATE);
                    String plugState = ARGS_NULL;
                    if(plugStateInfo != null) {
                        if (plugStateInfo.getParam().length >= 1) {
                            plugState = plugStateInfo.getParam()[0];
                            switch (plugState){
                                case ARGS_REAR_CAMERA_PLUG:
                                    showToast(R.string.rear_view_plugged);
                                    break;
                                case ARGS_REAR_CAMERA_UNPLUG:
                                    showToast(R.string.rear_view_unplugged);
                                    break;
                                default:
                                    Dbug.e(tag, "Unknown state.");
                                    break;
                            }
                        }
                    }
                    break;
            }
        }
    };

    private void initSocket(){
        boolean isSocketActive = CommandHub.getInstance().isActive();
        Dbug.e(tag, "========initSocket===, SocketConnect : "+ isSocketActive + " isSocketConnecting : "+ isSocketConnecting);
        if(!isSocketActive && !isSocketConnecting){
            Dbug.e(tag, "=============Connect device wifi success, now SERVICE_CMD_INIT_SOCKET");
            sendCommandToService(IConstant.SERVICE_CMD_INIT_SOCKET);
            isSocketConnecting = true;
            isNeedReconnection = false;
        }
    }


    public void newConnectDevice(String SSID, String password){
        if(TextUtils.isEmpty(SSID)){
            Dbug.e(tag, "parameter is empty!");
            return;
        }
        List<ScanResult> scanResultList = mWifiHelper.getSpecifiedSSIDList(WIFI_PREFIX);
        ScanResult scanResult = null;
        if(SSID.contains(" ")){
            SSID = SSID.replace(" ", "");
        }
        if(SSID.contains("\"")){
            SSID = SSID.replace("\"", "");
        }
        if(scanResultList != null && scanResultList.size() > 0){
            for (ScanResult result : scanResultList){
                String scanSSID = result.SSID;
                if(!TextUtils.isEmpty(scanSSID)){
                    if(scanSSID.contains("\"")){
                        scanSSID = scanSSID.replace("\"", "");
                    }
                    if(scanSSID.contains(" ")){
                        scanSSID = scanSSID.replace(" ", "");
                    }
                    if(scanSSID.equals(SSID)){
                        scanResult = result;
                        SSID = scanResult.SSID;
                        break;
                    }
                }
            }
        }
        if(scanResult == null){
            Dbug.e(tag, " scanResult is null ");
            connectDevWifiFailureCount++;
            if(connectDevWifiFailureCount < 5){
                handler.removeCallbacks(connectDeviceWiFi);
                handler.postDelayed(connectDeviceWiFi, 1000L);
            }else{
                handler.removeCallbacks(limitTimeConnect);
                if(mNotifyDialog != null && mNotifyDialog.isShowing()){
                    mNotifyDialog.dismiss();
                }
                if(mConnectionErrorNotify != null){
                    if (mConnectionErrorNotify.isShowing()){
                        mConnectionErrorNotify.dismiss();
                    }
                    mConnectionErrorNotify.setContent(R.string.connection_timeout);
                    if(!mConnectionErrorNotify.isShowing()){
                        mConnectionErrorNotify.show(getFragmentManager(), "ConnectionErrorNotify");
                    }
                    Dbug.e(tag, " failure connect device wifi number over 5, so stop connect device wifi! ");
                }else{
                    Dbug.e(tag, " mConnectionErrorNotify is null ");
                }
            }
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) MainApplication.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        WifiManager wifiManager = (WifiManager) MainApplication.getApplication().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        String ssid = null;
        if(wifiManager != null && wifiManager.getConnectionInfo() != null){
            ssid = wifiManager.getConnectionInfo().getSSID();
        }

        if (!TextUtils.isEmpty(ssid) && ssid.equals(SSID) && wifi.isConnected()){
            Dbug.e(tag, " == initSocket == 004");
            initSocket();
        }else if ((!scanResult.capabilities.contains("WPA"))){
            connectDevice(SSID, WifiConfiguration.KeyMgmt.NONE + "");
        }else{
            connectDevice(SSID, password);
        }
    }

    private void connectDevice(String ssid, String password){
        Dbug.e(tag, " connectDevice ssid : " + ssid + "  password : " + password);
        /**Check if Wi-Fi enable */
        WifiManager wifiManager = (WifiManager) MainApplication.getApplication().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            NotifyDialog notifyDialog = new NotifyDialog(R.string.dialog_tip, R.string.wifi_is_disable, R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                @Override
                public void onClick() {
                    release();
                }
            });
            if(!notifyDialog.isShowing()){
                notifyDialog.show(getFragmentManager(), "WiFiDisable");
            }
            return;
        }

        isReadyToConnect = true;

        if (TextUtils.isEmpty(ssid)){
            Dbug.e(tag, "connectDevice SSID is null");
            if(mWifiHelper != null){
                mWifiHelper.startScan();
            }
            return;
        }

        if (TextUtils.isEmpty(password)){
            Dbug.e(tag, "PWD is null");
            password = ARGS_AP_PWD_NONE;
        }

        if (mNotifyDialog != null && !mNotifyDialog.isShowing()){
            mNotifyDialog.show(getFragmentManager(), "NotifyDialog");
        }

        if(mWifiHelper != null) {
            if (ARGS_AP_PWD_NONE.equals(password)){
                Dbug.e(tag, "=======connectDevice=====SSID=" + ssid + ", password=" + WifiHelper.WifiCipherType.NONE);
                mWifiHelper.addNetWorkAndConnect(ssid, WifiConfiguration.KeyMgmt.NONE + "", WifiHelper.WifiCipherType.NONE);
            } else {
                mConnectingSSID = ssid;
                Dbug.e(tag, "=======connectDevice=====SSID=" + ssid + ", password=" + password);
                mWifiHelper.addNetWorkAndConnect(ssid, password, WifiHelper.WifiCipherType.WPA);
            }
        }
    }
    public void disconnectDevice() {
        Dbug.d(tag, "disconnectDevice-----------------");
        CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_DISABLE_DEVICE_WIFI, ARGS_DISABLE_DEVICE_WIFI);

        handler.removeCallbacks(closeSocket);
        handler.postDelayed(closeSocket, 5000L);
    }

    private final Runnable closeSocket = new Runnable() {
        @Override
        public void run() {
            sendCommandToService(IConstant.SERVICE_CMD_CLOSE_SOCKET);
            removeCurrentNetwork();
            mWifiHelper.connectOtherWifi(IConstant.WIFI_PREFIX);
        }
    };

    private final Runnable limitTimeConnect = new Runnable() {
        @Override
        public void run() {
            boolean isConnectDev =  CommandHub.getInstance().isActive();
            if(!isConnectDev){
                if(mNotifyDialog != null && mNotifyDialog.isShowing()){
                    mNotifyDialog.dismiss();
                }
                isReadyToConnect = false;
                if(mConnectionErrorNotify != null){
                    if (mConnectionErrorNotify.isShowing()){
                        mConnectionErrorNotify.dismiss();
                    }
                    mConnectionErrorNotify.setContent(R.string.connection_timeout);
                    if(!mConnectionErrorNotify.isShowing()){
                        mConnectionErrorNotify.show(getFragmentManager(), "ConnectionErrorNotify");
                    }
                    Dbug.e(tag, "connect device wifi time over 60s, so stop connect device wifi! ");
                }else{
                    Dbug.e(tag, "limitTimeConnect mConnectionErrorNotify is null ");
                }
            }
        }
    };

    private final static int MSG_ENTER_WORKSPACE = 0X100;
    /**Request UI description text*/
    private void requestData(final StateInfo stateInfo){
        ParseHelper parseHelper = ParseHelper.getInstance();
        parseHelper.requestDescriptionText(getApplicationContext(), stateInfo.getParam()[1], new ParseHelper.ResponseListener() {
            @Override
            public void onResponse(boolean isSuccess) {
                if (isSuccess) {
                    Dbug.d(tag, "----------requestData---------:");
                    if (mConnectionErrorNotify != null && mConnectionErrorNotify.isShowing()) {
                        mConnectionErrorNotify.dismiss();
                    }
                    if (mNotifyDialog != null && mNotifyDialog.isShowing()) {
                        mNotifyDialog.dismiss();
                    }
                    if (mPasswordErrorDialog != null && mPasswordErrorDialog.isShowing()) {
                        mPasswordErrorDialog.dismiss();
                    }
                    handler.sendEmptyMessageDelayed(MSG_ENTER_WORKSPACE, 500L);
                    Locale locale = AppUtil.getLanguage(stateInfo.getParam()[0]);
                    setLanguage(locale);
                    sendBroadcast(new Intent(ACTION_DEVICE_LANG_CHANGED));
                } else {
                    if (mNotifyDialog != null && mNotifyDialog.isShowing()) {
                        mNotifyDialog.dismiss();
                    }
                    handler.removeMessages(MSG_ENTER_WORKSPACE);
                    if (mDeviceCloseWifiDialog != null && mDeviceCloseWifiDialog.isShowing()) {
                        mDeviceCloseWifiDialog.dismiss();
                    }

                    if(readDataErrDialog == null){
                        readDataErrDialog =  new NotifyDialog(R.string.dialog_tip, R.string.request_failure, R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                            @Override
                            public void onClick() {
                                if(readDataErrDialog != null){
                                    readDataErrDialog.dismiss();
                                }
                                release();
                            }
                        });
                        readDataErrDialog.setCancelable(false);
                    }
                    if(!readDataErrDialog.isShowing()){
                        readDataErrDialog.show(getFragmentManager(), "read_data_error");
                    }

                }
            }
        });
    }

    private final Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what){
                case MSG_ENTER_WORKSPACE:

                    Dbug.d(tag, "=============Wifi is connected >>> isInBackground==" + isNeedReconnection);
                    if (!isNeedReconnection && BaseActivity.this instanceof LauncherActivity){
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                startActivity(new Intent(BaseActivity.this, MainActivity.class));
                                finish();
                            }
                        }, 1000L);
                    } else {
                        Dbug.d(tag, "Wifi is connected >>> isInBackground=="+isNeedReconnection);
                        Intent intent = new Intent(ACTION_DEVICE_CONNECTION_SUCCESS);
                        sendBroadcast(intent);
                    }
                    break;
            }
            return false;
        }
    });

    private void setLanguage(Locale locale){
        if (locale == null){
            return;
        }
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getApplicationContext().getResources().updateConfiguration(config, null);
//        Dbug.e(tag, "get current language:" + getResources().getConfiguration().locale.getCountry());
    }

    public NotifyDialog getNotifyDialog(){
        return mNotifyDialog;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Dbug.i(tag, ":=========onCreate==============isReadyToConnect=" + isReadyToConnect);
        mApplication = (MainApplication) getApplication();
        mWifiHelper = WifiHelper.getInstance(getApplicationContext());
        ActivityStack.getInstance().pushActivity(this);

        if (mNotifyDialog == null){
            mNotifyDialog = new NotifyDialog(true, R.string.connecting, R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                @Override
                public void onClick() {
                    release();
                }
            });
        }

        if (mUsbModeDialog == null){
            mUsbModeDialog = new NotifyDialog(R.string.dialog_tip, R.string.device_in_usb_mode,
                    R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                @Override
                public void onClick() {
                    Dbug.d(tag, "mUsbModeDialog : mUsbModeDialog exit");
                    release();
                }
            });
        }

        if (mConnectionErrorNotify == null){
            Dbug.w(tag, "ACTION_DEVICE_CONNECTION_ERROR:new NotifyDialog");
            mConnectionErrorNotify = new NotifyDialog(R.string.dialog_tip, R.string.connect_failed, R.string.offline_browse,
                    R.string.confirm, new NotifyDialog.OnNegativeClickListener() {
                @Override
                public void onClick() {
                   enterOfflineMode();
                }
            }, new NotifyDialog.OnPositiveClickListener() {
                @Override
                public void onClick() {
                    if (mConnectionErrorNotify != null && mConnectionErrorNotify.isShowing()){
                        mConnectionErrorNotify.dismiss();
                    }
                    sendCommandToService(IConstant.SERVICE_CMD_CLOSE_SOCKET);
                    PreferencesHelper.remove(getApplicationContext(), CURRENT_SSID);
                    PreferencesHelper.remove(getApplicationContext(), CURRENT_PWD);
                    searchDevice();
                }
            });
        }

        if(devRejectDialog == null){
            devRejectDialog = new NotifyDialog(R.string.dialog_tip, R.string.reject_connection, R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                @Override
                public void onClick() {
                    if(devRejectDialog != null){
                        devRejectDialog.dismiss();
                    }
                    release();
                }
            });
            devRejectDialog.setCancelable(false);
        }

        if (this instanceof LauncherActivity){
            /**Check if Wi-Fi enable */
            if (!mWifiHelper.isWifiOpen()) {
                NotifyDialog notifyDialog = new NotifyDialog(R.string.dialog_tip, R.string.wifi_is_disable, R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                    @Override
                    public void onClick() {
                        release();
                    }
                });
                if(!notifyDialog.isShowing()){
                    notifyDialog.show(getFragmentManager(), "WiFiDisable");
                }
            } else {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        searchDevice();
                    }
                }, 2000L);
                new Thread(getServerVersionInfo, "ServerVersionInfo").start();
//                searchDevice();
            }
        }
        registerReceiver();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Dbug.i(tag, "=============onStart==isNeedReconnection=:" + isNeedReconnection);
    }

    private void registerReceiver(){
        IntentFilter intentFilter = new IntentFilter(IAction.ACTION_DEVICE_WIFI_DISABLED);
        intentFilter.addAction(ACTION_SDCARD_STATE);
        intentFilter.addAction(ACTION_DEVICE_CONNECTION_ERROR);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(ACTION_REJECT_CONNECTION);
        intentFilter.addAction(ACTION_REQUEST_UI_DESCRIPTION);
        intentFilter.addAction(ACTION_QUIT_APP);
        intentFilter.addAction(ACTION_FORMAT_SDCARD);
        intentFilter.addAction(ACTION_RESET_DEVICE);
        intentFilter.addAction(ACTION_ENTER_OFFLINE_MODE);
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        intentFilter.addAction(ACTION_DEVICE_IN_USB_MODE);
        intentFilter.addAction(ACTION_CLOSE_DEV_WIFI);
        intentFilter.addAction(ACTION_INIT_CTP_SOCKET_SUCCESS);
        intentFilter.addAction(ACTION_REAR_CAMERA_PLUG_STATE);
        getApplicationContext().registerReceiver(mReceiver, intentFilter);
    }

    private void enterOfflineMode(){
        if (mNotifyDialog != null && mNotifyDialog.isShowing()){
            mNotifyDialog.dismiss();
        }
        if (mConnectionErrorNotify != null && mConnectionErrorNotify.isShowing()){
            mConnectionErrorNotify.dismiss();
        }
        sendCommandToService(IConstant.SERVICE_CMD_CLOSE_SOCKET);
        Intent intent = new Intent(BaseActivity.this, BrowseFileActivity.class);
        startActivity(intent);
        mApplication.setIsOffLineMode(true);
    }

    private void searchDevice(){
        final List<ScanResult> list = mWifiHelper.getSpecifiedSSIDList(WIFI_PREFIX);
        if (list != null && list.size() >= 0){
            final DeviceListDialog deviceListDialog = new DeviceListDialog();
            deviceListDialog.show(getFragmentManager(), "DeviceListDialog");
            deviceListDialog.setOnItemClickListener(new DeviceListDialog.OnItemClickListener() {
                @Override
                public void onItemClick(boolean isConnected, String ssid, String password) {
                    Dbug.e(tag, "~~~~~~~~~~~searchDevice()~~~~list.size() =>" + list.size() + ", isConnected=" + isConnected);
                    if (isConnected) {
                        Dbug.e(tag, " == initSocket == 005");
                        isSocketConnecting = false;
                        initSocket();
                    } else {
                        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                        String currentSSID = sharedPreferences.getString(CURRENT_SSID, null);
                        if(!TextUtils.isEmpty(currentSSID)){
                            if(!currentSSID.equals(ssid)){
                                sendBroadcast(new Intent(ACTION_CONNECT_OTHER_DEVICE));
                            }
                        }
                        PreferencesHelper.putStringValue(MainApplication.getApplication(), CURRENT_SSID, ssid);
                        PreferencesHelper.putStringValue(MainApplication.getApplication(), CURRENT_PWD, password);
                        connectDevice(ssid, password);
                    }
                }
            });
        } else {
            Dbug.e(tag, "No " + WIFI_PREFIX);
        }
    }

    public void showToast(String info) {
        if (mToastShort != null) {
            mToastShort.setText(info);
        } else {
            mToastShort = Toast.makeText(this, info, Toast.LENGTH_SHORT);
        }
        mToastShort.show();
    }

    public void showToast(int info) {
        showToast(getResources().getString(info));
    }

    public void showToastLong(String msg) {
        if (mToastLong != null) {
            mToastLong.setText(msg);
        } else {
            mToastLong = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        }
        mToastLong.show();
    }

    public void showToastLong(int msg) {
        showToastLong(getResources().getString(msg));
    }

    protected void changeFragment(int containerId, Fragment fragment) {
        changeFragment(containerId, fragment, null);
    }

    protected void changeFragment(int containerId, Fragment fragment, String tag) {
        if(fragment == null){return;}
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(containerId, fragment, tag);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commitAllowingStateLoss();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if(mWifiHelper == null){
            mWifiHelper = WifiHelper.getInstance(getApplicationContext());
        }
        if(mApplication == null){
            mApplication = (MainApplication) getApplication();
        }

        if(this instanceof MainActivity){
            if(mApplication.getIsOffLineMode()){
                if(mConnectionErrorNotify != null && !mConnectionErrorNotify.isShowing()) {
                    mConnectionErrorNotify.show(getFragmentManager(), "ConnectionErrorNotify");
                }
            }
        }


        Dbug.i(tag, "=============onResume==isNeedReconnection=:" + isNeedReconnection + ", isReadyToConnect==" + isReadyToConnect);

        if (isNeedReconnection && !(BaseActivity.this instanceof LauncherActivity) || isReadyToConnect){
            if((this instanceof BrowseFileActivity)){
                Dbug.w(tag, "=============onResume==isBrowsing=:" + mApplication.getIsBrowsing());
                if(mApplication.getIsBrowsing()){
                    isNeedReconnection = false;
                    return;
                }else{
                    setResult(BROWSE_ACTIVITY_RESULT_OK);
                    BaseActivity.this.finish();
                }
            }
            SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
            String currentSSID = sharedPreferences.getString(CURRENT_SSID, null);
            String currentPWD = sharedPreferences.getString(CURRENT_PWD, null);
            newConnectDevice(currentSSID, currentPWD);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        isNeedReconnection = AppUtil.isAppInBackground(MainApplication.getApplication());
        handler.removeCallbacks(limitTimeConnect);
        Dbug.i(tag, "=============onStop==isNeedReconnection=:" + isNeedReconnection);
        if (isNeedReconnection){
            if(BaseActivity.this instanceof BrowseFileActivity){
                if(!mApplication.getIsBrowsing()){
                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_EXIT_BROWSING_MODE, ARGS_EXIT_BROWSING_MODE);
                }else{
                    return;
                }
            }
            disconnectDevice();
        } else {
            isNeedReconnection = false;
        }

        if (mNotifyDialog != null && mNotifyDialog.isShowing()) {
            mNotifyDialog.dismiss();
        }
        if (mConnectionErrorNotify != null && mConnectionErrorNotify.isShowing()) {
            mConnectionErrorNotify.dismiss();
        }
        if (mPasswordErrorDialog != null && mPasswordErrorDialog.isShowing()) {
            mPasswordErrorDialog.dismiss();
        }
        if (mUsbModeDialog != null && mUsbModeDialog.isShowing()) {
            mUsbModeDialog.dismiss();
        }

    }

    private final Runnable connectDeviceWiFi = new Runnable() {
        @Override
        public void run() {
            boolean isSocketConnect = CommandHub.getInstance().isActive();
            Dbug.e(tag, "connectDeviceWiFi is start! isSocketConnect : " +isSocketConnect);
            if(isSocketConnect){
                handler.removeCallbacks(connectDeviceWiFi);
                return;
            }
            SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
            String currentSSID = sharedPreferences.getString(CURRENT_SSID, null);
            String currentPWD = sharedPreferences.getString(CURRENT_PWD, null);
            newConnectDevice(currentSSID, currentPWD);
        }
    };

    private final Runnable getServerVersionInfo = new Runnable() {
        @Override
        public void run() {
            String localVersionInfo =  AppUtil.getFromRaw(getApplicationContext(), R.raw.local_version_info);
            DeviceVersionInfo localDeviceVersionInfo = AppUtil.getLocalVersionInfo(localVersionInfo);
            if(localDeviceVersionInfo != null){
                String[] products = localDeviceVersionInfo.getProductTypes();
                List<String> productList = new ArrayList<>();
                Collections.addAll(productList, products);
                AppUtil.failedNum = -1;
                AppUtil.downloadTxt(productList, false, mApplication.getAppName());
            }
        }
    };

    protected void removeCurrentNetwork() {
        WifiManager wifiManager = (WifiManager) MainApplication.getApplication().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if(wifiInfo == null || TextUtils.isEmpty(wifiInfo.getSSID())){
            Dbug.e(tag, "-=-=-=wifiInfo is null or  wifiInfo.getSSID() is null");
            return;
        }
        String mSSID = wifiInfo.getSSID();
        if(mSSID.contains("\"")){
            mSSID = mSSID.replace("\"", "");
        }
        if(mSSID.contains(" ")){
            mSSID = mSSID.replace(" ", "");
        }
        if (mSSID.startsWith(WIFI_PREFIX)) {
            Dbug.w(tag, "Remove networkId:" + wifiInfo.getNetworkId());
            mWifiHelper.remoteNetWork(wifiInfo.getNetworkId());
//            if (isReadyToConnect){
//                isReadyToConnect = false;
//            }
        }
    }


    @Override
    protected void onDestroy() {
        if (mDeviceCloseWifiDialog != null){
            mDeviceCloseWifiDialog.dismiss();
            mDeviceCloseWifiDialog = null;
        }
        isNeedReconnection = false;
        if(!(this instanceof BrowseFileActivity)){
            isReadyToConnect = false;
        }
        handler.removeCallbacksAndMessages(null);
        getApplicationContext().unregisterReceiver(mReceiver);
        ActivityStack.getInstance().popActivity(this);
        super.onDestroy();
    }

    public void sendCommandToService(int cmd){
        String localApkVersion = mApplication.getAppLocalVersion();
        if(TextUtils.isEmpty(localApkVersion)){
            String versionInfo = AppUtil.getFromRaw(getApplicationContext(), R.raw.local_version_info);
            DeviceVersionInfo localVersionInfo = AppUtil.getLocalVersionInfo(versionInfo);
            if(localVersionInfo != null){
                String appVersion = localVersionInfo.getLocalAndroidVersion();
                if(!TextUtils.isEmpty(appVersion)){
                    mApplication.setAppLocalVersion(appVersion);
                }
            }
        }
        Intent intent = new Intent(getApplicationContext(), CommunicationService.class);
        intent.putExtra(IConstant.SERVICE_CMD, cmd);
        intent.putExtra(IConstant.APP_VERSION, localApkVersion);
        getApplicationContext().startService(intent);
    }

    public void release(){
        Dbug.w(tag, "APP release...isNeedReconnection=" + isNeedReconnection);

        if (mNotifyDialog != null && mNotifyDialog.isShowing()){
            mNotifyDialog.dismiss();
        }
        CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_DISABLE_DEVICE_WIFI, ARGS_DISABLE_DEVICE_WIFI);

        sendCommandToService(IConstant.SERVICE_CMD_CLOSE_SOCKET);

        try {
            /**Disconnect device Wi-Fi*/
            if (mWifiHelper != null) {
                removeCurrentNetwork();
                mWifiHelper.connectOtherWifi(IConstant.WIFI_PREFIX);
            }

            /**Remove current SSID & PWD from SharedPreferences*/
            PreferencesHelper.remove(getApplicationContext(), CURRENT_SSID);
            PreferencesHelper.remove(getApplicationContext(), CURRENT_PWD);

            /**Remove device version info from SharedPreferences*/
            PreferencesHelper.remove(getApplicationContext(), DEVICE_VERSION_MSG);

            MainApplication.getApplication().stopService(new Intent(MainApplication.getApplication(), CommunicationService.class));
            ActivityStack.getInstance().clearAllActivity();

            Dbug.e(tag, "Exit app");
            String versionPath = AppUtil.splicingFilePath(mApplication.getAppName(), VERSION, null, null);
            File versionFile = new File(versionPath);
            if (versionFile.exists()) {
                AppUtil.deleteFile(versionFile);
            }
            if (TextUtils.isEmpty(mApplication.getDeviceUUID())) {
                Dbug.e(tag, "Device UUID is null");
                return;
            }
            String thumbPath = AppUtil.splicingFilePath(mApplication.getAppName(), mApplication.getDeviceUUID(), THUMB, null);
            if(TextUtils.isEmpty(thumbPath)){
                return;
            }
            File thumbFile = new File(thumbPath);
            if (thumbFile.exists()) {
                AppUtil.deleteFile(thumbFile);
            }
        } catch (Exception e) {
            Dbug.e(tag, "Exception : " + e.getMessage());
            System.exit(0);
        }
    }

    public void onMountState(String mountState, final String msg) {
//        Dbug.e(tag, "mountState:" + mountState);
        switch (mountState){
            case ARGS_SDCARD_ERROR:
                if(!TextUtils.isEmpty(msg)){
                    showToastLong(msg);
                }
                break;
            case ARGS_SDCARD_OFFLINE:
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mApplication.setSdcardState(false);
                        cleanCache();
                        if (BaseActivity.this instanceof MainActivity || BaseActivity.this instanceof LauncherActivity) {
                            showToastLong(getString(R.string.sdcard_offline));
                        }else {
                            showToastLong(getString(R.string.sdcard_offline));
                            if(BaseActivity.this instanceof BrowseFileActivity){
                                setResult(BROWSE_ACTIVITY_RESULT_OK);
                            }
                            BaseActivity.this.finish();
                        }
                    }
                });
                break;
            case ARGS_SDCARD_ONLINE:
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mApplication.setSdcardState(true);
                        sendBroadcast(new Intent(ACTION_SDCARD_ONLINE));
                        if(BaseActivity.this instanceof MainActivity){
                            showToastLong(getString(R.string.sdcard_online));
                        }
                    }
                });
                break;
            default:
                if(!TextUtils.isEmpty(msg)){
                    showToastLong(msg);
                }
                break;
        }
    }

    private void cleanCache(){
        if(null != mApplication.getDeviceUUID()) {
            String cacheImagePath = AppUtil.splicingFilePath(mApplication.getAppName(), mApplication.getDeviceUUID(), IMAGE, null);
            String cacheVideoPath = AppUtil.splicingFilePath(mApplication.getAppName(),mApplication.getDeviceUUID(), VIDEO, null);
            File cacheImage = new File(cacheImagePath);
            File cacheVideo = new File(cacheVideoPath);
            try{
                if(cacheImage.exists()){
                    AppUtil.deleteFile(cacheImage);
                }
                if(cacheVideo.exists()){
                    AppUtil.deleteFile(cacheVideo);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
