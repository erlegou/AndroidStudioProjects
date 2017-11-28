package hunuo.com.wifiuav.ui.dialog;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.jieli.lib.stream.beans.MenuInfo;
import com.jieli.lib.stream.beans.StateInfo;
import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.tools.ParseHelper;
import com.jieli.lib.stream.util.ICommon;


import java.util.ArrayList;
import java.util.List;

import hunuo.com.wifiuav.MainApplication;
import hunuo.com.wifiuav.R;
import hunuo.com.wifiuav.base.BaseDialogFragment;
import hunuo.com.wifiuav.tool.CommandManager;
import hunuo.com.wifiuav.ui.lib.CustomTextView;
import hunuo.com.wifiuav.uity.Dbug;
import hunuo.com.wifiuav.uity.IAction;
import hunuo.com.wifiuav.uity.IConstant;
import hunuo.com.wifiuav.uity.PreferencesHelper;


public class SettingsDialog extends BaseDialogFragment implements ICommon, IAction, IConstant {
    private final String tag = getClass().getSimpleName();
    private ListView mListView;
    private TextView mReturn;
    private MenuAdapter mAdapter;
    private ParseHelper mParseHelper;
    private CommandHub mCmdSocket;
    private String mCurrentMode;
    private OnDismissListener mOnDismissListener;
    private List<MenuInfo> mCurrentContent = null;
    private ImageView mSettingIcon;
    private List<MenuInfo> mExtraList;
    private int mExtraLength = 0;
    private boolean isOpenFlash = false;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_GENERIC_DATA)){
                if (getActivity() == null) {
                    Dbug.e(tag, "activity is null: ");
                    return;
                }
                final StateInfo stateInfo = (StateInfo) intent.getSerializableExtra(KEY_GENERIC_STATE);
                if (stateInfo == null){
                    Dbug.e(tag, "error:stateInfo is null");
                    return;
                }
                handleGenericCommand(stateInfo);
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() != null){
            IntentFilter intentFilter = new IntentFilter(IAction.ACTION_GENERIC_DATA);
            getActivity().registerReceiver(mReceiver, intentFilter);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getActivity() != null){
            getActivity().unregisterReceiver(mReceiver);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Dialog dialog = new Dialog(getActivity(), android.R.style.Theme_Translucent_NoTitleBar);
        final View view = getActivity().getLayoutInflater().inflate(R.layout.video_settings_dialog, null);
        if(dialog.getWindow() == null) return dialog;
        setCancelable(false);
        dialog.getWindow().setContentView(view);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        final WindowManager.LayoutParams params = dialog.getWindow().getAttributes();

        params.width = 500;
        params.height = 540;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
            params.width = displayMetrics.heightPixels - 20;
            params.height = displayMetrics.heightPixels - 20;
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            params.width = displayMetrics.widthPixels - 20;
            params.height = displayMetrics.widthPixels - 20;
        }
        params.gravity = Gravity.CENTER;
        dialog.getWindow().setAttributes(params);

        isOpenFlash = PreferencesHelper.getSharedPreferences(MainApplication.getApplication())
                .getBoolean(TAKE_PHOTO_FLASH_SETTING, false);

        mSettingIcon = (ImageView) view.findViewById(R.id.setting_icon);

        mReturn = (TextView) view.findViewById(R.id.cancel_action);
        mReturn.setOnClickListener(mOnClickListener);

        mAdapter = new MenuAdapter(getActivity());

        mListView = (ListView) view.findViewById(android.R.id.list);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MenuInfo info = null;
                if(position >= 0 && position < mAdapter.getCount()){
                    info = mAdapter.getItem(position);
                }
                if(info == null) return;
                if (ICommon.CMD_NULL.equals(info.getCmdNumber()) && info.getId() >= 0){
                    /**Send command to device when submenu item was clicked.*/
                    //Dbug.i(tag, "00 ParentCmdNumber="+mAdapter.getItem(position).getParentNum() + ",
                    // CmdNum=" + mAdapter.getItem(position).getCmdNumber() + ", Id=" + mAdapter.getItem(position).getId());
                    mCmdSocket.sendCommand(CTP_ID_DEFAULT, info.getParentNum(), info.getId() + "");
                    if (mCurrentContent != null){
                        for (MenuInfo menuInfo : mCurrentContent){
                            menuInfo.setSelected(false);
                        }
                    }
                } else {
                    if (mExtraLength > 0 && position <= mExtraLength -1){
                        switch (position){
                            case 0:
                                if(getActivity() == null){
                                    return;
                                }
//                                Intent intent = new Intent(getActivity(), PersonalSettingActivity.class);
//                                startActivity(intent);
                                dismiss();
                                break;
                            case 1:
                                isOpenFlash = !isOpenFlash;
                                String imageName;
                                if(isOpenFlash){
                                    imageName = "ic_on";
                                }else{
                                    imageName = "ic_off";
                                }
                                MenuInfo mInfo = null;
                                if(position < mAdapter.getCount()){
                                    mInfo = mAdapter.getItem(position);
                                }
                                if(mInfo == null) break;
                                mInfo.setImage(imageName);
                                mAdapter.notifyDataSetChanged();
                                PreferencesHelper.putBooleanValue(MainApplication.getApplication(), TAKE_PHOTO_FLASH_SETTING, isOpenFlash);
                                if(getActivity() != null){
                                    getActivity().sendBroadcast(new Intent(ACTION_MODIFY_FLASH_SETTING));
                                }
                                break;
                        }
                        return;
                    }
                    /**Enter submenu*/
                    mCurrentContent = mParseHelper.getMenuData(mCurrentMode, info.getCmdNumber());
                    mAdapter.clear();
                    mAdapter.addAll(mCurrentContent);
                    mAdapter.notifyDataSetChanged();
                }
            }
        });

        return dialog;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mCmdSocket = CommandHub.getInstance();

        mParseHelper = ParseHelper.getInstance();

        Bundle bundle = getArguments();
        mCurrentMode = bundle.getString(JS_MODE_CATEGORY, JS_SETTINGS_MODE);
        switch (mCurrentMode) {
            case JS_PHOTO_MODE:
                mSettingIcon.setImageResource(R.mipmap.ic_photo);
                break;
            case JS_VIDEO_MODE:
                mSettingIcon.setImageResource(R.mipmap.ic_video);
                break;
            case JS_SETTINGS_MODE:
                mSettingIcon.setImageResource(R.mipmap.ic_settings);
                mExtraList = new ArrayList<>();
                String[] info = {getResources().getString(R.string.general_settings),
                        getResources().getString(R.string.flash_setting)};//getResources().getStringArray(R.array.system_changes_list);
                for (String anInfo : info) {
                    MenuInfo menuInfo = new MenuInfo();
                    if(getResources().getString(R.string.general_settings).equals(anInfo)){
                        menuInfo.setText(anInfo);
                        menuInfo.setImage("ic_arrow_right");
                    }else if(getResources().getString(R.string.flash_setting).equals(anInfo)){
                        String imageName;
                        if(isOpenFlash){
                            imageName = "ic_on";
                        }else{
                            imageName = "ic_off";
                        }
                        menuInfo.setText(anInfo);
                        menuInfo.setImage(imageName);
                    }
                    mExtraList.add(menuInfo);
                }
                mExtraLength = mExtraList.size();
                break;
        }

        List<MenuInfo> data = ParseHelper.getInstance().getMenuData(mCurrentMode);
        if (mExtraLength > 0){
            data.addAll(0, mExtraList);
        }
        if (data != null ){
            Log.i(tag, "data is =" + data.size());
            mAdapter.addAll(data);
            mListView.setAdapter(mAdapter);

            /**Initialize the status of all options*/
            String cmdNumber;
            String state;
            for (int i = 0; i < data.size(); i++){
                cmdNumber = data.get(i).getCmdNumber();
                if (!CMD_NULL.equals(cmdNumber)){
                    state = CommandManager.getInstance().getDeviceStatus(cmdNumber);
                    Dbug.i(tag, "cmdNumber="+cmdNumber + ",state="+state);
                    if (TextUtils.isEmpty(state)){
                        mCmdSocket.requestStatus(CTP_ID_DEFAULT, cmdNumber);
                    } else {
                        syncDeviceStatus(cmdNumber, state);
                    }
                }
            }
        } else {
            Log.e(tag, "onActivityCreated:data is null");
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mOnDismissListener != null){
            mOnDismissListener.onDismiss(mCurrentMode);
        }
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v == mReturn){
                /**Return to parent, or perform dismiss*/
                MenuInfo mInfo = null;
                if(mAdapter.getCount() > 0){
                    mInfo = mAdapter.getItem(0);
                }
                if(mInfo == null){
                    Dbug.e(tag, "-mOnClickListener- action :  mReturn Error : mInfo is null");
                    return;
                }
                if (mAdapter.getCount() > 0 && mInfo.getId() >= 0){
                    List<MenuInfo> data = mParseHelper.getMenuData(mCurrentMode);
                    if (data != null ){
                        mAdapter.clear();
                        if (mExtraLength > 0){
                            data.addAll(0, mExtraList);
                        }
                        mAdapter.addAll(data);
                        mAdapter.notifyDataSetChanged();
                    } else {
                        Dbug.e(tag, "Data is null");
                    }
                } else {
                    dismiss();
                }
            }
        }
    };

    private void syncDeviceStatus(String strCmdNumber, String contentId){
        for (int i = 0; i < mAdapter.getCount(); i++) {
            MenuInfo mInfo = mAdapter.getItem(i);
            if(mInfo == null) continue;
            if (mInfo.getCmdNumber().equals(strCmdNumber)) {
                MenuInfo menuInfo = mParseHelper.getMenuInfo(mCurrentMode, strCmdNumber, contentId);
                if (menuInfo == null) {
                    Dbug.e(tag, "menuInfo is null: ");
                    return;
                }
                if (TextUtils.isEmpty(menuInfo.getImage())) {
                    if (menuInfo.getStateBitmap() != null) {
                        mInfo.setStateBitmap(menuInfo.getStateBitmap());
                    }
                } else {
                    mInfo.setStateImage(menuInfo.getImage());
                }
                break;
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    private void handleGenericCommand(final StateInfo stateInfo) {
//        Dbug.e(tag, "getContentID: " + stateInfo.getParam()[0] + ", getCmdNumber=" + mAdapter.getItem(0).getCmdNumber());
        int contentId = Integer.parseInt(stateInfo.getParam()[0]);
        MenuInfo mInfo = null;
        if(mAdapter.getCount() > 0){
            mInfo = mAdapter.getItem(0);
        }
        if(mInfo == null){
            Dbug.e(tag, "-handleGenericCommand- Error : mInfo is null");
            return;
        }
        if (mAdapter.getCount() > 0 && !CMD_NULL.equals(mInfo.getCmdNumber())) {
            /**Handle the status from device and update UI*/
            syncDeviceStatus(stateInfo.getCmdNumber(), stateInfo.getParam()[0]);

        } else if (contentId >= 0) {
            /**Update status if set successfully*/
            mAdapter.notifyDataSetChanged();
        } else {
            Dbug.e(tag, "Unknown error.");
        }
    }

    private class MenuAdapter extends ArrayAdapter<MenuInfo> {
        private final LayoutInflater mLayoutInflater;
        private ViewHolder holder;

        MenuAdapter(Context context) {
            super(context, 0);
            mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        private int getResource(String imageName){
            if (TextUtils.isEmpty(imageName)){
                return 0;
            }
            return getResources().getIdentifier(imageName, RESOURCE_DIR , getActivity().getPackageName());
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null){
                holder = new ViewHolder();
                convertView = mLayoutInflater.inflate(R.layout.text_image_item, parent, false);
                holder.imageView = (ImageView) convertView.findViewById(R.id.image);
                holder.textView = (CustomTextView) convertView.findViewById(R.id.text);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final MenuInfo item = getItem(position);
            if(item == null) return convertView;
            if (TextUtils.isEmpty(item.getImage())){
                if (TextUtils.isEmpty(item.getStateImage())){
                    if (item.getStateBitmap() != null){
                        holder.imageView.setImageBitmap(item.getStateBitmap());
                    } else {
                        holder.imageView.setImageResource(0);
                    }
                } else {
                    holder.imageView.setImageResource(getResource(item.getStateImage()));
                }
            } else {
                holder.imageView.setImageResource(getResource(item.getImage()));
            }
            holder.textView.setText(item.getText());
            if (item.isSelected()){
                convertView.setBackgroundColor(getResources().getColor(R.color.dark_red));
            } else {
                convertView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            }
            return convertView;
        }

        private class ViewHolder {
            private ImageView imageView;
            private CustomTextView textView;
        }
    }

    public void setOnDismissListener(OnDismissListener listener){
        mOnDismissListener = listener;
    }

    public interface OnDismissListener {
        void onDismiss(String target);
    }
}
