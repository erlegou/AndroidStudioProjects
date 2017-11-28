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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * class name: LocalBrowseFragment
 * function : browse local download resource
 * @author JL
 * create time : 2016-01-13 10:20
 * version : v1.0
 * ///////////////////////////////////////////////////////////////////////////
 *
 */
public class LocalBrowseVideoFragment extends BaseFragment implements RefreshListView.IXListViewListener, IConstant{

    private String tag = getClass().getSimpleName();
    private RefreshListView mRefreshListView;
    private BrowseAdapter mBrowseAdapter;
    private TextView selectTitle;
    private CustomTextView choiceBtn;
    private CustomTextView backBtn;

    private ExecutorService service = null;
    private Future<String> future = null;
    private NotifyDialog notifyDialog;

    private List<String> selectedList = null;
    private Map<String, FileInfo> selectFileInfoMap = null;
    private List<FileInfo> allDataInfoList = null;
    private List<FileInfo> localFileInfoList = null;
    private List<FileInfo> tempList = null;
    private Map<String, String> durationMap = null;

    private boolean isChoicing = false;
    private boolean isDeleting = false;
    private boolean isAllSelect = false;
    private boolean isTaskOpen = false;
    private String defaultPath;
    private String appFilePath;
    private int failureTimes = 3;
    private Thread deleteThread;
    private ScanFilesHelper scanFilesHelper;

    private final int PAGE_ITEM_NUM = 7;
    private final int MSG_UPDATE_LIST = 0x01;
    private String mWhichDir;
    private boolean isRearViewBrowsing = false;

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(getActivity() == null || !isAdded() || getActivity().isFinishing()){
                Dbug.e(tag, "Activity is finishing, so handler can not to do any thing.");
                return false;
            }
            switch (msg.what){
                case MSG_UPDATE_LIST:
                    if(mApplication.getIsOffLineMode()){
                        allDataInfoList = AppUtil.getAllLocalFile(appFilePath, DOWNLOAD, true);
                    }else{
                        allDataInfoList = AppUtil.getLocalFileInfo(defaultPath, true);
                    }
                    if(localFileInfoList != null && localFileInfoList.size() > 0){
                        localFileInfoList.clear();
                    }
                    localFileInfoList = selectTypeList(allDataInfoList);
                    initializationData(localFileInfoList);
                    selectTitle.setVisibility(View.GONE);
                    break;
            }
            return false;
        }
    });

    public LocalBrowseVideoFragment() {
        // Required empty public constructor
    }

    public static LocalBrowseVideoFragment newInstance(String which) {
        LocalBrowseVideoFragment fragment = new LocalBrowseVideoFragment();
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
                        || !(mApplication.getCurrentFragment() instanceof LocalBrowseVideoFragment)){
                    return;
                }
                switch (operationType){
                    case IConstant.SELECT_BROWSE_FILE:
                        if(choiceBtn.getText().toString().equals(getString(R.string.operation_choice))) {
                            if (!isChoicing) {
                                if(null != localFileInfoList && localFileInfoList.size() == 0){
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
                                if(selectFileInfoMap != null){
                                    selectFileInfoMap.clear();
                                }
                                if(localFileInfoList != null){
                                    String showTxt = getString(R.string.selected) + selectedList.size() +
                                            getString(R.string.separator) + localFileInfoList.size() + getString(R.string.files);
                                    selectTitle.setVisibility(View.VISIBLE);
                                    selectTitle.setText(showTxt);
                                }
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
                                if(selectFileInfoMap.size() > 0){
                                    selectFileInfoMap.clear();
                                }
                                if(localFileInfoList != null) {
                                    String valueStr = getString(R.string.selected) + selectedList.size() +
                                            getString(R.string.separator) + localFileInfoList.size() + getString(R.string.files);
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
                                if(null != localFileInfoList){
                                    for (int i = 0; i < localFileInfoList.size(); i++){
                                        FileInfo info = localFileInfoList.get(i);
                                        if(info != null){
                                            String fileName = info.getTitle();
                                            if(!TextUtils.isEmpty(fileName)){
                                                selectedList.add(fileName);
                                                selectFileInfoMap.put(fileName, info);
                                            }
                                        }
                                    }
                                    String str = getString(R.string.selected) + selectedList.size() +
                                            getString(R.string.separator) + localFileInfoList.size() + getString(R.string.files);
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
                        if(isChoicing){
                            isChoicing = false;
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
                        if(isChoicing){
                            AlertView deleteAlertDialog = new AlertView(null, null, getString(R.string.operation_cancel),
                                    new String[]{getString(R.string.delete_file)}, null, getActivity(), AlertView.Style.ActionSheet, deleteOnItemClick);
                            deleteAlertDialog.show();
                        }else{
                            if(null != localFileInfoList){
                                if(localFileInfoList.size() == 0){
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
                        || !(mApplication.getCurrentFragment() instanceof LocalBrowseVideoFragment)){
                    return;
                }
                isChoicing = false;
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
        View view = inflater.inflate(R.layout.fragment_local_video, container, false);
        mRefreshListView = (RefreshListView) view.findViewById(R.id.local_brows_list_view);
        selectTitle = (TextView) view.findViewById(R.id.local_select_text);
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
        if(scanFilesHelper == null){
            scanFilesHelper = new ScanFilesHelper(getActivity().getApplicationContext());
        }

        appFilePath = AppUtil.splicingFilePath(mApplication.getAppName(), null, null, null);
        defaultPath = AppUtil.getAppStoragePath(mApplication, DOWNLOAD, isRearViewBrowsing);
        handler.sendMessage(handler.obtainMessage(MSG_UPDATE_LIST));

        choiceBtn = (CustomTextView) getActivity().findViewById(R.id.selection);
        backBtn = (CustomTextView) getActivity().findViewById(R.id.back);

        mRefreshListView.setOnItemClickListener(mListViewOnItemClick);
        mRefreshListView.setPullLoadEnable(true);
        mRefreshListView.setPullRefreshEnable(false);
        mRefreshListView.setXListViewListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("fragment_tag", tag);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        initListAndMap();
    }

    @Override
    public void onDestroy() {
        Dbug.e(tag, "== onDestroy == ");
        isChoicing = false;
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

    private List<FileInfo> selectTypeList(List<FileInfo> drsList){
        List<FileInfo> resultList = new ArrayList<>();
        if(null == drsList || drsList.size() == 0){
            return resultList;
        }
        for (int i = 0; i < drsList.size(); i++){
            FileInfo info = drsList.get(i);
            String filename = info.getTitle();
            Dbug.e(tag, " filename : " + filename);
            if(!TextUtils.isEmpty(filename) && (filename.endsWith(".avi")|| filename.endsWith(".AVI")
                    || filename.endsWith(".mov") ||filename.endsWith(".MOV")
                    || filename.endsWith(".mp4") || filename.endsWith(".MP4"))){
                resultList.add(info);
            }
        }
        selectTitle.setVisibility(View.GONE);
        isChoicing = false;
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
            FileInfo info = null;
            if(position < mBrowseAdapter.getCount()){
                info = mBrowseAdapter.getItem(position);
            }
            if(info == null){
                Dbug.e(tag, "info is null !");
                return;
            }
            if(isChoicing){
                String selectName = "";
                if(mBrowseAdapter.getCount() > 0 && position < mBrowseAdapter.getCount()) {
                    selectName = info.getTitle();
                }
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
                                        getString(R.string.separator) + localFileInfoList.size() + getString(R.string.files);
                                selectTitle.setVisibility(View.VISIBLE);
                                selectTitle.setText(changeStr);
                                return;
                            }
                        }
                        selectedList.add(selectName);
                        selectFileInfoMap.put(selectName, info);
                    }else{
                        selectedList.add(selectName);
                        selectFileInfoMap.put(selectName, info);
                    }
                }
                String changeStr = getString(R.string.selected)+selectedList.size()+
                        getString(R.string.separator)+ localFileInfoList.size() +getString(R.string.files);
                selectTitle.setVisibility(View.VISIBLE);
                selectTitle.setText(changeStr);

                mBrowseAdapter.notifyDataSetChanged();
            }else{
                if(!mApplication.getIsOffLineMode()){
                    if(mApplication.getDeviceUUID() == null){
                        return;
                    }
                    String filename = info.getTitle();
                    if (!filename.isEmpty()) {
                        String  newFilename = BufChangeHex.combinDataStr(filename, recoveryTimeField(info.getCreateDate()));
                        String localPath = defaultPath + File.separator + newFilename;
                        browseResources(filename, localPath);
                    }
                }else{
                    String fileName = info.getTitle();
                    String localPath = info.getPath();
                    browseResources(fileName, localPath);
                }
            }
        }
    };

    private void browseResources(String filename, String filepath){
        try{
            if(getActivity() == null){
                return;
            }
            File file = new File(filepath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if(!TextUtils.isEmpty(filename) &&(filename.endsWith(".png")
                    || filename.endsWith(".PNG") || filename.endsWith(".JPEG")
                    || filename.endsWith(".jpeg")||filename.endsWith(".jpg")
                    || filename.endsWith(".JPG"))) {
                if(!file.exists()){
                    showToastShort(getString(R.string.browse_file_err));
                    return;
                }
                intent.setDataAndType(Uri.parse("file://" + file.getPath()), "image/*");
            }else if(!TextUtils.isEmpty(filename) &&(filename.endsWith(".mov")
                    || filename.endsWith(".MOV") || filename.endsWith(".mp4")
                    || filename.endsWith(".MP4")|| filename.endsWith(".avi")
                    || filename.endsWith(".AVI"))){
                if(!file.exists()){
                    showToastShort(getString(R.string.browse_file_err));
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
                Dbug.e(tag, " browse path : file://" + file.getPath());
            }else{
                showToastShort(getString(R.string.open_file_err));
            }
            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivity(intent);
                mApplication.setIsBrowsing(true);
            }
        }catch (Exception e){
            Dbug.e(tag, " error  " + e.getMessage());
            e.printStackTrace();
        }
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
                if(tempList == null || allDataInfoList == null || allDataInfoList.size() == 0
                         || localFileInfoList == null || localFileInfoList.size() == 0){
                    onFinish(false);
                    return;
                }
                if (tempList.size() >= 0) {
                    int tempLen = mBrowseAdapter.getCount();
                    int length = localFileInfoList.size() - tempLen;
                    if (length < 0) {
                        onFinish(false);
                        return;
                    }
                    tempList.clear();
                    if (length >= PAGE_ITEM_NUM) {
                        for (int i = 0; i < PAGE_ITEM_NUM; i++) {
                            if (tempLen + i < localFileInfoList.size()) {
                                tempList.add(localFileInfoList.get(tempLen + i));
                            }
                        }
                        onLoad();
                    } else {
                        for (int i = 0; i < length; i++) {
                            if (tempLen + i < localFileInfoList.size()) {
                                tempList.add(localFileInfoList.get(tempLen + i));
                            }
                        }
                        if(localFileInfoList.size() < PAGE_ITEM_NUM){
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

    private void initListAndMap(){
        if(localFileInfoList == null){
            localFileInfoList = new ArrayList<>();
        }
        if(tempList == null){
            tempList = new ArrayList<>();
        }
        if(selectedList == null){
            selectedList = new ArrayList<>();
        }
        if(selectFileInfoMap == null){
            selectFileInfoMap = new HashMap<>();
        }
        if(allDataInfoList == null){
            allDataInfoList = new ArrayList<>();
        }
        if(durationMap == null){
            durationMap = new HashMap<>();
        }
    }

    private void destroyListAndMap(){
        if(localFileInfoList != null){
            localFileInfoList.clear();
            localFileInfoList = null;
        }
        if(tempList != null){
            tempList.clear();
            tempList = null;
        }
        if(null != selectedList){
            selectedList.clear();
            selectedList = null;
        }
        if(null != selectFileInfoMap){
            selectFileInfoMap.clear();
            selectFileInfoMap = null;
        }
        if(null != durationMap){
            durationMap.clear();
            durationMap = null;
        }
        System.gc();
    }

    private void initializationData(List<FileInfo> drsList){
        Dbug.e(tag, " - initializationData- start!");
        if(drsList == null || getActivity() == null || allDataInfoList == null ){
            onFinish(false);
            return;
        }
        if(drsList.size() == 0){
//            showToastShort(getString(R.string.ftp_data_null));
            onFinish(false);
            if(mBrowseAdapter != null){
                mBrowseAdapter.clear();
                mBrowseAdapter.addAll(drsList);
                mRefreshListView.setAdapter(mBrowseAdapter);
                mBrowseAdapter.notifyDataSetChanged();
            }
            return;
//            if(!mApplication.getIsOffLineMode()) {
//                if (mApplication.isSdcardState()) {
//                    if (allDataInfoList.size() == 0) {
//                        Intent intent = new Intent(IAction.ACTION_SELECT_BROWSE_MODE);
//                        intent.putExtra(IConstant.BROWSE_FRAGMENT_TYPE, 0);
//                        getActivity().sendBroadcast(intent);
//                        return;
//                    }
//                }
//            }
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

    private String recoveryTimeField(String drs){
        String result = null;
        if(drs == null || drs.isEmpty()){
            return null;
        }
        if(drs.length() >= 19){
            result = drs.substring(0, 4) + drs.substring(5, 7) + drs.substring(8, 10) +drs.substring(11, 13)
                    +drs.substring(14, 16) + drs.substring(17, 19);
        }
        return result;
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

    private synchronized void dealWithTask(boolean result) {
        if(selectFileInfoMap == null || selectedList == null){
            Dbug.e(tag, "dealWithTask : selectFileInfoMap or  selectedList is null! ");
            return;
        }
        if(isTaskOpen){
            if(result){
                if(selectedList.size() > 0){
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
                    final String downloadPath = localPath.substring(0, localPath.lastIndexOf("/"));
                    String thumbDirPath = downloadPath + File.separator + SUB_THUMB;
                    String thumbName = BufChangeHex.getVideoThumb(str, thumbDirPath);
                    final String thumbPath = thumbDirPath +File.separator + thumbName;
                    Dbug.e(tag, " localPath = " + localPath);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            String changeTxt = getString(R.string.selected) + selectedList.size() +
                                    getString(R.string.separator) + localFileInfoList.size() + getString(R.string.files);
                            selectTitle.setVisibility(View.VISIBLE);
                            selectTitle.setText(changeTxt);
                            File deleteFile = new File(localPath);
                            if (deleteFile.exists()) {
                                if (deleteFile.delete()) {
                                    Dbug.e(tag, " deleteFile success ! filename -> " + str);
                                    if(scanFilesHelper != null){
                                        scanFilesHelper.updateToDeleteFile(localPath);
                                    }
                                    if(str.endsWith(".avi") || str.endsWith(".AVI")){
                                        File thumb = new File(thumbPath);
                                        if (thumb.exists()) {
                                            if (thumb.delete()) {
                                                Dbug.e(tag, " thumb success ! thumbPath  = " +thumbPath);
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
                    Dbug.e(tag, " param is null! ");
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

    private void getPicture(final ImageView imageView, final String name,final String path){
        if(imageView == null || name == null || name.isEmpty()){
            Dbug.d(tag, "parameter is null.");
            return;
        }
        future = service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String downloadPath = path.substring(0, path.lastIndexOf("/"));
                    String filename = BufChangeHex.getVideoThumb(name, downloadPath);
                    final String filepath = downloadPath + "/" + filename;
                    File oldFile = new File(filepath);
                    if (oldFile.exists()) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = false;
                        options.inSampleSize = 10;
                        final Bitmap newBitmap = BitmapFactory.decodeFile(filepath, options);
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

    private void getVideoBitmap(final ImageView imageView, final TextView textView, final FileInfo info){
        if(imageView == null || textView == null || info == null){
            Dbug.d(tag, "parameter is null.");
            return;
        }
        final String path = info.getPath();
        final String name = info.getTitle();
        future = service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String downloadPath = path.substring(0, path.lastIndexOf("/"));
                    String filename = BufChangeHex.getVideoThumb(name, downloadPath);
                    final String filepath = downloadPath + "/" + filename;
                    File newFile = new File(filepath);
                    Dbug.e(tag, " filepath : " +filepath);
                    if (newFile.exists()) {
                        try {
                            String thumbFolderPath = downloadPath + File.separator +SUB_THUMB;
                            String thumbFileName = BufChangeHex.getVideoThumb(name, thumbFolderPath);
                            if(TextUtils.isEmpty(thumbFileName)){
                                String dateContent = info.getDateMes();
                                if(TextUtils.isEmpty(dateContent)) {
                                    dateContent = convertDateContent(info.getCreateDate());
                                }
                                thumbFileName = filename.substring(0, filename.indexOf(".")) + "_" + dateContent + ".jpg";
                            }
                            final String thumbPath = thumbFolderPath + File.separator + thumbFileName;
                            Dbug.e(tag, " thumbPath : " +thumbPath);
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
                                        if(durationMap != null){
                                            String durationStr = BufChangeHex.getVideoDuration(thumbPath);
                                            if (null == durationMap.get(name)) {
                                               if(durationStr != null){
                                                   durationMap.put(name, TimeFormater.getTimeFormatValue(Long.valueOf(durationStr)));
                                               }else{
                                                   durationMap.put(name, TimeFormater.getTimeFormatValue(0));
                                               }
                                            }
                                            textView.setText(durationMap.get(name));
                                        }
                                    }
                                });
                            }else{
                                Bitmap  bitmap = ThumbnailUtils.createVideoThumbnail(filepath, MediaStore.Video.Thumbnails.MINI_KIND);
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
                                            if (durationMap != null && !TextUtils.isEmpty(durationMap.get(name))) {
                                                textView.setText(durationMap.get(name));
                                            }

                                        }
                                    });
                                }else{
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
                                Dbug.d(tag, "the bitmap is null");
                            }
                        } catch (Exception e) {
                            Dbug.e(tag, "Exception --> " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }catch (Exception e){
                    Dbug.e(tag, "err =" +e.getMessage());
                    e.printStackTrace();
                }
            }
        },name);
    }

    private String convertDateContent(String createTime){
        String dateContent = "";
        if(!TextUtils.isEmpty(createTime)){
            Date newDate = null;
            try{
                newDate = TimeFormater.yyyyMMddHHmmss.parse(createTime);
            }catch (ParseException e){
                e.printStackTrace();
            }
            if(newDate != null){
                dateContent = TimeFormater.formatYMD_HMS(newDate);
            }
        }
        return dateContent;
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
            if (item.isDirectory()){
                holder.fileName.setText(item.getTitle());
                holder.fileThumb.setImageResource(R.mipmap.ic_directory);
            } else {
                holder.fileName.setText(item.getTitle());
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
                                    getPicture(holder.fileThumb, filename, item.getPath());
                                }
                            }else{
                                getPicture(holder.fileThumb, filename, item.getPath());
                            }
                        }else{
                            getPicture(holder.fileThumb, filename, item.getPath());
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
                        Dbug.e(tag, "bitmap cache size : " + mApplication.getBitmapCacheCount()+ " ,thumbPathMap size = " + mApplication.getThumbPathMapSize());
                        if (mApplication.getBitmapCacheCount() > 0 && mApplication.getThumbPathMapSize() > 0) {
                            String thumbPath = mApplication.getThumbPath(filename);
                            if(!TextUtils.isEmpty(thumbPath)){
                                Bitmap bitmap = mApplication.getBitmapInCache(thumbPath);
                                if(null != bitmap){
                                    Dbug.e(tag, " getView 0001");
                                    holder.fileThumb.setImageBitmap(bitmap);
                                }else{
                                    Dbug.e(tag, " getView 0002");
                                    getVideoBitmap(holder.fileThumb, holder.fileDuration, item);
                                }
                            }else{
                                Dbug.e(tag, " getView 0003");
                                getVideoBitmap(holder.fileThumb, holder.fileDuration, item);
                            }
                        }else{
                            Dbug.e(tag, " getView 0004");
                            getVideoBitmap(holder.fileThumb, holder.fileDuration, item);
                        }
                    }
                }else{
                    holder.fileThumb.setImageResource(R.mipmap.ic_file);
                }
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
