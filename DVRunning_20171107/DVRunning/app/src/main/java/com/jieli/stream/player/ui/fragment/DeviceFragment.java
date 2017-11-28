package com.jieli.stream.player.ui.fragment;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseFragment;
import com.jieli.stream.player.data.beans.FTPLoginInfo;
import com.jieli.stream.player.tool.FtpHandlerThread;
import com.jieli.stream.player.ui.dialog.WaitDialog;
import com.jieli.stream.player.ui.lib.SlidingTabLayout;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IAction;
import com.jieli.stream.player.util.IConstant;

import java.util.ArrayList;
import java.util.List;


public class DeviceFragment extends BaseFragment implements IConstant{

    private String tag = getClass().getSimpleName();
    private SlidingTabLayout mFmTabHost;
    private ViewPager mViewPager;
    private PagerAdapter mAdapter;

    private List<BaseFragment> fragments;
    private String tabMsgStr[] = null;
    private boolean isOpenList = false;
    private boolean isFirstUpdate = false;
    private FtpHandlerThread mWorkHandlerThread;

    private WaitDialog waitingForThumb;
    private WaitDialog waitingForDeleteFileDialog;
    private WaitDialog waitForSetDataDialog;
	private String mWhichDir;

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(isFirstUpdate){
                DeviceBrowsePhotoFragment.getInstance(mWhichDir).updateListMsg(msg);
                DeviceBrowseVideoFragment.getInstance(mWhichDir).updateListMsg(msg);
                if(msg.what == FtpHandlerThread.MSG_UPDATE_UI){
                    isFirstUpdate = false;
                }
            }else{
                if(mApplication.getCurrentFragment() instanceof  DeviceBrowsePhotoFragment){
                    DeviceBrowsePhotoFragment.getInstance(mWhichDir).updateListMsg(msg);
                }else if(mApplication.getCurrentFragment() instanceof  DeviceBrowseVideoFragment){
                    DeviceBrowseVideoFragment.getInstance(mWhichDir).updateListMsg(msg);
                }
            }

            return false;
        }
    });

	public static DeviceFragment newInstance(String which) {
		DeviceFragment fragment = new DeviceFragment();
		Bundle args = new Bundle();
		args.putString(ARG_WHICH_DIR, which);
		fragment.setArguments(args);
		return fragment;
	}

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action){
                case IAction.ACTION_UPDATE_LIST:
                    mHandler.post(updateList);
                    break;
                case IAction.ACTION_UPDATE_DEVICE_FILES_UI:
                    int dialogType = intent.getIntExtra(IConstant.DEVICE_FILES_UI_TYPE, -1);
                    int dialogState = intent.getIntExtra(IConstant.DEVICE_DIALOG_STATE, -1);
                    switch (dialogType){
                        case WAITING_FOR_THUMB:
                            switch (dialogState){
                                case ARGS_SHOW_DIALOG:
                                    if(waitingForThumb != null && !waitingForThumb.isShowing()){
                                        waitingForThumb.show();
                                    }
                                    break;
                                case ARGS_DISMISS_DIALOG:
                                    if(waitingForThumb != null && waitingForThumb.isShowing()){
                                        waitingForThumb.dismiss();
                                    }
                                    break;
                            }
                            break;
                        case WAITING_FOR_DATA:
                            switch (dialogState){
                                case ARGS_SHOW_DIALOG:
                                    if(waitForSetDataDialog != null && !waitForSetDataDialog.isShowing()){
                                        waitForSetDataDialog.show();
                                    }
                                    break;
                                case ARGS_DISMISS_DIALOG:
                                    if(waitForSetDataDialog != null && waitForSetDataDialog.isShowing()){
                                        waitForSetDataDialog.dismiss();
                                    }
                                    break;
                            }
                            break;
                        case WAITING_FOR_DELETE:
                            switch (dialogState){
                                case ARGS_SHOW_DIALOG:
                                    if(waitingForDeleteFileDialog != null && !waitingForDeleteFileDialog.isShowing()){
                                        waitingForDeleteFileDialog.show();
                                    }
                                    break;
                                case ARGS_DISMISS_DIALOG:
                                    if(waitingForDeleteFileDialog != null && waitingForDeleteFileDialog.isShowing()){
                                        waitingForDeleteFileDialog.dismiss();
                                    }
                                    break;
                            }
                            break;
                        case ALL_DIALOG_DISMISS:
                            if(waitingForThumb != null && waitingForThumb.isShowing()){
                                waitingForThumb.dismiss();
                            }
                            if(waitForSetDataDialog != null && waitForSetDataDialog.isShowing()){
                                waitForSetDataDialog.dismiss();
                            }
                            if(waitingForDeleteFileDialog != null && waitingForDeleteFileDialog.isShowing()){
                                waitingForDeleteFileDialog.dismiss();
                            }
                            break;
                    }
                    break;
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFirstUpdate = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Dbug.e(tag, " ====onCreateView=======");
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_device_browse_main, container, false);
        mFmTabHost = (SlidingTabLayout) view.findViewById(R.id.tabs);
        mViewPager = (ViewPager) view.findViewById(R.id.pager);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Dbug.e(tag, " ====onActivityCreated=======");
        super.onActivityCreated(savedInstanceState);
        if(getActivity() == null){
            Dbug.e(tag, "getActivity() is empty!");
            return;
        }

	    mWhichDir = getActivity().getIntent().getStringExtra(ARG_WHICH_DIR);//rear_view or front_view

        if(mApplication.getWorkHandlerThread() != null){
            if(!mApplication.getWorkHandlerThread().equals(mWorkHandlerThread)){
                mWorkHandlerThread = mApplication.getWorkHandlerThread();
            }
            mWorkHandlerThread.setUIHandler(mHandler);
        }

        isOpenList = mApplication.getIsFirstReadData();

        mHandler.post(updateList);
        initTabHost();
        initDialog();
        IntentFilter intentFilter = new IntentFilter(IAction.ACTION_UPDATE_LIST);
        intentFilter.addAction(IAction.ACTION_UPDATE_DEVICE_FILES_UI);
        getActivity().getApplicationContext().registerReceiver(mReceiver, intentFilter);
    }


    private void initTabHost() {
        if(fragments == null){
            fragments = new ArrayList<>();
        }
        tabMsgStr = getResources().getStringArray(R.array.browse_list);

        if(fragments.size() > 0){
            fragments.clear();
        }
        for (String title : tabMsgStr){
            if(title.equals(getString(R.string.gallery))){
                fragments.add(DeviceBrowsePhotoFragment.getInstance(mWhichDir));
            }else if(title.equals(getString(R.string.video_mode))){
                fragments.add(DeviceBrowseVideoFragment.getInstance(mWhichDir));
            }
        }
        mFmTabHost.setDistributeEvenly(true);
        mFmTabHost.setSelectedIndicatorColors(getResources().getColor(R.color.background_green));
        if(mAdapter == null){
            mAdapter = new PagerAdapter(getChildFragmentManager(), fragments);
        }
        mViewPager.setAdapter(mAdapter);

        mFmTabHost.setViewPager(mViewPager);
    }

    private void initDialog(){
        if(getActivity() == null){
            return;
        }
        if (waitingForThumb == null) {
            waitingForThumb = new WaitDialog(getActivity(), R.string.down_load_thumb);
            waitingForThumb.setCancelable(false);
//            waitingForThumb.setOnKeyListener(dialogOnKeyListener);
        }
        if(waitingForDeleteFileDialog == null){
            waitingForDeleteFileDialog = new WaitDialog(getActivity(), getString(R.string.deleting_files));
            waitingForDeleteFileDialog.setCancelable(false);
        }
        if(waitForSetDataDialog == null){
            waitForSetDataDialog = new WaitDialog(getActivity(), getString(R.string.read_data));
            waitForSetDataDialog.setCancelable(false);
            waitForSetDataDialog.setOnKeyListener(dialogOnKeyListener);
        }

    }

    private DialogInterface.OnKeyListener dialogOnKeyListener = new DialogInterface.OnKeyListener() {
        @Override
        public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
            if(keyCode == KeyEvent.KEYCODE_BACK){
                if(waitForSetDataDialog != null && waitForSetDataDialog.isShowing()){
                    waitForSetDataDialog.dismiss();
                }
                return  true;
            }
            return false;
        }
    };

    private void releaseDialog(){
        if(waitingForThumb != null){
            waitingForThumb.cancel();
            waitingForThumb = null;
        }
        if(waitingForDeleteFileDialog != null){
            waitingForDeleteFileDialog.cancel();
            waitingForDeleteFileDialog = null;
        }
        if(waitForSetDataDialog != null){
            waitForSetDataDialog.cancel();
            waitForSetDataDialog = null;
        }
        System.gc();
    }

    private void loginServer(String currentPath, boolean openList){
        if(getActivity() == null && !isAdded()){
            return;
        }
        FTPLoginInfo ftpLoginInfo = new FTPLoginInfo(INSIDE_FTP_HOST_NAME, DEFAULT_FTP_PORT, INSIDE_FTP_USER_NAME, INSIDE_FTP_PASSWORD);

        Message message = Message.obtain();
        message.what = FtpHandlerThread.MSG_CONNECT_SERVER;
        message.obj = currentPath;
        Bundle bundle = new Bundle();
        bundle.putSerializable(IConstant.FTP_LOGIN_INFO, ftpLoginInfo);
        bundle.putBoolean(IConstant.LIST_FTP_OPERATION, openList);
	    bundle.putString(ARG_WHICH_DIR, mWhichDir);
        message.setData(bundle);
        if (mWorkHandlerThread != null){
            if(mWorkHandlerThread.getWorkHandler() != null){
                mWorkHandlerThread.getWorkHandler().sendMessage(message);
            }else{
                Dbug.e(tag, "mWorkHandlerThread.getWorkHandler() is null");
            }
        } else {
            showToastLong("Login failed");
        }
    }

    private Runnable updateList = new Runnable() {
        @Override
        public void run() {
            loginServer(null, isOpenList);
            isFirstUpdate = true;
            if(isOpenList){
                isOpenList = false;
                mApplication.setIsFirstReadData(false);
            }
        }
    };

    @Override
    public void onResume() {
        Dbug.e(tag, " ====onResume=======");
        super.onResume();
        if(mApplication.getWorkHandlerThread() != null){
            if(!mApplication.getWorkHandlerThread().equals(mWorkHandlerThread)){
                mWorkHandlerThread = mApplication.getWorkHandlerThread();
                mWorkHandlerThread.setUIHandler(mHandler);
            }
        }
        if(mAdapter != null){
            BaseFragment fragment = mAdapter.currentFragment;
            if(fragment != null){
                if(!(fragment.equals(mApplication.getCurrentFragment()))){
                    mApplication.setCurrentFragment(fragment);
                }
            }
        }
    }

    @Override
    public void onPause() {
        Dbug.e(tag, " ====onPause=======");
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Dbug.e(tag, " ====onDestroy=======");
        isFirstUpdate = false;
        if(getActivity() != null && mReceiver != null){
            getActivity().getApplicationContext().unregisterReceiver(mReceiver);
        }
        if(mHandler != null){
            mHandler.removeCallbacksAndMessages(null);
        }
        if(fragments != null){
            fragments.clear();
            fragments = null;
        }
        mApplication.releaseBitmapCache();
        releaseDialog();
        super.onDestroy();
    }

    private class PagerAdapter extends FragmentStatePagerAdapter {
        private List<BaseFragment> fragments = null;
        private boolean isUpdateData = false;
        private BaseFragment currentFragment;

        PagerAdapter(FragmentManager fm, List<BaseFragment> fragmentList) {
            super(fm);
            this.fragments = fragmentList;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            if(object != null){
                if(!object.equals(currentFragment)){
                    currentFragment = (BaseFragment) object;
                    mApplication.setCurrentFragment(currentFragment);
                }
            }
            super.setPrimaryItem(container, position, object);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment = new Fragment();
            if(fragments != null && position < fragments.size()){
                fragment = fragments.get(position);
            }
            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabMsgStr[position];
        }


        @Override
        public int getItemPosition(Object object) {
            if (isUpdateData) {
                return PagerAdapter.POSITION_NONE;
            } else {
                return getItemPosition(object);
            }

        }

        @Override
        public int getCount() {
            if (fragments == null) {
                return 0;
            }
            return fragments.size();
        }
    }

}
