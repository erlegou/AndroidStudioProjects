package com.jieli.stream.player.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.util.ICommon;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseActivity;
import com.jieli.stream.player.base.BaseFragment;
import com.jieli.stream.player.tool.CommandManager;
import com.jieli.stream.player.ui.dialog.NotifyDialog;
import com.jieli.stream.player.ui.fragment.CameraFragment;
import com.jieli.stream.player.util.AppUtil;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IAction;
import com.jieli.stream.player.util.IConstant;

public class MainActivity extends BaseActivity implements ICommon {
    private final String tag = getClass().getSimpleName();
    private CommandHub mCommandHub;
    private NotifyDialog productTypeErrDialog, updateAPKDialog, updateSDKDialog, readDataErrDialog;
    private NotifyDialog onBackDialog, errUpdateDialog;
    private boolean isStop = false;
    private boolean isNeedCompareVersion = false;
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
                                showReadDataErrDialog();
                                break;
                            case PRODUCT_NOT_MATCH:
                                showProductErrorDialog();
                                break;
                            case APK_NOT_MATCH:
                                showUpdateAPKDialog(isMandatory);
                                break;
                            case SDK_NOT_MATCH:
                                showUpdateSDKDialog(isMandatory);
                                break;
                        }
                    }
                    break;
                case NO_UPDATE_FILE:
                    showErrorUpdateDialog();
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



/*    @Override
    public void onFileBrowse() {
        if(mApplication.getDeviceUUID() == null){
            Dbug.e(tag, "uuid is null!");
            return;
        }
        if (BufChangeHex.readSDCard() <= 100*1024*1024){
            showToast(R.string.phone_space_less);
        }
        Intent intent = new Intent(MainActivity.this, BrowseFileActivity.class);
        startActivityForResult(intent, 10526);
        initFileDir();
    }*/

/*    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode){
            case BROWSE_ACTIVITY_RESULT_OK:
                mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_EXIT_BROWSING_MODE, ARGS_EXIT_BROWSING_MODE);
            break;
        }
    }*/

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

    private void showUpdateAPKDialog(final boolean isMandatory){
        if(updateAPKDialog == null){
            updateAPKDialog = new NotifyDialog(R.string.dialog_tip, R.string.apk_version_no_match, R.string.cancel, R.string.confirm,
                    new NotifyDialog.OnNegativeClickListener() {
                        @Override
                        public void onClick() {
                            if(updateAPKDialog != null){
                                updateAPKDialog.dismiss();
                            }
                            if(isMandatory) {
                                Intent exitIntent = new Intent(IAction.ACTION_QUIT_APP);
                                sendBroadcast(exitIntent);
                            }
                        }
                    }, new NotifyDialog.OnPositiveClickListener() {
                @Override
                public void onClick() {
                    if(updateAPKDialog != null){
                        updateAPKDialog.dismiss();
                    }
                    startActivity(new Intent(MainActivity.this, PersonalSettingActivity.class));
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(IAction.ACTION_UPDATE_APK_SDK);
                            intent.putExtra(IConstant.UPDATE_TYPE, AppUtil.UPGRADE_APK_TYPE);
                            sendBroadcast(intent);
                        }
                    }, 1000L);
                }
            });
        }
        if(isMandatory){
            updateAPKDialog.setNegativeText(R.string.exit);
            updateAPKDialog.setCancelable(false);
        }else{
            updateAPKDialog.setNegativeText(R.string.cancel);
            updateAPKDialog.setCancelable(true);
        }
        if(!updateAPKDialog.isShowing()){
            updateAPKDialog.show(getFragmentManager(), "update_APK");
        }
    }

    private void showUpdateSDKDialog(final boolean isMandatory){
        if(updateSDKDialog == null){
            updateSDKDialog = new NotifyDialog(R.string.dialog_tip, R.string.sdk_version_no_match, R.string.cancel, R.string.confirm,
                    new NotifyDialog.OnNegativeClickListener() {
                        @Override
                        public void onClick() {
                            if (updateSDKDialog != null) {
                                updateSDKDialog.dismiss();
                            }
                            if (isMandatory) {
                                Intent exitIntent = new Intent(IAction.ACTION_QUIT_APP);
                                sendBroadcast(exitIntent);
                            }
                        }
                    }, new NotifyDialog.OnPositiveClickListener() {
                @Override
                public void onClick() {
                    if (updateSDKDialog != null) {
                        updateSDKDialog.dismiss();
                    }
                    startActivity(new Intent(MainActivity.this, PersonalSettingActivity.class));
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(IAction.ACTION_UPDATE_APK_SDK);
                            intent.putExtra(IConstant.UPDATE_TYPE, AppUtil.UPGRADE_SDK_TYPE);
                            sendBroadcast(intent);
                        }
                    }, 1000L);
                }
            });
        }
        if(isMandatory){
            updateSDKDialog.setNegativeText(R.string.exit);
            updateSDKDialog.setCancelable(false);
        }else{
            updateSDKDialog.setNegativeText(R.string.cancel);
            updateSDKDialog.setCancelable(true);
        }
        if(!updateSDKDialog.isShowing()){
            updateSDKDialog.show(getFragmentManager(), "update_SDK");
        }
    }

    private void showErrorUpdateDialog(){
        if(errUpdateDialog == null){
            errUpdateDialog = new NotifyDialog(R.string.dialog_tip, R.string.forced_upgrade_failed, R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                @Override
                public void onClick() {
                    if(errUpdateDialog != null){
                        errUpdateDialog.dismiss();
                    }
                    release();
                }
            });
            errUpdateDialog.setCancelable(false);
        }
        if(!errUpdateDialog.isShowing()){
            errUpdateDialog.show(getFragmentManager(), "error_Update");
        }
    }
    private void showProductErrorDialog(){
        if(productTypeErrDialog == null){
            productTypeErrDialog = new NotifyDialog(R.string.dialog_tip, R.string.product_not_match, R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                @Override
                public void onClick() {
                    if(productTypeErrDialog != null){
                        productTypeErrDialog.dismiss();
                    }
                    Intent exitIntent = new Intent(IAction.ACTION_QUIT_APP);
                    sendBroadcast(exitIntent);
                }
            });
            productTypeErrDialog.setCancelable(false);
        }
        if(!productTypeErrDialog.isShowing()){
            productTypeErrDialog.show(getFragmentManager(), " product_Type_Error");
        }
    }

    private void showReadDataErrDialog(){
        if(readDataErrDialog == null){
            readDataErrDialog =  new NotifyDialog(R.string.dialog_tip, R.string.version_info_err, R.string.exit, new NotifyDialog.OnConfirmClickListener() {
                @Override
                public void onClick() {
                    if(readDataErrDialog != null){
                        readDataErrDialog.dismiss();
                    }
                    Intent exitIntent = new Intent(IAction.ACTION_QUIT_APP);
                    sendBroadcast(exitIntent);
                }
            });
            readDataErrDialog.setCancelable(false);
        }
        if(!readDataErrDialog.isShowing()){
            readDataErrDialog.show(getFragmentManager(), "read_data_error");
        }
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
