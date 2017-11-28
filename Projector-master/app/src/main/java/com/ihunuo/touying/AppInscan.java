package com.ihunuo.touying;

import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tzy on 2017/11/15.
 */

public class AppInscan {
    int mNowactivit = 0;
    List<BluetoothDevice> mDeviceList = new ArrayList<>();

    private static  AppInscan mApp;

    public int getmNowactivit() {
        return mNowactivit;
    }

    public void setmNowactivit(int mNowactivit) {
        this.mNowactivit = mNowactivit;
    }
    public static AppInscan getmApp() {

        if (mApp==null)
        {
            mApp = new AppInscan();
        }

        return mApp;
    }

    public List<BluetoothDevice> getmDeviceList() {
        return mDeviceList;
    }

    public void setmDeviceList(List<BluetoothDevice> mDeviceList) {
        this.mDeviceList = mDeviceList;
    }

}