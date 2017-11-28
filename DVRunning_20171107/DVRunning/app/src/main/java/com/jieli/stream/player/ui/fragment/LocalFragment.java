package com.jieli.stream.player.ui.fragment;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseFragment;
import com.jieli.stream.player.ui.dialog.WaitDialog;
import com.jieli.stream.player.ui.lib.SlidingTabLayout;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IAction;
import com.jieli.stream.player.util.IConstant;

import java.util.ArrayList;
import java.util.List;


public class LocalFragment extends BaseFragment {

    private String tag = getClass().getSimpleName();
    private SlidingTabLayout mFmTabHost;
    private ViewPager mViewPager;
    private PagerAdapter mAdapter;

    private List<BaseFragment> fragments = null;
    private String tabMsgStr[] = null;
    private WaitDialog waitingForDeleteDialog;
    private String mWhichDir;

    public LocalFragment() {
        // Required empty public constructor
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action){
                case IAction.ACTION_UPDATE_LOCAL_FILES_UI:
                    int operationType = intent.getIntExtra(IConstant.LOCAL_FILES_UI, -1);
                    switch (operationType){
                        case 0: // show
                            showDeleteFilesDialog();
                            break;
                        case 1: //dismiss
                            dismissDeleteFilesDialog();
                            break;
                    }
                    break;
            }
        }
    };

    public static LocalFragment newInstance(String which) {
        LocalFragment fragment = new LocalFragment();
        Bundle args = new Bundle();
        args.putString(ARG_WHICH_DIR, which);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_browse_main, container, false);
        mFmTabHost = (SlidingTabLayout) view.findViewById(R.id.local_tabs);
        mViewPager = (ViewPager) view.findViewById(R.id.local_pager);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getActivity() == null){
            return;
        }
        mWhichDir = getActivity().getIntent().getStringExtra(ARG_WHICH_DIR);//rear_view or front_view
        initTabHost();
        IntentFilter filter = new IntentFilter(IAction.ACTION_UPDATE_LOCAL_FILES_UI);
        getActivity().getApplicationContext().registerReceiver(mReceiver, filter);
    }

    private void initTabHost() {
        if(fragments == null){
            fragments = new ArrayList<>();
        }
        tabMsgStr = getResources().getStringArray(R.array.local_browse_list);

        if(fragments.size() > 0){
            fragments.clear();
        }
        for (String title : tabMsgStr){
            if(title.equals(getString(R.string.gallery))){
                fragments.add(LocalBrowsePhotoFragment.newInstance(mWhichDir));
            }else if(title.equals(getString(R.string.video_mode))){
                fragments.add(LocalBrowseVideoFragment.newInstance(mWhichDir));
            }else if(title.equals(getString(R.string.record_mode))){
                fragments.add(RecordVideoFragment.newInstance(mWhichDir));
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

    private void showDeleteFilesDialog(){
        if(waitingForDeleteDialog == null){
            waitingForDeleteDialog = new WaitDialog(getActivity(), getString(R.string.deleting_files));
            waitingForDeleteDialog.setCancelable(false);
        }
        if(!waitingForDeleteDialog.isShowing()){
            waitingForDeleteDialog.show();
        }
    }

    private void dismissDeleteFilesDialog(){
        if(waitingForDeleteDialog != null && waitingForDeleteDialog.isShowing()){
            waitingForDeleteDialog.dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
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
    public void onDestroy() {
        Dbug.w(tag, " ======onDestroy=====  2333 ");
        if(fragments != null){
            fragments.clear();
            fragments = null;
        }
        if(waitingForDeleteDialog != null){
            waitingForDeleteDialog.cancel();
            waitingForDeleteDialog = null;
        }
        mApplication.releaseBitmapCache();
        getActivity().getApplicationContext().unregisterReceiver(mReceiver);
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
                return super.getItemPosition(object);
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
