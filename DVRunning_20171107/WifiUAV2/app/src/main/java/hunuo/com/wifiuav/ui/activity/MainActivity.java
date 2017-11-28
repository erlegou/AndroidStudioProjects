package hunuo.com.wifiuav.ui.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.util.ICommon;

import java.util.List;

import hunuo.com.wifiuav.R;
import hunuo.com.wifiuav.base.BaseActivity;
import hunuo.com.wifiuav.base.BaseFragment;
import hunuo.com.wifiuav.tool.CommandManager;
import hunuo.com.wifiuav.tool.WifiHelper;
import hunuo.com.wifiuav.ui.dialog.DeviceListDialog;
import hunuo.com.wifiuav.ui.dialog.NotifyDialog;
import hunuo.com.wifiuav.ui.fragment.CameraFragment;
import hunuo.com.wifiuav.uity.AppUtil;
import hunuo.com.wifiuav.uity.Dbug;
import hunuo.com.wifiuav.uity.IAction;
import hunuo.com.wifiuav.uity.IConstant;
import hunuo.com.wifiuav.uity.PreferencesHelper;


public class MainActivity extends BaseActivity implements ICommon{
//        extends BaseActvity
    private final String tag = getClass().getSimpleName();
    private CommandHub mCommandHub;
    private NotifyDialog productTypeErrDialog, updateAPKDialog, updateSDKDialog, readDataErrDialog;
    private NotifyDialog onBackDialog, errUpdateDialog;
    private boolean isStop = false;
    private boolean isNeedCompareVersion = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter mainFilter = new IntentFilter(ACTION_CONNECT_OTHER_DEVICE);
        mainFilter.addAction(ACTION_INIT_CTP_SOCKET_SUCCESS);
        getApplicationContext().registerReceiver(mReceiver, mainFilter);
        mCommandHub = CommandHub.getInstance();
        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN);
        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_REAR_RTS_OPEN);
        boolean isAllowUse = AppUtil.compareVersionInfo(MainActivity.this, mApplication, mHandler);
        mApplication.setIsAllowUse(isAllowUse);
        if (isAllowUse) {
            changeFragment(R.id.container, new CameraFragment());
        }
    }


    private final Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(isFinishing()){
                return false;
            }
            switch (msg.what){
                case SHOW_NOTIFY_DIALOG:
                    Bundle bundle = msg.getData();
                    if(bundle != null){
                        int dialogType = bundle.getInt(DIALOG_TYPE);
                        boolean isMandatory = bundle.getBoolean(MANDATORY_UPDATE, false);
                        switch (dialogType){
                            case READ_DATA_ERROR:
//                                showReadDataErrDialog();
                                break;
                            case PRODUCT_NOT_MATCH:
//                                showProductErrorDialog();
                                break;
                            case APK_NOT_MATCH:
//                                showUpdateAPKDialog(isMandatory);
                                break;
                            case SDK_NOT_MATCH:
//                                showUpdateSDKDialog(isMandatory);
                                break;
                        }
                    }
                    break;
                case NO_UPDATE_FILE:
//                    showErrorUpdateDialog();
                    break;
            }
            return false;
        }
    });

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action){
                case ACTION_CONNECT_OTHER_DEVICE:
                    Dbug.w(tag, "ACTION_CONNECT_OTHER_DEVICE");
                    isNeedCompareVersion = true;
                    mApplication.setCurrentProductType("");
                    break;
                case ACTION_INIT_CTP_SOCKET_SUCCESS:
                    if(isNeedCompareVersion){
                        isNeedCompareVersion = false;
                        Dbug.w(tag, "ACTION_INIT_CTP_SOCKET_SUCCESS compareVersionInfo ...");
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                AppUtil.compareVersionInfo(MainActivity.this, mApplication, mHandler);
                            }
                        }, 3000);
                    }
                    break;
            }
        }
    };

    @Override
    public void onBackPressed() {
        BaseFragment f = (BaseFragment) getSupportFragmentManager().findFragmentById(R.id.container);
        if (f instanceof CameraFragment){
            String message;

            if (ARGS_IN_RECORDING.equals(CommandManager.getInstance().getDeviceStatus(CMD_GET_RECORDING_STATUS))) {
                message = getString(R.string.whether_stop_recording);
            } else {
                message = getString(R.string.whether_exit_app);
            }
            if(onBackDialog == null){
                onBackDialog = new NotifyDialog(getString(R.string.dialog_tip), message, R.string.cancel, R.string.confirm,
                        new NotifyDialog.OnNegativeClickListener() {
                            @Override
                            public void onClick() {
                                if(onBackDialog != null && onBackDialog.isShowing()){
                                    onBackDialog.dismiss();
                                }
                            }
                        }, new NotifyDialog.OnPositiveClickListener() {
                    @Override
                    public void onClick() {
                        if(onBackDialog != null && onBackDialog.isShowing()){
                            onBackDialog.dismiss();
                        }
                        String recordState = CommandManager.getInstance().getDeviceStatus(CMD_GET_RECORDING_STATUS);
                        Dbug.d(tag, "recordState:"+recordState);
                        if (ARGS_IN_RECORDING.equals(recordState)) {
                            mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_STOP_RECORD, ARGS_STOP_RECORD);
                        } else {
                            release();
                        }
                    }
                });
                onBackDialog.setCancelable(false);
            }
            onBackDialog.setContent(message);
            if(!onBackDialog.isShowing()){
                onBackDialog.show(getFragmentManager(), "on_back");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(isStop && !mApplication.getIsAllowUse()){
            isStop = false;
            Dbug.w(tag,"compareVersionInfo on onResume！");
            AppUtil.compareVersionInfo(MainActivity.this, mApplication, mHandler);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        isStop = true;
    }

    @Override
    protected void onDestroy() {
        if(mHandler != null){
            mHandler.removeCallbacksAndMessages(null);
        }
        if(onBackDialog != null){
            onBackDialog.dismiss();
            onBackDialog = null;
        }
        isNeedCompareVersion = false;
        if(mReceiver != null){
            getApplicationContext().unregisterReceiver(mReceiver);
        }
        destroyDialog();
        release();
        super.onDestroy();
    }

    public void destroyDialog(){
        if(productTypeErrDialog != null){
            productTypeErrDialog.dismiss();
            productTypeErrDialog = null;
        }
        if(updateAPKDialog != null){
            updateAPKDialog.dismiss();
            updateAPKDialog = null;
        }
        if(updateSDKDialog != null){
            updateSDKDialog.dismiss();
            updateSDKDialog = null;
        }
        if(readDataErrDialog != null){
            readDataErrDialog.dismiss();
            readDataErrDialog = null;
        }
        System.gc();
    }

}