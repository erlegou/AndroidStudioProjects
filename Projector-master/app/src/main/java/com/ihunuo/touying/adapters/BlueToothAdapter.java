package com.ihunuo.touying.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ihunuo.touying.BuildConfig;
import com.ihunuo.touying.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tzy on 2017/11/15.
 */

public class BlueToothAdapter extends BaseAdapter {
    private final static String TAG = "BluedevicesAdapter";
    private List<BluetoothDevice> mDevices;
    private Context mContext;
    private ArrayList<Integer> mRssi;
    private int mSelectedIndex;
    private Listener mListener;

    public BlueToothAdapter(Context context, List<BluetoothDevice> MyDeviceList) {
        mContext = context;
        mRssi = new ArrayList<Integer>();
        mDevices = MyDeviceList;
    }

    public void add(BluetoothDevice device) {
        if (!mDevices.contains(device)) {
            boolean isTargetDevice = false;
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "debug");
                isTargetDevice = true;
            } else {
                if (device.getName() != null) {
                    if (device.getName().length() > 4) {
                        String strname = device.getName().substring(0, 4);
                        if (strname.equals("UFO3")) {
                            isTargetDevice = true;
                        }
                    }


                }

            }
            if (isTargetDevice) {
                mDevices.add(device);
                notifyDataSetChanged();
            }
        }
    }


    public List<BluetoothDevice> getBluetoothDeviceList() {
        return mDevices;
    }

    public void setBluetoothDeviceList(List<BluetoothDevice> bluetoothDeviceList) {
        mDevices = bluetoothDeviceList;
    }

    /**
     * 清空
     */
    public void clear() {
        mDevices.clear();

    }

    @Override
    public int getCount() {
        return mDevices.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < mDevices.size()) {
            return mDevices.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        convertView = LayoutInflater.from(mContext).inflate(R.layout.blue_tooth_item, null, false);
        TextView devicenametext = (TextView) convertView.findViewById(R.id.device_name);

        RelativeLayout relayout = (RelativeLayout) convertView.findViewById(R.id.ground_bg);

        BluetoothDevice device = mDevices.get(position);
        devicenametext.setText(device.getName());
        ImageView refeicon = (ImageView) convertView.findViewById(R.id.device_point);
        if (position == mSelectedIndex) {
            relayout.setBackgroundResource(R.drawable.bluetooth_chouce);
            refeicon.setVisibility(View.VISIBLE);
        } else {
            refeicon.setVisibility(View.GONE);
            relayout.setBackground(null);
        }
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mListener != null) {
                    mListener.onSelected(position);
                    setSelectedIndex(position);
                }
            }
        });
        return convertView;

    }

    public int getSelectedIndex() {
        return mSelectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        if (selectedIndex == mSelectedIndex) return;
        mSelectedIndex = selectedIndex;
        notifyDataSetChanged();
    }

    public Listener getListener() {
        return mListener;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public interface Listener {
        void onSelected(final int positon);

    }

    public void removeDeviceWithAddress(final String address) {
        for (BluetoothDevice device : mDevices) {
            if (device.getAddress().equals(address)) {
                mDevices.remove(device);
                break;
            }
        }
        notifyDataSetChanged();
    }
}
