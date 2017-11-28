package com.jieli.stream.player.ui.fragment;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bigkoo.alertview.AlertView;
import com.bigkoo.alertview.OnItemClickListener;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseFragment;
import com.jieli.stream.player.data.beans.FileInfo;
import com.jieli.stream.player.tool.FtpHandlerThread;
import com.jieli.stream.player.ui.dialog.DownloadDialog;
import com.jieli.stream.player.ui.dialog.NotifyDialog;
import com.jieli.stream.player.ui.lib.CustomTextView;
import com.jieli.stream.player.ui.lib.RefreshListView;
import com.jieli.stream.player.util.AppUtil;
import com.jieli.stream.player.util.BufChangeHex;
import com.jieli.stream.player.util.DataCleanManager;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IAction;
import com.jieli.stream.player.util.IConstant;
import com.jieli.stream.player.util.TimeFormater;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * class name: DeviceBrowsePhotoFragment
 * function : browse device TF card photo files
 * @author JL
 * create time : 2016-02-23 15:03
 * version : v1.0
 * ///////////////////////////////////////////////////////////////////////////
 *
 */
public class DeviceBrowsePhotoFragment extends BaseFragment implements RefreshListView.IXListViewListener, IConstant{

    private String tag = getClass().getSimpleName();
    private static DeviceBrowsePhotoFragment fragment;
    private TextView showTitle;
    private RefreshListView mRefreshListView;
    private BrowseAdapter mBrowseAdapter;
    private CustomTextView choiceBtn;
    private CustomTextView backBtn;

    private List<FileInfo> allDataList = null;
    private List<FileInfo> fileInfoList = null;
    private List<FileInfo> tempList = null;
    private Map<String,FileInfo> videoInfoMap = null;
    private List<String> selectedList = null;
    private Map<String, Integer> selectPosMap = null;
    private Map<String, String> selectDateMap = null;
    private Map<String, String> durationMap = null;
    private ExecutorService service = null;
    private Future<String> future = null;
    private List<String> lastNameList = null;

    private DownloadDialog downloadDialog = null;
    private NotifyDialog downloadNotifyDialog = null;

    private FtpHandlerThread mWorkHandlerThread;

    private boolean isChoicing = false;
    private boolean isLoading = false;
    private boolean isDeleting = false;
    private boolean isTaskOpen = false;
    private boolean isAllSelect = false;
    private String currentBrowsePath = "";
    private String downloadFilename = "";
    private int failureTimes = 3;
    private int taskNum = 0;
    private int opType = -1;

    private int PAGE_ITEM_NUM = 7;
    private static final int DELETE_STYLE = 0;
    private static final int DOWNLOAD_STYLE = 1;
    private String dDownloadPath, dImageThumbPath, dVideoThumbPath;
    private String mWhichDir;
    private boolean isRearViewBrowsing = false;

    public DeviceBrowsePhotoFragment() {
        // Required empty public constructor
    }

    public static DeviceBrowsePhotoFragment getInstance(String which){
        if(fragment == null){
            fragment = new DeviceBrowsePhotoFragment();
            Bundle args = new Bundle();
            args.putString(ARG_WHICH_DIR, which);
            fragment.setArguments(args);
            return fragment;
        }
        return fragment;
    }

    private Handler mHandler = new Handler();

    @Override
    public void updateListMsg(Object object) {
        super.updateListMsg(object);
        if(getActivity() == null || !isAdded() || getActivity().isFinishing()){
            Dbug.e(tag, "Activity is finishing, so handler can not to do any thing.");
            return;
        }
        Message msg = (Message)object;
        if(msg == null){
            return;
        }
        switch (msg.what) {
            case FtpHandlerThread.MSG_SHOW_MESSAGES:
                String string = (String) msg.obj;
                if (string != null) {
                    if (string.equals(getString(R.string.download_thumb_start))) {
                        sendBroadcastToUI(WAITING_FOR_THUMB, ARGS_SHOW_DIALOG);
                        mHandler.removeCallbacks(cancelDialogRunnable);
                    } else if (string.equals(getString(R.string.download_task_start))) {
                        isLoading = true;
                        if (downloadDialog != null) {
                            downloadDialog.getNumberProgressBar().setProgress(0);
                            downloadDialog.setDialogContent(downloadFilename);
                            String text = (taskNum - selectedList.size()) + getString(R.string.separator) + taskNum;
                            downloadDialog.setDialogTask(text);
                            if(!downloadDialog.isShowing()){
                                downloadDialog.show();
                            }
                        }
                        mHandler.removeCallbacks(cancelDialogRunnable);
                    } else if (string.equals(getString(R.string.download_thumb_success)) || string.equals(getString(R.string.download_thumb_failed))) {
                        if (string.equals(getString(R.string.download_thumb_failed))) {
                            String filename = msg.getData().getString("File_Name", null);
                            if (filename != null) {
                                if(lastNameList != null && lastNameList.size() > 0) {
                                    if (lastNameList.contains(filename)) {
                                        lastNameList.remove(filename);
                                    }
                                }
                            }
                        }
                        if (mBrowseAdapter != null) {
                            mBrowseAdapter.notifyDataSetChanged();
                        }
                        mHandler.removeCallbacks(cancelDialogRunnable);
                        mHandler.postDelayed(cancelDialogRunnable, 1000L);
                    } else if (string.equals(getString(R.string.ftp_client_exception))) {
                        Dbug.d(tag, "FTPClient error====================>>");

                        sendBroadcastToUI(ALL_DIALOG_DISMISS, ARGS_DISMISS_DIALOG);
                        if (downloadDialog != null && downloadDialog.isShowing()) {
                            downloadDialog.dismiss();
                        }
                        isLoading = false;
                        if(isChoicing){
                            backBtn.performClick();
                        }
                        isDeleting = false;

                    }else if(string.equals(getString(R.string.ftp_socket_err))){
                        sendBroadcastToUI(ALL_DIALOG_DISMISS, ARGS_DISMISS_DIALOG);
                        if (downloadDialog != null && downloadDialog.isShowing()) {
                            downloadDialog.dismiss();
                        }
                        isTaskOpen = false;
                        isDeleting = false;
                        isLoading = false;
                        showToastShort(R.string.ftp_socket_err);
                    }else if(string.equals(getString(R.string.read_data))){
                        sendBroadcastToUI(WAITING_FOR_DATA, ARGS_SHOW_DIALOG);
                    } else {
                        showToastShort(string);
                    }
                }
                if (string != null) {
                    if (string.equals(getString(R.string.download_file_success))) {
                        isLoading = false;
                        dealWithTask(DOWNLOAD_STYLE, true);
                    } else if (string.equals(getString(R.string.connect_ftp_failed))) {
                        Dbug.e(tag, getString(R.string.connect_ftp_failed));
                    } else if (string.equals(getString(R.string.download_file_failed)) || string.equals(getString(R.string.download_file_downloaded))) {
                        sendBroadcastToUI(WAITING_FOR_THUMB, ARGS_DISMISS_DIALOG);
                        isLoading = false;
                        dealWithTask(DOWNLOAD_STYLE, true);
                    } else if (string.equals(getString(R.string.delete_file_failed))) {
                        dealWithTask(DELETE_STYLE, true);
                    } else if (string.equals(getString(R.string.download_task_abort))) {
                        isLoading = false;
                        dealWithTask(DOWNLOAD_STYLE, true);
                    } else if (string.equals(getString(R.string.download_file_err))) {
                        if (downloadDialog != null && downloadDialog.isShowing()) {
                            downloadDialog.dismiss();
                        }
                        isLoading = false;
                        dealWithTask(DOWNLOAD_STYLE, true);
                    } else if (string.equals(getString(R.string.phone_space_inefficient))) {
                        sendBroadcastToUI(WAITING_FOR_THUMB, ARGS_DISMISS_DIALOG);
                        isLoading = false ;
                        backBtn.performClick();
                        if (mWorkHandlerThread != null) {
                            mWorkHandlerThread.setIsDestoryThread(true);
                        }
                    }else if(string.equals(getString(R.string.login_info_err))){
                        isLoading = false;
                        sendBroadcastToUI(ALL_DIALOG_DISMISS, ARGS_DISMISS_DIALOG);
                    }
                }
                break;
            case FtpHandlerThread.MSG_UPDATE_UI:
                sendBroadcastToUI(WAITING_FOR_DATA, ARGS_DISMISS_DIALOG);
                Dbug.e(tag, " -FtpHandlerThread.MSG_UPDATE_UI- ");
                allDataList = (List<FileInfo>) msg.obj;
                if (allDataList == null) {
                    Dbug.e(tag, " -onMainAction- allDataList is null! ");
                    onFinish(false);
//                    showToastShort(getString(R.string.ftp_data_null));
                    return;
                }
                if (allDataList.size() > 0) {
                    if (currentBrowsePath != null && !currentBrowsePath.equals(allDataList.get(0).getPath())) {
                        currentBrowsePath = allDataList.get(0).getPath();
                    }
                    Dbug.d(tag, " currentBrowsePath = " + currentBrowsePath);
                } else {
                    if(mBrowseAdapter != null){
                        mBrowseAdapter.clear();
                        mBrowseAdapter.addAll(allDataList);
                        mRefreshListView.setAdapter(mBrowseAdapter);
                        mBrowseAdapter.notifyDataSetChanged();
                    }
                    Dbug.e(tag, " allDataList is null! ");
                    onFinish(false);
//                    showToastShort(getString(R.string.ftp_data_null));
                    return;
                }
                if(fileInfoList != null && fileInfoList.size() > 0){
                    fileInfoList.clear();
                }
                fileInfoList = selectTypeList(allDataList);
                if(null != fileInfoList && fileInfoList.size() == 0){
                    Dbug.e(tag, " fileInfoList is null! ");
                    if(mBrowseAdapter != null){
                        mBrowseAdapter.clear();
                        mBrowseAdapter.addAll(fileInfoList);
                        mRefreshListView.setAdapter(mBrowseAdapter);
                        mBrowseAdapter.notifyDataSetChanged();
                    }
//                    showToastShort(getString(R.string.ftp_data_null));
                    onFinish(true);
                    return;
                }
                if (tempList == null) {
                    tempList = new ArrayList<>();
                }
                if (tempList.size() > 0) {
                    tempList.clear();
                }
                if (fileInfoList.size() >= PAGE_ITEM_NUM) {
                    for (int i = 0; i < PAGE_ITEM_NUM; i++) {
                        tempList.add(fileInfoList.get(i));
                    }
                    onLoad();
                } else {
                    for (int i = 0; i < fileInfoList.size(); i++) {
                        tempList.add(fileInfoList.get(i));
                    }
                    onFinish(false);
                }
                if (mBrowseAdapter == null) {
                    mBrowseAdapter = new BrowseAdapter(getActivity());
                    mBrowseAdapter.addAll(tempList);
                    mRefreshListView.setAdapter(mBrowseAdapter);
//                    mBrowseAdapter.notifyDataSetChanged();
                } else{
                    mBrowseAdapter.clear();
                    mBrowseAdapter.addAll(tempList);
                    mRefreshListView.setAdapter(mBrowseAdapter);
//                    mBrowseAdapter.notifyDataSetChanged();
                }
                break;
            case FtpHandlerThread.MSG_DELETE_SUCCESS:
                int position = msg.arg1;
                String deleteFileName = (String)msg.obj;
                if (deleteFileName != null && !deleteFileName.isEmpty() && (deleteFileName.contains(".jpg")
                        || deleteFileName.contains(".JPG") || deleteFileName.contains(".png")
                        || deleteFileName.contains(".PNG") || deleteFileName.contains(".jpeg")
                        || deleteFileName.contains(".JPEG"))) {
                    String imagePath =  dImageThumbPath;
                    String deletePath = imagePath + File.separator + deleteFileName;
                    File deleteThumb = new File(deletePath);
                    if(deleteThumb.exists() && deleteThumb.isFile()){
                        if(deleteThumb.delete()){
                            Dbug.e(tag, " ftp file delete,so thumb file delete.");
                        }
                    }
                }else if(deleteFileName != null && !deleteFileName.isEmpty() && (deleteFileName.contains(".avi")
                        || deleteFileName.contains(".AVI") || deleteFileName.contains(".mov")
                        ||deleteFileName.contains(".MOV") || deleteFileName.contains(".mp4")
                        || deleteFileName.contains(".MP4"))){
                    String videoPath =  dVideoThumbPath;
                    String deletePath = videoPath + File.separator + deleteFileName;
                    File deleteThumb = new File(deletePath);
                    if(deleteThumb.exists() && deleteThumb.isFile()){
                        if(deleteThumb.delete()){
                            Dbug.e(tag, " ftp file delete,so thumb file delete.");
                        }
                    }
                }
                showToastShort(getString(R.string.delete_file_success));
                if (mBrowseAdapter != null) {
                    if (position < mBrowseAdapter.getCount()) {
                        mBrowseAdapter.remove(mBrowseAdapter.getItem(position));
                    }
                }
                dealWithTask(DELETE_STYLE, true);
                break;
            case FtpHandlerThread.MSG_VIDEO_MESSAGE:
                FileInfo videoMsg = (FileInfo) msg.obj;
                if (videoMsg == null) {
                    return;
                }
                if (!videoMsg.getTitle().isEmpty() || videoMsg.getTotalTime() != 0
                        || videoMsg.getWidth() != 0 || videoMsg.getHeight() != 0) {
                    if (videoInfoMap != null) {
                        if (null == videoInfoMap.get(videoMsg.getTitle())) {
                            videoInfoMap.put(videoMsg.getTitle(), videoMsg);
                        }
                        mApplication.setVideoInfoMap(videoInfoMap);
                    }
                }
                break;
            case FtpHandlerThread.CURRENT_DOWNLOAD_PROGRESS:
                int progress = msg.arg1;
                if (downloadDialog != null && downloadDialog.isShowing()) {
                    downloadDialog.getNumberProgressBar().setProgress(progress);
                }
                break;
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(IAction.ACTION_BROWSE_MODE_OPERATION)){
                int operationType = intent.getIntExtra(IConstant.BROWSE_FILE_OPERATION_STYLE, -1);
                int fragmentType = intent.getIntExtra(IConstant.BROWSE_FRAGMENT_TYPE, -1);
                if(getActivity() == null || fragmentType != 0 || !isAdded() || isHidden()
                        || !(mApplication.getCurrentFragment() instanceof DeviceBrowsePhotoFragment)){
                    Dbug.e(tag, "fragment is finishing.");
                    return;
                }
                switch (operationType){
                    case IConstant.SELECT_BROWSE_FILE:
                        if(choiceBtn.getText().toString().equals(getString(R.string.operation_choice))) {
                            if (!isChoicing) {
                                if(null != fileInfoList && fileInfoList.size() == 0){
                                    showToastShort(R.string.ftp_data_null);
                                    return;
                                }
                                isChoicing = true;
                                isAllSelect = false;
                                choiceBtn.setText(getString(R.string.all_select));
                                backBtn.setText(getString(R.string.cancel));
                                if(selectedList != null){
                                    selectedList.clear();
                                }
                                if(selectPosMap != null){
                                    selectPosMap.clear();
                                }
                                if(selectDateMap != null){
                                    selectDateMap.clear();
                                }
                                String showTxt = getString(R.string.selected) + selectedList.size() +
                                        getString(R.string.separator) + fileInfoList.size() + getString(R.string.files);
                                showTitle.setVisibility(View.VISIBLE);
                                showTitle.setText(showTxt);
                                if (mBrowseAdapter != null) {
                                    mBrowseAdapter.notifyDataSetChanged();
                                }
                            }
                        }else if(choiceBtn.getText().toString().equals(getString(R.string.all_select))){
                            isChoicing = true;
                            if(isAllSelect){
                                isAllSelect = false;
                                if(selectedList.size() > 0){
                                    selectedList.clear();
                                }
                                if(selectPosMap.size() > 0){
                                    selectPosMap.clear();
                                }
                                if(selectDateMap.size() > 0){
                                    selectDateMap.clear();
                                }
                                String valueStr = getString(R.string.selected) + selectedList.size() +
                                        getString(R.string.separator) + fileInfoList.size() + getString(R.string.files);
                                showTitle.setVisibility(View.VISIBLE);
                                showTitle.setText(valueStr);
                            }else{
                                isAllSelect = true;
                                if(selectedList.size() > 0){
                                    selectedList.clear();
                                }
                                if(selectPosMap.size() > 0){
                                    selectPosMap.clear();
                                }
                                if(selectDateMap.size() > 0){
                                    selectDateMap.clear();
                                }
                                for (int i = 0; i < fileInfoList.size(); i++){
                                    FileInfo info = fileInfoList.get(i);
                                    selectedList.add(info.getTitle());
                                    selectPosMap.put(info.getTitle(), i);
                                    selectDateMap.put(info.getTitle(), info.getDateMes());
                                }
                                String str = getString(R.string.selected) + selectedList.size() +
                                        getString(R.string.separator) + fileInfoList.size() + getString(R.string.files);
                                showTitle.setVisibility(View.VISIBLE);
                                showTitle.setText(str);
                            }
                            if (mBrowseAdapter != null) {
                                mBrowseAdapter.notifyDataSetChanged();
                            }
                        }
                        break;
                    case IConstant.BACK_BROWSE_MODE:
                        if(isChoicing){
                            isChoicing = false;
                            isAllSelect = false;
                            isTaskOpen = false;
                            choiceBtn.setText(getString(R.string.operation_choice));
                            backBtn.setText(getString(R.string.return_back));
                            showTitle.setVisibility(View.GONE);
                            if(selectedList != null){
                                selectedList.clear();
                            }
                            if(selectPosMap != null){
                                selectPosMap.clear();
                            }
                            if(selectDateMap != null){
                                selectDateMap.clear();
                            }
                            if(opType == DELETE_STYLE){
                                getActivity().sendBroadcast(new Intent(IAction.ACTION_UPDATE_LIST));
                            }else{
                                if(mBrowseAdapter != null){
                                    mBrowseAdapter.notifyDataSetChanged();
                                }
                            }
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    sendBroadcastToUI(WAITING_FOR_DELETE, ARGS_DISMISS_DIALOG);
                                    isDeleting = false;
                                    isLoading = false;
                                    if(downloadDialog != null && downloadDialog.isShowing()){
                                        downloadDialog.dismiss();
                                    }
                                }
                            },1000L);
                        }else{
                            getActivity().setResult(BROWSE_ACTIVITY_RESULT_OK);
                            getActivity().finish();
                            Dbug.e(tag, " ---BACK_BROWSE_MODE--- , finish BrowseFileActivity!");
                        }
                        break;
                    case IConstant.DELETE_BROWSE_FILE:
                        if(isLoading || isDeleting){
                            return;
                        }
                        if(isChoicing){
                            AlertView deleteAlertDialog = new AlertView(null, null, getString(R.string.operation_cancel),
                                    new String[]{getString(R.string.delete_file)}, null, getActivity(), AlertView.Style.ActionSheet, deleteOnItemClick);
                            deleteAlertDialog.show();
                        }else{
                            if(null != fileInfoList){
                                if(fileInfoList.size() == 0){
                                    showToastShort(R.string.ftp_data_null);
                                }else{
                                    showToastShort(getString(R.string.select_err));
                                }
                            }
                        }
                        break;
                    case IConstant.DOWNLOAD_BROWSE_FILE:
                        if(isLoading || isDeleting){
                            return;
                        }
                        if(isChoicing){
                            AlertView downloadAlertDialog = new AlertView(null, null, getString(R.string.operation_cancel),
                                    new String[]{getString(R.string.download_file)}, null, getActivity(), AlertView.Style.ActionSheet, downloadOnItemClick);
                            downloadAlertDialog.show();
                        } else{
                            if(null != fileInfoList){
                                if(fileInfoList.size() == 0){
                                    showToastShort(R.string.ftp_data_null);
                                }else{
                                    showToastShort(getString(R.string.select_err));
                                }
                            }
                        }
                        break;
                }
            }else if(action.equals(IAction.ACTION_CHANGE_FRAGMENT)){
                if(getActivity() == null || !isAdded()
                        || !(mApplication.getCurrentFragment() instanceof DeviceBrowsePhotoFragment)){
                    sendBroadcastToUI(ALL_DIALOG_DISMISS, ARGS_DISMISS_DIALOG);
                    if (downloadDialog != null && downloadDialog.isShowing()) {
                        downloadDialog.dismiss();
                    }
                    isLoading = false;
                    isDeleting = false;
                    return;
                }
                isChoicing = false;
                isAllSelect = false;
                isTaskOpen = false;
                isDeleting = false;
                isLoading = false;
                choiceBtn.setText(getString(R.string.operation_choice));
                backBtn.setText(getString(R.string.return_back));
                showTitle.setVisibility(View.GONE);
                if(selectedList != null){
                    selectedList.clear();
                }
                if(selectPosMap != null){
                    selectPosMap.clear();
                }
                if(selectDateMap != null){
                    selectDateMap.clear();
                }
                if(mBrowseAdapter != null){
                    mBrowseAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getActivity() == null){
            return;
        }
        IntentFilter intentFilter = new IntentFilter(IAction.ACTION_BROWSE_MODE_OPERATION);
        intentFilter.addAction(IAction.ACTION_CHANGE_FRAGMENT);
        getActivity().getApplicationContext().registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_device_browse_photo, container, false);
        mRefreshListView = (RefreshListView) view.findViewById(R.id.device_photo_brows_list_view);
        showTitle = (TextView) view.findViewById(R.id.device_photo_select_text);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getActivity() == null){
            return;
        }
        mWhichDir = getActivity().getIntent().getStringExtra(ARG_WHICH_DIR);//rear_view or front_view
        isRearViewBrowsing = VIEW_REAR.equals(mWhichDir);
        if(service == null){
            service = Executors.newSingleThreadExecutor();
        }
        if(TextUtils.isEmpty(dDownloadPath)){
            dDownloadPath = AppUtil.getAppStoragePath(mApplication, DOWNLOAD, isRearViewBrowsing);
        }
        if(TextUtils.isEmpty(dImageThumbPath)){
            dImageThumbPath = AppUtil.getAppStoragePath(mApplication, IMAGE, isRearViewBrowsing);
        }
        if(TextUtils.isEmpty(dVideoThumbPath)){
            dVideoThumbPath = AppUtil.getAppStoragePath(mApplication, VIDEO, isRearViewBrowsing);
        }
        choiceBtn = (CustomTextView) getActivity().findViewById(R.id.selection);
        backBtn = (CustomTextView) getActivity().findViewById(R.id.back);

        mRefreshListView.setOnItemClickListener(mListViewOnItemClick);
        mRefreshListView.setPullLoadEnable(true);
        mRefreshListView.setPullRefreshEnable(false);
        mRefreshListView.setXListViewListener(this);

        initDialog();
    }

    @Override
    public void onPause() {
        Dbug.e(tag, "== onPause == ");
        super.onPause();
        if(mApplication != null && videoInfoMap.size() > 0){
            mApplication.setVideoInfoMap(videoInfoMap);
        }
        if (mWorkHandlerThread != null) {
            mWorkHandlerThread.setIsDestoryThread(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Dbug.e(tag, "== onResume == ");
        initListAndMap();
        if(mApplication.getIsBrowsing()){
            mApplication.setIsBrowsing(false);
        }

        if(mWorkHandlerThread == null && mApplication.getWorkHandlerThread() != null){
            mWorkHandlerThread = mApplication.getWorkHandlerThread();
        }
        if(mWorkHandlerThread != null){
            if(mWorkHandlerThread.getIsDestoryThread()){
                if(lastNameList !=  null && lastNameList.size() > 0){
                    lastNameList.clear();
                }
            }
            mWorkHandlerThread.setIsDestoryThread(false);
        }

        if(videoInfoMap != null){
            if(null != mApplication.getVideoInfoMap()){
                if(!videoInfoMap.equals(mApplication.getVideoInfoMap())){
                    videoInfoMap = mApplication.getVideoInfoMap();
                }
            }
        }
    }

    private List<FileInfo> selectTypeList(List<FileInfo> drsList){
        List<FileInfo> resultList = new ArrayList<>();
        if(null == drsList || drsList.size() == 0){
            return resultList;
        }
        for (int i = 0; i < drsList.size(); i++){
            FileInfo info = drsList.get(i);
            String filename = info.getTitle();
            if(!TextUtils.isEmpty(filename) && (filename.endsWith(".png")
                    || filename.endsWith(".PNG") || filename.endsWith(".JPEG")
                    || filename.endsWith(".jpeg")|| filename.endsWith(".jpg")
                    || filename.endsWith(".JPG"))){
                resultList.add(info);
            }
        }
        showTitle.setVisibility(View.GONE);
        isChoicing = false;
        isAllSelect = false;
        isTaskOpen = false;
        choiceBtn.setText(getString(R.string.operation_choice));
        if(selectedList != null){
            selectedList.clear();
        }
        if(selectPosMap != null){
            selectPosMap.clear();
        }
        if(selectDateMap != null){
            selectDateMap.clear();
        }
        if(lastNameList != null){
            lastNameList.clear();
        }
        return resultList;
    }

    private AdapterView.OnItemClickListener mListViewOnItemClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if(mBrowseAdapter == null || isLoading || isDeleting || position > mBrowseAdapter.getCount()){
                Dbug.e(tag,"========= onItemClick error =========");
                return;
            }
            position--;
            if(position < 0){
                position = 0;
            }
            FileInfo fileInfo = null;
            if(position < mBrowseAdapter.getCount()){
                fileInfo = mBrowseAdapter.getItem(position);
            }
            if(fileInfo == null){
                Dbug.e(tag, "mListViewOnItemClick fileInfo is null !");
                return;
            }
            Dbug.e(tag,"========= onItemClick click =========" + position);
            if (!fileInfo.isDirectory()) {
                if(isChoicing){
                    String selectName = fileInfo.getTitle();
                    if(TextUtils.isEmpty(selectName)){return;}
                    if(selectedList != null && selectPosMap != null && selectDateMap != null){
                        if(selectedList.size() > 0){
                            for(String s : selectedList){
                                if(s.equals(selectName)){
                                    selectedList.remove(selectName);
                                    selectPosMap.remove(selectName);
                                    selectDateMap.remove(selectName);
                                    mBrowseAdapter.notifyDataSetChanged();
                                    String changeStr = getString(R.string.selected) + selectedList.size() +
                                            getString(R.string.separator) + fileInfoList.size() + getString(R.string.files);
                                    showTitle.setVisibility(View.VISIBLE);
                                    showTitle.setText(changeStr);
                                    return;
                                }
                            }
                            selectedList.add(selectName);
                            selectPosMap.put(selectName, position);
                            selectDateMap.put(selectName, fileInfo.getDateMes());
                        }else{
                            selectedList.add(selectName);
                            selectPosMap.put(selectName, position);
                            selectDateMap.put(selectName, fileInfo.getDateMes());
                        }
                    }
                    String changeStr = getString(R.string.selected)+selectedList.size()+
                            getString(R.string.separator)+ fileInfoList.size() +getString(R.string.files);
                    showTitle.setVisibility(View.VISIBLE);
                    showTitle.setText(changeStr);
                    mBrowseAdapter.notifyDataSetChanged();
                }else{
                    String filename = fileInfo.getTitle();
                    if (!filename.isEmpty()) {
                        String newFilename = BufChangeHex.combinDataStr(filename, fileInfo.getDateMes());
                        String localPath = dDownloadPath+ File.separator + newFilename;
                        browseResources(fileInfo, localPath);
                    }
                }
                return;
            }
            Message message = Message.obtain();
            message.what = FtpHandlerThread.MSG_CHANGE_TO_SUBDIR;
            Bundle bundle = new Bundle();
            bundle.putString(IConstant.FILE_NAME, fileInfo.getTitle());
            message.setData(bundle);
            mWorkHandlerThread.getWorkHandler().sendMessage(message);
            Dbug.d(tag, "mCurrentPa=" + fileInfo.getTitle());
        }
    };


    private void browseResources(FileInfo fileInfo, String filepath){
        try{
            if(getActivity() == null){
                return;
            }
            String filename = fileInfo.getTitle();
            File file = new File(filepath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if(!TextUtils.isEmpty(filename) &&(filename.endsWith(".png")
                    || filename.endsWith(".PNG") || filename.endsWith(".JPEG")
                    || filename.endsWith(".jpeg")||filename.endsWith(".jpg")
                    || filename.endsWith(".JPG"))) {
                if(!file.exists()){
                    showToastShort(getString(R.string.browse_file_err));
//                    showDownLoadNotifyDialog(fileInfo,position);
                    return;
                }
                intent.setDataAndType(Uri.parse("file://" + file.getPath()), "image/*");
            }else if(!TextUtils.isEmpty(filename) &&(filename.endsWith(".mov")
                    || filename.endsWith(".MOV") || filename.endsWith(".mp4")
                    || filename.endsWith(".MP4")|| filename.endsWith(".avi")
                    || filename.endsWith(".AVI"))){
                if(!file.exists()){
                    showToastShort(getString(R.string.browse_file_err));
//                    showDownLoadNotifyDialog(fileInfo, position);
                    return;
                }
                String stend = "";
                if(!TextUtils.isEmpty(filename)) {
                    if (filename.endsWith(".mov") || filename.endsWith(".MOV")) {
                        stend = "mov";
                    } else if (filename.endsWith(".avi") || filename.endsWith(".AVI")) {
                        stend = "avi";
                    } else if (filename.endsWith(".mp4") || filename.endsWith(".MP4")) {
                        stend = "mp4";
                    }
                }
                intent.setDataAndType(Uri.parse("file://" + file.getPath()), "video/" + stend);
            }else{
                showToastShort(getString(R.string.open_file_err));
            }
            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivity(intent);
                mApplication.setIsBrowsing(true);
            }
        }catch (Exception e){
            Log.e(tag, " error  " + e.getMessage());
            e.printStackTrace();
        }
    }

    private OnItemClickListener deleteOnItemClick = new OnItemClickListener() {
        @Override
        public void onItemClick(Object o, int i) {
            switch (i){
                case -1: // cancel alertView
                    break;
                case 0: // delete files
                    if(selectedList.size() > 0){
                        isTaskOpen = true;
                        dealWithTask(DELETE_STYLE, false);
                        isDeleting = true;
                        sendBroadcastToUI(WAITING_FOR_DELETE, ARGS_SHOW_DIALOG);
                    }else{
                        showToastShort(getString(R.string.select_err));
                    }
                    break;
            }
        }
    };

    private OnItemClickListener  downloadOnItemClick = new OnItemClickListener() {
        @Override
        public void onItemClick(Object o, int i) {
            switch (i){
                case -1: // cancel alertView
                    break;
                case 0: // download files
                    if(selectedList.size() > 0){
                        if(BufChangeHex.readSDCard() <= 50*1024*1024){
                            isTaskOpen = false;
                            backBtn.performClick();
                            showToastShort(getString(R.string.phone_space_less));
                        }else{
                            isTaskOpen = true;
                            dealWithTask(DOWNLOAD_STYLE, false);
                            taskNum = selectedList.size();
                        }
                    }else{
                        showToastShort(getString(R.string.select_err));
                    }
                    break;
            }
        }
    };

    private void dealWithTask(final int style, boolean result) {
        if(selectPosMap == null || selectedList == null || selectDateMap == null){
            Dbug.e(tag, "dealWithTask : selectPosMap ,selectDateMap or selectedList is null! ");
            return;
        }
        Dbug.e(tag, "dealWithTask is start, isTaskOpen = " + isTaskOpen);
        if(isTaskOpen){
            if(opType != style){
                opType = style;
            }
            if(result){
                if(selectedList.size() > 0){
                    if(null != selectDateMap.get(selectedList.get(0))){
                        selectDateMap.remove(selectedList.get(0));
                    }
                    selectedList.remove(0);
                }
                failureTimes = 3;
            }else {
                failureTimes--;
                if (failureTimes <= 0) {
                    if(selectedList.size() > 0){
                        if(null != selectDateMap.get(selectedList.get(0))){
                            selectDateMap.remove(selectedList.get(0));
                        }
                        selectedList.remove(0);
                    }
                    failureTimes = 3;
                }
            }
            if(selectedList.size() > 0) {
                final String str = selectedList.get(0);
                if (!TextUtils.isEmpty(str)) {
                    final Integer position = selectPosMap.get(str);
                    final String dateMes = BufChangeHex.combinDataStr(str, selectDateMap.get(str));
                    String downPath = dDownloadPath;
                    File downFile = new File(downPath);
                    if(!downFile.exists()){
                        if(downFile.mkdir()){
                            Dbug.e(tag, "download folder create success!");
                        }
                    }
                    final String localPath =  downPath +File.separator + dateMes;
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(style == DELETE_STYLE){
                                if(mWorkHandlerThread != null){
                                    if (position != null) {
                                        mWorkHandlerThread.tryToDeleteFile(str, position, isRearViewBrowsing);
                                    }
                                }
                            }else if(style == DOWNLOAD_STYLE){
                                Dbug.d(tag, " downloadPath = " + localPath + " isLoading = "+ isLoading + " mWorkHandlerThread = " + mWorkHandlerThread);
                                if(!isLoading){
                                    if(mWorkHandlerThread != null){
                                        mWorkHandlerThread.tryToDownloadFile(str, localPath, isRearViewBrowsing);
                                        if(!downloadFilename.equals(str)){
                                            downloadFilename = str;
                                        }
                                    }
                                }
                            }
                        }
                    }, 300L);
                }else{
                    dealWithTask(style, false);
                    Dbug.e(tag, "dealWithTask false , due to " + str);
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        String changeTxt = getString(R.string.selected) + selectedList.size() +
                                getString(R.string.separator) + fileInfoList.size() + getString(R.string.files);
                        showTitle.setVisibility(View.VISIBLE);
                        showTitle.setText(changeTxt);
                    }
                });
            }else{
                if(style == DOWNLOAD_STYLE){
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String text = (taskNum - selectedList.size()) + getString(R.string.separator) + taskNum;
                            downloadDialog.setDialogTask(text);
                        }
                    });
                }
                backBtn.performClick();
            }
        }
    }

    private void initDialog(){
        if(getActivity() == null){
            return;
        }
        if(downloadDialog == null){
            downloadDialog = new DownloadDialog(getActivity());
            downloadDialog.setDialogTilte(getString(R.string.file_download));
            downloadDialog.getNumberProgressBar().setMax(100);
            downloadDialog.setOnCancelClickListener(new DownloadDialog.OnCancelBtnClickListener() {
                @Override
                public void onClick() {
                    if (downloadDialog != null && downloadDialog.isShowing()) {
                        if (null != mWorkHandlerThread) {
                            mWorkHandlerThread.setIsStopDownLoadThread(true);
                            if (selectedList != null) {
                                selectedList.clear();
                            }
                        }
                        downloadDialog.dismiss();
                    }
                }
            });
            downloadDialog.setCancelable(false);
            downloadDialog.setOnKeyListener(dialogOnKeyListener);
        }
    }

    private DialogInterface.OnKeyListener dialogOnKeyListener = new DialogInterface.OnKeyListener() {
        @Override
        public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
            if(keyCode == KeyEvent.KEYCODE_BACK){
                if(downloadDialog != null && downloadDialog.isShowing()){
                    downloadDialog.dismiss();
                    if (selectedList != null) {
                        selectedList.clear();
                    }
                    mWorkHandlerThread.setIsStopDownLoadThread(true);
                }
                return  true;
            }
            return false;
        }
    };

    private void initListAndMap(){
        if(allDataList == null){
            allDataList = new ArrayList<>();
        }
        if(fileInfoList == null){
            fileInfoList = new ArrayList<>();
        }
        if(tempList == null){
            tempList = new ArrayList<>();
        }
        if(selectedList == null){
            selectedList = new ArrayList<>();
        }
        if(selectPosMap == null){
            selectPosMap = new HashMap<>();
        }
        if(selectDateMap == null){
            selectDateMap = new HashMap<>();
        }
        if(lastNameList == null){
            lastNameList = new ArrayList<>();
        }
        if(videoInfoMap == null){
            videoInfoMap =  new HashMap<>();
        }
        if(durationMap == null){
            durationMap = new HashMap<>();
        }
    }

    private void destroyListAndMap(){
        if (tempList != null) {
            tempList.clear();
            tempList = null;
        }
        if (selectedList != null) {
            selectedList.clear();
            selectedList = null;
        }
        if (selectPosMap != null) {
            selectPosMap.clear();
            selectPosMap = null;
        }
        if(selectDateMap != null){
            selectDateMap.clear();
            selectDateMap = null;
        }
        if(lastNameList != null){
            lastNameList.clear();
            lastNameList = null;
        }
        if(fileInfoList != null){
            fileInfoList.clear();
            fileInfoList = null;
        }
        if(videoInfoMap != null){
            videoInfoMap.clear();
        }
        if(lastNameList != null){
            lastNameList.clear();
            lastNameList = null;
        }
        if(durationMap != null){
            durationMap.clear();
            durationMap = null;
        }
        System.gc();
    }

    private void sendBroadcastToUI(int dialogType, int state){
        if(getActivity() != null){
            Intent intent = new Intent(IAction.ACTION_UPDATE_DEVICE_FILES_UI);
            intent.putExtra(DEVICE_FILES_UI_TYPE, dialogType);
            intent.putExtra(DEVICE_DIALOG_STATE, state);
            getActivity().sendBroadcast(intent);
        }
    }

    private Runnable cancelDialogRunnable = new Runnable() {
        @Override
        public void run() {
            sendBroadcastToUI(WAITING_FOR_THUMB, ARGS_DISMISS_DIALOG);
            if(mBrowseAdapter != null){
                mBrowseAdapter.notifyDataSetChanged();
            }
            mHandler.removeCallbacks(cancelDialogRunnable);
        }
    };

    @Override
    public void onDestroy() {
        Dbug.e(tag, "== onDestroy == ");
        if(mApplication != null && videoInfoMap != null && videoInfoMap.size() > 0){
            mApplication.setVideoInfoMap(videoInfoMap);
        }
        if(isTaskOpen){
            isTaskOpen = false;
        }
        isLoading = false;
        isDeleting = false;
        isChoicing = false;
        fragment = null;
        if(!TextUtils.isEmpty(dDownloadPath)){
            dDownloadPath = null;
        }
        if(!TextUtils.isEmpty(dImageThumbPath)){
            dImageThumbPath = null;
        }
        if(!TextUtils.isEmpty(dVideoThumbPath)){
            dVideoThumbPath = null;
        }
        if(Thread.currentThread().isAlive()){
            Thread.currentThread().interrupt();
        }
        if(future != null){
            future.cancel(true);
            future = null;
        }
        if(service != null){
            if(!service.isShutdown()){
                service.shutdownNow();
            }
            service = null;
        }
        if(mHandler != null){
            mHandler.removeCallbacksAndMessages(null);
        }
        if(mReceiver != null && getActivity() != null){
            getActivity().getApplicationContext().unregisterReceiver(mReceiver);
        }
        if(downloadDialog != null){
            downloadDialog.cancel();
            downloadDialog = null;
        }
        if(downloadNotifyDialog != null){
            downloadNotifyDialog.dismiss();
            downloadNotifyDialog = null;
        }
        destroyListAndMap();
        super.onDestroy();
    }

    private void onLoad() {
        mRefreshListView.stopRefresh();
        mRefreshListView.stopLoadMore();
        mRefreshListView.setRefreshTime(TimeFormater.formatYMDHMS(System
                .currentTimeMillis()));
    }

    private void onFinish(boolean isShow) {
        mRefreshListView.stopRefresh();
        mRefreshListView.loadMoreFinish();
        if(isAdded()){
            if(isShow){
                showToastShort(R.string.data_load_finish);
            }
        }
    }

    @Override
    public void onRefresh() {
        onLoad();
//        if(mBitmapCache != null){
//            mBitmapCache.clearCache();
//        }
//        mHandler.postDelayed(updateList, 1000L);
    }

    @Override
    public void onLoadMore() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (null == tempList || fileInfoList == null || fileInfoList.size() == 0
                        || allDataList == null || allDataList.size() == 0 || mBrowseAdapter == null) {
                    onFinish(false);
                    return;
                }
                if (tempList.size() >= 0) {
                    int tempLen = mBrowseAdapter.getCount();
                    int length = fileInfoList.size() - tempLen;
                    if (length < 0) {
                        onFinish(false);
                        return;
                    }
                    tempList.clear();
                    if (length >= PAGE_ITEM_NUM) {
                        for (int i = 0; i < PAGE_ITEM_NUM; i++) {
                            if (tempLen + i < fileInfoList.size()) {
                                tempList.add(fileInfoList.get(tempLen + i));
                            }
                        }
                        onLoad();
                    } else {
                        for (int i = 0; i < length; i++) {
                            if (tempLen + i < fileInfoList.size()) {
                                tempList.add(fileInfoList.get(tempLen + i));
                            }
                        }
                        if(fileInfoList.size() < PAGE_ITEM_NUM){
                            onFinish(false);
                        }else{
                            onFinish(true);
                        }
                    }
                    if (mBrowseAdapter != null) {
                        mBrowseAdapter.addAll(tempList);
                        mBrowseAdapter.notifyDataSetChanged();
                    }
                }
            }
        }, 1000L);
    }

    private void showDownLoadNotifyDialog(final FileInfo fileInfo, final int position){
        if(getActivity() ==  null || fileInfo == null || position < 0){
            return;
        }
        if(downloadNotifyDialog == null){
            downloadNotifyDialog = new NotifyDialog(R.string.dialog_tip, R.string.download_file_tip, R.string.cancel, R.string.confirm,
                    new NotifyDialog.OnNegativeClickListener() {
                        @Override
                        public void onClick() {
                            if(downloadNotifyDialog != null){
                                downloadNotifyDialog.dismiss();
                                downloadNotifyDialog = null;
                            }
                        }
                    }, new NotifyDialog.OnPositiveClickListener() {
                @Override
                public void onClick() {
                    if(downloadNotifyDialog != null){
                        downloadNotifyDialog.dismiss();
                        downloadNotifyDialog = null;
                    }
                    long fileSize = fileInfo.getSize();
                    if(BufChangeHex.readSDCard() <= fileSize + 10*1024 *1024){
                        isTaskOpen = false;
                        backBtn.performClick();
                        showToastShort(getString(R.string.phone_space_less));
                    }else{
                        isTaskOpen = true;
                        isChoicing = true;
                        String fileName = fileInfo.getTitle();
                        selectedList.add(fileName);
                        selectPosMap.put(fileName, position);
                        selectDateMap.put(fileName, fileInfo.getDateMes());
                        dealWithTask(DOWNLOAD_STYLE, false);
                        taskNum = selectedList.size();
                    }
                }
            });
            downloadNotifyDialog.setCancelable(false);
        }
        if(!downloadNotifyDialog.isShowing()){
            downloadNotifyDialog.show(getActivity().getFragmentManager(), "download_notify");
        }
    }

    private void getPicture(final ImageView imageView, final String name, final String path, final String date){
        if(imageView == null || name == null || name.isEmpty() || path == null || path.isEmpty()){
            Log.d(tag, "parameter is null.");
            return;
        }
        future = service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final String newPath = dImageThumbPath + File.separator + name;
                    File newFile = new File(newPath);
                    if (newFile.exists()) {
                        BitmapFactory.Options options  = new BitmapFactory.Options();
                        options.inJustDecodeBounds = false;
                        options.inSampleSize = 10;
                        final Bitmap newBitmap = BitmapFactory.decodeFile(newPath, options);
                        if (newBitmap != null) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null) {
                                        mApplication.removeThumbPath(name);
                                        mApplication.addThumbPath(name, newPath);
                                        mApplication.addBitmapInCache(newBitmap, newPath);
                                    }
                                    imageView.setImageBitmap(newBitmap);
                                }
                            });
                        }else {
                            if(newFile.delete()){
                                Dbug.e(tag, "browse TF card image is not opened, so delete this thumb.");
                            }
                        }
                    } else {
                        final String filename = BufChangeHex.combinDataStr(name, date);
                        final String  filepath = dDownloadPath+ File.separator + filename;
                        File oldFile = new File(filepath);
                        if (oldFile.exists()) {
                            BitmapFactory.Options options  = new BitmapFactory.Options();
                            options.inJustDecodeBounds = false;
                            options.inSampleSize = 10;
                            final Bitmap newBitmap = BitmapFactory.decodeFile(filepath, options);
                            if (newBitmap != null) {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null) {
                                            mApplication.removeThumbPath(name);
                                            mApplication.addThumbPath(name, filepath);
                                            mApplication.addBitmapInCache(newBitmap, filepath);
                                        }
                                        imageView.setImageBitmap(newBitmap);
                                    }
                                });
                            }else{
                                if(oldFile.delete()){
                                    Dbug.e(tag, "download image is not opened, so delete this image.");
                                }
                            }
                        } else {
                            if (!lastNameList.contains(name) && !isLoading && !isDeleting) {
                                Log.e(tag, " filename ===> " + name);
                                mWorkHandlerThread.tryToDownloadThumbnail(name, path, date, isRearViewBrowsing);
                                lastNameList.add(name);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(tag, "err =" + e.getMessage());
                    e.printStackTrace();
                }
            }
        },name);
    }

    private void getVideoBitmap(final ImageView imageView, final TextView textView, final String name, final String path, final String date){
        if(imageView == null || textView == null || name == null || name.isEmpty() || path == null || path.isEmpty()){
            Dbug.d(tag, "parameter is null.");
            return;
        }
        future = service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String videoThumbPath = dVideoThumbPath;
                    String correctPath;
                    String newThumbName;
                    File newThumbFile;
                    newThumbName = BufChangeHex.getVideoThumb(name, videoThumbPath);
                    correctPath = videoThumbPath + File.separator +newThumbName;
                    newThumbFile = new File(correctPath);
                    if (newThumbFile.exists() && newThumbFile.isFile()) {
                        BitmapFactory.Options options  = new BitmapFactory.Options();
                        options.inJustDecodeBounds = false;
                        options.inSampleSize = 10;
                        final Bitmap newBitmap = BitmapFactory.decodeFile(correctPath, options);
                        final String filePath = newThumbFile.getPath();
                        if (newBitmap != null) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null) {
                                        mApplication.removeThumbPath(name);
                                        mApplication.addThumbPath(name, filePath);
                                        mApplication.addBitmapInCache(newBitmap, filePath);
                                    }
                                    imageView.setImageBitmap(newBitmap);
                                    if (durationMap != null) {
                                        if (null == durationMap.get(name)) {
                                            String videoDuration = BufChangeHex.getVideoDuration(filePath);
                                            if (videoDuration != null) {
                                                long duration = Long.parseLong(videoDuration);
                                                String durationStr = TimeFormater.getTimeFormatValue(duration);
                                                durationMap.put(name, durationStr);
                                            }
                                        }
                                        textView.setText(durationMap.get(name));
                                    }
                                }
                            });
                        } else {
                            if(newThumbFile.delete()){
                                Dbug.d(tag, "the video's thumb is null, so delete this video!");
                            }

                        }
                    } else {
                        final String filename =  BufChangeHex.combinDataStr(name, date);
                        final String  filepath = dDownloadPath + File.separator + filename;
                        File newFile = new File(filepath);
                        if (newFile.exists()) {
                            try{
                                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                                try{
                                    metadataRetriever.setDataSource(filepath);
                                }catch (Exception e){
                                    Dbug.e(tag,"MediaMetadataRetriever err : " + e.getMessage());
                                    e.printStackTrace();
                                }
                                final String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                                String videoWidth = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                                String videoHeight = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

                                Dbug.e(tag, " the video name =" + name + " duration = " + duration + " videoWidth = " + videoWidth + "  videoHeight = " + videoHeight);
                                if(duration == null || videoWidth == null || videoHeight == null){
                                    if (!lastNameList.contains(name) && !isLoading && !isDeleting) {
                                        Dbug.e(tag, " filename ===> " + name);
                                        mWorkHandlerThread.tryToDownloadThumbnail(name, path, date, isRearViewBrowsing);
                                        lastNameList.add(name);
                                    }
                                    return;
                                }
                                Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(filepath, MediaStore.Video.Thumbnails.MINI_KIND);
                                final Bitmap newBitmap = ThumbnailUtils.extractThumbnail(bitmap, 120, 70);
                                if (newBitmap != null) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null) {
                                                mApplication.removeThumbPath(name);
                                                mApplication.addThumbPath(name, filepath);
                                                mApplication.addBitmapInCache(newBitmap, filepath);
                                            }
                                            imageView.setImageBitmap(newBitmap);
                                            if(durationMap != null){
                                                if(null == durationMap.get(name)){
                                                    durationMap.put(name, TimeFormater.getTimeFormatValue(Long.valueOf(duration) / 1000));
                                                    textView.setText(TimeFormater.getTimeFormatValue(Long.valueOf(duration) / 1000));
                                                }
                                            }

                                        }
                                    });
                                } else {
                                    Dbug.d(tag, "the bitmap is null");
                                    if (!lastNameList.contains(name) && !isLoading && !isDeleting) {
                                        Dbug.e(tag, " filename ===> " + name);
                                        mWorkHandlerThread.tryToDownloadThumbnail(name, path, date, isRearViewBrowsing);
                                        lastNameList.add(name);
                                    }
                                }
                            }catch (Exception e){
                                Dbug.e(tag,"Exception --> " +e.getMessage());
                                e.printStackTrace();
                            }
                        } else {
                            if (!lastNameList.contains(name) && !isLoading && !isDeleting) {
                                Dbug.e(tag, " filename ===> " + name);
                                mWorkHandlerThread.tryToDownloadThumbnail(name, path, date, isRearViewBrowsing);
                                lastNameList.add(name);
                            }
                        }
                    }
                }catch (Exception e){
                    Dbug.e(tag, "err =" +e.getMessage());
                }
            }
        },name);
    }

    private class BrowseAdapter extends ArrayAdapter<FileInfo> {
        private LayoutInflater mLayoutInflater;
        private ViewHolder holder;

        BrowseAdapter(Context context) {
            super(context, 0);
            mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null){
                holder = new ViewHolder();
                convertView = mLayoutInflater.inflate(R.layout.browse_file_item, parent, false);
                holder.fileThumb = (ImageView) convertView.findViewById(R.id.browse_thumb);
                holder.checkbox = (ImageView) convertView.findViewById(R.id.file_chose_state);
                holder.fileName = (CustomTextView) convertView.findViewById(R.id.file_name);
                holder.fileSize = (TextView) convertView.findViewById(R.id.file_size);
                holder.fileDuration = (TextView) convertView.findViewById(R.id.browse_file_duration);
                holder.fileTime = (TextView) convertView.findViewById(R.id.file_create_time);
                holder.fileState = (ImageView) convertView.findViewById(R.id.file_down_state);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final FileInfo item = getItem(position);
            if(item == null) return convertView;
            String filename = item.getTitle();
            if (item.isDirectory()){
                holder.fileName.setText(item.getTitle());
                holder.fileThumb.setImageResource(R.mipmap.ic_directory);
            } else {
                holder.fileName.setText(item.getTitle());
                holder.fileSize.setText(DataCleanManager.getFormatSize((double) item.getSize()));
                holder.fileDuration.setVisibility(View.GONE);
                holder.fileTime.setText(item.getCreateDate());
                String filePath = AppUtil.getAppStoragePath(mApplication, THUMB, isRearViewBrowsing);
                File file2 = new File(filePath);
                if(!file2.exists()){
                    if(file2.mkdir()){
                        Dbug.e(tag, "mkdir sub's sub folder success, the path : " + filePath);
                    }
                }
                String localPath = filePath + "/" + filename;
                if(!TextUtils.isEmpty(filename) &&(filename.endsWith(".png")
                        || filename.endsWith(".PNG") || filename.endsWith(".JPEG")
                        || filename.endsWith(".jpeg")|| filename.endsWith(".jpg")
                        || filename.endsWith(".JPG"))){
                    //deal with picture
                    holder.fileThumb.setImageResource(R.mipmap.image_default_icon);
                    String downloadFilename =  BufChangeHex.combinDataStr(filename, item.getDateMes());
                    String  filepath = dDownloadPath + File.separator + downloadFilename;
                    File newFile = new File(filepath);
                    if(newFile.exists()){
                        holder.fileState.setImageResource(R.mipmap.download_ok);
                    }else{
//                        holder.fileState.setImageResource(R.mipmap.download_black);
                        holder.fileState.setImageResource(R.drawable.download_state_drawale);
                    }
                    if(mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null){
                        Dbug.e(tag, "bitmap cache size : " + mApplication.getBitmapCacheCount() + " ,thumbPathMap size = " + mApplication.getThumbPathMapSize());
                        if(mApplication.getBitmapCacheCount() > 0 && mApplication.getThumbPathMapSize() > 0) {
                            String thumbPath = mApplication.getThumbPath(filename);
                            if (null != thumbPath) {
                                Bitmap bitmap = mApplication.getBitmapInCache(thumbPath);
                                if (null != bitmap) {
                                    holder.fileThumb.setImageBitmap(bitmap);
                                } else {
                                    getPicture(holder.fileThumb, filename, localPath, item.getDateMes());
                                }
                            } else {
                                getPicture(holder.fileThumb, filename, localPath, item.getDateMes());
                            }
                        }else{
                            getPicture(holder.fileThumb, filename, localPath, item.getDateMes());
                        }
                    }
                }else if(!TextUtils.isEmpty(filename) &&(filename.endsWith(".mov")
                        || filename.endsWith(".MOV") || filename.endsWith(".mp4")
                        || filename.endsWith(".MP4")|| filename.endsWith(".avi")
                        || filename.endsWith(".AVI"))){
                    //deal with video
                    holder.fileDuration.setVisibility(View.VISIBLE);
                    holder.fileThumb.setImageResource(R.mipmap.image_default_icon);
                    holder.fileDuration.setText(TimeFormater.getTimeFormatValue(0));
                    String downloadFilename =  BufChangeHex.combinDataStr(filename, item.getDateMes());
                    String  filepath = dDownloadPath + File.separator + downloadFilename;

                    File newFile = new File(filepath);
                    if(newFile.exists()){
                        holder.fileState.setImageResource(R.mipmap.download_ok);
                    }else{
//                        holder.fileState.setImageResource(R.mipmap.download_black);
                        holder.fileState.setImageResource(R.drawable.download_state_drawale);
                    }
                    if(durationMap != null){
                        if(durationMap.size() > 0){
                            if(null != durationMap.get(filename)){
                                holder.fileDuration.setText(durationMap.get(filename));
                            }
                        }
                    }
                    if(mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null){
                        if(mApplication.getBitmapCacheCount() > 0 && mApplication.getThumbPathMapSize() > 0) {
                            String thumbPath = mApplication.getThumbPath(filename);
                            if (!TextUtils.isEmpty(thumbPath)) {
                                Bitmap bitmap = mApplication.getBitmapInCache(thumbPath);
                                if (null != bitmap) {
                                    holder.fileThumb.setImageBitmap(bitmap);
                                } else {
                                    getVideoBitmap(holder.fileThumb, holder.fileDuration, filename, localPath, item.getDateMes());
                                }
                            }else {
                                getVideoBitmap(holder.fileThumb, holder.fileDuration, filename, localPath, item.getDateMes());
                            }
                        }else{
                            getVideoBitmap(holder.fileThumb, holder.fileDuration, filename, localPath, item.getDateMes());
                        }
                    }
                }else{
                    holder.fileThumb.setImageResource(R.mipmap.ic_file);
                }
                holder.fileState.setTag(position);
                holder.fileState.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int position = (int)v.getTag();
                        Dbug.e(tag, "Photo getView position : " + position);
                        if(position >= 0 && position < mBrowseAdapter.getCount()){
                            FileInfo info = mBrowseAdapter.getItem(position);
                            if(info != null){
                                String downloadFilename = BufChangeHex.combinDataStr(info.getTitle(), info.getDateMes());
                                String filepath = dDownloadPath + File.separator + downloadFilename;
                                File newFile = new File(filepath);
                                if(!isChoicing){
                                    if(!newFile.exists()){
                                        showDownLoadNotifyDialog(info, position);
                                    }else{
                                        browseResources(info, filepath);
                                    }
                                }
                            }
                        }
                    }
                });
            }
            if(isChoicing){
                if (item.isDirectory()){
                    holder.checkbox.setVisibility(View.INVISIBLE);
                }else{
                    holder.checkbox.setVisibility(View.INVISIBLE);
                    if(selectedList != null){
                        if(selectedList.size() > 0){
                            for(int i = 0; i < selectedList.size(); i++){
                                String s = selectedList.get(i);
                                if(s.equals(filename)){
                                    holder.checkbox.setVisibility(View.VISIBLE);
                                    break;
                                }else{
                                    holder.checkbox.setVisibility(View.INVISIBLE);
                                }
                            }
                        }else{
                            holder.checkbox.setVisibility(View.INVISIBLE);
                        }
                    }
                }
            }else{
                holder.checkbox.setVisibility(View.INVISIBLE);
            }
            return convertView;
        }

        private class ViewHolder {
            private ImageView fileThumb;
            private ImageView checkbox;
            private CustomTextView fileName;
            private TextView fileSize;
            private TextView fileDuration;
            private TextView fileTime;
            private ImageView fileState;
        }
    }
}
