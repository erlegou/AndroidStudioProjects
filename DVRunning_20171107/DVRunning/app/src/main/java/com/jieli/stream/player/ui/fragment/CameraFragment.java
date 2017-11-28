package com.jieli.stream.player.ui.fragment;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.jieli.lib.stream.beans.MenuInfo;
import com.jieli.lib.stream.beans.StateInfo;
import com.jieli.lib.stream.interfaces.OnRTStreamListener;
import com.jieli.lib.stream.interfaces.OnRecordListener;
import com.jieli.lib.stream.tools.AVIStreamer;
import com.jieli.lib.stream.tools.AVPlayer;
import com.jieli.lib.stream.tools.AVPlayerException;
import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.tools.ParseHelper;
import com.jieli.lib.stream.util.ICommon;
import com.jieli.stream.player.MainApplication;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseFragment;
import com.jieli.stream.player.tool.CommandManager;
import com.jieli.stream.player.ui.activity.BrowseFileActivity;
import com.jieli.stream.player.ui.activity.TimelineActivity;
import com.jieli.stream.player.ui.dialog.NotifyDialog;
import com.jieli.stream.player.ui.dialog.SettingsDialog;
import com.jieli.stream.player.ui.dialog.VideosChoiceDialog;
import com.jieli.stream.player.ui.lib.MjpegView;
import com.jieli.stream.player.ui.lib.PopupMenu;
import com.jieli.stream.player.util.AppUtil;
import com.jieli.stream.player.util.BufChangeHex;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IAction;
import com.jieli.stream.player.util.IConstant;
import com.jieli.stream.player.util.PreferencesHelper;
import com.jieli.stream.player.util.ScanFilesHelper;
import com.jieli.stream.player.util.StorageUtil;
import com.jieli.stream.player.util.TimeFormater;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CameraFragment extends BaseFragment implements ICommon, IAction, IConstant {
    private static final String tag = CameraFragment.class.getSimpleName();

    private AVPlayer mAVPlayer;
    private boolean isTaking = false;
    private boolean isBrowseMode = false;
    private PowerManager.WakeLock wake_lock;
    private ImageButton mPhotoVideoButton, mImageQualityButton;
    private ImageButton mPlayButton, mModeSettingsButton, mSettingsButton;
    private ImageButton mPhotoBalanceButton, mContinuousShooting;
    private ImageButton mBattery, mBrowseButton, mPlaybackButton;
    private ImageButton mZoomBtn, mNarrowBtn;
    private ImageButton mRTSSizeBtn;
    private TextView mRecordFlag, mCountdown, mShowTime, mDualSwitchBtn;
    private int mItemSelectedPosition;
    private CommandHub mCommandHub;
    private CommandManager mCommandManager;

    private Gallery mGallery;
    private SettingsDialog mSettingsDialog;
    private RelativeLayout mTopPanel;
    private LinearLayout mBottomPanel;
    private RelativeLayout mDigitalZoom;
    private MjpegView mSurfaceView;
    private boolean mShowing = true;
    private boolean isZoomShowing = false;
    private boolean isOnLeft = false;
    private Intent toBrowseFileIntent;

    private final int SWIPE_MIN_DISTANCE = 120;
    private final int SWIPE_MAX_OFF_PATH = 250;
    private final int SWIPE_THRESHOLD_VELOCITY = 200;
    private TimeTask mTimeTask;
    private FrameLayout mScreenFlash;
    private ImageButton streamPlayModeBtn;
    private ImageView mControlRTSVoice;
    private boolean isTimelineRequest = false;
    private boolean isSelectRTSLevel = false;
//    private boolean isRTSOpening = false;
    private boolean isRTSVoiceOpen = false;
    private boolean isDigitalZoomCmdSend = false;
    private boolean isCmdExist = false;
    private boolean isCmdSend = false;
    private boolean isPhotoStyle = false;
    private boolean isVGA = true;
    private int currentDefinition = -1;
    private int mStreamSelectLevel = -1;
    private int currentSizePosition = 0;
    private int mSelectSizePosition = -1;
    private final int TIMELINE_REQUEST_CODE = 1024;
    private final int BROWSER_REQUEST_CODE = 1025;
    private MyHandler handler;
    private String currentMode;
    private String lastZoomMultiple;
    private String[] streamPlayMode = new String[]{"fl_icon", "sd_icon", "hd_icon"};
    private String[] sizeMode = new String[]{"rts_vga_icon", "rts_720p_icon"};

    private AVIStreamer mAVIStreamer;
    private boolean noCardRecording = false;
    private boolean noCardTaking = false;
    private boolean isOpenFlash = false;
    private NotifyDialog noCardRecordDialog;
    private int videoWidth = 640;
    private int videoHeight = 480;
    private int light = 0;
    private int frameRate = 30;
    private int noCardRecordMax = 30;
    private static int recordTimeCount = 0;
    private List<String> noCardRecordPathList;
    private String savePhotoPath;
    private String savePhotoName;
    private static final long MIN_STORAGE_SPACE = 30 * 1024 * 1024;//30Mb
    private static final long DEFAULT_VIDEO_SIZE = 60 * 1024 * 1024;//60Mb
    private static final int DEFAULT_INTERVAL_TIME = 60;//second
	private boolean isDualSwitch = false;
	private int mTimelineItemSelection = 0;
    private ScanFilesHelper scanFilesHelper;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_GENERIC_DATA:
                {
                    final StateInfo stateInfo = (StateInfo) intent.getSerializableExtra(KEY_GENERIC_STATE);

                    if (stateInfo == null){
                        Dbug.e(tag, "error:stateInfo is null");
                        return;
                    }
                    String param1 = stateInfo.getParam()[0];
                    String cmdResult = stateInfo.getCmdNumber();
                    Dbug.i(tag, "ACTION_GENERIC_DATA:stateInfo cmdResult=" + stateInfo.getCmdNumber() + ", param1=" + param1);
                    switch (TextUtils.isEmpty(cmdResult) ? ARGS_NULL : cmdResult) {
                        case CMD_PHOTO_SIZE:
                            if(param1.compareTo("0") < 0){
                                if(param1.equals(ARGS_CMD_NOT_REALIZE)){
                                    mImageQualityButton.setVisibility(View.GONE);
                                }
                                return;
                            }else{
                                if(mImageQualityButton.getVisibility() == View.GONE){
                                    mImageQualityButton.setVisibility(View.VISIBLE);
                                }
                            }
                            syncDeviceState(currentMode, cmdResult, param1, mImageQualityButton);
                            break;
                        case CMD_WHITE_BALANCE:
                            if(param1.compareTo("0") < 0){
                                if(param1.equals(ARGS_CMD_NOT_REALIZE)){
                                    mPhotoBalanceButton.setVisibility(View.GONE);
                                }
                                return;
                            }else{
                                if(mPhotoBalanceButton.getVisibility() == View.GONE){
                                    mPhotoBalanceButton.setVisibility(View.VISIBLE);
                                }
                            }
                            syncDeviceState(JS_SETTINGS_MODE, cmdResult, param1, mPhotoBalanceButton);
                            break;
                        case CMD_CONTINUOUS_SHOOTING:
                            if(param1.compareTo("0") < 0){
                                if(param1.equals(ARGS_CMD_NOT_REALIZE)){
                                    mContinuousShooting.setVisibility(View.GONE);
                                }
                                return;
                            }else{
                                if(mContinuousShooting.getVisibility() == View.GONE){
                                    mContinuousShooting.setVisibility(View.VISIBLE);
                                }
                            }
                            syncDeviceState(currentMode, cmdResult, param1,mContinuousShooting);
                            break;
                        case CMD_VIDEO_SIZE:
                            if(param1.compareTo("0") < 0){
                                if(param1.equals(ARGS_CMD_NOT_REALIZE)){
                                    mImageQualityButton.setVisibility(View.GONE);
                                }
                                return;
                            }else{
                                if(mImageQualityButton.getVisibility() == View.GONE){
                                    mImageQualityButton.setVisibility(View.VISIBLE);
                                }
                            }
                            syncDeviceState(currentMode, cmdResult, param1, mImageQualityButton);
                            break;
                        case CMD_TIMER_PICTURE:
                            if(param1.compareTo("0") < 0){
                                if(param1.equals(ARGS_CMD_NOT_REALIZE)){
                                    mPhotoVideoButton.setVisibility(View.GONE);
                                }
                                return;
                            }else{
                                if(mPhotoVideoButton.getVisibility() == View.GONE){
                                    mPhotoVideoButton.setVisibility(View.VISIBLE);
                                }
                            }
                            syncDeviceState(currentMode, cmdResult, param1, mPhotoVideoButton);
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
                            if(!ARGS_DEVICE_MODE_FAILURE.equals(param1)){
                                if(noCardRecording){
                                    noCardRecording = false;
                                    stopNoCardRecording();
                                }
                                if(noCardTaking){
                                    noCardTaking = false;
                                }
                                changeText(param1);
                            }
                            break;
                        case CMD_SP_SSID:
                        case CMD_SP_PASSWORD:
                        case CMD_SDCARD_STATE:
                            break;
                        case CMD_BATTERY_STATE:
                            switch (param1) {
                                case ARGS_BATTERY_NONE_GRID:
                                    mBattery.setImageResource(R.mipmap.ic_battery_0);
                                    break;
                                case ARGS_BATTERY_ONE_GRID:
                                    mBattery.setImageResource(R.mipmap.ic_battery_1);
                                    break;
                                case ARGS_BATTERY_TWO_GRID:
                                    mBattery.setImageResource(R.mipmap.ic_battery_3);
                                    break;
                                case ARGS_BATTERY_THREE_GRID:
                                    mBattery.setImageResource(R.mipmap.ic_battery_full);
                                    break;
                                case ARGS_BATTERY_CHARGING:
                                    mBattery.setImageResource(R.mipmap.ic_battery_charging);
                                    break;
                            }
                            break;
                        case CMD_TAKE_PHOTO:
                            shootSound();
                            if(isOpenFlash){
                                flashScreen();
                            }
                            isTaking = false;
                            break;
                        case CMD_START_RECORD:
                            if (ARGS_START_RECORD_SUCCESS.equals(param1)) {
                                if(noCardRecording){
                                    noCardRecording = false;
                                    stopNoCardRecording();
                                }
                                showRecordingUI();
                            } else {
                                if (stateInfo.getParam().length>1 && !TextUtils.isEmpty(stateInfo.getParam()[1]))
                                    showToastLong(stateInfo.getParam()[1]);
                            }
                            break;
                        case CMD_STOP_RECORD:
                            if (ARGS_STOP_RECORD_SUCCESS.equals(param1)) {
	                            if (mAVPlayer.isFrontStreamPlaying())
		                            mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN);
	                            if (mAVPlayer.isRearStreamPlaying())
		                            mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_REAR_RTS_OPEN);
                                HideRecordingUI();
                            } else {
                                if (stateInfo.getParam().length>1 && !TextUtils.isEmpty(stateInfo.getParam()[1]))
                                    showToastLong(stateInfo.getParam()[1]);
                            }
                            break;
                        case CMD_DELETE_FILE:
                        case CMD_DELETE_ALL:
                            break;
                        case CMD_VIDEO_PICTURE_QUALITY:
                            switch (param1) {
                                case ARGS_QUALITY_SUPERB:
                                    mPhotoVideoButton.setImageResource(R.mipmap.ic_quality_most);
                                    break;
                                case ARGS_QUALITY_GOOD:
                                    mPhotoVideoButton.setImageResource(R.mipmap.ic_quality_better);
                                    break;
                                case ARGS_QUALITY_GENERAL:
                                    mPhotoVideoButton.setImageResource(R.mipmap.ic_quality_normal);
                                    break;
                            }
                            break;
                        case CMD_METERING:
                            break;
                        case CMD_GET_RECORDING_STATUS:
                            switch (param1) {
                                case ARGS_NO_RECORDING:
                                    HideRecordingUI();
                                    break;
                                case ARGS_IN_RECORDING:
                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            showRecordingUI();
                                        }
                                    }, 300);
                                    break;
                            }
                            break;
                        case CMD_TIMER_PICTURE_STATUS:
                            int countdown = Integer.parseInt(param1);
                            if (countdown > 0) {
                                mTimerCount = countdown;
                                mCountdown.setVisibility(View.VISIBLE);
                                if (mDualSwitchBtn.isShown())
                                    mDualSwitchBtn.setVisibility(View.INVISIBLE);
                                handler.post(timerPicture);
                            } else {
                                return;
                            }
                            break;
                        case CMD_ENTER_BROWSING_MODE:
                            if (getActivity() == null) {
                                Dbug.e(tag, "getActivity() is null");
                                return;
                            }
                            int count = Integer.parseInt(param1);
                            if (count <= 0) {
                                if (stateInfo.getParam().length >1 && !TextUtils.isEmpty(stateInfo.getParam()[1]))
                                    showToastLong(stateInfo.getParam()[1]);
                                mApplication.setAllowBrowseDev(false);
                                if(isBrowseMode){
                                    isBrowseMode = false;
                                }
                                return;
                            }else{
                                if (isTimelineRequest) {
                                    isTimelineRequest = false;
//                                    startActivity(new Intent(getActivity(), TimelineActivity.class));
                                    Intent timelineIntent = new Intent(getActivity(), TimelineActivity.class);
	                                timelineIntent.putExtra("item_choice", mTimelineItemSelection);
                                    startActivityForResult(timelineIntent, TIMELINE_REQUEST_CODE);
                                }

                                mApplication.setAllowBrowseDev(true);
                                if(isBrowseMode){
                                    /**Send CMD to tell device that APP is exiting BROWSING MODE*/
                                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_EXIT_BROWSING_MODE, ARGS_EXIT_BROWSING_MODE);
                                }
                            }
                            isBrowseMode = true;
                            break;
                        case CMD_EXIT_BROWSING_MODE:
                            Dbug.d(tag, "CMD_EXIT_BROWSING_MODE------------------:");
//                            tryToPlay();
                            mApplication.setAllowBrowseDev(false);
                            isBrowseMode = false;
                            break;
	                    case CMD_REAR_RTS_OPEN:
		                    if(param1.compareTo("0") < 0){
			                    if(param1.equals(ARGS_CMD_NOT_REALIZE)){
				                    mDualSwitchBtn.setVisibility(View.GONE);
			                    }
			                    return;
		                    }else{
			                    if(mDualSwitchBtn.getVisibility() == View.GONE){
				                    mDualSwitchBtn.setVisibility(View.VISIBLE);
				                    return;
			                    }
		                    }
		                    if (mAVPlayer.isRearStreamPlaying()){
			                    if (mAVPlayer != null) {
                                    try {
                                        mAVPlayer.clearCache();
                                    } catch (AVPlayerException e) {
                                        e.printStackTrace();
                                    }
                                    mAVPlayer.setOnRTStreamListener(onRTStreamListener);
			                    }
			                    int videoWidth = Integer.parseInt(stateInfo.getParam()[3]);
			                    int videoHeight = Integer.parseInt(stateInfo.getParam()[4]);
			                    int light = Integer.parseInt(stateInfo.getParam()[5]);

			                    int cWidth = mSurfaceView.getContrastCompressWidth();
			                    int cHeight = mSurfaceView.getContrastCompressHeight();
			                    mSurfaceView.setJpegWidthAndHeightAndLevel(videoWidth, videoHeight, light);
			                    if (mSurfaceView.getVisibility() == View.GONE) {
				                    Dbug.w(tag, " rear view visible");
				                    mSurfaceView.setVisibility(View.VISIBLE);
			                    }else if(cWidth != videoWidth || cHeight != videoHeight){
				                    Dbug.w(tag, " ====update rear view==========");
				                    mSurfaceView.setVisibility(View.GONE);
				                    mSurfaceView.setJpegWidthAndHeightAndLevel(videoWidth, videoHeight, light);
				                    mSurfaceView.setVisibility(View.VISIBLE);
			                    }
			                    savePlayState();
                                mDualSwitchBtn.setText(getString(R.string.video_rear));
		                    } else {
			                    if (mAVPlayer.isFrontStreamPlaying()){
				                    Dbug.w(tag, "Front view is playing.");
				                    return;
			                    }
			                    int resolution = Integer.parseInt(stateInfo.getParam()[3]);
			                    Dbug.w(tag, "resolution = "+ resolution);
			                    String value = ARGS_RT_STREAM_VGA;
			                    switch (resolution){
				                    case 640:
					                    value = ARGS_RT_STREAM_VGA;
					                    break;
				                    case 720:
					                    value = ARGS_RT_STREAM_720P;
					                    break;
			                    }
			                    videoHeight = Integer.parseInt(stateInfo.getParam()[4]);
			                    mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_REAR_RTS_OPEN, stateInfo.getParam()[1], value);
		                    }
		                    break;

                        case CMD_RT_STREAM_OPEN:
	                        if (mAVPlayer.isRearStreamPlaying()){
		                        Dbug.w(tag, "Rear view is playing.");
		                        return;
	                        }
                            if (ARGS_RT_STREAM_OPEN_SUCCESS.equals(param1)) {
                                if (stateInfo.getParam().length <= 1) {
                                    Dbug.e(tag, "param2 not exit");
                                    return;
                                }
                                String param2 = stateInfo.getParam()[1];
                                Dbug.i(tag, "CMD_RT_STREAM_OPEN success...................param2=" + param2);
                                if (TextUtils.isEmpty(param2)) {
                                    Dbug.e(tag, "param2 not exit");
                                    return;
                                }
                                if (mAVPlayer != null) {
                                    try {
                                        mAVPlayer.clearCache();
                                    } catch (AVPlayerException e) {
                                        e.printStackTrace();
                                    }
                                    mAVPlayer.setOnRTStreamListener(onRTStreamListener);
                                }
                                switch (param2) {
                                    case ARGS_RT_STREAM_OPEN_FLUENCY:
                                        if (currentDefinition != 0) {
                                            currentDefinition = 0;
                                        }
                                        break;
                                    case ARGS_RT_STREAM_OPEN_SD:
                                        if (currentDefinition != 1) {
                                            currentDefinition = 1;
                                        }
                                        break;
                                    case ARGS_RT_STREAM_OPEN_HD:
                                        if (currentDefinition != 2) {
                                            currentDefinition = 2;
                                        }
                                        break;
                                    default:
                                        Dbug.e(tag, "CMD_RT_STREAM_OPEN fail");
                                        break;
                                }
                                if (stateInfo.getParam().length <= 2) {
                                    Dbug.e(tag, "param3 not exit");
                                    return;
                                }
                                String param3 = stateInfo.getParam()[2];
                                if (TextUtils.isEmpty(param3)) {
                                    Dbug.e(tag, "param3 is empty!");
                                    return;
                                }
                                switch (param3) {
                                    case ARGS_RT_STREAM_CLOSED:
//                                        isRTSOpening = false;
	                                    isCmdSend = false;
                                        break;
                                    case ARGS_RT_STREAM_OPENED:
//                                        isRTSOpening = true;
                                        isCmdSend = false;
                                        isSelectRTSLevel = false;
                                        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_CONTROL_RTS_VOICE);
                                        break;
                                }
                                if (stateInfo.getParam().length >= 6) {
                                    videoWidth = Integer.parseInt(stateInfo.getParam()[3]);
                                    videoHeight = Integer.parseInt(stateInfo.getParam()[4]);
                                    light = Integer.parseInt(stateInfo.getParam()[5]);
                                    Dbug.e(tag, " isRTSOpening = " + mAVPlayer.isFrontStreamPlaying() + " ,w=" + videoWidth + ", h=" + videoHeight + ", l=" + light);
                                    if(mAVPlayer.isFrontStreamPlaying()){
                                        int cWidth = mSurfaceView.getContrastCompressWidth();
                                        int cHeight = mSurfaceView.getContrastCompressHeight();
                                        mSurfaceView.setJpegWidthAndHeightAndLevel(videoWidth, videoHeight, light);
                                        if (mSurfaceView.getVisibility() == View.GONE) {
                                            Dbug.w(tag, " mSurfaceView visible");
                                            mSurfaceView.setVisibility(View.VISIBLE);
                                        }else if(cWidth != videoWidth || cHeight != videoHeight){
                                            Dbug.w(tag, " ====mSurfaceView==========");
                                            mSurfaceView.setVisibility(View.GONE);
                                            mSurfaceView.setJpegWidthAndHeightAndLevel(videoWidth, videoHeight, light);
                                            mSurfaceView.setVisibility(View.VISIBLE);
                                        }
                                    }
                                    if (videoWidth == 640 && videoHeight == 480) {
                                        currentSizePosition = 0;
                                    } else if (videoWidth == 1280 && videoHeight == 720) {
                                        currentSizePosition = 1;
                                    }else{
                                        currentSizePosition = 0;
                                    }

                                    if(stateInfo.getParam().length >= 7){
                                        int perFrame = Integer.parseInt(stateInfo.getParam()[6]);
                                        if(perFrame > 0){
                                            frameRate = 1000000/perFrame;
                                        }
                                    }

                                    if(stateInfo.getParam().length >= 8){
                                        String supportHD = stateInfo.getParam()[7];
                                        if(!TextUtils.isEmpty(supportHD)){
                                            if(supportHD.equals(ARGS_NO_SUPPORT_720P)){
                                                mRTSSizeBtn.setVisibility(View.GONE);
                                            }else{
                                                if(mRTSSizeBtn.getVisibility() == View.GONE){
                                                    mRTSSizeBtn.setVisibility(View.VISIBLE);
                                                }
                                            }
                                        }
                                    }else{
                                        if(mRTSSizeBtn.getVisibility() == View.GONE){
                                            mRTSSizeBtn.setVisibility(View.VISIBLE);
                                        }
                                    }
                                }
                                Dbug.i(tag, "currentDefinition : " + currentDefinition + ", sRTSOpening=" + mAVPlayer.isFrontStreamPlaying() + " currentSizePosition : " + currentSizePosition);
                                updateDefinition(currentDefinition, currentSizePosition);
                            }else {
                                isCmdSend = false;
                            }
                            break;
	                    case CMD_REAR_RTS_CLOSE:
                        case CMD_RT_STREAM_CLOSE:
                            if (ARGS_RT_STREAM_CLOSE_SUCCESS.equals(param1)){
//                                isRTSOpening = false;
                                Dbug.i(tag, "ARGS_RT_STREAM_CLOSE_SUCCESS: mStreamSelectLevel=" + mStreamSelectLevel +" isSelectRTSLevel :"
                                        +isSelectRTSLevel + ", currentDefinition :" +currentDefinition + ", isDualSwitch="+isDualSwitch);
	                            if (isDualSwitch) {
		                            isDualSwitch = false;
		                            if (CMD_REAR_RTS_CLOSE.equals(cmdResult)) {
                                        String definition = matchVideoClarity(currentDefinition);
                                        mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN, definition, currentSizePosition +"");
                                    } else if (CMD_RT_STREAM_CLOSE.equals(cmdResult)){
			                            mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_REAR_RTS_OPEN, ARGS_RT_STREAM_OPEN_FLUENCY, currentSizePosition +"");
		                            }
	                            }
                                if (isSelectRTSLevel) {
//                                    isSelectRTSLevel = false;
                                    if(isBrowseMode){
                                        Dbug.w(tag, "send CMD_RT_STREAM_OPEN cmd error!");
                                        return;
                                    }

                                    if(mSelectSizePosition >= 0 && mSelectSizePosition != currentSizePosition){
                                        currentSizePosition = mSelectSizePosition;
                                    }
                                    switch (mStreamSelectLevel) {
                                        case 0://FL
                                            if(!isCmdSend){
                                                isCmdSend = true;
                                                mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN, ARGS_RT_STREAM_OPEN_FLUENCY, currentSizePosition +"");
                                            }
                                            break;
                                        case 1://SD
                                            if(!isCmdSend) {
                                                isCmdSend = true;
                                                mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN, ARGS_RT_STREAM_OPEN_SD, currentSizePosition + "");
                                            }
                                            break;
                                        case 2://HD
                                            if(!isCmdSend) {
                                                isCmdSend = true;
                                                mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN, ARGS_RT_STREAM_OPEN_HD, currentSizePosition + "");
                                            }
                                            break;
                                        default:
                                            Dbug.i(tag, "Set stream play level fail");
                                            break;
                                    }
                                } else {
                                    if (mAVPlayer != null) {
                                        mAVPlayer.setOnRTStreamListener(null);
                                    }
                                }

                            } else {
                                Dbug.w(tag, "CMD_RT_STREAM_CLOSE fail");
                            }
                            break;
                        case CMD_CONTROL_RTS_VOICE:
                            if (ARGS_CONTROL_RTS_VOICE_SUCCESS.equals(param1)){
                                if (stateInfo.getParam().length <= 1) {
                                    Dbug.e(tag, "param2 not exit");
                                    return;
                                }
                                String param2 = stateInfo.getParam()[1];
                                switch (param2){
                                    case ARGS_CLOSE_RTS_VOICE_STATE:
                                        isRTSVoiceOpen = false;
                                        break;
                                    case ARGS_OPEN_RTS_VOICE_STATE:
                                        isRTSVoiceOpen = true;
                                        break;
                                }
                                if(mControlRTSVoice != null){
                                    if(isRTSVoiceOpen){
                                        mControlRTSVoice.setImageResource(R.mipmap.open_rts_voice);
                                    }else{
                                        mControlRTSVoice.setImageResource(R.mipmap.close_rts_voice);
                                    }
                                }
                            }else{
                                Dbug.e(tag, "CMD_CONTROL_RTS_VOICE failed!");
                            }
                            break;
                        case CMD_ENV_LIGHT_LEVEL:
                            if (stateInfo.getParam().length >= 1){
                                int ll = Integer.parseInt(stateInfo.getParam()[0]);
//                                Dbug.e(tag, "CMD_ENV_LIGHT_LEVEL set level=" + ll);
                                if(ll >= 0 && ll <= 16 && light != ll){
                                    light = ll;
                                }
                                mSurfaceView.updateLightLevel(light);
                            }
                            break;
                        case CMD_DIGITAL_ZOOM:
                            if(!ARGS_CMD_NOT_REALIZE.equals(param1)){
                                if(!isCmdExist){
                                    isCmdExist = true;
                                    if(isZoomShowing){
                                        showDigitalZoomUI();
                                    }else{
                                        hideDigitalZoomUI();
                                    }
                                }
                            }else{
                                isCmdExist = false;
                            }
                            if(isCmdExist){
                                isDigitalZoomCmdSend = false;
                                if (TextUtils.isEmpty(lastZoomMultiple)) {
                                    lastZoomMultiple = param1;
                                }else{
                                    if(!lastZoomMultiple.equals(param1)){
                                        lastZoomMultiple = param1;
                                    }else{
                                        String tip = getString(R.string.max_min_zoom_rate)+lastZoomMultiple;
                                        showToastShort(tip);
                                        return;
                                    }
                                }
                                String string = getString(R.string.current_zoom_rate) + lastZoomMultiple;
                                showToastShort(string);
                            }else{
                                isDigitalZoomCmdSend = false;
                                hideDigitalZoomUI();
                            }
                            break;
                        case ARGS_NULL:
                            Dbug.e(tag, "error:cmdResult= " + cmdResult);
                            break;
                    }
                    /*for (String p : stateInfo.getParam()) {
                        Dbug.e(tag, "cmdResult:" + cmdResult + ", param==" + p);
                    }*/
//                if (stateInfo.getParam().length > 1 && !TextUtils.isEmpty(stateInfo.getParam()[1])){
//                    Dbug.d(tag, "stateInfo.getParam()[1]==" + stateInfo.getParam()[1]);
//                    showToastShort(stateInfo.getParam()[1]);
//                }
                    break;
                case ACTION_DEVICE_CONNECTION_SUCCESS:
                    Dbug.i(tag, "ACTION_DEVICE_CONNECTION_SUCCESS:");
//                    syncDeviceStatus();
                    break;
                case ACTION_DEVICE_LANG_CHANGED:
                    initializeTextUI();
                    CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_MODE);
                    break;
                case ACTION_INIT_CTP_SOCKET_SUCCESS:
                    Dbug.i(tag, "ACTION_INIT_CTP_SOCKET_SUCCESS:");
                    noCardRecording = false;
                    CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_DIGITAL_ZOOM);
                    CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_ENV_LIGHT_LEVEL);
                    CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_SNAPSHOT);
                    stopNoCardRecording();
                    syncDeviceStatus();
                    break;
                case ACTION_SDCARD_ONLINE:
                    if (noCardRecordDialog != null && noCardRecordDialog.isShowing())
                        noCardRecordDialog.dismiss();

                    if(noCardRecording){
                        noCardRecording = false;
                        stopNoCardRecording();
                    }
                    if(noCardTaking){
                        noCardTaking = false;
                    }
                    break;
                case ACTION_MODIFY_FLASH_SETTING:
                    SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                    isOpenFlash = sharedPreferences.getBoolean(TAKE_PHOTO_FLASH_SETTING, false);
                    break;
            }
        }
    };

    private void syncDeviceState(String mode, String strCmdNumber, String contentId, ImageButton imageButton) {
        String jsonMode = "";
        switch (TextUtils.isEmpty(mode) ? ARGS_NULL : mode){
            case ARGS_VIDEO_MODE:
                jsonMode = JS_VIDEO_MODE;
                break;
            case ARGS_PHOTO_MODE:
                jsonMode = JS_PHOTO_MODE;
                break;
            case ARGS_NULL:
                Dbug.i(tag, "Unknown error=" + mode);
                break;
            default:
                jsonMode = JS_SETTINGS_MODE;
                break;
        }
        Dbug.i(tag, "syncDeviceState= jsonMode =" + jsonMode+ ", strCmdNumber=" + strCmdNumber + ", contentId=" + contentId);
        List<MenuInfo> menuList = ParseHelper.getInstance().getMenuData(jsonMode, strCmdNumber);
        if (menuList == null) {
            Dbug.e(tag, "No menu info=");
            return;
        }
        Dbug.i(tag, "syncDeviceState= size =" + menuList.size());
        int id = Integer.parseInt(contentId);
        for (MenuInfo menuInfo : menuList) {
            if (menuInfo.getId() == id) {
                if (TextUtils.isEmpty(menuInfo.getImage())) {
                    if (menuInfo.getStateBitmap() != null) {
                        imageButton.setImageBitmap(menuInfo.getStateBitmap());
                    }
                } else {
                    int resId = getResource(menuInfo.getImage());
                    imageButton.setImageResource(resId);
                }
                break;
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dbug.d(tag, "onStart:onStart");
        IntentFilter intentFilter = new IntentFilter(IAction.ACTION_SPECIAL_DATA);
        intentFilter.addAction(ACTION_DEVICE_CONNECTION_SUCCESS);
        intentFilter.addAction(ACTION_DEVICE_LANG_CHANGED);
        intentFilter.addAction(ACTION_INIT_CTP_SOCKET_SUCCESS);
        intentFilter.addAction(ACTION_SDCARD_ONLINE);
        intentFilter.addAction(ACTION_GENERIC_DATA);
        intentFilter.addAction(ACTION_MODIFY_FLASH_SETTING);
        mApplication.registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public void onResume() {
        super.onResume();
        Dbug.w(tag, "-----------onResume-------------:");
        if(mApplication.getIsOffLineMode()){
            tryToStop();
        }else{
            initializeTextUI();
            /**Get timer picture status when fragment first created*/
            mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_TIMER_PICTURE_STATUS);

            syncDeviceStatus();
        }
    }

	@Override
    public void onStop() {
        super.onStop();
        Dbug.w(tag, "-----------onStop-------------:");
        if (mAVPlayer != null){
            if(!isBrowseMode){
                tryToStop();
            }
        }
        if(noCardRecording){
            noCardRecording = false;
            stopNoCardRecording();
        }
        if(noCardTaking){
            noCardTaking = false;
        }
        isCmdSend = false;
        isSelectRTSLevel = false;
        mApplication.unregisterReceiver(mReceiver);
    }

    private void syncDeviceStatus(){
        Dbug.d(tag, "syncDeviceStatus is start!");
        String state = mCommandManager.getDeviceStatus(CMD_DEVICE_MODE);

        switch (TextUtils.isEmpty(state) ? ARGS_NULL : state){
            case ARGS_VIDEO_MODE:
                changeText(ARGS_VIDEO_MODE);
                break;
            case ARGS_PHOTO_MODE:
                changeText(ARGS_PHOTO_MODE);
                break;
            case ARGS_PLAY_MODE:
                changeText(ARGS_PLAY_MODE);
                break;
            case ARGS_USB_MODE:
                break;
            case ARGS_NULL:
            case ARGS_DEVICE_MODE_FAILURE:
                /**Get current mode*/
                mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_MODE);
                break;
        }

        state = mCommandManager.getDeviceStatus(CMD_BATTERY_STATE);
        switch (TextUtils.isEmpty(state) ? ARGS_NULL : state){
            case ARGS_BATTERY_NONE_GRID:
                mBattery.setImageResource(R.mipmap.ic_battery_0);
                break;
            case ARGS_BATTERY_ONE_GRID:
                mBattery.setImageResource(R.mipmap.ic_battery_1);
                break;
            case ARGS_BATTERY_TWO_GRID:
                mBattery.setImageResource(R.mipmap.ic_battery_3);
                break;
            case ARGS_BATTERY_THREE_GRID:
                mBattery.setImageResource(R.mipmap.ic_battery_full);
                break;
            case ARGS_BATTERY_CHARGING:
                mBattery.setImageResource(R.mipmap.ic_battery_charging);
                break;
            case ARGS_NULL:
            case ARGS_CMD_NOT_ALLOW:
                /**Get current battery status*/
                mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_BATTERY_STATE);
                break;
        }

        state = mCommandManager.getDeviceStatus(CMD_CONTROL_RTS_VOICE);
        switch (TextUtils.isEmpty(state) ? ARGS_NULL : state){
            case ARGS_CLOSE_RTS_VOICE_STATE:
                mControlRTSVoice.setImageResource(R.mipmap.close_rts_voice);
                break;
            case ARGS_OPEN_RTS_VOICE_STATE:
                mControlRTSVoice.setImageResource(R.mipmap.open_rts_voice);
                break;
            case ARGS_NULL:
            case ARGS_CMD_NOT_ALLOW:
                mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_CONTROL_RTS_VOICE);
                break;
        }

        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_ENTER_BROWSING_MODE);
        Dbug.w(tag, "-syncDeviceStatus- get Front Last State : " + mApplication.getFrontLastState());
	    if(mApplication.getFrontLastState())
		    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN);
	    if (mApplication.getRearLastState())
		    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_REAR_RTS_OPEN);
    }

    private void flashScreen() {
        AlphaAnimation fade = new AlphaAnimation(0.2f, 0.0f);
        fade.setDuration(200);
        fade.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mScreenFlash.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation anim) {
                mScreenFlash.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        if(mScreenFlash.getVisibility() == View.GONE){
            mScreenFlash.setVisibility(View.VISIBLE);
            mScreenFlash.startAnimation(fade);
        }
    }
    private void shootSound(){
        if(getActivity() == null){
            return;
        }
        if(mSettingsDialog != null && mSettingsDialog.isShowing()){
            mSettingsDialog.dismiss();
        }
        AudioManager audioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        int volume = audioManager.getStreamVolume( AudioManager.STREAM_MUSIC);
        Dbug.d(tag, "volume=:" + volume);


        if (volume != 0){
            MediaPlayer mMediaPlayer = MediaPlayer.create(getActivity(), (R.raw.camera_click));
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if(mp != null){
                        mp.stop();
                        mp.release();
                    }
                }
            });
            mMediaPlayer.start();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_camera, container, false);
        PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        wake_lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
        wake_lock.setReferenceCounted(false);

        mTopPanel = (RelativeLayout) view.findViewById(R.id.topPanel);
        mBottomPanel = (LinearLayout) view.findViewById(R.id.bottomPanel);
        mPlayButton = (ImageButton) view.findViewById(R.id.start_now);
        mModeSettingsButton = (ImageButton) view.findViewById(R.id.mode_info);
        mSettingsButton = (ImageButton) view.findViewById(R.id.settings);
        mPhotoVideoButton = (ImageButton) view.findViewById(R.id.photo_video_quality);
        mPhotoVideoButton.setVisibility(View.GONE);
        mImageQualityButton = (ImageButton) view.findViewById(R.id.resolution_video_size);
        mImageQualityButton.setVisibility(View.GONE);
        mPhotoBalanceButton = (ImageButton) view.findViewById(R.id.balance);
        mPhotoBalanceButton.setVisibility(View.GONE);
        mContinuousShooting = (ImageButton) view.findViewById(R.id.continuous_shooting);
        mContinuousShooting.setVisibility(View.GONE);
        mBattery = (ImageButton) view.findViewById(R.id.battery);
        mRecordFlag = (TextView) view.findViewById(R.id.record_flag);
        mCountdown = (TextView) view.findViewById(R.id.countdown);
        mShowTime = (TextView) view.findViewById(R.id.show_datetime);
        mShowTime.setVisibility(View.INVISIBLE);
        mScreenFlash = (FrameLayout) view.findViewById(R.id.screen_flash);
        streamPlayModeBtn = (ImageButton) view.findViewById(R.id.rts_play_mode);
        mControlRTSVoice = (ImageView) view.findViewById(R.id.rts_voice_control);
        mGallery = (Gallery) view.findViewById(R.id.gallery);
        mZoomBtn = (ImageButton) view.findViewById(R.id.zoom_btn);
        mNarrowBtn = (ImageButton) view.findViewById(R.id.narrow_btn);
        mDigitalZoom = (RelativeLayout) view.findViewById(R.id.digital_zoom_layout);
        mDigitalZoom.setVisibility(View.GONE);
        mRTSSizeBtn = (ImageButton) view.findViewById(R.id.rts_play_size);
	    mDualSwitchBtn = (TextView) view.findViewById(R.id.rts_front_rear_switch);

        mSurfaceView = (MjpegView) view.findViewById(R.id.surface_video);

        mPlaybackButton = (ImageButton) view.findViewById(R.id.playback_mode);
        mBrowseButton = (ImageButton) view.findViewById(R.id.browse);
        mBrowseButton.setOnClickListener(mOnClickListener);
        mModeSettingsButton.setOnClickListener(mOnClickListener);
        mPlayButton.setOnClickListener(mOnClickListener);
        mSettingsButton.setOnClickListener(mOnClickListener);
        mPlaybackButton.setOnClickListener(mOnClickListener);
        mControlRTSVoice.setOnClickListener(mOnClickListener);
        mPhotoVideoButton.setOnClickListener(mOnClickListener);
        mImageQualityButton.setOnClickListener(mOnClickListener);
        mPhotoBalanceButton.setOnClickListener(mOnClickListener);
        mContinuousShooting.setOnClickListener(mOnClickListener);
        mZoomBtn.setOnClickListener(mOnClickListener);
        mNarrowBtn.setOnClickListener(mOnClickListener);
        streamPlayModeBtn.setOnClickListener(mOnClickListener);
        mRTSSizeBtn.setOnClickListener(mOnClickListener);
	    mDualSwitchBtn.setOnClickListener(mOnClickListener);

        handler = new MyHandler(mShowTime, getActivity());

        //屏幕滑动
        final GestureDetector gesture = new GestureDetector(getActivity(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!mShowing) {
                    showOverlay();
                } else {
                    hideOverlay();
                }
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent motionEvent) {
                if(mShowing){
                    if(isCmdExist){
                        if(!isZoomShowing){
                            showDigitalZoomUI();
                        }else{
                            hideDigitalZoomUI();
                        }
                    }
                }
//                Dbug.i(tag, "-------------onSingleTapUp: mShowing="+mShowing + ", mTopPanel="+mTopPanel.isShown());
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                try {
                    if(noCardRecording){
                        return true;
                    }
                    if(noCardTaking){
                        return true;
                    }
                    if(isDigitalZoomCmdSend){
                        return true;
                    }
                    if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
                        return false;
                    }
                    if (ARGS_IN_RECORDING.equals(mCommandManager.getDeviceStatus(CMD_GET_RECORDING_STATUS))) {
                        showToastShort(getResources().getString(R.string.it_is_recording));
                        return true;
                    }
                    if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        if (!isOnLeft){
                            return true;
                        }
                        handler.removeMessages(MSG_CHANGE_MODE);
                        handler.sendMessageDelayed(handler.obtainMessage(MSG_CHANGE_MODE, ARGS_PHOTO_MODE), MSG_DELAY);
                    } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        if (isOnLeft){
                            return true;
                        }
                        handler.removeMessages(MSG_CHANGE_MODE);
                        handler.sendMessageDelayed(handler.obtainMessage(MSG_CHANGE_MODE, ARGS_VIDEO_MODE), MSG_DELAY);
                    }
                } catch (Exception e) {
                    // nothing
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gesture.onTouchEvent(event);
            }
        });

        return view;
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (ARGS_TAKING_PHOTO.equals(mCommandManager.getDeviceStatus(CMD_PHOTO_STATE))){
                showToastLong(getString(R.string.taking_photo));
                return;
            }
            String state;
            if (v == mModeSettingsButton){
                switch (mItemSelectedPosition) {
                    case 0:
                        /*Video settings*/
                        mSettingsDialog = new SettingsDialog();
                        Bundle bundle = mSettingsDialog.getArguments();
                        if(bundle != null){
                            bundle = mSettingsDialog.getArguments();
                        } else {
                            bundle = new Bundle();
                        }
                        bundle.putString(JS_MODE_CATEGORY, JS_VIDEO_MODE);
                        mSettingsDialog.setArguments(bundle);
                        mSettingsDialog.show(getActivity().getFragmentManager(), "VideoSettingsDialog");
                        mModeSettingsButton.setImageResource(R.mipmap.ic_video_prs);
                        mSettingsDialog.setOnDismissListener(onDismissListener);
                        break;
                    case 1:
                        /*Photo settings*/
                        mSettingsDialog = new SettingsDialog();
                        Bundle photoBundle = mSettingsDialog.getArguments();
                        if(photoBundle != null){
                            mSettingsDialog.getArguments();
                        } else {
                            photoBundle = new Bundle();
                        }
                        photoBundle.putString(JS_MODE_CATEGORY, JS_PHOTO_MODE);
                        mSettingsDialog.setArguments(photoBundle);
                        mSettingsDialog.show(getActivity().getFragmentManager(), "PhotoSettingsDialog");
                        mModeSettingsButton.setImageResource(R.mipmap.ic_photo_prs);
                        mSettingsDialog.setOnDismissListener(onDismissListener);
                        break;
                    default:
                        Dbug.e(tag, "mItemSelectedPosition error: " + mItemSelectedPosition);
                        break;
                }
            } else if (v == mPlayButton){//视频照片
                wake_lock.acquire();
                if (!mApplication.isSdcardState()) {//SD是否存在
                    switch (currentMode){
                        case ARGS_VIDEO_MODE:
                            if(noCardRecording){
                                stopNoCardRecording();
                            }else{
                                showNoCardRecordDialog();
                            }
                            break;
                        case ARGS_PHOTO_MODE:
                            if(!noCardTaking) {
                                if(!BufChangeHex.isFastDoubleClick(1000)){
                                    startNoCardTaking();
//                                    showToastShort(getString(R.string.sdcard_error));
                                }else{
                                    showToastShort(getString(R.string.please_wait));
                                }
                            }else{
                                showToastShort(getString(R.string.taking_photo));
                            }
                            break;
                        case ARGS_DEVICE_MODE_FAILURE:
                            mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_MODE);
                            break;
                    }
//                    showToastLong(getString(R.string.sdcard_error));
                    return;
                }
//                Dbug.i(tag, "mItemSelectedPosition=" + mItemSelectedPosition);
                switch (mItemSelectedPosition) {
                    case 0:/*Click to record*/
                        handler.removeMessages(MSG_TAKING_VIDEO);
                        handler.sendEmptyMessageDelayed(MSG_TAKING_VIDEO, 500);
                        break;
                    case 1:/*Click to photo*/
//                        mTimerCount = mApplication.getTimerPicture();
                        if (!isTaking) {
                            isTaking = true;
                            mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_TAKE_PHOTO, ARGS_TAKE_PHOTO);
                        } else {
                            showToastShort(getResources().getString(R.string.please_wait));
                        }
                        Dbug.d(tag, "-----------mTimerCount:" + mTimerCount);
                        /*if (mTimerCount > 0){
                            mCountdown.setVisibility(View.VISIBLE);
                            handler.post(timerPicture);
                        }*/
                        break;
                }
            } else if (v == mBrowseButton){
	            if (mDualSwitchBtn.isShown()){
		            showPlaybackEntranceWindow(1);
	            }else {
		            tryToStop();
		            enterFileBrowser(0);
	            }
	            Dbug.i(tag, " mBrowseButton isClick OK! isSdcardState : " + mApplication.isSdcardState());

            } else if (v == mSettingsButton){
                mSettingsDialog = new SettingsDialog();
                Bundle bundle = mSettingsDialog.getArguments();
                if(bundle != null){
                    bundle = mSettingsDialog.getArguments();
                } else {
                    bundle = new Bundle();
                }
                bundle.putString(JS_MODE_CATEGORY, JS_SETTINGS_MODE);
                mSettingsDialog.setOnDismissListener(onDismissListener);
                mSettingsDialog.setArguments(bundle);
                mSettingsDialog.show(getActivity().getFragmentManager(), "SettingsDialog");
                mSettingsButton.setImageResource(R.mipmap.ic_settings_prs);
            } else if (v == mPlaybackButton) {
                if (!mApplication.isSdcardState()) {
                    showToastLong(getString(R.string.sdcard_error));
                    return;
                }
	            if (mDualSwitchBtn.isShown()){
                    showPlaybackEntranceWindow(0);
	            } else {
                    tryToEnterPlayback();
	            }
            } else if(v == mControlRTSVoice){
                if(isRTSVoiceOpen){
                    mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_CONTROL_RTS_VOICE, ARGS_CLOSE_RTS_VOICE);
                }else{
                    mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_CONTROL_RTS_VOICE, ARGS_OPEN_RTS_VOICE);
                }
            }else if(v == mPhotoVideoButton) {
                state = mCommandManager.getDeviceStatus(CMD_TIMER_PICTURE);
                if (TextUtils.isEmpty(state)) {
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_TIMER_PICTURE);
                } else {
                    showPopupMenu(v, JS_PHOTO_MODE, CMD_TIMER_PICTURE, state);
                }
            }else if(v == mImageQualityButton){
                Dbug.e(tag, "mImageQualityButton is clicked!  currentMode = " +currentMode);
                switch (currentMode){
                    case ARGS_VIDEO_MODE:
                        state = mCommandManager.getDeviceStatus(CMD_VIDEO_SIZE);
                        if(TextUtils.isEmpty(state)){
                            mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_VIDEO_SIZE);
                        }else{
                            showPopupMenu(v, JS_VIDEO_MODE, CMD_VIDEO_SIZE, state);
                        }
                        break;
                    case ARGS_PHOTO_MODE:
                        state = mCommandManager.getDeviceStatus(CMD_PHOTO_SIZE);
                        if(TextUtils.isEmpty(state)){
                            mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_PHOTO_SIZE);
                        }else{
                            showPopupMenu(v, JS_PHOTO_MODE, CMD_PHOTO_SIZE, state);
                        }
                        break;
                }
            }else if(v == mPhotoBalanceButton){
                state = mCommandManager.getDeviceStatus(CMD_WHITE_BALANCE);
                if(TextUtils.isEmpty(state)){
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_WHITE_BALANCE);
                }else{
                    showPopupMenu(v, JS_SETTINGS_MODE, CMD_WHITE_BALANCE, state);
                }
            }else if(v == mContinuousShooting){
                state = mCommandManager.getDeviceStatus(CMD_CONTINUOUS_SHOOTING);
                if(TextUtils.isEmpty(state)){
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_CONTINUOUS_SHOOTING);
                }else{
                    showPopupMenu(v, JS_PHOTO_MODE, CMD_CONTINUOUS_SHOOTING, state);
                }
            }else if(v == mZoomBtn){
                if(isCmdExist){
                    if(!isDigitalZoomCmdSend){
                        mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_DIGITAL_ZOOM, ARGS_ZOOM_FOCAL_LENGTH);
                        isDigitalZoomCmdSend = true;
                    }else{
                        showToastShort(R.string.please_wait);
                    }
                }
            }else if(v == mNarrowBtn){
                if(isCmdExist){
                    if(!isDigitalZoomCmdSend){
                        mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_DIGITAL_ZOOM, ARGS_NARROW_FOCAL_LENGTH);
                        isDigitalZoomCmdSend = true;
                    }else{
                        showToastShort(R.string.please_wait);
                    }
                }
            }else if(v == streamPlayModeBtn){
                List<String> data = new ArrayList<>();
                Collections.addAll(data, streamPlayMode);
                final PopupMenu popupMenu = new PopupMenu(getActivity(), data, null);
                popupMenu.setOnPopItemClickListener(new PopupMenu.OnPopItemClickListener() {
                    @Override
                    public void onItemClick(List<String> data, List<Integer> resIds, int index) {
//                        Dbug.w(tag, "streamPlayModeBtn tryToStop======position=" + index + " ,mStreamSelectLevel= " + mStreamSelectLevel
//                                    + " ,isRTSOpening = " +isRTSOpening);
	                    if (mAVPlayer.isRearStreamPlaying()){
		                    return;
	                    }
                        if (mStreamSelectLevel != index) {
                            isSelectRTSLevel = true;
                            mStreamSelectLevel = index;
                            tryToStop();
                        }
                        popupMenu.dismiss();
                    }
                });
                popupMenu.showAsDropDown(v);
            }else if(v == mRTSSizeBtn){
                List<String> data = new ArrayList<>();
                Collections.addAll(data, sizeMode);
                final PopupMenu popupMenu = new PopupMenu(getActivity(), data, null);
                popupMenu.setOnPopItemClickListener(new PopupMenu.OnPopItemClickListener() {
                    @Override
                    public void onItemClick(List<String> data, List<Integer> resIds, int index) {
                        Dbug.d(tag, "mRTSSizeBtn tryToStop======position=" + index + " ,mSelectSizePosition= " + mSelectSizePosition);
	                    if (mAVPlayer.isRearStreamPlaying()){
		                    return;
	                    }
                        if (mSelectSizePosition != index) {
                            isSelectRTSLevel = true;
                            mSelectSizePosition = index;
                            tryToStop();
                            mSurfaceView.setVisibility(View.GONE);
                        }
                        popupMenu.dismiss();
                    }
                });
                popupMenu.showAsDropDown(v);
            } else if (v == mDualSwitchBtn) {
	            String string = "try to open :" + (mAVPlayer.isFrontStreamPlaying() ? "front view" : "rear view");
//	            showToastLong(string);
	            Dbug.i(tag, string);
	            isDualSwitch = true;
	            if (mAVPlayer.isFrontStreamPlaying()) {
		            mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_RT_STREAM_CLOSE, ARGS_RT_STREAM_CLOSE);
	            } else if (mAVPlayer.isRearStreamPlaying()){
		            mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_REAR_RTS_CLOSE, ARGS_REAR_RTS_CLOSE);
	            }
            }
        }
    };

    private void showPopupMenu(View view, String mode, final String commonCmd, final String modeIndex){
        if(view == null || TextUtils.isEmpty(mode) || TextUtils.isEmpty(commonCmd) || TextUtils.isEmpty(modeIndex)){
            Dbug.e(tag,"showPopupMenu param is null! ");
            return;
        }
        List<MenuInfo> menuData = ParseHelper.getInstance().getMenuData(mode, commonCmd);
        List<String> resNames = new ArrayList<>();
        List<Integer> resId = new ArrayList<>();

        for(int i = 0; i < menuData.size(); i++){
            MenuInfo info = menuData.get(i);
            if(info != null){
                resNames.add(info.getImage());
                resId.add(info.getId());
            }
        }
        final PopupMenu popupMenu = new PopupMenu(getActivity(), resNames, resId);
        popupMenu.setOnPopItemClickListener(new PopupMenu.OnPopItemClickListener() {
            @Override
            public void onItemClick(List<String> data, List<Integer> resIds, int index) {
                if (index < data.size()) {
                    String currentMode = resIds.get(index) + "";
                    if (!modeIndex.equals(currentMode)) {
                        /**Send command to device when submenu item was clicked.*/
                        CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, commonCmd, currentMode);
                    }
                }
                popupMenu.dismiss();
            }
        });
        popupMenu.showAsDropDown(view);
    }

    private static final String OPERATION = "operation";

    private void initializeTextUI() {
        SimpleAdapter mSimpleAdapter = new SimpleAdapter(getActivity(), getData(), R.layout.photo_video_item, new String[]{OPERATION},
                new int[]{R.id.text});
        mGallery.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                showToastLong(getString(R.string.switch_mode_tips));
                return true;
            }
        });
        mGallery.setAdapter(mSimpleAdapter);
        mGallery.setSpacing(40);
    }

    private ArrayList<Map<String, Object>> getData(){
        ArrayList<Map<String, Object>> list = new ArrayList<>();
        String[] operations = getResources().getStringArray(R.array.camera_list);

        for (String operation : operations){
            Map<String, Object> map = new HashMap<>();
            map.put(OPERATION, operation);
            list.add(map);
        }
        return list;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Dbug.i(tag, "Create --------");
        mAVPlayer = new AVPlayer();
        boolean isSuccess = mAVPlayer.createSocket(ICommon.AP_RT_STREAM_PORT, -1, "", AVPlayer.TYPE_RT_VIDEO);
        if (isSuccess) {
            mAVPlayer.setQueueMax(1, 30, 256);
            try {
                mAVPlayer.startListening();
            } catch (AVPlayerException e) {
                com.jieli.lib.stream.util.Dbug.e(tag, "Start Process fail");
                e.printStackTrace();
            }
            com.jieli.lib.stream.util.Dbug.i(tag, "Create socket success");
        } else {
            Dbug.e(tag, "Create socket fail");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Dbug.w(tag, "onActivityCreated --------");
        if(getActivity() == null) return;
        mCommandHub = CommandHub.getInstance();
        mCommandManager = CommandManager.getInstance();
	    ///Request if devices support those functions
        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_DIGITAL_ZOOM);
        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_ENV_LIGHT_LEVEL);
        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_SNAPSHOT);

        if(noCardRecordPathList == null){
            noCardRecordPathList = new ArrayList<>();
        }
        if(scanFilesHelper == null){
            //媒体扫描
            scanFilesHelper =  new ScanFilesHelper(getActivity().getApplicationContext());
        }

        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
        isOpenFlash = sharedPreferences.getBoolean(TAKE_PHOTO_FLASH_SETTING, false);

        if (mAVIStreamer == null) {
            mAVIStreamer = new AVIStreamer();
            mAVIStreamer.setOnRecordListener(new OnRecordListener() {
                @Override
                public void onStart(String filePath) {
                    Dbug.w(tag, "OnRecordListener onStart filePath:" + filePath);
                }

                /**
                 * 录制完成回调
                 * @param filePath     保存路径
                 * @param isSuccess    是否成功
                 * @param isContinue   是否继续录制
                 */
                @Override
                public void onCompletion(String filePath, boolean isSuccess, boolean isContinue) {
                    Dbug.e(tag, "OnRecordListener onCompletion filePath:" + filePath);
                    if (!isSuccess) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showToastShort(R.string.recording_failed);
                                noCardRecording = false;
                                stopNoCardRecording();
                            }
                        });
                        if (!TextUtils.isEmpty(filePath)) {
                            File errorFile = new File(filePath);
                            if (errorFile.exists() && errorFile.isFile()) {
                                if (errorFile.delete()) {
                                    Dbug.w(tag, "no card record failed!");
                                }
                            }
                        }
                    } else {
                        if (scanFilesHelper != null && !TextUtils.isEmpty(filePath)) {
                            scanFilesHelper.scanFiles(filePath);
                        }
                        if (isContinue) {
                            if (noCardRecordPathList != null) {
                                int size = noCardRecordPathList.size();
                                if (size == 0) {
                                    noCardRecordPathList.add(filePath);
                                } else {
                                    if (size < (noCardRecordMax - 1)) {
                                        noCardRecordPathList.add(filePath);
                                    } else {
                                        String deletePath = noCardRecordPathList.get(0);
                                        File deleteFile = new File(deletePath);
                                        if (deleteFile.exists() && deleteFile.isFile()) {
                                            if (deleteFile.delete()) {
                                                Dbug.e(tag, "memory size is full,so delete the earliest video. path :" + deletePath);
                                            }
                                        }
                                        noCardRecordPathList.remove(0);
                                        noCardRecordPathList.add(filePath);
                                        Dbug.e(tag, "remove after noCardRecordPathList size : " + noCardRecordPathList.size());
                                    }
                                }
                            }
                        }
                    }
                }

                @Override
                public void onError(int state, String messages) {
                    if (state == AVIStreamer.RecordError.ERROR_OUT_OF_STORAGE_SPACE) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showToastLong(R.string.phone_space_inefficient);
                                noCardRecording = false;
                                stopNoCardRecording();
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        if (null != wake_lock && wake_lock.isHeld()){
            wake_lock.release();
        }
        if(mAVIStreamer != null){
            mAVIStreamer.release();
            mAVIStreamer = null;
        }
        if(scanFilesHelper != null){
            scanFilesHelper.release();
            scanFilesHelper = null;
        }
        videoWidth = 640;
        videoHeight = 480;
        light = 0;
        isCmdSend = false;
        isDigitalZoomCmdSend = false;
        noCardTaking = false;
        noCardRecording = false;
        isPhotoStyle = false;
        toBrowseFileIntent = null;
        isTaking = false;
        mSelectSizePosition = -1;

        if(noCardRecordDialog != null && noCardRecordDialog.isShowing()){
            noCardRecordDialog.dismiss();
            noCardRecordDialog = null;
        }
        if(noCardRecordPathList != null){
            noCardRecordPathList.clear();
            noCardRecordPathList = null;
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        Dbug.e(tag, "----------------onDestroy :");
        if (mAVPlayer != null) {
            try {
                mAVPlayer.destroy();
            } catch (AVPlayerException e) {
                e.printStackTrace();
            }
        }
        if (mSurfaceView != null) {
            mSurfaceView.release();
        }
        super.onDestroy();
    }

    private final SettingsDialog.OnDismissListener onDismissListener = new SettingsDialog.OnDismissListener() {
        @Override
        public void onDismiss(String target) {
            mSettingsDialog = null;
            if (TextUtils.isEmpty(target)){
                Dbug.e(tag, "OnDismissListener: target is null");
                return;
            }

            String stringPosition = String.valueOf(mItemSelectedPosition);
            switch (stringPosition){
                case ARGS_PHOTO_MODE:
                    mModeSettingsButton.setImageResource(R.mipmap.ic_photo);
                    break;
                case ARGS_VIDEO_MODE:
                    mModeSettingsButton.setImageResource(R.mipmap.ic_video);
                    break;
                default:
                    break;
            }
            if (target.equals(JS_SETTINGS_MODE)){
                mSettingsButton.setImageResource(R.mipmap.ic_settings);
            }
        }
    };

    private void updateDefinition(int position, int sizePos){
        if(getActivity() == null || position < 0 || streamPlayModeBtn == null|| mRTSSizeBtn == null){
            return;
        }
        if(sizePos < 0){
            sizePos = 0;
        }
        if(position != mStreamSelectLevel){
            mStreamSelectLevel = position;
        }
        /**Update spinner selection*/
        if(position < streamPlayMode.length){
            streamPlayModeBtn.setImageResource(getResource(streamPlayMode[position]));
        }
        if(sizePos < sizeMode.length){
            mRTSSizeBtn.setImageResource(getResource(sizeMode[sizePos]));
            mSelectSizePosition = sizePos;
        }
        Dbug.i(tag, "updateDefinition setSelection = " + position + " size mode position : " + sizePos
                    + " mStreamSelectLevel : " +mStreamSelectLevel);
        String definition = matchVideoClarity(position);

        String sizeMode = ARGS_RT_STREAM_VGA;
        switch (sizePos){
            case 0:
                sizeMode = ARGS_RT_STREAM_VGA;
                break;
            case 1:
                sizeMode = ARGS_RT_STREAM_720P;
                break;
        }
        if(!mAVPlayer.isFrontStreamPlaying()){
            if(!isCmdSend){
                isCmdSend = true;
                mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN, definition, sizeMode);
            }
        } else {
	        savePlayState();
            if (mDualSwitchBtn.isShown())
                mDualSwitchBtn.setText(getString(R.string.video_front));
        }
    }

    private int getResource(String imageName){
        if (TextUtils.isEmpty(imageName) || getActivity() == null){
            return 0;
        }
        return getActivity().getResources().getIdentifier(imageName, RESOURCE_DIR , getActivity().getPackageName());
    }

    private void changeText(String mode) {
        if (mSettingsDialog != null && mSettingsDialog.isShowing()){
            mSettingsDialog.dismiss();
        }
        if(TextUtils.isEmpty(mode) || ARGS_DEVICE_MODE_FAILURE.equals(mode)){
            Dbug.e(tag,"changeText mode is null!");
            return;
        }
        currentMode = mode;
        String state;
        switch (mode) {
            case ARGS_VIDEO_MODE:
            {
                mItemSelectedPosition = Integer.parseInt(mode);
                mGallery.setSelection(mItemSelectedPosition);
                isOnLeft = true;
                /*update top bar state*/
                mPlayButton.setBackgroundResource(R.mipmap.ic_video_mode);
                mModeSettingsButton.setImageResource(R.mipmap.ic_video);
                mContinuousShooting.setVisibility(View.GONE);

                /**Send command to get top bar state*/
                state = mCommandManager.getDeviceStatus(CMD_VIDEO_PICTURE_QUALITY);
                mPhotoVideoButton.setVisibility(View.GONE);
                switch (TextUtils.isEmpty(state) ? ARGS_NULL : state){
                    case ARGS_QUALITY_SUPERB:
                        mPhotoVideoButton.setImageResource(R.mipmap.ic_quality_most);
                        break;
                    case ARGS_QUALITY_GOOD:
                        mPhotoVideoButton.setImageResource(R.mipmap.ic_quality_better);
                        break;
                    case ARGS_QUALITY_GENERAL:
                        mPhotoVideoButton.setImageResource(R.mipmap.ic_quality_normal);
                        break;
                    case ARGS_NULL:
                        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_VIDEO_PICTURE_QUALITY);
                        break;
                }

                state = mCommandManager.getDeviceStatus(CMD_VIDEO_SIZE);
                if (TextUtils.isEmpty(state)) {
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_VIDEO_SIZE);
                } else {
                    if(mImageQualityButton.getVisibility() == View.GONE){
                        mImageQualityButton.setVisibility(View.VISIBLE);
                    }
                    syncDeviceState(currentMode, CMD_VIDEO_SIZE, state, mImageQualityButton);
                }

                state = mCommandManager.getDeviceStatus(CMD_WHITE_BALANCE);
                if (TextUtils.isEmpty(state)) {
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_WHITE_BALANCE);
                } else {
                    if(mPhotoBalanceButton.getVisibility() == View.GONE){
                        mPhotoBalanceButton.setVisibility(View.VISIBLE);
                    }
                    syncDeviceState(JS_SETTINGS_MODE, CMD_WHITE_BALANCE, state, mPhotoBalanceButton);
                }

                state = mCommandManager.getDeviceStatus(CMD_GET_RECORDING_STATUS);
                switch (TextUtils.isEmpty(state) ? ARGS_NULL : state){
                    case ARGS_NO_RECORDING:
                        HideRecordingUI();
                        break;
                    case ARGS_IN_RECORDING:
                        showRecordingUI();
                        break;
                    case ARGS_NULL:
                        /**Update recording status*/
                        mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_GET_RECORDING_STATUS);
                        break;
                }
                break;
            }
            case ARGS_PHOTO_MODE:
                mItemSelectedPosition = Integer.parseInt(mode);
                mGallery.setSelection(mItemSelectedPosition);
                isOnLeft = false;
                isTaking = false;
                /*update top bar state*/
                mPlayButton.setBackgroundResource(R.mipmap.ic_photo_mode);
                mModeSettingsButton.setImageResource(R.mipmap.ic_photo);

                mPhotoVideoButton.setVisibility(View.VISIBLE);
                /**Send command to update top bar state*/
                state = mCommandManager.getDeviceStatus(CMD_TIMER_PICTURE);
                if (TextUtils.isEmpty(state)) {
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_TIMER_PICTURE);
                } else {
                    if(mPhotoVideoButton.getVisibility() == View.GONE){
                        mPhotoVideoButton.setVisibility(View.VISIBLE);
                    }
                    syncDeviceState(currentMode, CMD_TIMER_PICTURE, state, mPhotoVideoButton);
                }

                state = mCommandManager.getDeviceStatus(CMD_PHOTO_SIZE);
                if (TextUtils.isEmpty(state)) {
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_PHOTO_SIZE);
                } else {
                    if(mImageQualityButton.getVisibility() == View.GONE){
                        mImageQualityButton.setVisibility(View.VISIBLE);
                    }
                    syncDeviceState(currentMode, CMD_PHOTO_SIZE, state, mImageQualityButton);
                }

                state = mCommandManager.getDeviceStatus(CMD_WHITE_BALANCE);
                if (TextUtils.isEmpty(state)) {
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_WHITE_BALANCE);
                } else {
                    if(mPhotoBalanceButton.getVisibility() == View.GONE){
                        mPhotoBalanceButton.setVisibility(View.VISIBLE);
                    }
                    syncDeviceState(JS_SETTINGS_MODE, CMD_WHITE_BALANCE, state, mPhotoBalanceButton);
                }

                state = mCommandManager.getDeviceStatus(CMD_CONTINUOUS_SHOOTING);
                if (TextUtils.isEmpty(state)) {
                    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_CONTINUOUS_SHOOTING);
                } else {
                    if(mContinuousShooting.getVisibility() == View.GONE){
                        mContinuousShooting.setVisibility(View.VISIBLE);
                    }
                    syncDeviceState(currentMode, CMD_CONTINUOUS_SHOOTING, state, mContinuousShooting);
                }
            case ARGS_PLAY_MODE:
                break;
            case ARGS_USB_MODE:
                break;
            default:
                break;
        }
    }

    private void startFlick( View view ){
        if( null == view ){
            return;
        }

        view.setVisibility(View.VISIBLE);
        Animation alphaAnimation = new AlphaAnimation( 1, 0 );
        alphaAnimation.setDuration( 500 );
        alphaAnimation.setInterpolator( new LinearInterpolator( ) );
        alphaAnimation.setRepeatCount( Animation.INFINITE );
        alphaAnimation.setRepeatMode(Animation.REVERSE);
        view.startAnimation(alphaAnimation);
    }

    private void stopFlick( View view ){

        if( null == view ){
            return;
        }
        view.setVisibility(View.INVISIBLE);
        view.clearAnimation();
    }

    private final int MSG_DELAY = 250;//MS
    private static final int MSG_CHANGE_MODE = 0X100;
    private static final int MSG_TAKING_VIDEO = 0X102;
    private static final int MSG_SHOW_RECORDING_TIME = 0x104;
    private static class MyHandler extends Handler {
        private final WeakReference<TextView> mTextViewRef;
        private final WeakReference<Activity> mActivityRef;
        private Toast mToastShort;

        MyHandler(TextView textView, Activity activity) {
            mTextViewRef = new WeakReference<>(textView);
            mActivityRef = new WeakReference<>(activity);
        }
        private void showToastShort(String msg) {
            if(mActivityRef.get() == null){
                return;
            }
            if (mToastShort != null) {
                mToastShort.setText(msg);
            } else {
                mToastShort = Toast.makeText(mActivityRef.get(), msg, Toast.LENGTH_SHORT);
            }
            mToastShort.show();
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what){
                case MSG_CHANGE_MODE:
                    String cmd = (String) message.obj;
                    switch (TextUtils.isEmpty(cmd) ? ARGS_NULL : cmd){
                        case ARGS_VIDEO_MODE:
                            CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_DEVICE_MODE, ARGS_VIDEO_MODE);
                            break;
                        case ARGS_PHOTO_MODE:
                            CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_DEVICE_MODE, ARGS_PHOTO_MODE);
                            break;
                        case ARGS_NULL:
                            Dbug.e(tag, "message.obj:error="+cmd);
                            break;
                        case ARGS_CMD_NOT_ALLOW:
                            CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_DEVICE_MODE);
                            break;
                    }
                    break;
                case MSG_TAKING_VIDEO:
                    String state = CommandManager.getInstance().getDeviceStatus(CMD_GET_RECORDING_STATUS);
                    Dbug.w(tag, "-onReceive- MSG_TAKING_VIDEO # state = " +state);
                    switch (TextUtils.isEmpty(state) ? ARGS_NULL : state){
                        case ARGS_NO_RECORDING:
                            CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_START_RECORD, ARGS_START_RECORD);
                            break;
                        case ARGS_IN_RECORDING:
                            CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_STOP_RECORD, ARGS_STOP_RECORD);
                            break;
                        case ARGS_NULL:
                            if (mActivityRef.get() != null) {
                                showToastShort(mActivityRef.get().getResources().getString(R.string.operation_error));
                            }
                            break;
                        case ARGS_CMD_NOT_ALLOW:
                            CommandHub.getInstance().requestStatus(CTP_ID_DEFAULT, CMD_GET_RECORDING_STATUS);
                            break;
                    }
                    break;
                case MSG_SHOW_RECORDING_TIME:
                    recordTimeCount++;
                    String recordTimeFormat = showRecordingTimeFormat(recordTimeCount);
                    if(mTextViewRef.get() != null){
                        if(!TextUtils.isEmpty(recordTimeFormat)){
                            mTextViewRef.get().setText(recordTimeFormat);
                        }
                    }
                    break;
            }
        }
    }

    private static class TimeTask extends Thread {
        private WeakReference<Handler> handlerRef = null;
        private boolean isTimeRunning = false;

        TimeTask(Handler handler) {
            handlerRef = new WeakReference<>(handler);
        }

        void stopTask(){
            isTimeRunning = false;
        }

        @Override
        public void run() {
            super.run();
            isTimeRunning = true;

            while (isTimeRunning){
                SystemClock.sleep(1000);
                if (handlerRef != null && handlerRef.get() != null) {
                    handlerRef.get().obtainMessage(MSG_SHOW_RECORDING_TIME).sendToTarget();
                }
            }
        }
    }

    private int mTimerCount = 0;
    private final Runnable timerPicture = new Runnable() {
        @Override
        public void run() {
            if (mTimerCount > 0){
                String timeCount = mTimerCount + "";
                mCountdown.setText(timeCount);
                handler.postDelayed(timerPicture, 1000);
            } else {
                handler.removeCallbacks(timerPicture);
                mCountdown.setVisibility(View.GONE);
                if (mDualSwitchBtn.getVisibility() == View.INVISIBLE)
                    mDualSwitchBtn.setVisibility(View.VISIBLE);
                mTimerCount = 0;
            }
            mTimerCount --;
        }
    };

	private void enterFileBrowser(int selectedPosition){
		if (getActivity() == null) {
			Dbug.e(tag, "getActivity() is null!");
			return;
		}
		if(mApplication.getDeviceUUID() == null){
			Dbug.e(tag, "uuid is null!");
			return;
		}
		if (BufChangeHex.readSDCard() <= 100*1024*1024){
			showToastShort(R.string.phone_space_less);
		}
		if(toBrowseFileIntent == null){
			toBrowseFileIntent = new Intent(getActivity(), BrowseFileActivity.class);
			if (selectedPosition == 1){
				toBrowseFileIntent.putExtra("which_dir", VIEW_REAR);
//				AppUtil.createAppDownloadDirectory(getActivity(), REAR_DOWNLOAD);
			} else{
//				AppUtil.createAppDownloadDirectory(getActivity(), "download");
				toBrowseFileIntent.putExtra("which_dir", VIEW_FRONT);
			}
			startActivityForResult(toBrowseFileIntent, BROWSER_REQUEST_CODE);
		}
//		/**Create dir for APP*/
//		AppUtil.createAppDownloadDirectory(getActivity());
	}

    private void showRecordingUI(){
        if(mSettingsDialog != null && mSettingsDialog.isAdded()){
            mSettingsDialog.dismiss();
        }
        mPlayButton.setBackgroundResource(R.mipmap.ic_start_recording);
        if(!mApplication.isSdcardState() && mTimeTask != null){
            recordTimeCount = 0;
            mShowTime.setVisibility(View.VISIBLE);
        }
        startFlick(mRecordFlag);
        mTopPanel.setVisibility(View.INVISIBLE);
        mBrowseButton.setVisibility(View.INVISIBLE);
        mPlaybackButton.setVisibility(View.INVISIBLE);
        mModeSettingsButton.setVisibility(View.INVISIBLE);
        mSettingsButton.setVisibility(View.INVISIBLE);
        if (mDualSwitchBtn.isShown())
            mDualSwitchBtn.setVisibility(View.INVISIBLE);
    }

    private void HideRecordingUI(){
        if(noCardRecording){
            return;
        }
        mPlayButton.setBackgroundResource(R.mipmap.ic_video_mode);
        if(mTimeTask != null){
            recordTimeCount = 0;
            mShowTime.setVisibility(View.INVISIBLE);
        }
        stopFlick(mRecordFlag);
        mTopPanel.setVisibility(View.VISIBLE);
        mTopPanel.requestLayout();
        mBrowseButton.setVisibility(View.VISIBLE);
        mPlaybackButton.setVisibility(View.VISIBLE);
        mModeSettingsButton.setVisibility(View.VISIBLE);
        mSettingsButton.setVisibility(View.VISIBLE);
        if (mDualSwitchBtn.getVisibility() == View.INVISIBLE)
            mDualSwitchBtn.setVisibility(View.VISIBLE);
    }

    private void showDigitalZoomUI(){
        if(!isCmdExist){
            return;
        }
        isZoomShowing = true;
        if(mDigitalZoom != null){
            mDigitalZoom.setVisibility(View.VISIBLE);
        }
    }

    private void hideDigitalZoomUI(){
        isZoomShowing = false;
        if(mDigitalZoom != null){
            mDigitalZoom.setVisibility(View.GONE);
        }
    }

    /**
     * show overlay
     */
    private void showOverlay() {
        mShowing = true;
        if (mTopPanel !=null){
            if (!ARGS_IN_RECORDING.equals(mCommandManager.getDeviceStatus(CMD_GET_RECORDING_STATUS))) {
                mTopPanel.setVisibility(View.VISIBLE);
                if (mDualSwitchBtn.getVisibility() == View.INVISIBLE && mTimerCount <=0)
                    mDualSwitchBtn.setVisibility(View.VISIBLE);
            }
        }
        if (mBottomPanel != null){
            mBottomPanel.setVisibility(View.VISIBLE);
        }
    }

    /**
     * hider overlay
     */
    private void hideOverlay() {
        mShowing = false;
        if (mTopPanel !=null){
            mTopPanel.setVisibility(View.GONE);
        }
        if (mBottomPanel != null){
            mBottomPanel.setVisibility(View.GONE);
        }
        hideDigitalZoomUI();

        if (mDualSwitchBtn.isShown())
            mDualSwitchBtn.setVisibility(View.INVISIBLE);
    }

    private final OnRTStreamListener onRTStreamListener = new OnRTStreamListener() {
        @Override
        public void onVideo(byte[] videoData, int resolutionType, int rtsChannel) {
//            Dbug.i(tag, "OnRTStreamListener : onVideo=" + videoData.length+ " ,resolutionType : " +resolutionType);
            if(isPhotoStyle){
                isPhotoStyle = false;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSurfaceView.setVisibility(View.GONE);
                        mSurfaceView.setJpegWidthAndHeightAndLevel(videoWidth, videoHeight, light);
                        mSurfaceView.setVisibility(View.VISIBLE);
                    }
                });
                return;
            }

            if(noCardRecording){//录制开始
                if(mAVIStreamer != null){
//                    Dbug.w(tag, "OnRTStreamListener :  onVideo addData = " + videoData.length);
                    /**保存到手机本地*/
                    mAVIStreamer.addData(AVIStreamer.TYPE_AVI_VIDEO, videoData, videoData.length);
                }
            }
            if(noCardTaking){
                noCardTaking = false;
                if(!TextUtils.isEmpty(savePhotoPath) && !TextUtils.isEmpty(savePhotoName)){
                    if(BufChangeHex.byte2File(videoData, savePhotoPath, savePhotoName)){
                        Dbug.w(tag, "save picture ok. photoName : " + savePhotoName);
                        try {
                            String savePath = savePhotoPath + File.separator + savePhotoName;
                            if(!TextUtils.isEmpty(savePath)){
                                scanFilesHelper.scanFiles(savePath);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

           if(mAVPlayer.isFrontStreamPlaying() && rtsChannel==AVPlayer.RTS_CHANNEL_FRONT){
               switch (resolutionType) {
                   case AVPlayer.TYPE_RESOLUTION_DEFAULT:
//                       Dbug.i(tag, "OnRTStreamListener : DefaultData=" + videoData.length);
                       if(mSurfaceView != null){
                           mSurfaceView.drawBitmap(videoData, true);
                       }
                       break;
                   case AVPlayer.TYPE_RESOLUTION_VGA:
//                       Dbug.w(tag, "OnRTStreamListener : VGAData=" + videoData.length);
                       handler.post(new Runnable() {
                           @Override
                           public void run() {
                               if (!isVGA) {
                                   isVGA = true;
                                   mSurfaceView.setVisibility(View.GONE);
                                   mSurfaceView.setJpegWidthAndHeightAndLevel(640, 480, light);
                                   mSurfaceView.setVisibility(View.VISIBLE);
                               }
                           }
                       });
                       if(mSurfaceView != null){
                           mSurfaceView.drawBitmap(videoData, true);
                       }
                       break;
                   case AVPlayer.TYPE_RESOLUTION_720P:
//                       Dbug.w(tag, "OnRTStreamListener : HDData=" + videoData.length);
                       handler.post(new Runnable() {
                           @Override
                           public void run() {
                               if(isVGA) {
                                   isVGA = false;
                                   mSurfaceView.setVisibility(View.GONE);
                                   mSurfaceView.setJpegWidthAndHeightAndLevel(1280, 720, light);
                                   mSurfaceView.setVisibility(View.VISIBLE);
                               }
                           }
                       });
                       if(mSurfaceView != null){
                           mSurfaceView.drawBitmap(videoData, true);
                       }
                       break;
               }

           } else if (AVPlayer.RTS_CHANNEL_REAR== rtsChannel && mAVPlayer.isRearStreamPlaying()){
	           switch (resolutionType) {
		           case AVPlayer.TYPE_REAR_RESOLUTION_VGA:
			           if (mSurfaceView != null){
				           mSurfaceView.drawBitmap(videoData, true);
			           }
			           break;
		           case AVPlayer.TYPE_REAR_RESOLUTION_720P:
			           if (mSurfaceView != null){
				           mSurfaceView.drawBitmap(videoData, true);
			           }
			           break;
		           default:
			           break;
	           }
	        }
        }

        @Override
        public void onAudio(byte[] audioData) {
//            Dbug.w(tag, "OnRTStreamListener : onAudio=" + audioData.length);
            if(noCardRecording){
                if(mAVIStreamer != null){
//                    Dbug.w(tag, "OnRTStreamListener : onAudio addData = " + audioData.length);
                    /**保存到手机本地*/
                    mAVIStreamer.addData(AVIStreamer.TYPE_AVI_AUDIO, audioData, audioData.length);
                }
            }
            if(isRTSVoiceOpen){
                if(mSurfaceView != null){
                    mSurfaceView.writeAudioData(audioData);
                }
            }
        }

        @Override
        public void onPhoto(byte[] photoData) {
//            Dbug.w(tag, "OnRTStreamListener : onPhoto=" + photoData.length);
            if(!isPhotoStyle){
                isPhotoStyle = true;
            }
            if(mSurfaceView != null) {
//                mSurfaceView.clearCanvas();
                mSurfaceView.drawThumbnail(photoData);
            }
        }
    };

    private void tryToStop() {
        Dbug.i(tag, "tryToStop: CMD_RT_STREAM_CLOSE= front:" + mAVPlayer.isFrontStreamPlaying()
        +", rear:"+mAVPlayer.isRearStreamPlaying());
//	    savePlayState();
	    if(mAVPlayer.isFrontStreamPlaying()){
            mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_RT_STREAM_CLOSE, ARGS_RT_STREAM_CLOSE);
        }
        /*else{
            mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_RT_STREAM_OPEN);
        }*/

	    if (mAVPlayer.isRearStreamPlaying())
		    mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_REAR_RTS_CLOSE, ARGS_REAR_RTS_CLOSE);
	    /*else
		    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_REAR_RTS_OPEN);
		    */
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Dbug.i(tag, "========================requestCode= " + requestCode + ", resultCode=" + resultCode);
        switch (resultCode) {
            case ACTIVITY_RESULT_OK://From TimelineActivity
            case BROWSE_ACTIVITY_RESULT_OK://From BrowseFileActivity
                toBrowseFileIntent = null;
                mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_EXIT_BROWSING_MODE, ARGS_EXIT_BROWSING_MODE);
                break;
        }
    }

    private void showNoCardRecordDialog(){
        if(noCardRecordDialog == null){
            noCardRecordDialog = new NotifyDialog(R.string.dialog_tip, R.string.no_card_record_tip, R.string.cancel, R.string.record_mode,
                    new NotifyDialog.OnNegativeClickListener() {
                        @Override
                        public void onClick() {
                            if (noCardRecordDialog != null) {
                                noCardRecordDialog.dismiss();
                            }
                        }
                    }, new NotifyDialog.OnPositiveClickListener() {
                @Override
                public void onClick() {
                    if (noCardRecordDialog != null) {
                        noCardRecordDialog.dismiss();
                    }
                    initAVIStreamer(frameRate, videoWidth, videoHeight, DEFAULT_INTERVAL_TIME);
                    startNoCardRecording();
                }
            });
            noCardRecordDialog.setCancelable(false);
        }
        if(!noCardRecordDialog.isShowing()){
            noCardRecordDialog.show(getActivity().getFragmentManager(), "No_Card_Record");
        }
    }

    /**
     * 设置AVIStream的参数
     * @param frameRate       帧数(fps)
     * @param videoWidth      视频宽度
     * @param videoHeight     视频高度
     * @param intervalTime    视频循环间隔时间(单位:sec)
     */
    private void initAVIStreamer(int frameRate, int videoWidth, int videoHeight, int intervalTime) {
        Dbug.i(tag, "initAVIStreamer frameRate=" + frameRate);
        if (TextUtils.isEmpty(mApplication.getDeviceUUID())) {
            Dbug.e(tag, "initAVIStreamer UUID is null");
            return;
        }
        String recordDir = AppUtil.getAppStoragePath(mApplication, RECORD, mAVPlayer.isRearStreamPlaying());
        if (TextUtils.isEmpty(recordDir)){
            Dbug.e(tag, "Record dir is null");
            return;
        }
        File dir = new File(recordDir);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                throw new IllegalAccessError("Create " + recordDir +" failure.");
            }
        }
        Dbug.i(tag, "initAVIStreamer dirPath = " + recordDir);

        if(mAVIStreamer != null){
            mAVIStreamer.configureAudio(8000, 16, 1);
            mAVIStreamer.configureVideo(frameRate, videoWidth, videoHeight);
            mAVIStreamer.setFilePath(recordDir);
            mAVIStreamer.setDuration(intervalTime);
        }
    }

    private void startNoCardRecording() {
        if (mAVIStreamer != null && !mAVIStreamer.isRecording()) {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)){
                long availableMemory = StorageUtil.getSdCardFreeBytes();
                if (availableMemory <= MIN_STORAGE_SPACE) {
                    showToastLong(R.string.phone_space_inefficient);
                    return;
                }
                if(availableMemory < (DEFAULT_VIDEO_SIZE * noCardRecordMax)){
                    noCardRecordMax =  (int)(((availableMemory / 5 ) * 4) / DEFAULT_VIDEO_SIZE);
                }

                if (mTimeTask == null) {
                    mTimeTask = new TimeTask(handler);
                }
                if (mTimeTask.getState() == Thread.State.NEW) {
                    mTimeTask.start();
                }

                showRecordingUI();

                Dbug.e(tag, "startNoCardRecording startRecording=");
                mAVIStreamer.startRecording();
                noCardRecording = true;
            } else {
                // No external media
                showToastLong(R.string.not_found_phone_sdcard);
            }
        } else {
            Dbug.i(tag, "startNoCardRecording Already start recording=");
        }
    }

    private void stopNoCardRecording() {
        if (mAVIStreamer != null && mAVIStreamer.isRecording()) {
            mAVIStreamer.stopRecording();//结束录制
            noCardRecording = false;
            HideRecordingUI();
            if(noCardRecordPathList.size() > 0){
                noCardRecordPathList.clear();
            }
        } else {
            Dbug.i(tag, "stopNoCardRecording Already stop recording=");
        }

        if (mTimeTask != null){
            mTimeTask.stopTask();
            mTimeTask = null;
        }
    }

    private void startNoCardTaking() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)){
            long availableMemory = StorageUtil.getSdCardFreeBytes();
            if (availableMemory <= MIN_STORAGE_SPACE) {
                showToastLong(R.string.phone_space_inefficient);
                return;
            }
            String recordDir = AppUtil.getAppStoragePath(mApplication, RECORD, mAVPlayer.isRearStreamPlaying());
            if (TextUtils.isEmpty(recordDir)){
                Dbug.e(tag, "Record dir is null");
                return;
            }
            File dir = new File(recordDir);
            if (!dir.exists()) {
                if (!dir.mkdir()) {
                    throw new IllegalAccessError("Create " + recordDir +" failure.");
                }
            }
            savePhotoPath = recordDir;
            savePhotoName = "JPG_" + TimeFormater.formatYMD_HMS(System.currentTimeMillis()) + ".jpg";
            shootSound();
            if(isOpenFlash){
                flashScreen();
            }
            noCardTaking = true;
        } else {
            // No external media
            showToastLong(R.string.not_found_phone_sdcard);
        }
    }

    private static String showRecordingTimeFormat(int time){
        if(time < 0){return null;}
        int sec = time % 60;
        int min = time / 60 % 60;
        int hour = time / 60 / 60 % 24;
        return String.format(Locale.getDefault(), "%02d : %02d : %02d", hour, min, sec);
    }

	private void showPlaybackEntranceWindow(final int which) {
		VideosChoiceDialog dialog = new VideosChoiceDialog();
		dialog.show(getActivity().getFragmentManager(), VideosChoiceDialog.class.getSimpleName());
		dialog.setOnItemClickListener(new VideosChoiceDialog.OnItemClickListener() {
			@Override
			public void onItemClick(int position) {
                if (which == 0){
					mTimelineItemSelection = position;
                    tryToEnterPlayback();

				} else if (which == 1){
					tryToStop();
					enterFileBrowser(position);
				}

				Dbug.e(tag, "CMD_ENTER_BROWSING_MODE sent!!! position="+position);
			}
		});
	}

	private void savePlayState(){
		mApplication.setFrontLastState(mAVPlayer.isFrontStreamPlaying());
		mApplication.setRearLastState(mAVPlayer.isRearStreamPlaying());
	}

    private String matchVideoClarity(int position){
        String definition;
        switch (position){
            default:
            case 0:
                definition = ARGS_RT_STREAM_OPEN_FLUENCY;
                break;
            case 1:
                definition = ARGS_RT_STREAM_OPEN_SD;
                break;
            case 2:
                definition = ARGS_RT_STREAM_OPEN_HD;
                break;
        }
        return definition;
    }

    private void tryToEnterPlayback(){
        //tryToStop();//device closes RTS itself.

        isTimelineRequest = true;
        /**Request device for enter browsing mode*/
        mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_ENTER_BROWSING_MODE, ARGS_ENTER_BROWSING_MODE);
    }
}