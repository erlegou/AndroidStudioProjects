package hunuo.com.wifiuav.ui.dialog;

import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import hunuo.com.wifiuav.R;
import hunuo.com.wifiuav.base.BaseDialogFragment;
import hunuo.com.wifiuav.tool.WifiHelper;
import hunuo.com.wifiuav.ui.activity.LauncherActivity;
import hunuo.com.wifiuav.uity.Dbug;
import hunuo.com.wifiuav.uity.IAction;
import hunuo.com.wifiuav.uity.IConstant;
import hunuo.com.wifiuav.uity.PreferencesHelper;


public class DeviceListDialog extends BaseDialogFragment implements IConstant, IAction {
    private final String tag = getClass().getSimpleName();
    private ListView mListView;
    private DeviceAdapter mAdapter;
    private NotifyDialog mRefreshDialog;
    private final Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        setCancelable(false);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Dbug.d(tag, "------onCreateView--------:");
        View view = inflater.inflate(R.layout.device_list_dialog, container, false);
        mListView = (ListView) view.findViewById(android.R.id.list);
        TextView exitText = (TextView) view.findViewById(R.id.exit_action);
        exitText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if(getActivity() != null){
                    getActivity().sendBroadcast(new Intent(ACTION_QUIT_APP));
                }
            }
        });

        TextView offlineBtn = (TextView) view.findViewById(R.id.offline_btn);
        offlineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(getActivity() == null){
                    return;
                }
                if (!(getActivity() instanceof LauncherActivity)) {
                    dismiss();
                }
                getActivity().sendBroadcast(new Intent(ACTION_ENTER_OFFLINE_MODE));
            }
        });

        TextView refresh = (TextView) view.findViewById(R.id.refresh_button);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRefreshDialog = new NotifyDialog(true, R.string.please_wait);
                mRefreshDialog.show(getFragmentManager(), "mRefreshDialog");
                mHandler.post(scanWifiTask);
            }
        });

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                String selectedSSID = mList.get(position).get(TEXT).toString();
                ScanResult scanResult = mAdapter.getItem(position);
                String selectedSSID = null;
                if(scanResult != null){
                    selectedSSID = scanResult.SSID;
                    Dbug.d("aaa","ssid = " + selectedSSID);
                }
                ConnectivityManager cm = (ConnectivityManager) getActivity().getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                WifiManager wifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                String ssid = wifiManager.getConnectionInfo().getSSID();
                String currentConnectedSSID = null;
                if (!TextUtils.isEmpty(ssid)) {
                    currentConnectedSSID = ssid.replace("\"", "");
                }

                Dbug.i(tag, currentConnectedSSID + "==============" + selectedSSID + ", wifi.isConnected()=" +
                        wifi.isConnected());

                /**The selected Wi-Fi is connected*/
                if (!TextUtils.isEmpty(currentConnectedSSID) && currentConnectedSSID.equals(selectedSSID) && wifi.isConnected()) {
                    dismiss();
                    Dbug.d(tag, "Wi-Fi is already connected:" + selectedSSID);
                    if (mOnItemClickListener != null) {
                        mOnItemClickListener.onItemClick(true, null, null);
                    }
                } else if (scanResult != null && (!scanResult.capabilities.contains("WPA"))) {
                    /**No need password*/
                    dismiss();
                    if (mOnItemClickListener != null) {
                        Dbug.d(tag, "No need password, " + scanResult.capabilities);
                        mOnItemClickListener.onItemClick(false, selectedSSID, WifiConfiguration.KeyMgmt.NONE + "");
                    }
                } else {
                    SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(getActivity());
                    String selectedSSIDPwd = sharedPreferences.getString(selectedSSID, null);
//                    Dbug.d(tag, "selectedSSIDPwd:" + selectedSSIDPwd);
                    /**The selected Wi-Fi has connected before*/
                    if (!TextUtils.isEmpty(selectedSSIDPwd)) {
                        dismiss();
                        Dbug.w(tag, "currentSSID and currentPWD not empty.");
                        if (mOnItemClickListener != null) {
                            mOnItemClickListener.onItemClick(false, selectedSSID, selectedSSIDPwd);
                        }
                    } else {
//                        InputPasswordDialog inputPasswordDialog = InputPasswordDialog.newInstance(selectedSSID);
//                        inputPasswordDialog.show(getFragmentManager(), "inputPasswordDialog");
//                        inputPasswordDialog.setOnInputCompletionListener(new InputPasswordDialog.OnInputCompletionListener() {
//                            @Override
//                            public void onCompletion(String ssid, String password) {
//                                dismiss();
//                                Dbug.w(tag, "onCompletion. ssid=" + ssid);
//                                if (mOnItemClickListener != null) {
//                                    mOnItemClickListener.onItemClick(false, ssid, password);
//                                }
//                            }
//                        });
                    }
                }
            }
        });
        return view;
    }

    private OnItemClickListener mOnItemClickListener;
    public void setOnItemClickListener(OnItemClickListener listener){
        mOnItemClickListener = listener;
    }
    public interface OnItemClickListener{
        void onItemClick(boolean isConnected, String ssid, String password);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getDialog() == null || getDialog().getWindow() == null) return;
        final WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();

        params.width = 100;
        params.height = 150;
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
            params.width = displayMetrics.heightPixels * 4 / 5;
            params.height = displayMetrics.heightPixels * 3 / 4;
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            params.width = displayMetrics.widthPixels * 4 / 5;
            params.height = displayMetrics.widthPixels * 3 / 4;
        }
        params.gravity = Gravity.CENTER;
        getDialog().getWindow().setAttributes(params);

        mAdapter = new DeviceAdapter(getActivity());
        mListView.setAdapter(mAdapter);
        refreshList();
    }

    private final int SCAN_TIME = 5;
    private int mTime = 0;
    private final Runnable scanWifiTask = new Runnable() {
        @Override
        public void run() {
            if (mTime >= SCAN_TIME){
                mTime = 0;
                mHandler.removeCallbacks(scanWifiTask);
                if (mRefreshDialog != null && mRefreshDialog.isShowing()) {
                    mRefreshDialog.dismiss();
                }
                refreshList();
            } else {
                mTime ++;
                mHandler.postDelayed(scanWifiTask, 300);
            }
        }
    };

    private void refreshList(){
        List<ScanResult> scanResults  = WifiHelper.getInstance(getActivity()).getSpecifiedSSIDList(WIFI_PREFIX);
        mAdapter.clear();
        mAdapter.addAll(scanResults);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mHandler.removeCallbacks(scanWifiTask);
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private class DeviceAdapter extends ArrayAdapter<ScanResult>{
        private final LayoutInflater mLayoutInflater;
        private ViewHolder mViewHolder;

        DeviceAdapter(Context context) {
            super(context, 0);
            mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null){
                convertView = mLayoutInflater.inflate(R.layout.device_list_item, parent, false);
                mViewHolder = new ViewHolder();
                mViewHolder.textView = (TextView) convertView.findViewById(R.id.text);
                convertView.setTag(mViewHolder);
            } else {
                mViewHolder = (ViewHolder) convertView.getTag();
            }
            final ScanResult systemInfo = getItem(position);
            if(systemInfo != null){
                mViewHolder.textView.setText(systemInfo.SSID);
            }
            return convertView;
        }

        private class ViewHolder {
            private TextView textView;
        }
    }
}
