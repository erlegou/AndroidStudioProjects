package hunuo.com.wifiuav.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;

import com.jieli.lib.stream.beans.StateInfo;
import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.tools.ParseHelper;
import com.jieli.lib.stream.util.ICommon;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import hunuo.com.wifiuav.MainApplication;
import hunuo.com.wifiuav.tool.CommandManager;
import hunuo.com.wifiuav.uity.Dbug;
import hunuo.com.wifiuav.uity.IAction;
import hunuo.com.wifiuav.uity.IConstant;
import hunuo.com.wifiuav.uity.PreferencesHelper;

public class CommunicationService extends Service implements ICommon, IAction, CommandHub.OnDeviceListener, IConstant {
    private static final String tag = CommunicationService.class.getSimpleName();
    private CommandHub mCommandHub;
    private final Handler mHandler = new Handler();
    private boolean isRequest = false;
    private String appVersion;
    private CommandManager mCommandManager = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Dbug.d(tag, "=====CommunicationService==onCreate=====");
        /**Establish socket*/
        mCommandHub = CommandHub.getInstance();
        mCommandManager = CommandManager.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null){
            return START_STICKY;
        }
        int cmd = intent.getIntExtra(IConstant.SERVICE_CMD, -1);
        appVersion = intent.getStringExtra(IConstant.APP_VERSION);
        Dbug.d(tag, "onStartCommand==========cmd=" + cmd);
        switch (cmd){
            case IConstant.SERVICE_CMD_INIT_SOCKET:
                mHandler.removeCallbacks(createDeviceSocket);
                mHandler.postDelayed(createDeviceSocket, 250L);
//                mCommandHub.requestStatus(ICommon.CTP_ID_DEFAULT, ICommon.CMD_DEVICE_VERSION);
                break;
            case IConstant.SERVICE_CMD_CLOSE_SOCKET:
                closeSocket();
                break;
            case IConstant.SERVICE_CMD_CLEAR_DEVICE_STATUS:
                CommandManager.getInstance().clearDeviceStatus();
                break;
        }
        return START_STICKY;
    }

    private void closeSocket(){
        CommandManager.getInstance().clearDeviceStatus();
        mCommandHub.closeClient();
    }

    private Runnable createDeviceSocket = new Runnable() {
        @Override
        public void run() {
            if(TextUtils.isEmpty(appVersion)){
                appVersion = MainApplication.getApplication().getAppLocalVersion();
            }
            mCommandHub.setLocalAppVersion(appVersion);
            mCommandHub.createClient();
            mCommandHub.setOnDeviceListener(CommunicationService.this);
        }
    };

    @Override
    public void onDestroy() {
        Dbug.w(tag, "onDestroy=============");
        release();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Dbug.w(tag, "onTaskRemoved=============");
        release();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    private void release(){
        mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_DISABLE_DEVICE_WIFI, ARGS_DISABLE_DEVICE_WIFI);
        mCommandHub.closeClient();
        mHandler.removeCallbacksAndMessages(null);
        CommandManager.getInstance().clearDeviceStatus();
    }

    @Override
    public void onConnected() {
        Intent intent = new Intent(ACTION_INIT_CTP_SOCKET_SUCCESS);
        sendBroadcast(intent);
    }

    @Override
    public void onError(int errorType) {
        Intent intent = new Intent(ACTION_DEVICE_CONNECTION_ERROR);
        intent.putExtra(KEY_DEVICE_CONNECTION_ERROR, errorType);
        sendBroadcast(intent);
    }

    @Override
    public void onReceive(StateInfo stateInfo) {
        //Dbug.e(tag, "put cmdNumber:" + stateInfo.getCmdNumber() + ", id=" + stateInfo.getParam()[0]);
        if(stateInfo == null || stateInfo.getParam() == null ||  stateInfo.getParam().length <= 0){
            return;
        }
        String param1 = stateInfo.getParam()[0];
        CommandManager.getInstance().setDeviceStatus(stateInfo.getCmdNumber(), param1);

        switch (stateInfo.getCmdNumber()){
            case CMD_START_RECORD:
                mCommandManager.setDeviceStatus(CMD_GET_RECORDING_STATUS, param1);
                break;
            case CMD_STOP_RECORD:
                mCommandManager.setDeviceStatus(CMD_GET_RECORDING_STATUS, param1);
                break;
            case CMD_DEVICE_WIFI_DISABLED:
                Intent deviceWifiIntent = new Intent(IAction.ACTION_DEVICE_WIFI_DISABLED);
                sendBroadcast(deviceWifiIntent);
                return;
            case CMD_DEVICE_MODE:
                if (ARGS_USB_MODE.equals(param1)){
                    Intent deviceInUsbMode = new Intent(ACTION_DEVICE_IN_USB_MODE);
                    sendBroadcast(deviceInUsbMode);
                    return;
                }
                break;
        }

        switch (stateInfo.getCmdNumber()) {
            case CMD_SDCARD_STATE:
                Intent sdcardIntent = new Intent(IAction.ACTION_SDCARD_STATE);
                sdcardIntent.putExtra(KEY_SDCARD_STATE, stateInfo);
                sendBroadcast(sdcardIntent);
                break;
            case CMD_REAR_CAMERA_PLUG_STATE:
                Intent plugStateIntent = new Intent(IAction.ACTION_REAR_CAMERA_PLUG_STATE);
                plugStateIntent.putExtra(KEY_REAR_CAMERA_PLUG_STATE, stateInfo);
                sendBroadcast(plugStateIntent);
                break;
            case CMD_APP_REQUEST_CONNECTION:
                if (ARGS_DEVICE_HAS_TAKEN.equals(param1) || ARGS_DEVICE_IN_USB_MODE.equals(param1)){
                    Intent requestConnection = new Intent(IAction.ACTION_REJECT_CONNECTION);
                    requestConnection.putExtra(KEY_REJECT_CONNECTION, param1);
                    sendBroadcast(requestConnection);
                } else if (ARGS_ACCEPT_CONNECTION.equals(param1)){
//                    mCommandHub.requestStatus(ICommon.CTP_ID_DEFAULT, ICommon.CMD_DEVICE_VERSION);
                    if(!isRequest){
                        new Thread(getDeviceVersionInfo).start();
                    }
                }
                break;
            case CMD_DEVICE_UUID:
//                Dbug.i(tag, "stateInfo.getContent():"+stateInfo.getParam()[1]);
                MainApplication.getApplication().setDeviceUUID(stateInfo.getParam()[1]);
                break;
//            case CMD_DEVICE_VERSION:
////                Dbug.d(tag, "CMD_DEVICE_VERSION:"+stateInfo.getParam()[1]);
//                String versionInfo = stateInfo.getParam()[1];
//                if(versionInfo != null && !versionInfo.isEmpty()){
//                    SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
//                    String saveVersionInfo = sharedPreferences.getString(DEVICE_VERSION_INFO, null);
//                    SharedPreferences.Editor editor = sharedPreferences.edit();
//                    if(saveVersionInfo == null){
//                        editor.putString(DEVICE_VERSION_INFO, versionInfo);
//                        editor.apply();
//                    }else{
//                        if(!saveVersionInfo.equals(versionInfo)){
//                            editor.remove(DEVICE_VERSION_INFO);
//                            editor.putString(DEVICE_VERSION_INFO, versionInfo);
//                            editor.apply();
//                        }
//                    }
//                }
//                mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_LANGUAGE);
//                Dbug.d(tag, "requestStatus CMD_DEVICE_LANGUAGE over.");
//                break;
            case CMD_DEVICE_LANGUAGE:
//                Dbug.d(tag, "CMD_DEVICE_LANGUAGE: lang code=" + stateInfo.getContentID() + ", lang UI="+ stateInfo.getContent());
                Intent requestIntent = new Intent(IAction.ACTION_REQUEST_UI_DESCRIPTION);
                requestIntent.putExtra(KEY_REQUEST_UI_DESCRIPTION, stateInfo);
                sendBroadcast(requestIntent);

                /**Sync device's date*/
                //eg: 2015 01 02 12 12 12
                String dateParam = formatDateSplitSpace(new Date());
                mCommandHub.sendCommand(ICommon.CTP_ID_DEFAULT, CMD_DEVICE_DATE, dateParam);

                /**Sync device's battery*/
                mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_BATTERY_STATE);

                break;
            case CMD_FIRMWARE_UPGRADE:
                Intent intent = new Intent(IAction.ACTION_ALLOW_FIRMWARE_UPGRADE);
                intent.putExtra(KEY_SDCARD_STATE, stateInfo);
                sendBroadcast(intent);
                break;
            case CMD_AP_SSID:
                String ssid = stateInfo.getParam()[0];
                Dbug.i(tag, "CMD_AP_SSID============ssid:" + ssid);
                if (!TextUtils.isEmpty(ssid)){

                    if (MainApplication.getApplication().isModifySSID()){
                        MainApplication.getApplication().setModifySSID(false);

                        /**Send command to restart*/
                        mCommandHub.sendCommand(ICommon.CTP_ID_DEFAULT, ICommon.CMD_RESTART_DEVICE, ICommon.ARGS_RESTART_DEVICE);

                        closeSocket();

                        sendBroadcast(new Intent(ACTION_CHANGE_SSID_SUCCESS));

                        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                        String currentPWD = sharedPreferences.getString(CURRENT_PWD, null);
                        if (!TextUtils.isEmpty(currentPWD)){
                            MainApplication.getApplication().setLastModifySSID(ssid);
                            PreferencesHelper.putStringValue(MainApplication.getApplication(), ssid, currentPWD);
                        }
                    } else {
                        /**Save device Wi-Fi SSID */
                        PreferencesHelper.putStringValue(MainApplication.getApplication(), CURRENT_SSID, ssid);
                        PreferencesHelper.putStringValue(MainApplication.getApplication(), ssid, null);
                        mCommandHub.sendCommand(ICommon.CTP_ID_DEFAULT, CMD_AP_PASSWORD, " ");
                    }
                }
                break;
            case CMD_AP_PASSWORD:
                String pwd = stateInfo.getParam()[0];

                if (!TextUtils.isEmpty(pwd)){

                    if (MainApplication.getApplication().isModifyPWD()){
                        MainApplication.getApplication().setModifyPWD(false);

                        /**Send command to restart*/
                        mCommandHub.sendCommand(ICommon.CTP_ID_DEFAULT, ICommon.CMD_RESTART_DEVICE, ICommon.ARGS_RESTART_DEVICE);

                        closeSocket();

                        sendBroadcast(new Intent(ACTION_CHANGE_PWD_SUCCESS));
                    } else {
                        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                        String currSSID = sharedPreferences.getString(CURRENT_SSID, null);
                        if (!TextUtils.isEmpty(currSSID)){

                            if (ARGS_AP_PWD_NONE.equals(pwd)){
                                PreferencesHelper.putStringValue(MainApplication.getApplication(), CURRENT_PWD, "");
                                PreferencesHelper.putStringValue(MainApplication.getApplication(), currSSID, "");
                            } else {
                                PreferencesHelper.putStringValue(MainApplication.getApplication(), currSSID, pwd);
                                PreferencesHelper.putStringValue(MainApplication.getApplication(), CURRENT_PWD, pwd);
                            }
                        }
                    }
                }
                break;
            case CMD_FORMAT_SDCARD:
                if(stateInfo.getParam().length >= 1 && ARGS_FORMAT_SDCARD_SUCCESS.equals(param1)){
                    sendBroadcast(new Intent(IAction.ACTION_FORMAT_SDCARD));
                }
                break;
            case CMD_RESET_DEVICE:
                mCommandManager.clearDeviceStatus();
                sendBroadcast(new Intent(IAction.ACTION_RESET_DEVICE));
                break;
            case CMD_ALL_VIDEO_DESC_NAME:
	        case CMD_REAR_ALL_VIDEOS_INFO:
                String state = stateInfo.getParam()[0];
                if(ARGS_ALL_VIDEO_DESC_NAME_SUCCESS.equals(state)) {
                    final String videoListFileName = stateInfo.getParam()[1];
                    if(!TextUtils.isEmpty(videoListFileName)){
                        new VideoInfoTxtAndCaptureTxt(getApplicationContext(), videoListFileName, MainApplication.getApplication().getCaptureSize()).start();
                    }
                }
                break;
            case CMD_SNAPSHOT:
                if(stateInfo.getParam().length >= 1){
                    int captureSize = Integer.valueOf(param1);
                    MainApplication.getApplication().setCaptureSize(captureSize);
                }
                break;
            case CMD_DISABLE_DEVICE_WIFI:
                Intent closeDevWifi = new Intent(IAction.ACTION_CLOSE_DEV_WIFI);
                closeDevWifi.putExtra(CLOSE_DEV_WIFI, stateInfo);
                sendBroadcast(closeDevWifi);
                break;
            case CMD_GET_VIDEO_THUMBNAIL:
	        case CMD_REAR_GET_VIDEO_THUMBNAIL:
            case CMD_DEVICE_MODE:
            case CMD_SP_SSID:
            case CMD_SP_PASSWORD:
            case CMD_PHOTO_STATE:
            case CMD_BATTERY_STATE:
            case CMD_TAKE_PHOTO:
            case CMD_START_RECORD:
            case CMD_STOP_RECORD:
            case CMD_DELETE_FILE:
            case CMD_DELETE_ALL:
            case CMD_VIDEO_PICTURE_QUALITY:
            case CMD_METERING:
            case CMD_GET_RECORDING_STATUS:
            case CMD_TIMER_PICTURE_STATUS:
            case CMD_ENTER_BROWSING_MODE:
            case CMD_EXIT_BROWSING_MODE:
            case CMD_DEVICE_DATE:
            case CMD_VIDEO_START_PLAYBACK:
	        case CMD_REAR_VIDEO_PLAYBACK_START:
            case CMD_RT_STREAM_OPEN:
            case CMD_RT_STREAM_CLOSE:
            case CMD_VIDEO_STOP:
	        case CMD_REAR_VIDEO_PLAYBACK_STOP:
            case CMD_CONTROL_RTS_VOICE:
            case CMD_ENV_LIGHT_LEVEL:
            case CMD_DIGITAL_ZOOM:
	        case CMD_REAR_RTS_OPEN:
	        case CMD_REAR_RTS_CLOSE:
                Intent special = new Intent(IAction.ACTION_SPECIAL_DATA);
                special.putExtra(KEY_SPECIAL_STATE, stateInfo);
                sendBroadcast(special);
                break;
            default:
                Intent currMode = new Intent(IAction.ACTION_GENERIC_DATA);
                currMode.putExtra(KEY_GENERIC_STATE, stateInfo);
                sendBroadcast(currMode);
                ParseHelper.getInstance().updateState(stateInfo.getCmdNumber(), param1);
                break;
        }
    }

    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy MM dd HH mm ss", Locale.getDefault());
    private String formatDateSplitSpace(Date date) {
        return sDateFormat.format(date);
    }

    private static class VideoInfoTxtAndCaptureTxt extends Thread{
        private String videoInfoTxtName;
        private int captureSize;
	    private WeakReference<Context> mContextWeakReference;

        VideoInfoTxtAndCaptureTxt(Context context, String fileName, int size){
            this.videoInfoTxtName = fileName;
            this.captureSize = size;
	        mContextWeakReference = new WeakReference<>(context);
        }

        private void getCaptureTxt(final int size){
	        if (mContextWeakReference.get() == null)
		        return;
            if(size >= 0){
                ParseHelper.getInstance().requestCaptureText(mContextWeakReference.get(), size, new ParseHelper.ResponseListener() {
                    @Override
                    public void onResponse(boolean isSuccess) {
	                    if (mContextWeakReference.get() == null){
		                    Dbug.e(tag, "Context is null");
		                    return;
	                    }
                        if (isSuccess) {
                            Intent videoFileName = new Intent(IAction.ACTION_RESPONDING_VIDEO_DESC_REQUEST);
	                        mContextWeakReference.get().sendBroadcast(videoFileName);
                        } else {
                            ParseHelper.getInstance().requestCaptureText(mContextWeakReference.get(), size,  new ParseHelper.ResponseListener() {
                                @Override
                                public void onResponse(boolean isSuccess) {
	                                if (mContextWeakReference.get() == null){
		                                Dbug.e(tag, "Context is null again.");
		                                return;
	                                }
                                    Intent videoFileName = new Intent(IAction.ACTION_RESPONDING_VIDEO_DESC_REQUEST);
                                    mContextWeakReference.get().sendBroadcast(videoFileName);
                                }
                            });
                        }
                    }
                });
            }else{
                Intent videoFileName = new Intent(IAction.ACTION_RESPONDING_VIDEO_DESC_REQUEST);
                mContextWeakReference.get().sendBroadcast(videoFileName);
            }
        }

        @Override
        public void run() {
	        if (mContextWeakReference.get() == null)
		        return;
            ParseHelper.getInstance().requestVideoInfoText(mContextWeakReference.get(), videoInfoTxtName, new ParseHelper.ResponseListener() {
                @Override
                public void onResponse(boolean isSuccess) {
                    if (isSuccess) {
                        //Dbug.w(tag, "Request " + videoListFileName + " success!!");
                        getCaptureTxt(captureSize);
                    } else {
                        ParseHelper.getInstance().requestVideoInfoText(mContextWeakReference.get(), videoInfoTxtName, new ParseHelper.ResponseListener() {
                            @Override
                            public void onResponse(boolean isSuccess) {
	                            if (mContextWeakReference.get() == null){
		                            Dbug.e(tag, "mContextWeakReference is null again.");
		                            return;
	                            }
                                if (isSuccess) {
                                    getCaptureTxt(captureSize);
                                } else {
                                    Dbug.e(tag, "Request " + videoInfoTxtName + " fail!");
	                                mContextWeakReference.get().sendBroadcast(new Intent(IAction.ACTION_GET_VIDEO_INFO_ERROR));
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    private Runnable getDeviceVersionInfo = new Runnable() {
        @Override
        public void run() {
            isRequest = true;
            ParseHelper.getInstance().requestDeviceVersionText(getApplicationContext(), DEVICE_VERSION_INFO_NAME, new ParseHelper.ResponseListener() {
                @Override
                public void onResponse(boolean isSuccess) {
                    isRequest = false;
                    if (isSuccess) {
                        String deviceVersionMsg = ParseHelper.getInstance().getDeviceVersionMsg();
                        if(!TextUtils.isEmpty(deviceVersionMsg)) {
                            PreferencesHelper.putStringValue(getApplicationContext(), DEVICE_VERSION_MSG, deviceVersionMsg);
                        }
                        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_LANGUAGE);
                    }else{
                        ParseHelper.getInstance().requestDeviceVersionText(getApplicationContext(), DEVICE_VERSION_INFO_NAME, new ParseHelper.ResponseListener() {
                            @Override
                            public void onResponse(boolean isSuccess) {
                                if(isSuccess){
                                    String deviceVersionMsg = ParseHelper.getInstance().getDeviceVersionMsg();
                                    if(!TextUtils.isEmpty(deviceVersionMsg)){
                                        PreferencesHelper.putStringValue(getApplicationContext(), DEVICE_VERSION_MSG, deviceVersionMsg);
                                    }
                                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_LANGUAGE);
                                }else{
                                    ParseHelper.getInstance().requestDeviceVersionText(getApplicationContext(), DEVICE_VERSION_INFO_NAME, new ParseHelper.ResponseListener() {
                                        @Override
                                        public void onResponse(boolean isSuccess) {
                                            if(isSuccess){
                                                String deviceVersionMsg = ParseHelper.getInstance().getDeviceVersionMsg();
                                                if(!TextUtils.isEmpty(deviceVersionMsg)){
                                                    PreferencesHelper.putStringValue(getApplicationContext(), DEVICE_VERSION_MSG, deviceVersionMsg);
                                                }
                                                mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_LANGUAGE);
                                            }else{
                                                mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_LANGUAGE);
                                            }
                                        }
                                    });
                                }
                            }
                        });
                    }
                }
            });
        }
    };
}
