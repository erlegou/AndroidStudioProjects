package com.ihunuo.touying;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.NdefMessage;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.eleven.app.bluetoothlehelper.BluetoothLEService;
import com.eleven.app.bluetoothlehelper.Peripheral;
import com.eleven.app.bluetoothlehelper.PeripheralCallback;

import java.util.Date;
import java.util.List;

public class BaseActivity extends AppCompatActivity {

    private final static String TAG = "BaseActivity";
    //蓝牙模块
    public BluetoothLEService mBluetoothLEService;
    public BluetoothAdapter mBluetoothAdapter;
    public BluetoothManager mBluetoothManager;
    public Handler mHandler = new Handler();

    public  static int value;

    public MaterialDialog mloaddiag;
    //发送监听
    private OnSendDataListener mOnSendDataListener;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
    }

    //新蓝牙模块
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG,"进入connection函数");

            mBluetoothLEService = ((BluetoothLEService.LocalBinder) service).getService();

            List<Peripheral> peripheralList = mBluetoothLEService.getConnectedPeripherals();
            for (Peripheral p : peripheralList) {
                p.setCallback(mPeripheralCallback);
            }

            if (AppInscan.getmApp().getmNowactivit()==1)
            {
                stratScan(true);
            }


        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
//            List<Peripheral> peripheralList = mBluetoothLEService.getConnectedPeripherals();
//            for (Peripheral p : peripheralList) {
//                p.setCallback(null);
//            }
        }
    };
    private PeripheralCallback mPeripheralCallback = new PeripheralCallback() {
        private String TAG = PeripheralCallback.class.getSimpleName();
        @Override
        public void onConnected(Peripheral peripheral) {
            super.onConnected(peripheral);

        }

        @Override
        public void onDisconnected(final Peripheral peripheral) {
            super.onDisconnected(peripheral);
            Log.e(TAG, "onDisconnected");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DisConnectCallback();
                    showToast("连接断开");
                    peripheral.setCallback(null);
                    //重新搜索

                }
            });

        }

        @Override
        public void onConnecting(final Peripheral peripheral) {
            super.onConnecting(peripheral);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ConnectCallback( peripheral);
                }
            });

        }

        @Override
        public void onServicesDiscovered(final Peripheral peripheral, final int status) {
            super.onServicesDiscovered(peripheral, status);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    peripheral.setNotify(AppConfig.TRANSFER_SERVICE_UUID, AppConfig.TRANSFER_NOTITY_UUID, true);

                    mloaddiag.dismiss();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //获取电量
                            ConnectCallback(peripheral);
                        }
                    });

                }
            },200);

        }

        @Override
        public void onCharacteristicChanged(final Peripheral peripheral, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(peripheral, characteristic);
            Log.d(TAG, "onCharacteristicChanged current time: " + System.currentTimeMillis());
            final   byte[] result = characteristic.getValue();

            byte bt[] = new byte[result.length];
//            decrypt(result,result.length,bt);
            int head = result[0]&0XFF;


            int myvalue = 0;

            myvalue = 0;

            myvalue =  (result[1]&0xff);
            myvalue<<=8;
            myvalue|=(result[0]&0xff);

            if (((myvalue-value)>5||(value-myvalue)>5)&&myvalue<500)
            {
                value = myvalue;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        GetPracticeCallback();
                    }
                });
            }







        }

        @Override
        public void onCharacteristicRead(final Peripheral peripheral,final BluetoothGattCharacteristic characteristic,final int status) {
            super.onCharacteristicRead(peripheral, characteristic, status);
        }
    };




    public Peripheral getPeripheral() {

        if (mBluetoothLEService==null)
        {
            showToast("plese check your loction power");
            return null;
        }

        List<Peripheral> peripherals = mBluetoothLEService.getConnectedPeripherals();
        if (peripherals==null)
        {
            return null;
        }
        if (peripherals.size() > 0) {
            return peripherals.get(0);
        }
        return null;
    }
    private void sendFail() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mOnSendDataListener != null) {
                    mOnSendDataListener.onFailed();
                }
            }
        });
    }


    public interface OnSendDataListener {
        void onFinish();
        void onFailed();
    }
    public OnSendDataListener getOnSendDataListener() {
        return mOnSendDataListener;
    }

    public void setOnSendDataListener(OnSendDataListener onSendDataListener) {
        mOnSendDataListener = onSendDataListener;
    }

    public void showToast(final String text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BaseActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }
    public boolean checkConnection() {
        if (mBluetoothLEService != null) {
            List<Peripheral> peripherals = mBluetoothLEService.getConnectedPeripherals();
            for (int i =0;i<peripherals.size();i++)
            {
                Peripheral p = peripherals.get(i);
                if (p != null && p.getState() == Peripheral.STATE_CONNECTED)
                {
                    return true;
                }
            }
            return false;
        } else {

            return false;
        }
    }


    public PeripheralCallback getprecallback()
    {
        return mPeripheralCallback;
    }




    /**
     * 加密
     * @param s 原数组
     * @param len 长度
     * @param t 加密后数组
     */
    void encrypt(byte [] s, int len, byte [] t)
    {
        byte KEY1=0x17;
        byte KEY2=0x27;

        byte  check1,check2,check;
        int  i;

        //*****  计算校验码
        check1 =s[0];
        check2 =s[0];
        for(  i=1;i<len-1;i++)
        {
            check1=(byte)(check1+s[i]);
            check2=(byte)(check2^s[i]);

        }

        check =(byte) (check1+check2);

        s[len-1] = check;
        //*******  end *******
        t[len-1] = s[len-1];
        t[len-2] = s[len-2];
        for (  i = len-3; i >= 0; i--)
        {
            t[i] = (byte) ((((s[i]^t[i+1]) + t[i+1]) ^ KEY1) + KEY2);
        }
    }

    /**
     * 解密
     * @param s 需要解密的数组
     * @param len 长度
     * @param t 解密后的数组
     */
    void decrypt(byte [] s, int len, byte [] t)
    {
        byte KEY1=0x17;
        byte KEY2=0x27;

        byte  check1,check2,check;
        int i;
        t[len-1] = s[len-1];
        t[len-2] = s[len-2];

        for (  i = len-3; i >= 0; i--)
        {
            t[i] = (byte) ((((s[i]-KEY2) ^ KEY1) - s[i+1]) ^ s[i+1]);
        }
        //＊＊＊＊＊计算校验码＊＊＊＊＊＊＊＊＊＊
        check1=t[0];
        check2=t[0];

        for (i=1; i< len-1;i++)
        {
            check1=(byte)(check1+t[i]);
            check2=(byte)(check2^t[i]);
        }


        check =(byte) (check1+check2);
        //*********end ***********************
    }



    //蓝牙搜索模块
    public void stratScan(boolean scan) {
        if (scan) {
            Log.d(TAG, "开始扫描");
            AppInscan.getmApp().getmDeviceList().clear();
            mBluetoothLEService.stopScan(mLeScanCallback);
            mBluetoothLEService.startScan(mLeScanCallback);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLEService.stopScan(mLeScanCallback);

                }
            }, 5000);
        } else {
            mBluetoothLEService.stopScan(mLeScanCallback);
        }
    }

    public void stopscan() {mBluetoothLEService.stopScan(mLeScanCallback);
    }
    private BluetoothAdapter.LeScanCallback mLeScanCallback =  new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
//            Log.d(TAG, "扫描到设备: " + device.getName());
            final BluetoothDevice finalDevice = device;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (finalDevice.getName()!=null)
                    {
//                        if (finalDevice.getName().equals("test"))
//                        {

                        if (!AppInscan.getmApp().getmDeviceList().contains(finalDevice))
                        {
                            AppInscan.getmApp().getmDeviceList().add(finalDevice);


                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ScanCallback();
                            }
                        });



                        // }
                    }

                }
            });
        }
    };

    public void ConnectCallback(Peripheral peripheral)
    {

    }
    public void DisConnectCallback()
    {

    }
    protected void GetPracticeCallback()
    {

    }
    public void ScanCallback()
    {

    }
    public void showdiss(Context mconx, String string)
    {
        if (mloaddiag==null)
        {
            mloaddiag =  new MaterialDialog.Builder(mconx)
                    .content(string)
                    .cancelable(false)
                    .progress(true,100)
                    .negativeText(R.string.cancel)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onNegative(MaterialDialog dialog) {
                            super.onNegative(dialog);
                            dialog.dismiss();
                        }
                    })
                    .show();
        }
        else
        {
            mloaddiag.show();
        }
    }
    public void loaddismiss(Context mconx)
    {

        mloaddiag.dismiss();

    }

    public void connecdevice(int postion)
    {
        if (AppInscan.getmApp().getmDeviceList().size()>0)
        {
            showdiss(this,getResources().getString(R.string.connection));
            Peripheral pre = mBluetoothLEService.findPeripheralWithAddress(AppInscan.getmApp().getmDeviceList().get(postion).getAddress());

            if (pre!=null&&pre.getState()==Peripheral.STATE_CONNECTED)
            {
                Intent intent = new Intent(BaseActivity.this, MainActivity.class);
                startActivityForResult(intent, 100);
            }
            else
            {
                pre = mBluetoothLEService.connect(AppInscan.getmApp().getmDeviceList().get(postion).getAddress());
                pre.setCallback(mPeripheralCallback);

            }
        }

    }
    @Override
    protected void onResume() {
        super.onResume();
        mBluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "make suer you Buletooth more than v4.0!", Toast.LENGTH_LONG).show();
        }

        Intent intent = new Intent(this,BluetoothLEService.class);

        if (AppInscan.getmApp().getmNowactivit()==1)
        {
            startService(intent);
        }
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mServiceConnection);
        mHandler.removeCallbacksAndMessages(null);
    }


}
