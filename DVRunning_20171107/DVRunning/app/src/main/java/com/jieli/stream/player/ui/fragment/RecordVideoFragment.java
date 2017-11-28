package com.jieli.stream.player.ui.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
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
import com.jieli.stream.player.ui.dialog.NotifyDialog;
import com.jieli.stream.player.ui.lib.CustomTextView;
import com.jieli.stream.player.ui.lib.RefreshListView;
import com.jieli.stream.player.util.AppUtil;
import com.jieli.stream.player.util.BufChangeHex;
import com.jieli.stream.player.util.DataCleanManager;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IAction;
import com.jieli.stream.player.util.IConstant;
import com.jieli.stream.player.util.ScanFilesHelper;
import com.jieli.stream.player.util.TimeFormater;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RecordVideoFragment extends BaseFragment implements RefreshListView.IXListViewListener, IConstant{

    private String tag =  getClass().getSimpleName();
    private RefreshListView mRefreshListView;
    private BrowseAdapter mBrowseAdapter;
    private TextView selectTitle;
    private CustomTextView choiceBtn;
    private CustomTextView backBtn;

    private ExecutorService service = null;
    private Future<String> future = null;
    private NotifyDialog notifyDialog;

    private List<FileInfo> allDataInfoList = null;
    private List<FileInfo> recordFileInfoList = null;
    private List<FileInfo> tempList = null;
    private Map<String, FileInfo> selectFileInfoMap = null;
    private Map<String, String> durationMap = null;
    private List<String> selectedList = null;

    private boolean isSelecting = false;
    private boolean isDeleting = false;
    private boolean isAllSelect = false;
    private boolean isTaskOpen = false;
    private String appFilePath;
    private String defaultPath;
    private int failureTimes = 3;
    private Thread deleteThread;
    private ScanFilesHelper scanFilesHelper;

    private final int PAGE_ITEM_NUM = 7;
    private final int MSG_UPDATE_LIST = 0xA1;
    private String mWhichDir;
    private boolean isRearViewBrowsing = false;

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(getActivity() == null || !isAdded() || getActivity().isFinishing()){
                return false;
            }
            switch (msg.what){
                case MSG_UPDATE_LIST:
                    if(mApplication.getIsOffLineMode()){
                        allDataInfoList = AppUtil.getAllLocalFile(appFilePath, RECORD, false);
                    }else{
                        allDataInfoList = AppUtil.getLocalFileInfo(defaultPath, false);
                    }
                    if(recordFileInfoList != null && recordFileInfoList.size() > 0){
                        recordFileInfoList.clear();
                    }
                    recordFileInfoList = selectTypeList(allDataInfoList);
                    initializationData(recordFileInfoList);
                    selectTitle.setVisibility(View.GONE);
                    break;
            }
            return false;
        }
    });

    public RecordVideoFragment() {
        // Required empty public constructor
    }

    public static RecordVideoFragment newInstance(String which) {
        RecordVideoFragment fragment = new RecordVideoFragment();
        Bundle args = new Bundle();
        args.putString(ARG_WHICH_DIR, which);
        fragment.setArguments(args);
        return fragment;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(IAction.ACTION_BROWSE_MODE_OPERATION)){
                int operationType = intent.getIntExtra(IConstant.BROWSE_FILE_OPERATION_STYLE, -1);
                int fragmentType = intent.getIntExtra(IConstant.BROWSE_FRAGMENT_TYPE, -1);
                if(getActivity() == null || fragmentType != 1 || !isAdded() || isHidden()
                        || !(mApplication.getCurrentFragment() instanceof RecordVideoFragment)){
                    return;
                }
                switch (operationType){
                    case IConstant.SELECT_BROWSE_FILE:
                        if(choiceBtn.getText().toString().equals(getString(R.string.operation_choice))) {
                            if (!isSelecting) {
                                if(null == recordFileInfoList || recordFileInfoList.size() == 0){
                                    showToastLong(R.string.ftp_data_null);
                                    return;
                                }
                                isSelecting = true;
                                isAllSelect = false;
                                choiceBtn.setText(getString(R.string.all_select));
                                backBtn.setText(getString(R.string.cancel));
                                if(selectedList != null){
                                    selectedList.clear();
                                }
                                if(selectFileInfoMap != null){
                                    selectFileInfoMap.clear();
                                }
                                if(recordFileInfoList != null){
                                    String showTxt = getString(R.string.selected) + selectedList.size() +
                                            getString(R.string.separator) + recordFileInfoList.size() + getString(R.string.files);
                                    selectTitle.setVisibility(View.VISIBLE);
                                    selectTitle.setText(showTxt);
                                }
                                if (mBrowseAdapter != null) {
                                    mBrowseAdapter.notifyDataSetChanged();
                                }
                            }
                        }else if(choiceBtn.getText().toString().equals(getString(R.string.all_select))){
                            isSelecting = true;
                            if(isAllSelect){
                                isAllSelect = false;
                                if(selectedList.size() > 0){
                                    selectedList.clear();
                                }
                                if(selectFileInfoMap.size() > 0){
                                    selectFileInfoMap.clear();
                                }
                                if(recordFileInfoList != null) {
                                    String valueStr = getString(R.string.selected) + selectedList.size() +
                                            getString(R.string.separator) + recordFileInfoList.size() + getString(R.string.files);
                                    selectTitle.setVisibility(View.VISIBLE);
                                    selectTitle.setText(valueStr);
                                }
                            }else{
                                isAllSelect = true;
                                if(selectedList.size() > 0){
                                    selectedList.clear();
                                }
                                if(selectFileInfoMap.size() > 0){
                                    selectFileInfoMap.clear();
                                }
                                if(null != recordFileInfoList){
                                    for (int i = 0; i < recordFileInfoList.size(); i++){
                                        FileInfo info = recordFileInfoList.get(i);
                                        if(info != null){
                                            String fileName = info.getTitle();
                                            if(!TextUtils.isEmpty(fileName)){
                                                selectedList.add(fileName);
                                                selectFileInfoMap.put(fileName, info);
                                            }
                                        }
                                    }
                                    String str = getString(R.string.selected) + selectedList.size() +
                                            getString(R.string.separator) + recordFileInfoList.size() + getString(R.string.files);
                                    selectTitle.setVisibility(View.VISIBLE);
                                    selectTitle.setText(str);
                                }
                            }
                            if (mBrowseAdapter != null) {
                                mBrowseAdapter.notifyDataSetChanged();
                            }
                        }
                        break;
                    case IConstant.BACK_BROWSE_MODE:
                        if(isSelecting){
                            isSelecting = false;
                            isAllSelect = false;
                            choiceBtn.setText(getString(R.string.operation_choice));
                            backBtn.setText(getString(R.string.return_back));
                            selectTitle.setVisibility(View.GONE);
                            if(selectedList != null){
                                selectedList.clear();
                            }
                            if(selectFileInfoMap != null){
                                selectFileInfoMap.clear();
                            }
                            sendBroadcastToUI(1);
                            handler.sendMessage(handler.obtainMessage(MSG_UPDATE_LIST));
                            isDeleting = false;
                        }else{
                            getActivity().setResult(BROWSE_ACTIVITY_RESULT_OK);
                            getActivity().finish();
                        }
                        break;
                    case IConstant.DELETE_BROWSE_FILE:
                        if(isDeleting){
                            return;
                        }
                        if(isSelecting){
                            AlertView deleteAlertDialog = new AlertView(null, null, getString(R.string.operation_cancel),
                                    new String[]{getString(R.string.delete_file)}, null, getActivity(), AlertView.Style.ActionSheet, deleteOnItemClick);
                            deleteAlertDialog.show();
                        }else{
                            if(null != recordFileInfoList){
                                if(recordFileInfoList.size() == 0){
                                    showToastLong(R.string.ftp_data_null);
                                }else{
                                    showToastShort(getString(R.string.select_err));
                                }
                            }
                        }
                        break;
                }
            }else if(action.equals(IAction.ACTION_CHANGE_FRAGMENT)){
                if(getActivity() == null || !isAdded()
                        || !(mApplication.getCurrentFragment() instanceof LocalBrowseVideoFragment)){
                    return;
                }
                isSelecting = false;
                isAllSelect = false;
                choiceBtn.setText(getString(R.string.operation_choice));
                backBtn.setText(getString(R.string.return_back));
                selectTitle.setVisibility(View.GONE);
                if(selectedList != null){
                    selectedList.clear();
                }
                if(selectFileInfoMap != null){
                    selectFileInfoMap.clear();
                }
                sendBroadcastToUI(1);
                handler.sendMessage(handler.obtainMessage(MSG_UPDATE_LIST));
                isDeleting = false;
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
        View view = inflater.inflate(R.layout.fragment_record_video, container, false);
        mRefreshListView = (RefreshListView) view.findViewById(R.id.record_video_list_view);
        selectTitle = (TextView) view.findViewById(R.id.record_video_select_text);
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
//        mediaConverter = MediaConverter.getInstance(getActivity().getApplicationContext());

        if(service == null){
            service = Executors.newSingleThreadExecutor();
        }
        if(scanFilesHelper != null){
            scanFilesHelper = new ScanFilesHelper(getActivity().getApplicationContext());
        }


        appFilePath = AppUtil.splicingFilePath(mApplication.getAppName(), null, null, null);
        defaultPath = AppUtil.getAppStoragePath(mApplication, RECORD, isRearViewBrowsing);
        handler.sendMessage(handler.obtainMessage(MSG_UPDATE_LIST));

        choiceBtn = (CustomTextView) getActivity().findViewById(R.id.selection);
        backBtn = (CustomTextView) getActivity().findViewById(R.id.back);

        mRefreshListView.setOnItemClickListener(mListViewOnItemClick);
        mRefreshListView.setPullLoadEnable(true);
        mRefreshListView.setPullRefreshEnable(false);
        mRefreshListView.setXListViewListener(this);
    }

    private AdapterView.OnItemClickListener mListViewOnItemClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if(mBrowseAdapter == null || isDeleting ||  position > mBrowseAdapter.getCount()){
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
                Dbug.e(tag, " fileInfo is null !");
                return;
            }
            if(isSelecting){
                String selectName = fileInfo.getTitle();
                if(TextUtils.isEmpty(selectName)){
                    Dbug.e(tag, " selectName is null !");
                    return;
                }
                if(selectedList != null && selectFileInfoMap != null){
                    if(selectedList.size() > 0){
                        for(String s : selectedList){
                            if(s.equals(selectName)){
                                selectedList.remove(selectName);
                                selectFileInfoMap.remove(selectName);
                                mBrowseAdapter.notifyDataSetChanged();
                                String changeStr = getString(R.string.selected) + selectedList.size() +
                                        getString(R.string.separator) + recordFileInfoList.size() + getString(R.string.files);
                                selectTitle.setVisibility(View.VISIBLE);
                                selectTitle.setText(changeStr);
                                return;
                            }
                        }
                        selectedList.add(selectName);
                        selectFileInfoMap.put(selectName, fileInfo);
                    }else{
                        selectedList.add(selectName);
                        selectFileInfoMap.put(selectName, fileInfo);
                    }
                }
                String changeStr = getString(R.string.selected) + selectedList.size()+
                        getString(R.string.separator)+ recordFileInfoList.size() + getString(R.string.files);
                selectTitle.setVisibility(View.VISIBLE);
                selectTitle.setText(changeStr);
                mBrowseAdapter.notifyDataSetChanged();
            }else{
                if(!mApplication.getIsOffLineMode()){
                    if(mApplication.getDeviceUUID() == null){
                        return;
                    }
                    browseResources(fileInfo);
                }else{
                    browseResources(fileInfo);
                }
            }
        }
    };

    private OnItemClickListener deleteOnItemClick = new OnItemClickListener() {
        @Override
        public void onItemClick(Object o, int i) {
            switch (i){
                case -1: // cancel alertView
                    break;
                case 0: // delete files
                    if(selectedList.size() > 0){
                        isTaskOpen = true;
                        isDeleting = true;
                        deleteThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                dealWithTask(false);
                            }
                        });
                        deleteThread.start();
                        sendBroadcastToUI(0);
                    }else{
                        showToastShort(getString(R.string.select_err));
                    }
                    break;
            }
        }
    };

    private void sendBroadcastToUI(int mode){
        if(getActivity() != null){
            Intent intent = new Intent(IAction.ACTION_UPDATE_LOCAL_FILES_UI);
            intent.putExtra(IConstant.LOCAL_FILES_UI, mode);
            getActivity().sendBroadcast(intent);
        }
    }

    private boolean browseResources(FileInfo info){
        try{
            if(getActivity() == null || info == null){
                Dbug.e(tag, " browseResources  parameter is empty!");
                return false;
            }
            String filename = info.getTitle();
            String filepath = info.getPath();
            if(TextUtils.isEmpty(filename) || TextUtils.isEmpty(filepath)){
                Dbug.e(tag, " filename is empty or filepath is empty!");
                return false;
            }
            File file = new File(filepath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if(!filename.isEmpty() &&(filename.endsWith(".png")
                    || filename.endsWith(".PNG") || filename.endsWith(".JPEG")
                    || filename.endsWith(".jpeg")||filename.endsWith(".jpg")
                    || filename.endsWith(".JPG"))) {
                if(!file.exists()){
                    showToastShort(getString(R.string.browse_file_err));
                    return false;
                }
                intent.setDataAndType(Uri.parse("file://" + file.getPath()), "image/*");
            }else if(!filename.isEmpty() &&(filename.endsWith(".mov")
                    || filename.endsWith(".MOV") || filename.endsWith(".mp4")
                    || filename.endsWith(".MP4")|| filename.endsWith(".avi")
                    || filename.endsWith(".AVI"))){
                if(!file.exists()){
                    showToastShort(getString(R.string.browse_file_err));
                    return false;
                }
                String stend = "";
                if(filename.endsWith(".mov") || filename.endsWith(".MOV")){
                    stend = "mov";
                }else if(filename.endsWith(".avi") || filename.endsWith(".AVI")){
                    stend = "avi";
                }else if(filename.endsWith(".mp4") || filename.endsWith(".MP4")){
                    stend = "mp4";
                }
                intent.setDataAndType(Uri.parse("file://" + file.getPath()), "video/" + stend);
                Dbug.e(tag, " browse path : file://" + file.getPath());
            }else{
                showToastShort(getString(R.string.open_file_err));
            }
            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivity(intent);
                mApplication.setIsBrowsing(true);
                return true;
            }
        }catch (Exception e){
            Dbug.e(tag, " error  " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        initListAndMap();
    }

    @Override
    public void onDestroy() {
        Dbug.e(tag, "== onDestroy == ");
        isSelecting = false;
        isDeleting = false;
        isTaskOpen = false;
        if(notifyDialog != null){
            if(notifyDialog.isShowing()){
                notifyDialog.dismiss();
            }
            notifyDialog = null;
        }
        if(scanFilesHelper != null){
            scanFilesHelper.release();
            scanFilesHelper = null;
        }
        if(deleteThread != null && deleteThread.isAlive()){
            deleteThread.interrupt();
            deleteThread = null;
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
        if(handler != null){
            handler.removeCallbacksAndMessages(null);
        }
        if(mReceiver != null && getActivity() != null){
            getActivity().getApplicationContext().unregisterReceiver(mReceiver);
        }
        destroyListAndMap();
        super.onDestroy();
    }

    private void initListAndMap(){
        if(allDataInfoList ==  null){
            allDataInfoList = new ArrayList<>();
        }
        if(recordFileInfoList == null){
            recordFileInfoList = new ArrayList<>();
        }
        if(tempList == null){
            tempList = new ArrayList<>();
        }
        if(durationMap == null){
            durationMap = new HashMap<>();
        }
        if(selectedList == null){
            selectedList = new ArrayList<>();
        }
        if(selectFileInfoMap == null){
            selectFileInfoMap = new HashMap<>();
        }
    }

    private void destroyListAndMap(){
        if(allDataInfoList !=  null){
            allDataInfoList.clear();
            allDataInfoList = null;
        }
        if(recordFileInfoList != null){
            recordFileInfoList.clear();
            recordFileInfoList = null;
        }
        if(tempList != null){
            tempList.clear();
            tempList = null;
        }
        if(durationMap != null){
            durationMap.clear();
            durationMap = null;
        }
        if(selectedList != null){
            selectedList.clear();
            selectedList = null;
        }
        if(selectFileInfoMap != null){
            selectFileInfoMap.clear();
            selectFileInfoMap = null;
        }
        System.gc();
    }

    private void onLoad() {
        mRefreshListView.stopRefresh();
        mRefreshListView.stopLoadMore();
        mRefreshListView.setRefreshTime(TimeFormater.formatYMDHMS(System
                .currentTimeMillis()));
    }

    private void onFinish(Boolean isShow) {
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
    }

    @Override
    public void onLoadMore() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (tempList == null || allDataInfoList == null || allDataInfoList.size() == 0
                        || recordFileInfoList == null || recordFileInfoList.size() == 0) {
                    onFinish(false);
                    return;
                }
                if (tempList.size() >= 0) {
                    int tempLen = mBrowseAdapter.getCount();
                    int length = recordFileInfoList.size() - tempLen;
                    if (length < 0) {
                        onFinish(false);
                        return;
                    }
                    tempList.clear();
                    if (length >= PAGE_ITEM_NUM) {
                        for (int i = 0; i < PAGE_ITEM_NUM; i++) {
                            if (tempLen + i < recordFileInfoList.size()) {
                                tempList.add(recordFileInfoList.get(tempLen + i));
                            }
                        }
                        onLoad();
                    } else {
                        for (int i = 0; i < length; i++) {
                            if (tempLen + i < recordFileInfoList.size()) {
                                tempList.add(recordFileInfoList.get(tempLen + i));
                            }
                        }
                        if (recordFileInfoList.size() < PAGE_ITEM_NUM) {
                            onFinish(false);
                        } else {
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

    private List<FileInfo> selectTypeList(List<FileInfo> drsList){
        List<FileInfo> resultList = new ArrayList<>();
        if(null == drsList){
            return resultList;
        }
        for (int i = 0; i < drsList.size(); i++){
            FileInfo info = drsList.get(i);
//            String filename = info.getTitle();
            resultList.add(info);
//            if(!TextUtils.isEmpty(filename) && (filename.endsWith(".avi")|| filename.endsWith(".AVI")
//                    || filename.endsWith(".mov") ||filename.endsWith(".MOV")
//                    || filename.endsWith(".mp4") || filename.endsWith(".MP4"))){
//                resultList.add(info);
//            }
        }
        selectTitle.setVisibility(View.GONE);
        isSelecting = false;
        isAllSelect = false;
        choiceBtn.setText(getString(R.string.operation_choice));
        if(selectedList != null){
            selectedList.clear();
        }
        if(selectFileInfoMap != null){
            selectFileInfoMap.clear();
        }
        return resultList;
    }

    private void initializationData(List<FileInfo> drsList){
        Dbug.e(tag, " - initializationData- start!");
        if(drsList == null || getActivity() == null || allDataInfoList == null ){
            onFinish(false);
            return;
        }
        if(drsList.size() == 0){
//            showToastLong(getString(R.string.ftp_data_null));
            onFinish(false);
            if(mBrowseAdapter != null){
                mBrowseAdapter.clear();
                mBrowseAdapter.addAll(drsList);
                mRefreshListView.setAdapter(mBrowseAdapter);
                mBrowseAdapter.notifyDataSetChanged();
            }
            return;
        }
        if (tempList == null) {
            tempList = new ArrayList<>();
        }
        if (tempList.size() > 0) {
            tempList.clear();
        }
        if (drsList.size() >= PAGE_ITEM_NUM) {
            for (int i = 0; i < PAGE_ITEM_NUM; i++) {
                tempList.add(drsList.get(i));
            }
            onLoad();
        } else {
            for (int i = 0; i < drsList.size(); i++) {
                tempList.add(drsList.get(i));
            }
            onFinish(false);
        }
        if (mBrowseAdapter == null) {
            mBrowseAdapter = new BrowseAdapter(getActivity());
            mBrowseAdapter.addAll(tempList);
            mRefreshListView.setAdapter(mBrowseAdapter);
            mBrowseAdapter.notifyDataSetChanged();
        } else if (tempList.size() >= 0 && mBrowseAdapter != null) {
            mBrowseAdapter.clear();
            mBrowseAdapter.addAll(tempList);
            mRefreshListView.setAdapter(mBrowseAdapter);
            mBrowseAdapter.notifyDataSetChanged();
        }
    }

    private synchronized void dealWithTask(boolean result) {
        if(selectFileInfoMap == null || selectedList == null){
            Dbug.e(tag, "dealWithTask : selectFileInfoMap or  selectedList is null! ");
            return;
        }
        if(isTaskOpen){
            if(result){
                if(selectedList.size() > 0 ){
                    String lastName = selectedList.get(0);
                    selectFileInfoMap.remove(lastName);
                    selectedList.remove(0);
                }
            }else {
                failureTimes--;
                if (failureTimes <= 0) {
                    if(selectedList.size() > 0){
                        String lastName = selectedList.get(0);
                        selectFileInfoMap.remove(lastName);
                        selectedList.remove(0);
                    }
                    failureTimes = 3;
                }
            }
            if(selectedList.size() > 0) {
                final String str = selectedList.get(0);
                if (!TextUtils.isEmpty(str) && null != selectFileInfoMap.get(str)) {
                    final String localPath = selectFileInfoMap.get(str).getPath();
                    Dbug.e(tag, " localPath = " + localPath);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            String changeTxt = getString(R.string.selected) + selectedList.size() +
                                    getString(R.string.separator) + recordFileInfoList.size() + getString(R.string.files);
                            selectTitle.setVisibility(View.VISIBLE);
                            selectTitle.setText(changeTxt);
                            File deleteFile = new File(localPath);
                            if (deleteFile.exists()) {
                                if (deleteFile.delete()) {
                                    Dbug.e(tag, " deleteFile success ! filename -> " + str);
                                    if(scanFilesHelper != null){
                                        scanFilesHelper.updateToDeleteFile(localPath);
                                    }
                                    if(str.endsWith(".avi") || str.endsWith(".AVI")) {
                                        String thumbPath = localPath.substring(0, localPath.lastIndexOf("/")) + File.separator + SUB_THUMB;
                                        String thumbName = BufChangeHex.getVideoThumb(str, thumbPath);
                                        thumbPath = thumbPath + File.separator + thumbName;
                                        File thumb = new File(thumbPath);
                                        if (thumb.exists()) {
                                            if (thumb.delete()) {
                                                Dbug.e(tag, " thumb success ! ");
                                            }
                                        }
                                    }
                                    dealWithTask(true);
                                } else {
                                    Dbug.e(tag, " deleteFile failed ! ");
                                    dealWithTask(false);
                                }
                            } else {
                                Dbug.e(tag, " deleteFile is not exist ! ");
                                dealWithTask(true);
                            }
                        }
                    });
                }else{
                    Dbug.e(tag, "param is null!");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dealWithTask(true);
                        }
                    });
                }
            }else{
                isTaskOpen = false;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        backBtn.performClick();
                    }
                });
            }
        }else{
            if(selectedList.size() > 0){
                selectedList.clear();
            }
            sendBroadcastToUI(1);
        }
    }

    private void getPicture(final ImageView imageView, final FileInfo info){
        if(imageView == null || info == null){
            Dbug.d(tag, "-getPicture- parameter is null.");
            return;
        }
        final String name = info.getTitle();
        final String path = info.getPath();
        future = service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    File oldFile = new File(path);
                    if (oldFile.exists()) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = false;
                        options.inSampleSize = 10;
                        final Bitmap newBitmap = BitmapFactory.decodeFile(path, options);
                        if (newBitmap != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null) {
                                        mApplication.removeThumbPath(name);
                                        mApplication.addThumbPath(name, path);
                                        mApplication.addBitmapInCache(newBitmap, path);
                                    }
                                    imageView.setImageBitmap(newBitmap);
                                }
                            });
                        } else {
                            if (oldFile.delete()) {
                                Dbug.e(tag, "download image is not opened, so delete this image.");
                            }
                        }
                    }
                } catch (Exception e) {
                    Dbug.e(tag, "err =" + e.getMessage());
                    e.printStackTrace();
                }
            }
        },name);
    }

    private void getVideoBitmap(final ImageView imageView, final TextView textView, final FileInfo info) {
        if (imageView == null || textView == null || info == null) {
            Dbug.d(tag, "-getVideoBitmap- parameter is null.");
            return;
        }
        final String filepath = info.getPath();
        final String name = info.getTitle();
        String recordPath = "";
        if (filepath.contains("/")) {
            recordPath = filepath.substring(0, filepath.lastIndexOf("/"));
        }
        final String recordThumbPath = recordPath + File.separator + SUB_THUMB;
        File file = new File(recordThumbPath);
        if(!file.exists()){
            if(file.mkdir()){
                Dbug.w(tag, " recordThumbPath ok !");
            }
        }
        future = service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    File newFile = new File(filepath);
                    if (newFile.exists()) {
                        try {
                            String thumbName = "";
                            if (name.contains(".")) {
                                thumbName = BufChangeHex.getVideoThumb(name, recordThumbPath);
                            }
                            final String thumbPath = recordThumbPath + File.separator + thumbName;
                            File thumb = new File(thumbPath);
                            if (thumb.exists()) {
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inJustDecodeBounds = false;
                                options.inSampleSize = 10;
                                final Bitmap thumbBitmap = BitmapFactory.decodeFile(thumbPath, options);
                                if (thumbBitmap != null) {
                                    handler.post(new Runnable() {
                                                     @Override
                                                     public void run() {
                                                         if (mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null) {
                                                             mApplication.removeThumbPath(name);
                                                             mApplication.addThumbPath(name, thumbPath);
                                                             mApplication.addBitmapInCache(thumbBitmap, thumbPath);
                                                         }
                                                         imageView.setImageBitmap(thumbBitmap);
                                                         if (durationMap != null) {
                                                             String durationStr = BufChangeHex.getVideoDuration(thumbPath);
                                                             if (null == durationMap.get(name)) {
                                                                 if (durationStr != null) {
                                                                     durationMap.put(name, TimeFormater.getTimeFormatValue(Long.valueOf(durationStr)));
                                                                 } else {
                                                                     durationMap.put(name, TimeFormater.getTimeFormatValue(0));
                                                                 }
                                                             }
                                                             textView.setText(durationMap.get(name));
                                                         }
                                                     }
                                                 }
                                    );
                                } else {
                                    if (thumb.delete()) {
                                        Dbug.w(tag, " thumb is null, so delete it!");
                                    }
                                    Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(filepath, MediaStore.Video.Thumbnails.MINI_KIND);
                                    final Bitmap newBitmap = ThumbnailUtils.extractThumbnail(bitmap, 120, 70);
                                    if (newBitmap != null) {
                                        handler.post(new Runnable() {
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
                                    }
                                    Dbug.d(tag, "the bitmap is null");
                                }
                            } else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        String thumbPath = recordThumbPath + File.separator + name;
                                        if (AppUtil.getRecordVideoThumb(info, thumbPath)) {
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (mBrowseAdapter != null) {
                                                        mBrowseAdapter.notifyDataSetChanged();
                                                    }
                                                }
                                            });
                                        }
                                    }
                                }).start();
                            }
                        } catch (Exception e) {
                            Dbug.e(tag, "Exception --> " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    Dbug.e(tag, "err =" + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, name);
    }

    private class BrowseAdapter extends ArrayAdapter<FileInfo> {
        private LayoutInflater mLayoutInflater;
        private ViewHolder holder;

        BrowseAdapter(Context context) {
            super(context, 0);
            mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return super.getCount();
        }

        @Override
        public FileInfo getItem(int position) {
            return super.getItem(position);
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
//            String showText;
//            if(filename.length() >= 15){
//                showText = filename.substring(0, 10) + "...";
//            }else{
//                showText = filename;
//            }
            if (item.isDirectory()){
                holder.fileName.setText(filename);
                holder.fileThumb.setImageResource(R.mipmap.ic_directory);
            } else {
                holder.fileName.setText(filename);
                holder.fileSize.setText(DataCleanManager.getFormatSize((double) item.getSize()));
                holder.fileDuration.setVisibility(View.GONE);
                holder.fileTime.setText(item.getCreateDate());
                holder.fileState.setVisibility(View.GONE);
                if(!TextUtils.isEmpty(filename) &&(filename.endsWith(".png")
                        || filename.endsWith(".PNG") || filename.endsWith(".JPEG")
                        || filename.endsWith(".jpeg")|| filename.endsWith(".jpg")
                        || filename.endsWith(".JPG"))){
                    //deal with picture
                    holder.fileThumb.setImageResource(R.mipmap.image_default_icon);
                    if(mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null){
                        if(mApplication.getBitmapCacheCount() > 0 && mApplication.getThumbPathMapSize() > 0){
                            String thumbPath = mApplication.getThumbPath(filename);
                            if(!TextUtils.isEmpty(thumbPath)){
                                Bitmap bitmap = mApplication.getBitmapInCache(thumbPath);
                                if(null != bitmap){
                                    holder.fileThumb.setImageBitmap(bitmap);
                                }else{
                                    getPicture(holder.fileThumb, item);
                                }
                            }else{
                                getPicture(holder.fileThumb, item);
                            }
                        }else{
                            getPicture(holder.fileThumb, item);
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
                    if(durationMap != null){
                        if(durationMap.size() > 0 ){
                            if(null != durationMap.get(filename)){
                                holder.fileDuration.setText(durationMap.get(filename));
                            }
                        }
                    }
                    if(mApplication.getBitmapCache() != null && mApplication.getThumbPathMap() != null) {
                        Dbug.e(tag, "bitmap cache size : " + mApplication.getBitmapCacheCount() + " ,thumbPathMap size = " + mApplication.getThumbPathMapSize());
                        if (mApplication.getBitmapCacheCount() > 0 && mApplication.getThumbPathMapSize() > 0) {
                            String thumbPath = mApplication.getThumbPath(filename);
                            if(!TextUtils.isEmpty(thumbPath)){
                                Bitmap bitmap = mApplication.getBitmapInCache(thumbPath);
                                if(null != bitmap){
                                    holder.fileThumb.setImageBitmap(bitmap);
                                }else{
                                    getVideoBitmap(holder.fileThumb, holder.fileDuration, item);
                                }
                            }else{
                                getVideoBitmap(holder.fileThumb, holder.fileDuration, item);
                            }
                        }else{
                            getVideoBitmap(holder.fileThumb, holder.fileDuration, item);
                        }
                    }
                }else{
                    holder.fileThumb.setImageResource(R.mipmap.ic_file);
                }
            }
            if(isSelecting){
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
