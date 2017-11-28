package com.jieli.stream.player.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.jieli.lib.stream.beans.DeviceVersionInfo;
import com.jieli.lib.stream.beans.StateInfo;
import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.tools.ParseHelper;
import com.jieli.stream.player.MainApplication;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseActivity;
import com.jieli.stream.player.data.beans.FTPLoginInfo;
import com.jieli.stream.player.data.beans.SystemInfo;
import com.jieli.stream.player.tool.CommandManager;
import com.jieli.stream.player.tool.FtpHandlerThread;
import com.jieli.stream.player.tool.WifiHelper;
import com.jieli.stream.player.ui.dialog.BrowseFileDialog;
import com.jieli.stream.player.ui.dialog.ModifyDeviceNameDialog;
import com.jieli.stream.player.ui.dialog.ModifyDevicePasswordDialog;
import com.jieli.stream.player.ui.dialog.NotifyDialog;
import com.jieli.stream.player.util.AppUtil;
import com.jieli.stream.player.util.DataCleanManager;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.FTPClientUtil;
import com.jieli.stream.player.util.IConstant;
import com.jieli.stream.player.util.PreferencesHelper;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PersonalSettingActivity extends BaseActivity implements BrowseFileDialog.OnSelectResultListener{
    private final String tag = getClass().getSimpleName();
    private WifiHelper mWifiHelper;
    private FtpHandlerThread mWorkHandlerThread;
    private String updateFilePath = "";
    private String updateTXTPath = "";
    private boolean upgradeBtnIsClicked = false;
    private boolean upgradeFileExist = false;
    private int updateType = -1;
    private int updateAPKPos = -1;
    private int updateSDKPos = -1;
    private ListView mListView;
    private NotifyDialog upgradeFirmwareDialog, upgradingDialog;
    private NotifyDialog mFormatDialog, mResetDialog;
    private NotifyDialog openWifiDialog, checkUpdateDialog, upgradeTxtDialog;
    private NotifyDialog versionInfoDialog, recordErrorDialog, useNetWorkDialog;
    private String otherWifiSSID;
    private boolean isConnecting = false;
    private boolean isSDOffLine = false;
    private FTPClientUtil ftpClientUtil;
    private FTPClient mFTPClient;
    private String dImageThumbPath, dVideoThumbPath;


    private static final int UPLOAD_FTP_FILE = 0xa1;


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action){
                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    boolean isWifiConnect;
                    if(isConnecting){
                        if(TextUtils.isEmpty(otherWifiSSID)){
                            otherWifiSSID = mWifiHelper.getOtherWifiSSID();
                        }else{
                            if(!otherWifiSSID.equals(mWifiHelper.getOtherWifiSSID())){
                                otherWifiSSID = mWifiHelper.getOtherWifiSSID();
                            }
                        }
                    }
                    if(networkInfo == null) {
                        Dbug.e(tag, "networkInfo is null");
                        return;
                    }
                    isWifiConnect = (networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED);
                    Dbug.e(tag, " networkInfo.getType() : " +networkInfo.getType());
                    if(networkInfo.getType() == ConnectivityManager.TYPE_MOBILE){
                        if(isWifiConnect && isConnecting){
                            mHandler.removeCallbacks(tryToLoginSever);
                            mHandler.postDelayed(tryToLoginSever, 2000L);
                        }
                    }else if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI){
                        if(wifiInfo == null || TextUtils.isEmpty(wifiInfo.getSSID())){
                            Dbug.e(tag, "wifiInfo is null or  wifiInfo.getSSID() is null");
                            Dbug.e(tag, " isConnecting :" + isConnecting + " available :" + networkInfo.isAvailable() + " otherWifiSSID :" + otherWifiSSID);
                            if(isConnecting && TextUtils.isEmpty(otherWifiSSID) && networkInfo.isAvailable()){
                                if(useNetWorkDialog != null && !useNetWorkDialog.isShowing()){
                                    useNetWorkDialog.show(getFragmentManager(), "use_network");
                                }
                            }
                            return;
                        }
                        String mSSID = wifiInfo.getSSID().replace("\"", "");
                        mSSID = mSSID.replace(" ", "");
                        String otherSSID = otherWifiSSID;
                        if(!TextUtils.isEmpty(otherWifiSSID)){
                            otherSSID = otherSSID.replace("\"", "");
                            otherSSID = otherSSID.replace(" ", "");
                        }
                        Dbug.e(tag, "Is WIFi connected: " + isWifiConnect + "  ,Current Wifi SSID :" + mSSID +
                                "  ,otherWifiSSID : " +otherWifiSSID + " ,isConnecting : " +isConnecting);
                        if(isWifiConnect){
                            if(isConnecting) {
                                if (mSSID.equals(otherSSID)) {
                                    mHandler.removeCallbacks(tryToLoginSever);
                                    mHandler.postDelayed(tryToLoginSever, 2000L);
                                } else {
                                    mWifiHelper.connectOtherWifi(IConstant.WIFI_PREFIX);
                                    otherWifiSSID = mWifiHelper.getOtherWifiSSID();
                                }
                            }
                        }
                    }
                    break;
                case ACTION_ALLOW_FIRMWARE_UPGRADE:
                    final StateInfo mStateInfo = (StateInfo)intent.getSerializableExtra(KEY_SDCARD_STATE);
                    Dbug.e(tag, "mStateInfo ==> " + mStateInfo.getParam()[0]);
                    if(ARGS_FIRMWARE_UPGRADE_SUCCESS.equals(mStateInfo.getParam()[0])){
                        String filename = updateFilePath.substring(updateFilePath.lastIndexOf("/") + 1);
                        upgradeFileExist = false;
                        Dbug.e(tag, "filename ==> " + filename);
                        FTPLoginInfo ftpLoginInfo = new FTPLoginInfo(INSIDE_FTP_HOST_NAME, DEFAULT_FTP_PORT, INSIDE_FTP_USER_NAME, INSIDE_FTP_PASSWORD);
                        if(mWorkHandlerThread != null && !TextUtils.isEmpty(filename)){
                            Dbug.e(tag, "mWorkHandlerThread ==> tryToUploadFile" );
                            mWorkHandlerThread.tryToUploadFile(updateFilePath, filename, ftpLoginInfo);
                        }
                    }else{
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (ARGS_IN_RECORDING.equals(CommandManager.getInstance().getDeviceStatus(CMD_GET_RECORDING_STATUS))) {
                                    if (!recordErrorDialog.isShowing()) {
                                        recordErrorDialog.show(getFragmentManager(), "record_error");
                                    }
                                    return;
                                }
                                if (upgradeFirmwareDialog != null) {
                                    upgradeFirmwareDialog.dismiss();
                                }
                                if (upgradingDialog != null) {
                                    upgradingDialog.dismiss();
                                }
                                String[] param = mStateInfo.getParam();
                                if (param == null || param.length < 0) {
                                    showToast(R.string.update_firmware_apk_failed);
                                    return;
                                }
                                StringBuilder stringBuffer = new StringBuilder();
                                for (String arg : param) {
                                    if (arg.equals("-1")) {
                                        continue;
                                    }
                                    stringBuffer.append(arg);
                                }
                                if (!TextUtils.isEmpty(stringBuffer.toString())) {
                                    String failedStr = getString(R.string.update_firmware_apk_failed) + "\n" + stringBuffer.toString();
                                    showToastLong(failedStr);
                                    Dbug.e(tag, "update failed reason : " + stringBuffer.toString());
                                } else {
                                    showToast(R.string.update_firmware_apk_failed);
                                }
                            }
                        });
                    }
                    break;
                case ACTION_DEVICE_LANG_CHANGED:
                    updateTextView();
                    break;
                case ACTION_CHANGE_SSID_SUCCESS:
                case ACTION_CHANGE_PWD_SUCCESS:
                    removeCurrentNetwork();
                    if(mWifiHelper != null){
                        mWifiHelper.startScan();
                    }
                    startActivity(new Intent(PersonalSettingActivity.this, LauncherActivity.class));
                    finish();
                    break;
                case ACTION_SPECIAL_DATA:
                    final StateInfo stateInfo = (StateInfo) intent.getSerializableExtra(KEY_SPECIAL_STATE);
                    String param1 = stateInfo.getParam()[0];
                    Dbug.w(tag, " param1 : " + param1);
                    String cmdResult = stateInfo.getCmdNumber();
                    switch (TextUtils.isEmpty(cmdResult) ? ARGS_NULL : cmdResult) {
                        case CMD_GET_RECORDING_STATUS:
                            break;
                    }
                    break;
                case ACTION_UPDATE_APK_SDK:
                    int updateType = intent.getIntExtra(UPDATE_TYPE, -1);
                    Dbug.e(tag, "ACTION_UPDATE_APK_SDK updateType: " + updateType);
                    switch (updateType){
                        case AppUtil.UPGRADE_APK_TYPE:
                            View view;
                            if(null != mListView.getAdapter() && updateAPKPos > -1){
                                view = mListView.getAdapter().getView(updateAPKPos, null, null);
                                if(view != null ){
                                    mListView.performItemClick(view, updateAPKPos, mListView.getItemIdAtPosition(updateAPKPos));
                                }
                            }
                            break;
                        case AppUtil.UPGRADE_SDK_TYPE:
                            View itemView;
                            if(null != mListView.getAdapter() && updateSDKPos > -1){
                                itemView = mListView.getAdapter().getView(updateSDKPos, null, null);
                                if(itemView != null){
                                    mListView.performItemClick(itemView, updateSDKPos, mListView.getItemIdAtPosition(updateSDKPos));
                                }
                            }
                            break;
                    }
                    break;
                case ACTION_INIT_CTP_SOCKET_SUCCESS:
                    if(upgradeFirmwareDialog != null && upgradeFirmwareDialog.isShowing()){
                        upgradeFirmwareDialog.dismiss();
                    }
                    if(upgradingDialog != null && upgradingDialog.isShowing()){
                        upgradingDialog.dismiss();
                    }
                    if(openWifiDialog != null && openWifiDialog.isShowing()){
                        openWifiDialog.dismiss();
                    }
                    if(upgradeFileExist){
                        mHandler.removeCallbacks(cancelUpdateDialog);
                        Message message = Message.obtain();
                        Bundle bundle = new Bundle();
                        bundle.putString(UPDATE_TEXT, updateTXTPath);
                        bundle.putString(UPDATE_FILE, updateFilePath);
                        message.what = UPLOAD_FTP_FILE;
                        message.setData(bundle);
                        mHandler.removeMessages(UPLOAD_FTP_FILE);
                        mHandler.sendMessageDelayed(message, 2000L);
                    }
                    break;
            }
        }
    };

    private void updateTextView(){
        mListView = (ListView) findViewById(android.R.id.list);
        TextView title = (TextView) findViewById(R.id.top_title);
        title.setText(R.string.general_settings);
        ImageView backBtn = (ImageView) findViewById(R.id.back);
        backBtn.setOnClickListener(controlBtnClick);
        long cacheSize = getCacheSize();
        final List<SystemInfo> mList = new ArrayList<>();
        String[] info = getResources().getStringArray(R.array.system_changes_list);
        for (int i = 0; i < info.length; i++){
            String name = info[i];
            SystemInfo systemInfo = new SystemInfo();
            if(name.equals(getString(R.string.check_for_update))){
                updateAPKPos = i;
            }else if(name.equals(getString(R.string.firmware_upgrade))){
                updateSDKPos = i;
            }else if(name.equals(getString(R.string.clear_cache))){
//                name = String.format(name, DataCleanManager.getFormatSize((double) cacheSize));
                name = getString(R.string.clear_cache) +" "+ DataCleanManager.getFormatSize((double) cacheSize);
            }else if(name.equals(getString(R.string.storage_url))){
                String currentPath = ROOT_PATH + File.separator + mApplication.getAppName();
                name = String.format(getString(R.string.storage_url), currentPath);
            }
            systemInfo.setInfoName(name);
            mList.add(systemInfo);
        }

        SystemAdapter mSystemAdapter = new SystemAdapter(this);
        mSystemAdapter.addAll(mList);
        mListView.setAdapter(mSystemAdapter);
        mSystemAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_setting);

        if(TextUtils.isEmpty(dImageThumbPath)){
            dImageThumbPath = AppUtil.splicingFilePath(mApplication.getAppName(), mApplication.getDeviceUUID(), IMAGE, null);
        }
        if(TextUtils.isEmpty(dVideoThumbPath)){
            dVideoThumbPath = AppUtil.splicingFilePath(mApplication.getAppName(), mApplication.getDeviceUUID(), VIDEO, null);
        }

        mWorkHandlerThread = new FtpHandlerThread("personal_setting_thread", getApplicationContext(), mApplication);
        mWorkHandlerThread.start();
        mWorkHandlerThread.setUIHandler(mHandler);

        updateTextView();

        initDialog();

        mListView.setOnItemClickListener(mOnItemClickListener);

        mWifiHelper = WifiHelper.getInstance(getApplicationContext());
        ftpClientUtil = new FTPClientUtil();
        mFTPClient = ftpClientUtil.getFTPClient();

        IntentFilter intentFilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ACTION_ALLOW_FIRMWARE_UPGRADE);
        intentFilter.addAction(ACTION_DEVICE_LANG_CHANGED);
        intentFilter.addAction(ACTION_CHANGE_SSID_SUCCESS);
        intentFilter.addAction(ACTION_CHANGE_PWD_SUCCESS);
        intentFilter.addAction(ACTION_SPECIAL_DATA);
        intentFilter.addAction(ACTION_UPDATE_APK_SDK);
        intentFilter.addAction(ACTION_INIT_CTP_SOCKET_SUCCESS);
        getApplicationContext().registerReceiver(mReceiver, intentFilter);
    }

    private final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            switch (position) {
                case 0://Modify Wi-Fi name
                    ModifyDeviceNameDialog modifyWifiDialog = ModifyDeviceNameDialog.newInstance();
                    modifyWifiDialog.show(getFragmentManager(), "ModifyDeviceNameDialog");
                    break;
                case 1://Modify Wi-Fi pwd
                    ModifyDevicePasswordDialog modifyDevicePasswordDialog = ModifyDevicePasswordDialog.newInstance();
                    modifyDevicePasswordDialog.show(getFragmentManager(), "ModifyDevicePasswordDialog");
                    break;
                case 2://Format
                    if (mFormatDialog == null) {
                        mFormatDialog = new NotifyDialog(R.string.dialog_tip, R.string.ask_if_format,
                                R.string.cancel, R.string.confirm, new NotifyDialog.OnNegativeClickListener() {
                            @Override
                            public void onClick() {
                                if (mFormatDialog != null) {
                                    mFormatDialog.dismiss();
                                }
                            }
                        }, new NotifyDialog.OnPositiveClickListener() {
                            @Override
                            public void onClick() {
                                Dbug.d(tag, "onClick formatDialog:");
                                if (mFormatDialog != null) {
                                    mFormatDialog.dismiss();
                                }
                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_FORMAT_SDCARD, ARGS_FORMAT_SDCARD);
                            }
                        });
                    }
                    if (!mFormatDialog.isShowing()) {
                        mFormatDialog.show(getFragmentManager(), "formatDialog");
                    }
                    break;
                case 3://Reset device
                    if (mResetDialog == null) {
                        mResetDialog = new NotifyDialog(R.string.dialog_tip, R.string.ask_to_restart_device_and_app,
                                R.string.cancel, R.string.confirm, new NotifyDialog.OnNegativeClickListener() {
                            @Override
                            public void onClick() {
                                Dbug.d(tag, "cancel resetDialog!");
                                if (mResetDialog != null) {
                                    mResetDialog.dismiss();
                                }
                            }
                        }, new NotifyDialog.OnPositiveClickListener() {
                            @Override
                            public void onClick() {
                                Dbug.d(tag, "onClick resetDialog:");
                                if (mResetDialog != null) {
                                    mResetDialog.dismiss();
                                }
                                showToastLong(getString(R.string.update_finish));
                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_RESET_DEVICE, ARGS_RESET_DEVICE);
                            }
                        });
                    }
                    if (!mResetDialog.isShowing()) {
                        mResetDialog.show(getFragmentManager(), "resetDialog");
                    }
                    break;
                case 4://Check for update
                    updateType = AppUtil.UPGRADE_APK_TYPE;
                    final String checkUpdateMessage = getString(R.string.upgrade_firmware_tip) + '\n' + getString(R.string.current_network) +
                            showNetWorkType(WifiHelper.getConnectedType(getApplicationContext())) + '\n' +
                            getString(R.string.operation) + ": " + getString(R.string.check_for_update);
                    if (checkUpdateDialog == null) {
                        checkUpdateDialog = new NotifyDialog(getString(R.string.dialog_tip), checkUpdateMessage, R.string.cancel, R.string.confirm,
                                new NotifyDialog.OnNegativeClickListener() {
                                    @Override
                                    public void onClick() {
                                        if (checkUpdateDialog != null) {
                                            checkUpdateDialog.dismiss();
                                        }
                                    }
                                }, new NotifyDialog.OnPositiveClickListener() {
                            @Override
                            public void onClick() {
                                if (checkUpdateDialog != null) {
                                    checkUpdateDialog.dismiss();
                                }
                                upgradeFileExist = false;
                                mHandler.removeCallbacks(checkUpdate);
                                mHandler.post(checkUpdate);
                            }
                        });
                    }
                    checkUpdateDialog.setContent(checkUpdateMessage);
                    if (!checkUpdateDialog.isShowing()) {
                        checkUpdateDialog.show(getFragmentManager(), "check_update_apk");
                    }
                    break;
                case 5://firmware upgrade
                    if (!mApplication.isSdcardState()) {
                        showToastLong(getString(R.string.sdcard_error));
                        return;
                    }
                    updateType = AppUtil.UPGRADE_SDK_TYPE;
                    final String checkUpdateInfo = getString(R.string.upgrade_firmware_tip) + '\n' + getString(R.string.current_network) +
                            showNetWorkType(WifiHelper.getConnectedType(getApplicationContext())) + '\n' +
                            getString(R.string.operation) + ": " + getString(R.string.firmware_upgrade);
                    if (checkUpdateDialog == null) {
                        checkUpdateDialog = new NotifyDialog(getString(R.string.dialog_tip), checkUpdateInfo, R.string.cancel, R.string.confirm,
                                new NotifyDialog.OnNegativeClickListener() {
                                    @Override
                                    public void onClick() {
                                        if (checkUpdateDialog != null) {
                                            checkUpdateDialog.dismiss();
                                        }
                                    }
                                }, new NotifyDialog.OnPositiveClickListener() {
                            @Override
                            public void onClick() {
                                if (checkUpdateDialog != null) {
                                    checkUpdateDialog.dismiss();
                                }
                                upgradeFileExist = false;
                                mHandler.removeCallbacks(checkUpdate);
                                mHandler.post(checkUpdate);
                            }
                        });
                    }
                    checkUpdateDialog.setContent(checkUpdateInfo);
                    if (!checkUpdateDialog.isShowing()) {
                        checkUpdateDialog.show(getFragmentManager(), "check_update_sdk");
                    }
                    break;
                case 6: //clean Cache
                    cleanCache();
                    break;
                case 7: //Version info
                    String versionInfo = AppUtil.getFromRaw(getApplicationContext(), R.raw.local_version_info);
                    SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(getApplicationContext());
                    String deviceVersionMsg = sharedPreferences.getString(DEVICE_VERSION_MSG, null);
                    DeviceVersionInfo localVersionInfo = AppUtil.getLocalVersionInfo(versionInfo);
                    DeviceVersionInfo deviceVersionInfo = ParseHelper.parseDeviceVersionText(deviceVersionMsg);
                    String currentAPK = null;
                    String currentSDK = null;
                    if (localVersionInfo != null) {
                        currentAPK = localVersionInfo.getLocalAndroidVersion();
                    }
                    if (deviceVersionInfo != null) {
                        currentSDK = deviceVersionInfo.getFirmwareVersion();
                    }
                    if (!TextUtils.isEmpty(currentAPK) || !TextUtils.isEmpty(currentSDK)) {
                        showVersionInfoDialog(currentAPK, currentSDK);
                    }
                    break;
                case 8: // storage url
                    BrowseFileDialog browseFileDialog = new BrowseFileDialog();
                    browseFileDialog.setOnSelectResultListener(PersonalSettingActivity.this);
                    browseFileDialog.show(getFragmentManager(), "browse_file_dialog");
                    break;
            }
        }
    };

    @Override
    public void onResult(String path) {
        if(!TextUtils.isEmpty(path)){
            String newPathName = path.substring(ROOT_PATH.length());
            if(newPathName.startsWith(File.separator)){
                newPathName = newPathName.substring(newPathName.indexOf(File.separator)+1);
            }
            Dbug.i(tag, " ============= newPathName : " +newPathName+"================");
            if(!newPathName.equals(mApplication.getAppName())){
                PreferencesHelper.putStringValue(getApplicationContext(), KEY_ROOT_PATH_NAME, newPathName);
                mApplication.setAppName(newPathName);
                updateTextView();
                showToastLong(R.string.modify_storage_url_success);
            }
        }
    }

    /**
     * Identify the type of mobile phone network
     */
    private String showNetWorkType(WifiHelper.NetState netState) {
        String result = null;
        if (netState == WifiHelper.NetState.NET_NO) {
            result = getString(R.string.net_type_no);
        } else if (netState == WifiHelper.NetState.NET_2G) {
            result = getString(R.string.net_type_2G);
        } else if (netState == WifiHelper.NetState.NET_3G) {
            result = getString(R.string.net_type_3G);
        } else if (netState == WifiHelper.NetState.NET_4G) {
            result = getString(R.string.net_type_4G);
        } else if (netState == WifiHelper.NetState.NET_WIFI) {
            result = getString(R.string.net_type_wifi);
        }
        return result;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void initDialog() {
        if (upgradeFirmwareDialog == null) {
            upgradeFirmwareDialog = new NotifyDialog(true, R.string.checking_updating);
        }
        if (upgradingDialog == null) {
            upgradingDialog = new NotifyDialog(true, R.string.updating_tip);
        }
        if (useNetWorkDialog == null) {
            useNetWorkDialog = new NotifyDialog(R.string.dialog_tip, R.string.use_network_tip, R.string.cancel, R.string.confirm,
                    new NotifyDialog.OnNegativeClickListener() {
                        @Override
                        public void onClick() {
                            if (useNetWorkDialog != null) {
                                useNetWorkDialog.dismiss();
                            }
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showUpdateFailedMsg(R.string.update_failed);
                                }
                            });
                        }
                    }, new NotifyDialog.OnPositiveClickListener() {
                @Override
                public void onClick() {
                    if (useNetWorkDialog != null) {
                        useNetWorkDialog.dismiss();
                    }
                    mHandler.removeCallbacks(tryToLoginSever);
                    mHandler.postDelayed(tryToLoginSever, 2000L);
                }
            });
            useNetWorkDialog.setCancelable(false);
        }
        if (recordErrorDialog == null) {
            recordErrorDialog = new NotifyDialog(R.string.dialog_tip, R.string.recording_err, R.string.cancel, R.string.confirm,
                    new NotifyDialog.OnNegativeClickListener() {
                        @Override
                        public void onClick() {
                            if (recordErrorDialog != null) {
                                recordErrorDialog.dismiss();
                            }
                            if (upgradeFirmwareDialog != null) {
                                upgradeFirmwareDialog.dismiss();
                            }
                            if (upgradingDialog != null) {
                                upgradingDialog.dismiss();
                            }
                        }
                    }, new NotifyDialog.OnPositiveClickListener() {
                @Override
                public void onClick() {
                    if (recordErrorDialog != null) {
                        recordErrorDialog.dismiss();
                    }
                    //stop record
                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_STOP_RECORD, ARGS_STOP_RECORD);
                    try {
                        SystemClock.sleep(1000L);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (upgradeTxtDialog != null && !upgradeTxtDialog.isShowing()) {
                        upgradeTxtDialog.show(getFragmentManager(), "update_TXT");
                    }
                }
            });
        }
    }

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (PersonalSettingActivity.this.isFinishing()) {
                Dbug.e(tag, " Activity is finishing, so handler can not to do any thing.");
                return false;
            }
            switch (msg.what) {
                case UPLOAD_FTP_FILE:
                    final String localPath = msg.getData().getString(UPDATE_FILE);
                    String txtPath = msg.getData().getString(UPDATE_TEXT);
                    if (upgradeFirmwareDialog != null && upgradeFirmwareDialog.isShowing()) {
                        upgradeFirmwareDialog.dismiss();
                    }
                    if (TextUtils.isEmpty(localPath) || TextUtils.isEmpty(txtPath)) {
                        showToast(R.string.update_firmware_apk_failed);
                        Dbug.e(tag, "UPLOAD_FTP_FILE localPath ==> null ! ");
                        return false;
                    }
                    Dbug.w(tag, "UPLOAD_FTP_FILE localPath ==>  " + localPath);
                    updateFilePath = localPath;
                    updateTXTPath = txtPath;
                    if (upgradeBtnIsClicked) {
                        upgradeBtnIsClicked = false;
                    }
                    if (!CommandHub.getInstance().isActive()) {
                        sendCommandToService(IConstant.SERVICE_CMD_INIT_SOCKET);
                        mApplication.setIsOffLineMode(false);
                        if (getNotifyDialog() != null) {
                            getNotifyDialog().dismiss();
                        }
                    }
                    String updateTxtFileStr = AppUtil.readTxtFile(updateTXTPath);
                    if (TextUtils.isEmpty(updateTxtFileStr)) {
                        showToast(R.string.update_firmware_apk_failed);
                        return false;
                    }
                    if (upgradeTxtDialog == null) {
                        upgradeTxtDialog = new NotifyDialog(getString(R.string.dialog_tip), updateTxtFileStr, R.string.cancel, R.string.confirm,
                                new NotifyDialog.OnNegativeClickListener() {
                                    @Override
                                    public void onClick() {
                                        if (upgradeTxtDialog != null) {
                                            upgradeTxtDialog.dismiss();
                                        }
                                        upgradeFileExist = false;
                                    }
                                }, new NotifyDialog.OnPositiveClickListener() {
                            @Override
                            public void onClick() {
                                if (upgradeTxtDialog != null) {
                                    upgradeTxtDialog.dismiss();
                                }
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        switch (updateType) {
                                            case AppUtil.UPGRADE_APK_TYPE:
                                                File apkFile = new File(localPath);
                                                if (!apkFile.exists()) {
                                                    showToast(getString(R.string.update_failed));
                                                    return;
                                                }
                                                Intent updateAPK = new Intent(Intent.ACTION_VIEW);
                                                updateAPK.setDataAndType(Uri.parse("file://" + apkFile.getPath()), "application/vnd.android.package-archive");
                                                startActivity(updateAPK);
                                                upgradeFileExist = false;
                                                break;
                                            case AppUtil.UPGRADE_SDK_TYPE:
                                                CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_GET_RECORDING_STATUS);
                                                /** send firmware upgrade cmd to device*/
                                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_FIRMWARE_UPGRADE, ARGS_FIRMWARE_UPGRADE);
                                                if (upgradingDialog != null && !upgradingDialog.isShowing()) {
                                                    upgradingDialog.show(getFragmentManager(), "firmware_upgrading");
                                                }
                                                break;
                                        }
                                    }
                                }, 2000L);
                            }
                        });
                        upgradeTxtDialog.setContentTextLeft(true);
                        upgradeTxtDialog.setCancelable(false);
                    }
                    upgradeTxtDialog.setContent(updateTxtFileStr);
                    if (!upgradeTxtDialog.isShowing()) {
                        upgradeTxtDialog.show(getFragmentManager(), "update_TXT");
                    }
                    Dbug.i(tag, "===UPLOAD_FTP_FILE===");
                    break;
                case FtpHandlerThread.MSG_SHOW_MESSAGES:
                    String string = (String) msg.obj;
                    if (string != null) {
                        if (string.equals(getString(R.string.upload_file_success))) {
                            Dbug.e(tag, "upload_file_success == 2333333");
                            /** device will be restart when app send restart cmd to device*/
                            CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_RESTART_DEVICE, ARGS_RESTART_DEVICE);
                            if (upgradeFirmwareDialog != null && upgradeFirmwareDialog.isShowing()) {
                                upgradeFirmwareDialog.dismiss();
                            }
                            if (upgradingDialog != null && upgradingDialog.isShowing()) {
                                upgradingDialog.dismiss();
                            }
                            showToastLong(getString(R.string.update_finish));
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    release();
                                }
                            }, 2000L);
                        } else if (string.equals(getString(R.string.upload_file_failed))) {
                            Dbug.e(tag, "upload_file_failed == 555555");
                            if (upgradeFirmwareDialog != null && upgradeFirmwareDialog.isShowing()) {
                                upgradeFirmwareDialog.dismiss();
                            }
                            if (upgradingDialog != null && upgradingDialog.isShowing()) {
                                upgradingDialog.dismiss();
                            }
                            showToastLong(getString(R.string.update_firmware_apk_failed));
                        }
                    }
                    break;
                default:
                    break;
            }
            return false;
        }
    });

    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            getApplicationContext().unregisterReceiver(mReceiver);
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        if(mWorkHandlerThread != null){
            mWorkHandlerThread.quit();
        }
        if (mFormatDialog != null) {
            mFormatDialog.dismiss();
            mFormatDialog = null;
        }
        if (mResetDialog != null) {
            mResetDialog.dismiss();
            mResetDialog = null;
        }
        if (openWifiDialog != null) {
            openWifiDialog.dismiss();
            openWifiDialog = null;
        }
        if(!TextUtils.isEmpty(dImageThumbPath)){
            dImageThumbPath = null;
        }
        if(!TextUtils.isEmpty(dVideoThumbPath)){
            dVideoThumbPath = null;
        }
        System.gc();
        Dbug.e(tag, "===== onDestroy ====");
        super.onDestroy();
    }

    /**
     * check and update
     */
    private Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            ////Delete the old APK if exist
            String uploadPath = AppUtil.splicingFilePath(mApplication.getAppName(), UPLOAD, null, null);
            File file = new File(uploadPath);
            if (file.exists()) {
                File[] files = file.listFiles();
                for (File f : files){
                    if (f.getName().endsWith(".apk") || f.getName().endsWith(".APK")) {
                        if (!f.delete()){
                            Dbug.e(tag, "Delete failure:" + f.getName());
                        }
                    }
                }
            }
            boolean isWifi = mWifiHelper.isWiFiActive(getApplicationContext());
            isConnecting = false;
            Dbug.e(tag, "isWifi : " + isWifi + ", current wifi name = " + mWifiHelper.getWifiConnectionInfo().getSSID());
            if (isWifi) {
                String versionInfo = AppUtil.getFromRaw(getApplicationContext(), R.raw.local_version_info);
                SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(getApplicationContext());
                String deviceVersionMsg = sharedPreferences.getString(DEVICE_VERSION_MSG, null);
                DeviceVersionInfo localVersionInfo = AppUtil.getLocalVersionInfo(versionInfo);
                DeviceVersionInfo serverVersionInfo = mApplication.getServerVersionInfo();
                DeviceVersionInfo deviceVersionInfo = ParseHelper.parseDeviceVersionText(deviceVersionMsg);
                String updatePath = null;
                String serverLatestAPK = null;
                String serverLatestSDK = null;
                String currentAPK = null;
                String currentSDK = null;
                if (localVersionInfo != null) {
                    currentAPK = localVersionInfo.getLocalAndroidVersion();
                }
                if(deviceVersionInfo != null){
                    currentSDK = deviceVersionInfo.getFirmwareVersion();
                }

                if (TextUtils.isEmpty(currentAPK) || TextUtils.isEmpty(currentSDK)) {
                    Dbug.e(tag, "read data error!");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showUpdateFailedMsg(R.string.version_info_err);
                        }
                    });
                    return;
                }
                Dbug.e(tag, " currentAPK : " + currentAPK + '\n' + " currentSDK : " + currentSDK);
                switch (updateType) {
                    case AppUtil.UPGRADE_APK_TYPE:
                        if (serverVersionInfo != null) {
                            String[] serverAndroidVersions = serverVersionInfo.getServerAndroidVersions();
                            if (serverAndroidVersions != null && serverAndroidVersions.length > 0) {
                                serverLatestAPK = serverAndroidVersions[0];
                                if (!TextUtils.isEmpty(serverLatestAPK) && serverLatestAPK.compareTo(currentAPK) > 0) {
                                    updatePath = ANDROID_DIR + File.separator + serverLatestAPK;
                                }
                            }
                        }
                        break;
                    case AppUtil.UPGRADE_SDK_TYPE:
                        if (serverVersionInfo != null) {
                            String[] serverFirmwareVersions = serverVersionInfo.getServerFirmwareVersions();
                            if (serverFirmwareVersions != null && serverFirmwareVersions.length > 0) {
                                serverLatestSDK = serverFirmwareVersions[0];
                                if (!TextUtils.isEmpty(serverLatestSDK) && serverLatestSDK.compareTo(currentSDK) > 0) {
                                    updatePath = FIRMWARE_DIR + File.separator + serverLatestSDK;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
                if (TextUtils.isEmpty(updatePath)) {
                    Dbug.e(tag, " serverLatestAPK : " + serverLatestAPK + '\n' + " serverLatestSDK : " + serverLatestSDK);
                    Dbug.e(tag, "updatePath is empty!");
                    if (serverVersionInfo != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateFailedMsg(R.string.latest_version);
                            }
                        });
                    } else {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateFailedMsg(R.string.update_failed);
                            }
                        });
                    }
                    return;
                }
                String changePath = mApplication.getCurrentProductType() + File.separator + updatePath;
                Dbug.e(tag, "=connectRunnable= changePath :" + changePath);
                if (ftpClientUtil.connectAndLoginFTP(FTP_HOST_NAME, DEFAULT_FTP_PORT, FTP_USER_NAME, FTP_PASSWORD, true, changePath)) {
                    try {
                        if (mFTPClient == null) {
                            mFTPClient = ftpClientUtil.getFTPClient();
                        }
                        final String[] ftpFilesName = mFTPClient.listNames();
                        if (ftpFilesName == null || ftpFilesName.length == 0) {
                            ftpClientUtil.disconnect();
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showUpdateFailedMsg(R.string.file_no_exist);
                                }
                            });
                            return;
                        }
                        String updateFileName = null;
                        String updateTXTName = null;
                        for (String fileName : ftpFilesName) {
                            switch (updateType) {
                                case AppUtil.UPGRADE_APK_TYPE:
                                    if (!TextUtils.isEmpty(fileName)) {
                                        if (fileName.endsWith(".apk") || fileName.endsWith(".APK")) {
                                            updateFileName = fileName;
                                            break;
                                        }
                                    }
                                    break;
                                case AppUtil.UPGRADE_SDK_TYPE:
                                    if (!TextUtils.isEmpty(fileName)) {
                                        if (fileName.endsWith(".bfu") || fileName.endsWith(".BFU")) {
                                            updateFileName = fileName;
                                            break;
                                        }
                                    }
                                    break;
                            }
                        }
                        for (String name : ftpFilesName) {
                            Dbug.w(tag, " name :" + name);
                            if (!TextUtils.isEmpty(name)) {
                                if (name.endsWith(".txt") || name.endsWith(".TXT")) {
                                    updateTXTName = name;
                                    break;
                                }
                            }
                        }
                        if (TextUtils.isEmpty(updateFileName) || TextUtils.isEmpty(updateTXTName)) {
                            if (ftpFilesName.length > 0) {
                                switch (updateType) {
                                    case AppUtil.UPGRADE_APK_TYPE:
                                        if (currentAPK.equals(serverLatestAPK)) {
                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    showToastLong(R.string.latest_version);
                                                }
                                            });
                                        } else {
                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    showToastLong(R.string.file_no_exist);
                                                }
                                            });
                                        }
                                        break;
                                    case AppUtil.UPGRADE_SDK_TYPE:
                                        if (currentSDK.equals(serverLatestSDK)) {
                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    showToastLong(R.string.latest_version);
                                                }
                                            });
                                        } else {
                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    showToastLong(R.string.file_no_exist);
                                                }
                                            });
                                        }
                                        break;
                                }
                            } else {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        showToastLong(R.string.update_failed);
                                    }
                                });
                            }
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (upgradeFirmwareDialog != null && upgradeFirmwareDialog.isVisible()) {
                                        upgradeFirmwareDialog.dismiss();
                                    }
                                    SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                                    String currentSSID = sharedPreferences.getString(CURRENT_SSID, null);
                                    String currentPWD = sharedPreferences.getString(CURRENT_PWD, null);
                                    newConnectDevice(currentSSID, currentPWD);
                                }
                            });
                            ftpClientUtil.disconnect();
                            upgradeFileExist = false;
                            Dbug.e(tag, "updateFileName is null, this is the latest version!");
                            return;
                        }
                        Dbug.e(tag, "updateFileName ==>" + updateFileName + " updateTXTName : " + updateTXTName);
                        String localUploadPath = AppUtil.splicingFilePath(mApplication.getAppName(), UPLOAD, null, null);
                        File uploadFile = new File(localUploadPath);
                        if (!uploadFile.exists()) {
                            if (uploadFile.mkdir()) {
                                Dbug.w(tag, " upload dir is exists!");
                            }
                        }
                        String saveFilePath;
                        String saveTxtPath;
                        FileOutputStream outUpdateFileStream = null;
                        FileOutputStream outUpdateTxtStream = null;
                        InputStream inputUpdateFileStream = null;
                        InputStream inputUpdateTxtStream = null;
                        saveFilePath = localUploadPath + File.separator + updateFileName;
                        saveTxtPath = localUploadPath + File.separator + updateTXTName;
                        File saveFile = new File(saveFilePath);
                        if (saveFile.exists() && saveFile.isFile()) {
                            if (saveFile.delete()) {
                                Dbug.e(tag, "delete exists update file !");
                            }
                        }
                        File saveTXT = new File(saveTxtPath);
                        if (saveTXT.exists() && saveTXT.isFile()) {
                            if (saveTXT.delete()) {
                                Dbug.e(tag, "delete exists update txt !");
                            }
                        }
                        try {
                            try {
                                outUpdateFileStream = new FileOutputStream(saveFile);
                                outUpdateTxtStream = new FileOutputStream(saveTXT);
                            } catch (IOException e) {
                                e.printStackTrace();
                                Dbug.e(tag, "FileOutputStream IOException => err | " + e.getMessage());
                                ftpClientUtil.disconnect();
                                upgradeFileExist = false;
                                return;
                            }
                            mFTPClient.enterLocalPassiveMode();
                            mFTPClient.setFileType(FTP.BINARY_FILE_TYPE);
//                            mFTPClient.setBufferSize(1024 * 1024 * 3); //3M
                            byte[] buffer = new byte[44 * 1460];
                            int length;
                            Dbug.e(tag, "download file start!");
                            inputUpdateFileStream = mFTPClient.retrieveFileStream(updateFileName);
                            if (inputUpdateFileStream == null) {
                                upgradeFileExist = false;
                                return;
                            }
                            while ((length = inputUpdateFileStream.read(buffer)) != -1) {
                                outUpdateFileStream.write(buffer, 0, length);
                            }
                            if (mFTPClient.completePendingCommand()) {
                                Dbug.e(tag, "download file success!");
                                upgradeFileExist = true;
                                inputUpdateTxtStream = mFTPClient.retrieveFileStream(updateTXTName);
                                if (inputUpdateTxtStream == null) {
                                    upgradeFileExist = false;
                                    return;
                                }
                                while ((length = inputUpdateTxtStream.read(buffer)) != -1) {
                                    outUpdateTxtStream.write(buffer, 0, length);
                                }
                                if (mFTPClient.completePendingCommand()) {
                                    Dbug.e(tag, "download txt success!");
                                    upgradeFileExist = true;
                                } else {
                                    Dbug.e(tag, "download txt failed!");
                                    upgradeFileExist = false;
                                }
                            } else {
                                Dbug.e(tag, "download file failed!");
                                upgradeFileExist = false;
                            }
                        } catch (IOException e) {
                            Dbug.e(tag, "IOException => " + e.getMessage());
                            e.printStackTrace();
                            upgradeFileExist = false;
                        } finally {
                            try {
                                if (outUpdateFileStream != null) {
                                    outUpdateFileStream.close();
                                }
                                if (outUpdateTxtStream != null) {
                                    outUpdateTxtStream.close();
                                }
                                if (inputUpdateFileStream != null) {
                                    inputUpdateFileStream.close();
                                }
                                if (inputUpdateTxtStream != null) {
                                    inputUpdateTxtStream.close();
                                }
                                ftpClientUtil.disconnect();
                                updateFilePath = saveFilePath;
                                updateTXTPath = saveTxtPath;
                                if (upgradeFileExist) {
                                    String currentSSID = sharedPreferences.getString(CURRENT_SSID, null);
                                    String currentPWD = sharedPreferences.getString(CURRENT_PWD, null);
                                    newConnectDevice(currentSSID, currentPWD);
//                                    mHandler.postDelayed(cancelUpdateDialog, 12000L);
                                } else {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            showUpdateFailedMsg(R.string.update_failed);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Dbug.e(tag, "finally Exception : " + e.getMessage());
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        showUpdateFailedMsg(R.string.update_failed);
                                    }
                                });
                            }
                        }
                    } catch (IOException e) {
                        Dbug.e(tag, "IOException => err | " + e.getMessage());
                        ftpClientUtil.disconnect();
                        e.printStackTrace();
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateFailedMsg(R.string.update_failed);
                            }
                        });
                    }
                } else {
                    Dbug.e(tag, "connectFTP failed");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showUpdateFailedMsg(R.string.update_failed);
                        }
                    });
                }
            } else { // wifi is not available.
                Dbug.e(tag, getString(R.string.wifi_is_disable));
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (upgradeFirmwareDialog != null) {
                            upgradeFirmwareDialog.dismiss();
                        }
                        if (openWifiDialog == null) {
                            openWifiDialog = new NotifyDialog(R.string.dialog_tip, R.string.wifi_is_disable, R.string.confirm, new NotifyDialog.OnConfirmClickListener() {
                                @Override
                                public void onClick() {
                                    if (openWifiDialog != null) {
                                        openWifiDialog.dismiss();
                                    }
                                    showUpdateFailedMsg(R.string.update_failed);
//                                    Intent intent = new Intent("android.net.wifi.PICK_WIFI_NETWORK");
//                                    startActivity(intent);
                                }
                            });
                        }
                        if (!openWifiDialog.isShowing()) {
                            openWifiDialog.show(getFragmentManager(), "WifiIsNoOpenDialog");
                        }
                        showToastLong(R.string.update_failed);
                    }
                });
            }
        }
    };

    private View.OnClickListener controlBtnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onBackPressed();
        }
    };

    private void showUpdateFailedMsg(int RId) {
        if (upgradeFirmwareDialog != null && upgradeFirmwareDialog.isShowing()) {
            upgradeFirmwareDialog.dismiss();
        }
        isConnecting = false;
        upgradeFileExist = false;
        mHandler.removeCallbacks(tryToLoginSever);
        showToastLong(RId);
        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
        String currentSSID = sharedPreferences.getString(CURRENT_SSID, null);
        String currentPWD = sharedPreferences.getString(CURRENT_PWD, null);
        if (!CommandHub.getInstance().isActive()) {
            newConnectDevice(currentSSID, currentPWD);
        }
    }

    private Runnable checkUpdate = new Runnable() {
        @Override
        public void run() {
            boolean isWifi = WifiHelper.isWifi(getApplicationContext());
            Dbug.w(tag, " isWiFi " + isWifi);
            if (isWifi) {
                upgradeBtnIsClicked = true;
                isConnecting = true;
                disconnectDevice();
                if (upgradeFirmwareDialog != null) {
                    if (upgradeFirmwareDialog.isShowing()) {
                        upgradeFirmwareDialog.dismiss();
                    }
                    upgradeFirmwareDialog.show(getFragmentManager(), "firmware_upgrade");
                }
                mHandler.postDelayed(cancelUpdateDialog, 30000L);
//                mHandler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        DeviceVersionInfo serverVersionInfo = mApplication.getServerVersionInfo();
//                        if(serverVersionInfo != null){
//                            new Thread(connectRunnable).start();
//                        }else{
//                            new Thread(getServerVersionInfo).start();
//                        }
//
//                    }
//                }, 6000L);
            } else {
                if (openWifiDialog == null) {
                    openWifiDialog = new NotifyDialog(false, R.string.wifi_is_disable, R.string.confirm, new NotifyDialog.OnConfirmClickListener() {
                        @Override
                        public void onClick() {
                            openWifiDialog.dismiss();
                            showUpdateFailedMsg(R.string.update_failed);
//                            Intent intent = new Intent("android.net.wifi.PICK_WIFI_NETWORK");
//                            startActivity(intent);
                        }
                    });
                }
                if (!openWifiDialog.isShowing()) {
                    openWifiDialog.show(getFragmentManager(), "WifiIsNoOpenDialog");
                }
//                        showToast(R.string.wifi_is_disable);
            }
        }
    };

    private Runnable cancelUpdateDialog = new Runnable() {
        @Override
        public void run() {
            isConnecting = false;
            showUpdateFailedMsg(R.string.update_failed);
        }
    };

    private long getCacheSize() {
        long cacheSize = 0;
        if (TextUtils.isEmpty(dImageThumbPath) || TextUtils.isEmpty(dVideoThumbPath)){
            Dbug.e(tag, "dImageThumbPath or dVideoThumbPath is null.");
            return 0;
        }

        if (!TextUtils.isEmpty(mApplication.getDeviceUUID())) {
            File cacheImage = new File(dImageThumbPath);
            File cacheVideo = new File(dVideoThumbPath);
            try {
                if (cacheImage.exists() && cacheVideo.exists()) {
                    cacheSize = DataCleanManager.getFolderSize(cacheImage) + DataCleanManager.getFolderSize(cacheVideo);
                }else if(cacheImage.exists()){
                    cacheSize = DataCleanManager.getFolderSize(cacheImage);
                }else if(cacheVideo.exists()){
                    cacheSize = DataCleanManager.getFolderSize(cacheVideo);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return cacheSize;
    }

    private void cleanCache() {
        if (!TextUtils.isEmpty(dImageThumbPath) && !TextUtils.isEmpty(dVideoThumbPath)
                && !TextUtils.isEmpty(mApplication.getDeviceUUID())) {
            try {
                File cacheImage = new File(dImageThumbPath);
                File cacheVideo = new File(dVideoThumbPath);
                long cacheSize = getCacheSize();
                if (cacheSize == 0) {
                    showToast(getString(R.string.empty_cache));
                    return;
                }
                if (cacheImage.exists()) {
                    AppUtil.deleteFile(cacheImage);
                }
                if (cacheVideo.exists()) {
                    AppUtil.deleteFile(cacheVideo);
                }
                showToast(getString(R.string.clear_cache_success));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        updateTextView();
    }

    private Runnable tryToLoginSever = new Runnable() {
        @Override
        public void run() {
            if (upgradeBtnIsClicked) {
                upgradeBtnIsClicked = false;
            }
            mHandler.removeCallbacks(cancelUpdateDialog);
            new Thread(getServerVersionInfo).start();
        }
    };

    private Runnable getServerVersionInfo = new Runnable() {
        @Override
        public void run() {
            Dbug.e(tag, "getServerVersionInfo start!");
            String[] products = new String[1];
            products[0] = mApplication.getCurrentProductType();
            List<String> productList = new ArrayList<>();
            Collections.addAll(productList, products);
            Dbug.e(tag, "CurrentProductType :" + mApplication.getCurrentProductType());
            AppUtil.failedNum = -1;
            AppUtil.downloadTxt(productList, false, mApplication.getAppName());

            mHandler.postDelayed(checkAndUpdate, 1000L);

        }
    };

    private Runnable checkAndUpdate = new Runnable() {
        @Override
        public void run() {
            String serverVersionPath = AppUtil.splicingFilePath(mApplication.getAppName(), VERSION, null, null);
            String newPath = serverVersionPath + File.separator + mApplication.getCurrentProductType() + "_" + AppUtil.VERSION_JSON;
            String serverInfo = AppUtil.readTxtFile(newPath);
            if (!TextUtils.isEmpty(serverInfo)) {
                DeviceVersionInfo serverVersionInfo = AppUtil.getServerVersionInfo(serverInfo);
                mApplication.setServerVersionInfo(serverVersionInfo);
                new Thread(connectRunnable).start();
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showUpdateFailedMsg(R.string.update_failed);
                    }
                });
            }
        }
    };

    private void showVersionInfoDialog(String currentAPK, String currentSDK) {
        String versionMessage = getString(R.string.application_version) + currentAPK + '\n' +
                getString(R.string.firmware_version) + currentSDK;
        if (versionInfoDialog == null) {
            versionInfoDialog = new NotifyDialog(getString(R.string.version_info), versionMessage,
                    R.string.confirm, new NotifyDialog.OnConfirmClickListener() {
                @Override
                public void onClick() {
                    if (versionInfoDialog != null) {
                        versionInfoDialog.dismiss();
                    }
                }
            });
            versionInfoDialog.setCancelable(false);
        }
        versionInfoDialog.setContent(versionMessage);
        if (!versionInfoDialog.isShowing()) {
            versionInfoDialog.show(getFragmentManager(), "version_information");
        }
    }

    @Override
    public void onMountState(String mountState, String msg) {
        switch (mountState) {
            case ARGS_SDCARD_OFFLINE:
                mApplication.setSdcardState(false);
                showToast(getString(R.string.sdcard_offline));
                isSDOffLine = true;
                break;
            case ARGS_SDCARD_ONLINE:
                if (isSDOffLine) {
                    isSDOffLine = false;
                    showToast(getString(R.string.sdcard_online));
                    mApplication.setSdcardState(true);
                }
                break;
        }
    }

    private class SystemAdapter extends ArrayAdapter<SystemInfo> {
        private LayoutInflater mLayoutInflater;
        private ViewHolder mViewHolder;

        SystemAdapter(Context context) {
            super(context, 0);
            mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.system_info_item, parent, false);
                mViewHolder = new ViewHolder();
                mViewHolder.infoName = (TextView) convertView.findViewById(R.id.info_name);
                convertView.setTag(mViewHolder);
            } else {
                mViewHolder = (ViewHolder) convertView.getTag();
            }
            final SystemInfo systemInfo = getItem(position);
            if(systemInfo != null){
                mViewHolder.infoName.setText(systemInfo.getInfoName());
            }
            return convertView;
        }

        private class ViewHolder {
            private TextView infoName;
        }
    }
}
