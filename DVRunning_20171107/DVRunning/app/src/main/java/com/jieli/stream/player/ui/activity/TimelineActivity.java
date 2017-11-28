package com.jieli.stream.player.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jieli.lib.stream.beans.StateInfo;
import com.jieli.lib.stream.beans.VideoInfo;
import com.jieli.lib.stream.interfaces.OnBufferingListener;
import com.jieli.lib.stream.interfaces.OnConnectionListener;
import com.jieli.lib.stream.interfaces.OnDownloadListener;
import com.jieli.lib.stream.interfaces.OnPlayStateListener;
import com.jieli.lib.stream.interfaces.OnPlaybackListener;
import com.jieli.lib.stream.interfaces.OnRecordListener;
import com.jieli.lib.stream.tools.AVIStreamer;
import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.tools.ParseHelper;
import com.jieli.lib.stream.tools.StreamPlayer;
import com.jieli.lib.stream.tools.VideoManager;
import com.jieli.lib.stream.util.ICommon;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseActivity;
import com.jieli.stream.player.ui.dialog.DownloadDialog;
import com.jieli.stream.player.ui.dialog.NotifyDialog;
import com.jieli.stream.player.ui.lib.TLView;
import com.jieli.stream.player.ui.lib.VideoView;
import com.jieli.stream.player.util.AppUtil;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.ScanFilesHelper;
import com.jieli.stream.player.util.StorageUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TimelineActivity extends BaseActivity implements OnPlayStateListener, OnConnectionListener, OnBufferingListener, OnDownloadListener{
    private final String tag = getClass().getSimpleName();
    private TLView mScalePanel;
    private VideoView mVideoView;
    private VideoManager mVideoManager;
    private StreamPlayer mStreamPlayer;
    private ImageButton mPlayPauseBtn;
    private ImageButton mInterceptionBtn;
    private ImageButton mDownloadBtn;
    private boolean isStopped = true;
    private CommandHub mCommandHub;
    private boolean isSeek = false;
    private volatile boolean isRequestThumbnail = false;
    private volatile boolean isRequestDownload = false;
    private volatile boolean isWait4Download = false;
    private boolean isPlaybackToFast = false;
    private volatile boolean isBuffering = false;
    private static boolean isConnected = false;
    private final SimpleDateFormat yyyy_MMddHHmmss = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private NotifyDialog mLoadingDialog, mNetErrorDialog;
    private AVIStreamer mAVIStreamer;
    private ProgressBar mProgressBar;
    private PowerManager.WakeLock mWakeLock;
    private TextView mFastForward, mNormalSpeed;
    private boolean isReadyOk = false;
    private DownloadDialog mDownloadDialog;
    private static final long MIN_STORAGE_SPACE = 10 * 1024 * 1024;//10Mb
    private boolean isWait4PlaybackCommand = false;
    private final String[] mFastForwardLevel = {"F", "2X", "4X", "8X", "16X", "32X", "64X"};
    private int level = 0;
    private View mTouchView;
    private VideoInfo mVideoInfo;
    private boolean isRearViewBrowsing = false;
    private ScanFilesHelper scanFilesHelper;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()){
                case ACTION_RESPONDING_VIDEO_DESC_REQUEST:
                    mScalePanel.SetCalStuff(ParseHelper.getInstance().getSortedVideos());
                    mScalePanel.invalidate();
                    if (mLoadingDialog != null && mLoadingDialog.isShowing()) {
                        mLoadingDialog.dismiss();
                    }
                    isReadyOk = true;
                    break;
                case ACTION_GET_VIDEO_INFO_ERROR:
                    if (mLoadingDialog != null && mLoadingDialog.isShowing()) {
                        mLoadingDialog.dismiss();
                    }
                    if (mNetErrorDialog == null) {
                        mNetErrorDialog = new NotifyDialog(R.string.dialog_tip, R.string.net_error, R.string.confirm,
                                new NotifyDialog.OnConfirmClickListener() {
                                    @Override
                                    public void onClick() {
                                        if(mNetErrorDialog != null && mNetErrorDialog.isShowing()){
                                            mNetErrorDialog.dismiss();
                                        }
                                        onBackPressed();
                                    }
                                });
                    }
                    if(!mNetErrorDialog.isShowing()){
                        mNetErrorDialog.show(getFragmentManager(), "mNetErrorDialog");
                    }
                    break;
                case ACTION_DEVICE_CONNECTION_SUCCESS:
                    onBackPressed();
                    break;
                case ACTION_SPECIAL_DATA:
                    final StateInfo stateInfo = (StateInfo) intent.getSerializableExtra(KEY_SPECIAL_STATE);
                    String cmdResult = stateInfo.getCmdNumber();
                    switch (TextUtils.isEmpty(cmdResult) ? ARGS_NULL : cmdResult) {
                        case CMD_VIDEO_START_PLAYBACK:
	                    case CMD_REAR_VIDEO_PLAYBACK_START:
                            isWait4Download = false;
                            if (stateInfo.getParam().length >=2) {
                                if (TextUtils.isEmpty(stateInfo.getParam()[1])) {
                                    return;
                                }
                                int rate = Integer.parseInt(stateInfo.getParam()[1]);
                                rate = rate > 0 ? rate : 50000;///if rate is zero
                                int mFrameRate = 1000000/rate;

                                int width = Integer.parseInt(stateInfo.getParam()[2]);
                                int height = Integer.parseInt(stateInfo.getParam()[3]);
                                Dbug.i(tag, "param2=" + rate + ", mFrameRate=" + mFrameRate + ", width=" + width + ", height=" + height);
                                if (ARGS_VIDEO_START_DOWNLOAD.equals(stateInfo.getParam()[4])) {
                                    initAVIStreamer(mFrameRate, width, height);
                                    startRecording();
                                }
                            } else {
                                if (stateInfo.getParam().length == 1){
                                    Dbug.i(tag, "param1=" + stateInfo.getParam()[0]);
                                }
                                Dbug.e(tag, "stateInfo.getParam().length=" + stateInfo.getParam().length);
                            }

                            break;
                    }
                    break;
            }
        }
    };

    @Subscribe
    public void onEventMainThread(final Calendar calendar) {
        Dbug.i(tag, "onEventMainThread :" + yyyy_MMddHHmmss.format(calendar.getTimeInMillis())
                + ", getTimeInMillis()=" + calendar.getTimeInMillis() + ", isRequestThumbnail="+isRequestThumbnail);
        VideoInfo videoInfo = mVideoManager.getSelectedPosition(calendar.getTimeInMillis());
        if (videoInfo != null && mStreamPlayer != null) {
	        int port;
	        switch (mStreamPlayer.getPlaybackMode()){
		        case PLAYBACK_MODE_FRONT:
			        port = AP_THUMBNAIL_PORT;
			        break;
		        case PLAYBACK_MODE_REAR:
			        port = AP_REAR_PLAYBACK_THUMBNAIL_PORT;
			        break;
		        case PLAYBACK_MODE_NOT_READY:
		        default:
			        Dbug.e(tag, "thumbnail:playback mode not ready...");
			        return;
	        }
            mStreamPlayer.tryToGetVideoThumbnail(mCommandHub.getDeviceIP(), port, videoInfo, 1, 0);
        } else {
            if (mScalePanel.isSelectionMode()) {
                mScalePanel.setThumbnail(null);
            }
            Dbug.w(tag, "The position of time not found video info");
        }
    }

    private final OnPlaybackListener onFrameReceivedListener = new OnPlaybackListener() {
        @Override
        public void onThumbnail(byte[] thumbnail, long millisecond, boolean isFinished) {
            //Dbug.e(tag, "onThumbnail: isFinished=" + isFinished + ", millisecond=" + millisecond
            //        +", format millisecond=" + yyyy_MMddHHmmss.format(millisecond) + ", thumbnail=" + thumbnail);
            if (isFinished) {
                isRequestThumbnail = false;
                return;
            }

            /**若当前位置没有视频将不显示缩略图*/
            if (mScalePanel.isSelectionMode()) {
                mScalePanel.setThumbnail(thumbnail);
            } else {
                if (mVideoInfo != null) {
                    mVideoView.drawThumbnail(thumbnail);
                } else {
                    Dbug.w(tag, "Current thumbnail data is null");
                }
            }
        }

        @Override
        public void onAudio(final byte[] audio, boolean isPlayMode) {
//            Dbug.w(tag, "onAudioReceived audioFrame=" + audioFrame.length);
            if (isPlayMode) {
                mVideoView.writeAudioData(audio);
            } else {
                /**保存到手机本地*/
                mAVIStreamer.addData(AVIStreamer.TYPE_AVI_AUDIO, audio, audio.length);
            }
        }

        @Override
        public void onVideo(final byte[] video, boolean isPlayMode) {
//            Dbug.e(tag, "onVideoReceived=" + videoFrame.length);
            if (isPlayMode) {
                mVideoView.drawBitmap(video, true);
            }else {
                /**保存到手机本地*/
                mAVIStreamer.addData(AVIStreamer.TYPE_AVI_VIDEO, video, video.length);
            }
        }

        @Override
        public void onPlayFile(final String fileName){
            /**当前播放的文件名*/
            /*if (!TextUtils.isEmpty(fileName)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showToast(fileName);
                    }
                });
            }*/
        }
    };

    /**
     * 获取电源锁，保持该服务在屏幕熄灭时仍然获取CPU时，保持运行
     */
    private void acquireWakeLock() {
        if (null == mWakeLock) {
            PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
            mWakeLock.setReferenceCounted(false);
            if (null != mWakeLock) {
                mWakeLock.acquire();
            }
        }
    }

    /**
     * 释放设备电源锁
     */
    private void releaseWakeLock() {
        if (null != mWakeLock && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        mWakeLock = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timeline);
	    mCommandHub = CommandHub.getInstance();
        scanFilesHelper = new ScanFilesHelper(getApplicationContext());
	    int choice = getIntent().getIntExtra("item_choice", 0);
	    Dbug.e(tag, "create : choice="+choice);
        isRearViewBrowsing = choice == 1;
	    if (isRearViewBrowsing)
		    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_REAR_ALL_VIDEOS_INFO);
	    else
		    mCommandHub.requestStatus(CTP_ID_DEFAULT, CMD_ALL_VIDEO_DESC_NAME);
        mTouchView = findViewById(R.id.touch_view);
        mPlayPauseBtn = (ImageButton) findViewById(R.id.play_pause);
        mPlayPauseBtn.setOnClickListener(onClickListener);
        mInterceptionBtn = (ImageButton) findViewById(R.id.interception);
        mInterceptionBtn.setOnClickListener(onClickListener);
        mDownloadBtn = (ImageButton) findViewById(R.id.download);
        mDownloadBtn.setOnClickListener(onClickListener);
        mScalePanel = (TLView) findViewById(R.id.scalePanel);
        mVideoView = (VideoView) findViewById(R.id.mjpeg_view);
        mFastForward = (TextView) findViewById(R.id.fast_forward);
        mNormalSpeed = (TextView) findViewById(R.id.ff_revert);
        if (mFastForwardLevel.length > 0) {
            mFastForward.setText(mFastForwardLevel[0]);
        }
        String str = "1X";
        mNormalSpeed.setText(str);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mLoadingDialog = new NotifyDialog(true, R.string.refresh_listview_header_hint_loading);
        mLoadingDialog.show(getFragmentManager(), "mNotifyDialog");

        IntentFilter intentFilter = new IntentFilter(ACTION_DEVICE_CONNECTION_SUCCESS);
        intentFilter.addAction(ACTION_RESPONDING_VIDEO_DESC_REQUEST);
        intentFilter.addAction(ACTION_GET_VIDEO_INFO_ERROR);
        intentFilter.addAction(ACTION_SPECIAL_DATA);
        getApplicationContext().registerReceiver(mReceiver, intentFilter);

        mScalePanel.setOnValueChangeListener(onValueChangeListener);

        mVideoManager = VideoManager.getInstance();

        EventBus.getDefault().register(this);

        acquireWakeLock();

        mFastForward.setOnClickListener(onClickListener);
        mNormalSpeed.setOnClickListener(onClickListener);
        if (mAVIStreamer == null) {
            mAVIStreamer = new AVIStreamer();
            mAVIStreamer.setOnRecordListener(new OnRecordListener() {
                @Override
                public void onStart(String filePath) {
                    Dbug.w(tag, "onStart filePath:" + filePath);
                }

                @Override
                public void onCompletion(String filePath, boolean isSuccess, boolean isContinue) {
                    Dbug.e(tag, "onCompletion filePath:" + filePath);
                    if (!isSuccess) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showToast(R.string.recording_failed);
                            }
                        });
                        if(!TextUtils.isEmpty(filePath)){
                            File errorFile = new File(filePath);
                            if(errorFile.exists() && errorFile.isFile()){
                                if(errorFile.delete()){
                                    Dbug.w(tag, "playback mode record failed!");
                                }
                            }
                        }
                    }else{
                        if (scanFilesHelper != null && !TextUtils.isEmpty(filePath)) {
                            scanFilesHelper.scanFiles(filePath);
                        }
                    }
                }

                @Override
                public void onError(int state, String messages) {
                    if (state == AVIStreamer.RecordError.ERROR_OUT_OF_STORAGE_SPACE) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showToastLong(R.string.phone_space_inefficient);
                                stopRecording();
                            }
                        });
                    }
                }
            });
        }

        mTouchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideOrShowUI();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Dbug.i(tag, "onStart=");
        if (mStreamPlayer == null) {
            mStreamPlayer = new StreamPlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Dbug.i(tag, "onResume=");
        if (mStreamPlayer != null && !isConnected) {
	        int port;
	        switch (mStreamPlayer.getPlaybackMode()){
		        case PLAYBACK_MODE_FRONT:
			        port = AP_VIDEO_PORT;
			        break;
		        case PLAYBACK_MODE_REAR:
			        port = AP_REAR_PLAYBACK_RTS_PORT;
			        break;
		        case PLAYBACK_MODE_NOT_READY:
		        default:
			        Dbug.e(tag, "playback mode not ready...");
			        return;
	        }
            if (mStreamPlayer.connect(-1, mCommandHub.getDeviceIP(), port)) {
                Dbug.i(tag, "Connected success=");
            } else {
                Dbug.e(tag, "Connected failure=");
            }
            mStreamPlayer.setOnPlaybackListener(onFrameReceivedListener);
            mStreamPlayer.setOnPlayStateListener(this);
            mStreamPlayer.setOnConnectionListener(this);
            mStreamPlayer.setOnBufferListener(this);
            mStreamPlayer.setOnDownloadListener(this);
        } else {
            Dbug.e(tag, "onResume: mStreamPlayer is null");
        }
    }

    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!isConnected) {
                Dbug.w(tag, "OnClickListener: it's not connection=");
                return;
            }

            if (isBuffering) {
                showToast(R.string.buffering);
                return;
            }

            if (mInterceptionBtn == v) {
                mHandler.removeMessages(MSG_PLAYBACK_MODE_CHANGED);
                mHandler.sendEmptyMessageDelayed(MSG_PLAYBACK_MODE_CHANGED, 300);
                return;
            }

            /**Download(recording) button*/
            if (mDownloadBtn == v) {
                if (mAVIStreamer != null) {
                    if (mStreamPlayer != null && (mStreamPlayer.isPaused() || mStreamPlayer.isPlaying())) {
                        isRequestDownload = true;
                        Dbug.i(tag, "Stop playing to record: disconnect=");
                        mStreamPlayer.disconnect();
                    } else {
                        if (mAVIStreamer.isRecording()) {
                            stopRecording();
                        } else {
                            if (!isWait4Download) {
                                isWait4Download = true;
                                tryToRecord();
                            }
                        }
                    }
                }
                return;
            }

            if (v == mNormalSpeed) {
                mHandler.removeMessages(MSG_PLAYBACK_SPEED_NORMAL);
                mHandler.sendEmptyMessageDelayed(MSG_PLAYBACK_SPEED_NORMAL, 300);
                return;
            }

            if (mVideoManager.getSelectedPosition(mScalePanel.getCurrentMiddleTime()) == null) {
                Dbug.e(tag, "Current position of video is null 000000");
                showToast(R.string.no_video_that_postion);
                return;
            }

            /**Fast playback level button*/
            if (v == mFastForward) {
                mHandler.removeMessages(MSG_UPDATE_PLAYBACK_SPEED_LEVEL);
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_PLAYBACK_SPEED_LEVEL, 400);
                //mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_SPEED_LEVEL);
            }

            Dbug.i(tag, "----onclick level=" + level+ ", isStopped=" + isStopped);
            if (v == mPlayPauseBtn) {
                if (isStopped) {
                    if (mStreamPlayer == null) {
                        Dbug.e(tag, "On click: StreamPlayer is null");
                        return;
                    }
                    /**原速播放时，打开缓冲*/
                    if (level == 0) {
                        mStreamPlayer.openBuffer(true);
                    } else {
                        mStreamPlayer.openBuffer(false);
                    }
                    if (!isWait4PlaybackCommand) {
                        isWait4PlaybackCommand = true;
                        tryToStartPlayback();
                    } else {
                        Dbug.w(tag, "isWait4PlaybackCommand=" + isWait4PlaybackCommand);
                    }
                } else {
                    if (mStreamPlayer != null) {
                        if (mStreamPlayer.isPlaying()) {
                            mStreamPlayer.pause();
                            onStateChanged(StreamPlayer.PlayState.PAUSE);
                        } else if (mStreamPlayer.isPaused()){
                            mStreamPlayer.play();
                            onStateChanged(StreamPlayer.PlayState.PLAY);
                        } else {
                            onStateChanged(StreamPlayer.PlayState.STOP);
                            Dbug.e(tag, "mPlayPauseBtn click: not paused & play isWait4PlaybackCommand" + isWait4PlaybackCommand);
                        }
                    }
                }
            }
        }
    };

    @Override
    public void onStateChanged(int state) {
        isStopped = false;
        switch (state) {
            case StreamPlayer.PlayState.PAUSE:
                Dbug.d(tag, "onStateChanged state=PAUSE");
                //isPlaying = false;
                mPlayPauseBtn.setImageResource(R.mipmap.ic_play);
                mHandler.removeCallbacks(secondRunnable);

                /**Stop it when setting pause*/
                stopRecording();
                break;
            case StreamPlayer.PlayState.START:
                isWait4PlaybackCommand = false;
                Dbug.d(tag, "onStateChanged state=START");
                if (isSeek) {
                    isSeek = false;
                }
                mPlayPauseBtn.setImageResource(R.mipmap.ic_pause);
                mHandler.post(secondRunnable);
                mScalePanel.setMovingLock(false);
                break;
            case StreamPlayer.PlayState.PLAY:
                Dbug.d(tag, "onStateChanged state=PLAY");
                Dbug.e(tag, "onStateChanged: isSeek=" + isSeek + ", isPlaybackToFast=" + isPlaybackToFast+ ", isWait4PlaybackCommand=" + isWait4PlaybackCommand);
                mHandler.post(secondRunnable);
                //isPlaying = true;
                mPlayPauseBtn.setImageResource(R.mipmap.ic_pause);
                break;
            case StreamPlayer.PlayState.STOP:
                Dbug.d(tag, "onStateChanged state=STOP");
                mHandler.removeCallbacks(secondRunnable);
                mPlayPauseBtn.setImageResource(R.mipmap.ic_play);
                //isPlaying = false;
                isStopped = true;
                Dbug.e(tag, "onStateChanged: isSeek=" + isSeek + ", isPlaybackToFast=" + isPlaybackToFast+ ", isWait4PlaybackCommand=" + isWait4PlaybackCommand);
                if (isSeek || isPlaybackToFast) {
                    if (!isWait4PlaybackCommand) {
                        isWait4PlaybackCommand = true;
                        tryToStartPlayback();
                    } else {
                        Dbug.w(tag, "onStateChanged: isWait4PlaybackCommand=" + isWait4PlaybackCommand);
                    }
                }
                isPlaybackToFast = false;
                break;
            case StreamPlayer.PlayState.COMPLETION:
                Dbug.d(tag, "onStateChanged state=COMPLETION");
                mHandler.removeCallbacks(secondRunnable);
                if (mStreamPlayer != null) {
                    long lTime = mStreamPlayer.getCurrentPosition();
                    mScalePanel.setTimeOffset(lTime);
                    Dbug.e(tag, "lTime =" + yyyy_MMddHHmmss.format(lTime));
                }

                //isPlaying = false;
                isStopped = true;

                if (mStreamPlayer != null) {
                    Dbug.i(tag, "COMPLETION : disconnect ");
                    mStreamPlayer.disconnect();
                }
                mPlayPauseBtn.setImageResource(R.mipmap.ic_play);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mProgressBar.isShown()) {
                            mProgressBar.setVisibility(View.INVISIBLE);
                        }
                        mScalePanel.setMovingLock(false);
                    }
                });
                /**Stop it when playing end */
                stopRecording();
                break;
        }
    }

    private final TLView.OnValueChangeListener onValueChangeListener = new TLView.OnValueChangeListener() {
        @Override
        public void onValueChangeEnd(final Calendar calendar) {
            if (mStreamPlayer == null) {
                Dbug.e(tag, "onValueChangeEnd: StreamPlayer is null");
                return;
            }
            Dbug.w(tag, "onValueChangeEnd: isPaused()=" + mStreamPlayer.isPaused() + ", isSeek=" + isSeek + ", isPlaying()=" + mStreamPlayer.isPlaying());

            if (!isConnected) {
                Dbug.e(tag, "onValueChangeEnd: disconnected!!");
                return;
            }
            mVideoInfo = mVideoManager.getSelectedPosition(calendar.getTimeInMillis());
            if (mStreamPlayer.isPlaying()) {
                if (mVideoInfo != null) {
                    Dbug.i(tag, "onValueChangeEnd: Seeking now, send CMD_VIDEO_STOP isSeek=" + isSeek);
                    if (!isSeek) {
                        mHandler.removeCallbacks(secondRunnable);
                        mScalePanel.setMovingLock(true);
                        isSeek = true;
                        /**播放时拖动，先停止(断开)*/
                        Dbug.i(tag, "onValueChangeEnd:***********isSeek disconnect 111=");
                        mStreamPlayer.disconnect();
                    }
                } else {
                    Dbug.w(tag, "onValueChangeEnd: The position of video was not found.");
                }
            } else {
                if (mVideoInfo != null){
                    if(isReadyOk){
                        if (mStreamPlayer.isPaused()) {
                            Dbug.i(tag, "onValueChangeEnd: isPaused --> disconnect 222=");
                            /**暂停时拖动，先停止(断开)*/
                            mStreamPlayer.disconnect();
                        }
                        if (!isRequestThumbnail) {
                            Dbug.w(tag, "onValueChangeEnd: tryToGetVideoThumbnail 11-:" + yyyy_MMddHHmmss.format(calendar.getTimeInMillis())
                                    + ", isRequestThumbnail=" + isRequestThumbnail);
                            isRequestThumbnail = true;
	                        int port;
	                        switch (mStreamPlayer.getPlaybackMode()){
		                        case PLAYBACK_MODE_FRONT:
			                        port = AP_THUMBNAIL_PORT;
			                        break;
		                        case PLAYBACK_MODE_REAR:
			                        port = AP_REAR_PLAYBACK_THUMBNAIL_PORT;
			                        break;
		                        case PLAYBACK_MODE_NOT_READY:
		                        default:
			                        Dbug.e(tag, "thumbnail:playback mode not ready...");
			                        return;
	                        }
                            mStreamPlayer.tryToGetVideoThumbnail(mCommandHub.getDeviceIP(), port, mVideoInfo, 1, 0);
                        }
                    }
                } else {
                    //Dbug.w(tag, "onValueChangeEnd: The position of thumbnail was not found. isSelectionMode= " + mScalePanel.isSelectionMode());
                    if (mScalePanel.isSelectionMode()) {
                        mScalePanel.setThumbnail(null);
                    } else {
                        mVideoView.clearCanvas();
                    }
                }
            }
        }
    };

    @Override
    public void onPlayFail(String errorMessage) {
    }

    private final int MSG_UPDATE_PLAYBACK_SPEED_LEVEL   = 0x100;
    private final int MSG_PLAYBACK_MODE_CHANGED         = 0x101;
    private final int MSG_PLAYBACK_SPEED_NORMAL         = 0x102;
    private final Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PLAYBACK_SPEED_LEVEL:
                    Dbug.e(tag, "MSG_UPDATE_PLAYBACK_SPEED_LEVEL level=" + level + ", isPlaybackToFast=" + isPlaybackToFast + ", isConnected=" + isConnected);
                    if (mStreamPlayer != null){
                        if ((level == 0) && !isPlaybackToFast && (mStreamPlayer.isPlaying()/* || mStreamPlayer.isPaused()*/)) {
                            Dbug.e(tag, "disconnect=---------PlaybackToFast-----------------------------------");
                            isPlaybackToFast = true;
                            /**播放时切换到快速播放，先停止(断开)，清除缓存*/
                            mStreamPlayer.disconnect();
                        }
                        mStreamPlayer.openBuffer(false);

                        level ++;
                        if (level >= mFastForwardLevel.length) {
                            level = 1;
                        }
                        Dbug.i(tag, "++++++++++level=" + level);
                        mFastForward.setText(mFastForwardLevel[level]);
	                    if (level>= 1 || level <=6){
		                    if (mStreamPlayer.getPlaybackMode() == ICommon.PLAYBACK_MODE_FRONT)
			                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_PLAYBACK_SPEED, level+"");
		                    else if (mStreamPlayer.getPlaybackMode() == ICommon.PLAYBACK_MODE_REAR)
			                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_REAR_VIDEO_PLAYBACK_SPEED, level+"");
	                    }
/*                        switch (level) {
                            case 0:
                                break;
                            case 1:
                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_PLAYBACK_SPEED, ARGS_PLAYBACK_SPEED_X2);
                                break;
                            case 2:
                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_PLAYBACK_SPEED, ARGS_PLAYBACK_SPEED_X4);
                                break;
                            case 3:
                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_PLAYBACK_SPEED, ARGS_PLAYBACK_SPEED_X8);
                                break;
                            case 4:
                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_PLAYBACK_SPEED, ARGS_PLAYBACK_SPEED_X16);
                                break;
                            case 5:
                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_PLAYBACK_SPEED, ARGS_PLAYBACK_SPEED_X32);
                                break;
                            case 6:
                                CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_PLAYBACK_SPEED, ARGS_PLAYBACK_SPEED_X64);
                                break;
                            default:
                                Dbug.i(tag, "Unknown error=" + level);
                                break;
                        }*/
                    }
                    break;
                case MSG_PLAYBACK_MODE_CHANGED:
                    if (mStreamPlayer == null) {
                        Dbug.e(tag, "MSG_PLAYBACK_MODE_CHANGED : mStreamPlayer is null");
                        return false;
                    }
                    Dbug.i(tag, "MSG_PLAYBACK_MODE_CHANGED isPlaying=" + mStreamPlayer.isPlaying()
                            + ", mStreamPlayer.isPaused=" + mStreamPlayer.isPaused() + ", isSelectionMode=" + mScalePanel.isSelectionMode());

                    if (!mScalePanel.isSelectionMode()) {
                        if (!isStopped || mStreamPlayer != null && (mStreamPlayer.isPlaying() || mStreamPlayer.isPaused())){
                            mStreamPlayer.disconnect();
                        }
                    }
                    hideOrShowInterceptionUI();
                    break;
                case MSG_PLAYBACK_SPEED_NORMAL:
                    level = 0;
                    isPlaybackToFast = false;
                    if (mStreamPlayer != null) {
                        mStreamPlayer.openBuffer(true);
                    }
                    mFastForward.setText(mFastForwardLevel[0]);
                    if(isReadyOk){
	                    if (mStreamPlayer.getPlaybackMode() == ICommon.PLAYBACK_MODE_FRONT)
		                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_PLAYBACK_SPEED,  ARGS_PLAYBACK_SPEED_X1);
	                    else if (mStreamPlayer.getPlaybackMode() == ICommon.PLAYBACK_MODE_REAR)
		                    CommandHub.getInstance().sendCommand(CTP_ID_DEFAULT, CMD_REAR_VIDEO_PLAYBACK_SPEED, 0+"");
                    }
                    break;
            }
            return false;
        }
    });

    private void hideOrShowUI() {
        if (mScalePanel.isSelectionMode()) {
            return;
        }
        Dbug.e(tag, "hideOrShowUI=" + mScalePanel.isShown());
        if (mScalePanel.isShown()) {
            mPlayPauseBtn.setVisibility(View.INVISIBLE);
            mFastForward.setVisibility(View.INVISIBLE);
            mNormalSpeed.setVisibility(View.INVISIBLE);
            mInterceptionBtn.setVisibility(View.INVISIBLE);
            mScalePanel.setVisibility(View.INVISIBLE);
        } else {
            mPlayPauseBtn.setVisibility(View.VISIBLE);
            mFastForward.setVisibility(View.VISIBLE);
            mNormalSpeed.setVisibility(View.VISIBLE);
            mInterceptionBtn.setVisibility(View.VISIBLE);
            mScalePanel.requestLayout();
            mScalePanel.setVisibility(View.VISIBLE);
        }
    }

    private void hideOrShowInterceptionUI() {
        if (mScalePanel.isSelectionMode()) {
            mScalePanel.setSelectionMode(false);
            mDownloadBtn.setVisibility(View.INVISIBLE);
            mPlayPauseBtn.setVisibility(View.VISIBLE);
            mFastForward.setVisibility(View.VISIBLE);
            mNormalSpeed.setVisibility(View.VISIBLE);
            mTouchView.setVisibility(View.VISIBLE);
            mInterceptionBtn.setImageResource(R.mipmap.ic_interception);
        } else {
            mDownloadBtn.setVisibility(View.VISIBLE);
            mPlayPauseBtn.setVisibility(View.INVISIBLE);
            mFastForward.setVisibility(View.INVISIBLE);
            mNormalSpeed.setVisibility(View.INVISIBLE);
            mTouchView.setVisibility(View.GONE);
            mInterceptionBtn.setImageResource(R.mipmap.ic_return);
            mScalePanel.setSelectionMode(true);
        }
    }

    private final Runnable secondRunnable = new Runnable() {
        @Override
        public void run() {
            mHandler.postDelayed(secondRunnable, 1000);
            if (mStreamPlayer != null) {
                long lTime = mStreamPlayer.getCurrentPosition();
                mScalePanel.setTimeOffset(lTime);
                //Dbug.e(tag, "lTime =" + yyyy_MMddHHmmss.format(lTime));
            }
        }
    };

    @Override
    protected void onStop() {
        Dbug.i(tag, "onStop=");
        mHandler.removeCallbacks(secondRunnable);

        /**Stop it if recording */
        stopRecording();

        //mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_VIDEO_STOP, ARGS_VIDEO_STOP);

        if (mStreamPlayer != null) {
            mStreamPlayer.setOnConnectionListener(null);
            mStreamPlayer.setOnPlayStateListener(null);
            mStreamPlayer.setOnPlaybackListener(null);
            mStreamPlayer.setOnBufferListener(null);
            mStreamPlayer.setOnDownloadListener(null);
            onStateChanged(StreamPlayer.PlayState.STOP);
            Dbug.i(tag, "onStop :disconnect 333=");
            mStreamPlayer.disconnect();
            mStreamPlayer.release();
            mStreamPlayer = null;
        }
        /***Re-initialize the boolean values*/
        isSeek = false;
        isBuffering = false;
        isConnected = false;
        if (level != 0) {
            /**Reset the playback speed*/
            mHandler.sendEmptyMessage(MSG_PLAYBACK_SPEED_NORMAL);
        }

        super.onStop();
    }

    @Override
    public void onBackPressed() {
        mHandler.removeCallbacksAndMessages(null);

        /**Compatible some phones'UI incorrect when pressing back*/
        mScalePanel.setVisibility(View.INVISIBLE);
        mPlayPauseBtn.setVisibility(View.INVISIBLE);
        mInterceptionBtn.setVisibility(View.INVISIBLE);

        /**Stop it if recording */
        stopRecording();

        if (mAVIStreamer != null) {
            mAVIStreamer.setOnRecordListener(null);
            mAVIStreamer.release();
            mAVIStreamer = null;
        }
        TimelineActivity.this.setResult(ACTIVITY_RESULT_OK);
        TimelineActivity.this.finish();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        releaseWakeLock();
        isReadyOk = false;
        getApplicationContext().unregisterReceiver(mReceiver);
        super.onDestroy();
        if(scanFilesHelper != null){
            scanFilesHelper.release();
            scanFilesHelper = null;
        }
    }

    private void tryToRecord() {
        long start = mScalePanel.getInterceptionStartTime();
        long end = mScalePanel.getInterceptionEndTime();
        Dbug.e(tag, "tryToRecord : start=" + yyyy_MMddHHmmss.format(start) + ", start time="+ start + ", end=" + yyyy_MMddHHmmss.format(end) + ", end time=" + end);

        /**这里测试下载当前选中的文件,也可以下载指定的两个时间点的视频。真正下载动作，需要等 CMD_VIDEO_START_PLAYBACK 回复，再去调用startRecording()*/
        boolean isSuccess = mVideoManager.tryToDownload(start, end);
        if (!isSuccess) {
            showToast(R.string.selected_area_incorrect);
            isWait4Download = false;
        }
    }

    private synchronized void tryToStartPlayback() {
        VideoInfo videoInfo = mVideoManager.getSelectedPosition(mScalePanel.getCurrentMiddleTime());
        if (videoInfo == null) {
            Dbug.w(tag, "The video not exist in selected position.");
            showToast(R.string.no_video_that_postion);
            isSeek = false;
            return;
        }
        Dbug.w(tag, "tryToStartPlayback=" + yyyy_MMddHHmmss.format(videoInfo.getStartTime().getTime()) + ", offset=" + videoInfo.getTimeOffset()
                + ", isConnect=" + isConnected );
        if(isReadyOk){
            if(!TextUtils.isEmpty(videoInfo.getFilePath())){
                int index = ParseHelper.getInstance().getSelectVideoIndexInTxt(videoInfo.getFilePath());
	            if (mStreamPlayer.getPlaybackMode() == ICommon.PLAYBACK_MODE_FRONT) {
		            mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_VIDEO_START_PLAYBACK, videoInfo.getFilePath(), videoInfo.getTimeOffset() + "", ARGS_VIDEO_START_PLAYBACK, index + "");
	            } else if (mStreamPlayer.getPlaybackMode() == ICommon.PLAYBACK_MODE_REAR){
		            mCommandHub.sendCommand(CTP_ID_DEFAULT, CMD_REAR_VIDEO_PLAYBACK_START, videoInfo.getFilePath(), videoInfo.getTimeOffset() + "", ARGS_VIDEO_START_PLAYBACK, index + "");
	            } else {
		            Dbug.e(tag, "playback mode=" + mStreamPlayer.getPlaybackMode());
	            }
            }
        }
    }

    @Override
    public void onMountState(String mountState, String msg) {
        switch (mountState){
            case ARGS_SDCARD_OFFLINE:
                onBackPressed();
                break;
        }
    }

    private void initAVIStreamer(int frameRate, int width, int height) {
        Dbug.i(tag, "initAVIStreamer frameRate=" + frameRate);
        if (TextUtils.isEmpty(mApplication.getDeviceUUID())) {
            Dbug.e(tag, "UUID is null");
            return;
        }
        String recordDir = AppUtil.getAppStoragePath(mApplication, RECORD, isRearViewBrowsing);
        if (recordDir == null)
            throw new NullPointerException("Record dir is null");
        File dir = new File(recordDir);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                throw new IllegalAccessError("Create " + recordDir +" failure.");
            }
        }
        Dbug.i(tag, "directory=" + recordDir);

        mAVIStreamer.configureAudio(8000, 16, 1);
        mAVIStreamer.configureVideo(frameRate, width, height);
        mAVIStreamer.setFilePath(recordDir);
        //mAVIStreamer.setDuration(60);
    }

    private void showProgressDialog() {
        if (mDownloadDialog == null) {
            mDownloadDialog = new DownloadDialog(this);
            mDownloadDialog.setCanceledOnTouchOutside(false);
            mDownloadDialog.setOnCancelClickListener(new DownloadDialog.OnCancelBtnClickListener() {
                @Override
                public void onClick() {
                    if (mDownloadDialog.isShowing()) {
                        mDownloadDialog.dismiss();
                    }
                    stopRecording();
                }
            });
        }
        if (mDownloadDialog.isShowing()) {
            mDownloadDialog.dismiss();
        }
        mDownloadDialog.getNumberProgressBar().setMax(100);
        mDownloadDialog.setDialogTilte(getString(R.string.download_file));
        mDownloadDialog.show();
    }

    private void startRecording() {
        if (mAVIStreamer != null && !mAVIStreamer.isRecording()) {

            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)){

                if (StorageUtil.getSdCardFreeBytes() < MIN_STORAGE_SPACE) {
                    showToastLong(R.string.phone_space_inefficient);
                    return;
                }
                showProgressDialog();

                if (mAVIStreamer != null) {
                    Dbug.e(tag, "startRecording=");
                    mStreamPlayer.openBuffer(false);
                    mAVIStreamer.startRecording();
                }
            } else {
                // No external media
                showToastLong(R.string.not_found_phone_sdcard);
            }
        } else {
            Dbug.i(tag, "Already start recording=");
        }
    }

    private void stopRecording() {
        if (mAVIStreamer != null && mAVIStreamer.isRecording()) {

            if (mDownloadDialog != null && mDownloadDialog.isShowing()) {
                mDownloadDialog.dismiss();
            }

            if (mStreamPlayer != null) {
                Dbug.i(tag, "stopRecording COMPLETION : disconnect ");
                mStreamPlayer.disconnect();
            }

            if (mAVIStreamer != null) {
                mAVIStreamer.stopRecording();
            }
        } else {
            Dbug.i(tag, "Already stop recording=");
        }
    }

    @Override
    public void onBuffering(boolean isBuffering) {
        this.isBuffering = isBuffering;
        Dbug.i(tag, "onBuffering =" + isBuffering);
        if (mVideoView != null) {
            mVideoView.updateAudioTrackPlayState(isBuffering);
        }

        if (isBuffering) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mHandler.removeCallbacks(secondRunnable);
                    mProgressBar.setVisibility(View.VISIBLE);
                    mScalePanel.setMovingLock(true);
                }
            });

        } else {
            if (mProgressBar.isShown()) {
                Dbug.w(tag, "onBuffering over=");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mHandler.post(secondRunnable);
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mScalePanel.setMovingLock(false);
                    }
                });
            }
        }
    }

    @Override
    public void onConnected() {
        isConnected = true;
        Dbug.e(tag, "onConnected: isRequestDownload=" + isRequestDownload + ", level=" + level);
        /*if (isSeek || isPlaybackToFast) {
            isPlaybackToFast = false;
            tryToStartPlayback();
        }*/

        if (isRequestDownload) {
            isRequestDownload = false;
            tryToRecord();
        }

/*        if (isInterception) {
            isInterception = false;
            hideOrShowInterceptionUI();
        }*/

        if (mProgressBar.isShown()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mScalePanel.setMovingLock(false);
                }
            });
        }
    }

    @Override
    public void onDisconnected() {
        //Dbug.e(tag, "onDisconnected= ");
        isConnected = false;

        if (mStreamPlayer != null) {
            Dbug.i(tag, "-------------reconnect-----------------------");
	        int port;
	        switch (mStreamPlayer.getPlaybackMode()){
		        case PLAYBACK_MODE_FRONT:
			        port = AP_VIDEO_PORT;
			        break;
		        case PLAYBACK_MODE_REAR:
			        port = AP_REAR_PLAYBACK_RTS_PORT;
			        break;
		        case PLAYBACK_MODE_NOT_READY:
		        default:
			        Dbug.e(tag, "Playback mode not ready...");
			        return;
	        }
            if (mStreamPlayer.connect(-1, mCommandHub.getDeviceIP(), port)) {
                Dbug.i(tag, "-------------reconnect successful--------");
            } else {
                Dbug.e(tag, "Connect fail 33");
            }
        }
    }

    @Override
    public void onError(){
        if (isFinishing()) {
            return;
        }
        isConnected = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onStateChanged(StreamPlayer.PlayState.STOP);
                if (mNetErrorDialog == null) {
                    mNetErrorDialog = new NotifyDialog(R.string.dialog_tip, R.string.net_error, R.string.confirm,
                            new NotifyDialog.OnConfirmClickListener() {
                        @Override
                        public void onClick() {
                            if(mNetErrorDialog != null && mNetErrorDialog.isShowing()){
                                mNetErrorDialog.dismiss();
                            }
                            onBackPressed();
                        }
                    });
                }
                if(!mNetErrorDialog.isShowing()){
                    mNetErrorDialog.show(getFragmentManager(), "mNetErrorDialog");
                }
            }
        });
    }

    @Override
    public void onProgress(final float progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mDownloadDialog != null) {
                    final int finalProgress = (int) (progress * 100);
                    //Dbug.i(tag, "progress=" + progress + ", finalProgress=" + finalProgress);
                    mDownloadDialog.getNumberProgressBar().setProgress(finalProgress);
                }
            }
        });
    }

    @Override
    public void onCompletion() {
        Dbug.i(tag, "Download Completion=");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopRecording();
            }
        });
    }
}