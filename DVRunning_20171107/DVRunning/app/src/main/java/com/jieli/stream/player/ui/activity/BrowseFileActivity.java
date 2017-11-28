package com.jieli.stream.player.ui.activity;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;

import com.jieli.lib.stream.beans.StateInfo;
import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.util.ICommon;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseActivity;
import com.jieli.stream.player.base.BaseFragment;
import com.jieli.stream.player.tool.FtpHandlerThread;
import com.jieli.stream.player.ui.fragment.DeviceFragment;
import com.jieli.stream.player.ui.fragment.LocalFragment;
import com.jieli.stream.player.ui.lib.CustomTextView;
import com.jieli.stream.player.util.DataCleanManager;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IAction;
import com.jieli.stream.player.util.IConstant;


/**
 * class name: BrowseFileActivity
 * function : Browse files
 * @author JL
 * create time : 2015-11-10 10:43
 * version : v1.0
 * ///////////////////////////////////////////////////////////////////////////
 *
 */
public class BrowseFileActivity extends BaseActivity implements ICommon{
    private final String tag = getClass().getSimpleName();

    private ImageView downloadBtn;
    private CustomTextView devBrowseBtn;
    private CustomTextView localBrowseBtn;

    private int currentMode = -1;
    private boolean isQuest = false;

    private static final int DELAY_TIME = 300;
    private static final int MSG_DEVICE_MODE = 0xa0;
    private static final int MSG_LOCAL_MODE = 0xa1;

    private FtpHandlerThread mWorkHandlerThread;
	private String mWhichFTPServerDirectory;

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(BrowseFileActivity.this.isFinishing()){
                Dbug.e(tag, "Activity is finishing, not do any thing.");
                return false;
            }
            switch (msg.what){
                case MSG_DEVICE_MODE:
                    if(mApplication.getIsOffLineMode()) {
                        showToastLong(getString(R.string.offline_mode_tip));
                        return false;
                    }else {
                        if (mApplication.isSdcardState()) {
                            if(!mApplication.getAllowBrowseDev()){
                                showToastLong(getString(R.string.please_wait));
                                Dbug.w(tag, "MSG_DEVICE_MODE request CMD_ENTER_BROWSING_MODE ");
                                isQuest = true;
                                CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_ENTER_BROWSING_MODE);
                                return false;
                            }
                            selectFragment(0);
                        } else {
                            showToastLong(R.string.sdcard_error);
                        }
                    }
                    break;
                case MSG_LOCAL_MODE:
                    selectFragment(1);
                    break;
            }
            return false;
        }
    });

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case IAction.ACTION_SELECT_BROWSE_MODE:
                    int mode = intent.getIntExtra(IConstant.BROWSE_FRAGMENT_TYPE, -1);
                    if (mode != -1) {
                        switch (mode){
                            case 0:
                                mHandler.removeMessages(MSG_DEVICE_MODE);
                                mHandler.sendEmptyMessageDelayed(MSG_DEVICE_MODE, DELAY_TIME);
                                break;
                            case 1:
                                mHandler.removeMessages(MSG_LOCAL_MODE);
                                mHandler.sendEmptyMessageDelayed(MSG_LOCAL_MODE, DELAY_TIME);
                                break;
                        }
                    }
                    break;
                case ACTION_SPECIAL_DATA:
                    final StateInfo stateInfo = (StateInfo) intent.getSerializableExtra(KEY_SPECIAL_STATE);
                    String param1 = stateInfo.getParam()[0];

                    String cmdResult = stateInfo.getCmdNumber();
                    switch (TextUtils.isEmpty(cmdResult) ? ARGS_NULL : cmdResult) {
                        case CMD_DEVICE_MODE:
//                            /**若当前设备处于非USB模式，则发命令请求其切到USB模式*/
//                            if(!ARGS_USB_MODE.equals(param1)){
//                                if(mApplication.isSdcardState()){
//                                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_ENTER_BROWSING_MODE, ARGS_ENTER_BROWSING_MODE);
//                                }
//                            }
                            break;
                        case CMD_ENTER_BROWSING_MODE:
                            int count = Integer.parseInt(param1);
                            if (count <= 0) {
                                mApplication.setAllowBrowseDev(false);
                                if(isQuest) {
                                    isQuest = false;
                                    if (mApplication.isSdcardState()) {
                                        CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_ENTER_BROWSING_MODE, ARGS_ENTER_BROWSING_MODE);
                                        Dbug.e(tag,"CMD_ENTER_BROWSING_MODE cmd sent!!!!!!!!!!!!!002");
                                    }
                                }
                            }else{
                                if(!mApplication.getAllowBrowseDev()){
                                    mApplication.setIsFirstReadData(true);
                                }
                                mApplication.setAllowBrowseDev(true);
                                isQuest = false;
                            }
                            break;
                    }
                    break;
                case ACTION_DEVICE_CONNECTION_SUCCESS:
                    Dbug.w(tag, "ACTION_DEVICE_CONNECTION_SUCCESS request CMD_ENTER_BROWSING_MODE ");
                    CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_ENTER_BROWSING_MODE);
                    break;
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Dbug.d(tag, "==========onCreate=========");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
	    mWhichFTPServerDirectory = getIntent().getStringExtra("which_dir");//rear_view or front_view
        mWorkHandlerThread = new FtpHandlerThread("browse_thread", getApplicationContext(), mApplication);
        mWorkHandlerThread.start();
        mApplication.setWorkHandlerThread(mWorkHandlerThread);
        if(mApplication.isSdcardState()){
            /*Request device for enter browsing mode*/
            CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_ENTER_BROWSING_MODE, ARGS_ENTER_BROWSING_MODE);
            Dbug.w(tag, "onCreate send CMD_ENTER_BROWSING_MODE 003");
        }

        initUI();

        mApplication.setIsFirstReadData(true);

        IntentFilter browseFilter = new IntentFilter(IAction.ACTION_SELECT_BROWSE_MODE);
        browseFilter.addAction(ACTION_SPECIAL_DATA);
        browseFilter.addAction(ACTION_DEVICE_CONNECTION_SUCCESS);
        getApplicationContext().registerReceiver(mReceiver, browseFilter);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Dbug.e(tag, " IsOffLineMode : " +mApplication.getIsOffLineMode() +"  isSdcardState : " +  mApplication.isSdcardState()
                        + " AllowBrowseDev : " +mApplication.getAllowBrowseDev());
                if(mApplication.getIsOffLineMode()){
                    selectFragment(1);
                }else{
                    if (!mApplication.isSdcardState()) {
                        showToastLong(getString(R.string.sdcard_error));
                        selectFragment(1);
                    }else{
                        if(mApplication.getAllowBrowseDev()){
                            selectFragment(0);
                        }else{
                            selectFragment(1);
                        }
                    }
                }
            }
        }, 1000L);
    }

    private void initUI(){
        CustomTextView choiceBtn = (CustomTextView) findViewById(R.id.selection);
        CustomTextView backBtn = (CustomTextView) findViewById(R.id.back);
        ImageView deleteBtn = (ImageView) findViewById(R.id.delete);
        downloadBtn = (ImageView) findViewById(R.id.ftp_download);
        devBrowseBtn = (CustomTextView) findViewById(R.id.device_mode_btn);
        localBrowseBtn = (CustomTextView) findViewById(R.id.local_mode_btn);

        choiceBtn.setOnClickListener(controlClickListener);
        backBtn.setOnClickListener(controlClickListener);
        deleteBtn.setOnClickListener(controlClickListener);
        downloadBtn.setOnClickListener(controlClickListener);
        devBrowseBtn.setOnClickListener(controlClickListener);
        localBrowseBtn.setOnClickListener(controlClickListener);
    }

    private void selectFragment(int type){
        BaseFragment fragment;
        switch (type){
            case 0: // device mode
                currentMode = type;
                fragment = (BaseFragment) getSupportFragmentManager().findFragmentByTag("DeviceFragment");
                if(fragment == null){
                    fragment = DeviceFragment.newInstance(mWhichFTPServerDirectory);
                }
                changeFragment(R.id.browse_fragment_layout, fragment, "DeviceFragment");
                devBrowseBtn.setTextColor(getResources().getColor(R.color.btn_blue));
                localBrowseBtn.setTextColor(getResources().getColor(R.color.text_white));
                devBrowseBtn.setBackgroundResource(R.drawable.shape_button_bg);
                localBrowseBtn.setBackgroundResource(R.drawable.shape_button_blue);
                downloadBtn.setBackgroundResource(R.drawable.download_drawable);
                break;
            case 1: // local mode
                currentMode = type;
                fragment = (BaseFragment)getSupportFragmentManager().findFragmentByTag("LocalFragment");
                if(fragment == null){
                    fragment = LocalFragment.newInstance(mWhichFTPServerDirectory);
                }
                changeFragment(R.id.browse_fragment_layout, fragment, "LocalFragment");
                devBrowseBtn.setTextColor(getResources().getColor(R.color.text_white));
                localBrowseBtn.setTextColor(getResources().getColor(R.color.btn_blue));
                devBrowseBtn.setBackgroundResource(R.drawable.shape_button_blue);
                localBrowseBtn.setBackgroundResource(R.drawable.shape_button_bg);
                downloadBtn.setBackgroundResource(R.mipmap.download_icon_gray);
                break;
        }
        mApplication.releaseBitmapCache();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            setResult(BROWSE_ACTIVITY_RESULT_OK);
            finish();
            Dbug.d(tag, "====onKeyDown======finish()");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        Dbug.d(tag, "==========onPause=========");
        super.onPause();
    }

    @Override
    public void onResume() {
        Dbug.d(tag, "==========onResume=========");
        super.onResume();
        if(mWorkHandlerThread != null && null != mApplication){
            mApplication.setWorkHandlerThread(mWorkHandlerThread);
        }
    }

    @Override
    public void onDestroy() {
        Dbug.d(tag, "==========onDestroy=========");
        isQuest = false;
        mApplication.setAllowBrowseDev(false);
        mApplication.setIsFirstReadData(false);
        if(mWorkHandlerThread != null){
            mWorkHandlerThread.setIsStopDownLoadThread(true);
            mWorkHandlerThread.setIsDestoryThread(true);
            mWorkHandlerThread.tryToCancelThreadPool();
            mWorkHandlerThread.getWorkHandler().removeCallbacksAndMessages(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mWorkHandlerThread.quitSafely();
            } else {
                mWorkHandlerThread.quit();
            }
            mWorkHandlerThread = null;
        }
        if(mHandler != null){
            mHandler.removeCallbacksAndMessages(null);
        }
        DataCleanManager.cleanExternalCache(getApplicationContext());
        if(null != mReceiver){
            getApplicationContext().unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    private View.OnClickListener controlClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent operationIntent = new Intent(IAction.ACTION_BROWSE_MODE_OPERATION);
            switch (v.getId()){
                case R.id.selection:
                    operationIntent.putExtra(IConstant.BROWSE_FILE_OPERATION_STYLE, IConstant.SELECT_BROWSE_FILE);
                    break;
                case R.id.back:
                    operationIntent.putExtra(IConstant.BROWSE_FILE_OPERATION_STYLE, IConstant.BACK_BROWSE_MODE);
                    break;
                case R.id.delete:
                    operationIntent.putExtra(IConstant.BROWSE_FILE_OPERATION_STYLE, IConstant.DELETE_BROWSE_FILE);
                    break;
                case R.id.ftp_download:
                    operationIntent.putExtra(IConstant.BROWSE_FILE_OPERATION_STYLE, IConstant.DOWNLOAD_BROWSE_FILE);
                    break;
                case R.id.device_mode_btn:
                    mHandler.removeMessages(MSG_DEVICE_MODE);
                    mHandler.sendEmptyMessageDelayed(MSG_DEVICE_MODE, DELAY_TIME);
                    break;
                case R.id.local_mode_btn:
                    mHandler.removeMessages(MSG_LOCAL_MODE);
                    mHandler.sendEmptyMessageDelayed(MSG_LOCAL_MODE, DELAY_TIME);
                    break;
            }
            Dbug.e(tag, " currentMode = " + currentMode);
            operationIntent.putExtra(IConstant.BROWSE_FRAGMENT_TYPE, currentMode);
            sendBroadcast(operationIntent);
        }
    };

}
