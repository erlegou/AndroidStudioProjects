package hunuo.com.wifiuav;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.text.TextUtils;

import com.jieli.lib.stream.beans.DeviceVersionInfo;


import java.util.HashMap;
import java.util.Map;

import hunuo.com.wifiuav.base.BaseFragment;
import hunuo.com.wifiuav.data.FileInfo;
import hunuo.com.wifiuav.tool.FtpHandlerThread;
import hunuo.com.wifiuav.uity.BitmapCache;
import hunuo.com.wifiuav.uity.Dbug;
import hunuo.com.wifiuav.uity.IAction;
import hunuo.com.wifiuav.uity.IConstant;
import hunuo.com.wifiuav.uity.PreferencesHelper;

public class MainApplication extends Application implements IConstant {
    private final String tag = getClass().getSimpleName();
    private static MainApplication sMyApplication = null;
    private boolean sdcardState = false;
    private Map<String,FileInfo> videoInfoMap = new HashMap<>();
    private int mTimerPicture = 0;
    private boolean isBrowsing = false;
    private String mDeviceUUID = null;
    private FtpHandlerThread mWorkHandlerThread;
    private boolean isModifySSID = false;
    private boolean isModifyPWD = false;
    private boolean allowBrowseDev = false;
    private boolean isOffLineMode = false;
    private boolean isFirstReadData = false;
    private String lastModifySSID = null;
    private String appName = null;
    private BaseFragment currentFragment = null;
    private DeviceVersionInfo serverVersionInfo = null;
    private String currentProductType = null;
    private String appLocalVersion = null;
    private boolean isAllowUse;
    private int captureSize;
    private BitmapCache mBitmapCache;
    private Map<String, String> thumbPathMap;
    private String doualFlag = null;
    private boolean isFrontLastTimePlaying = false;
    private boolean isRearLastTimePlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sMyApplication = this;
        appName = PreferencesHelper.getSharedPreferences(getApplicationContext()).getString(KEY_ROOT_PATH_NAME, null);
        PackageManager pm = this.getPackageManager();
        if(TextUtils.isEmpty(appName)){
            appName = getApplicationInfo().loadLabel(pm).toString();
        }
        try {
            appLocalVersion  = pm.getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        /**Remove current SSID & PWD from SharedPreferences*/
        PreferencesHelper.remove(getApplicationContext(), CURRENT_SSID);
        PreferencesHelper.remove(getApplicationContext(), CURRENT_PWD);

        /**Remove device version info from SharedPreferences*/
        PreferencesHelper.remove(getApplicationContext(), DEVICE_VERSION_MSG);
        Dbug.e(tag, " appName : " +appName + ", appLocalVersion = "+appLocalVersion);

        mBitmapCache = BitmapCache.getInstance();
        thumbPathMap = new HashMap<>();
    }

    public static synchronized MainApplication getApplication() {
        return sMyApplication;
    }

    public Map<String, FileInfo> getVideoInfoMap(){
        return videoInfoMap;
    }

    public void setVideoInfoMap(Map<String, FileInfo> infoMap){
        this.videoInfoMap = infoMap;
    }

    public boolean isSdcardState() {
        return sdcardState;
    }

    public void setSdcardState(boolean sdcardState) {
        this.sdcardState = sdcardState;
    }

    public int getTimerPicture() {
        return mTimerPicture;
    }

    public void setTimerPicture(int time) {
        this.mTimerPicture = time;
    }

    public String getDeviceUUID() {
        return mDeviceUUID;
    }

    public void setDeviceUUID(String uuid) {
        this.mDeviceUUID = uuid;
    }

    public void setWorkHandlerThread(FtpHandlerThread handlerThread){
        this.mWorkHandlerThread = handlerThread;
    }

    public FtpHandlerThread getWorkHandlerThread(){
        return mWorkHandlerThread;
    }

    public void setIsBrowsing(boolean bl){
        this.isBrowsing = bl;
    }

    public boolean getIsBrowsing(){
        return isBrowsing;
    }

    public boolean isModifySSID() {
        return isModifySSID;
    }

    public void setModifySSID(boolean isModifySSID) {
        this.isModifySSID = isModifySSID;
    }

    public boolean isModifyPWD() {
        return isModifyPWD;
    }

    public void setModifyPWD(boolean isModifyPWD) {
        this.isModifyPWD = isModifyPWD;
    }

    public String getLastModifySSID() {
        return lastModifySSID;
    }

    public void setLastModifySSID(String lastModifySSID) {
        this.lastModifySSID = lastModifySSID;
    }

    public void setAllowBrowseDev(boolean bl){
        this.allowBrowseDev = bl;
    }

    public boolean getAllowBrowseDev(){
        return allowBrowseDev;
    }

    public void setIsOffLineMode(boolean bl){
        this.isOffLineMode = bl;
    }

    public boolean getIsOffLineMode(){
        return isOffLineMode;
    }

    public String getAppName(){
        return appName;
    }

    /**
     * 只有修改路径时才能使用
     * @param appName   新的路径名
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setIsFirstReadData(boolean bl){
        this.isFirstReadData = bl;
    }

    public boolean getIsFirstReadData(){
        return isFirstReadData;
    }

    public BaseFragment getCurrentFragment(){
        return currentFragment;
    }

    public void setCurrentFragment(BaseFragment fragment){
        if(currentFragment != null){
            if(!currentFragment.equals(fragment)){
                this.currentFragment = fragment;
                sendBroadcast(new Intent(IAction.ACTION_CHANGE_FRAGMENT));
            }
        }else{
            this.currentFragment = fragment;
            sendBroadcast(new Intent(IAction.ACTION_CHANGE_FRAGMENT));
        }
        Dbug.d(tag, " currentFragment : " +currentFragment);
    }

    public void setServerVersionInfo(DeviceVersionInfo versionInfo){
        this.serverVersionInfo = versionInfo;
    }

    public DeviceVersionInfo getServerVersionInfo(){
        return serverVersionInfo;
    }

    public void setCurrentProductType(String type){
        this.currentProductType = type;
    }

    public String getCurrentProductType(){
        return currentProductType;
    }

    public void setIsAllowUse(boolean bl){
        this.isAllowUse = bl;
    }

    public boolean getIsAllowUse(){
        return isAllowUse;
    }

    public void setAppLocalVersion(String vs){
        this.appLocalVersion = vs;
    }

    public String getAppLocalVersion(){
        return appLocalVersion;
    }

    public void setCaptureSize(int size){
        this.captureSize = size;
    }

    public int getCaptureSize(){
        return captureSize;
    }

    public BitmapCache getBitmapCache(){
        return mBitmapCache;
    }

    public void addBitmapInCache(Bitmap bitmap, String path){
        if(mBitmapCache != null){
            mBitmapCache.addCacheBitmap(bitmap, path);
        }
    }

    public Bitmap getBitmapInCache(String path){
        Bitmap bitmap = null;
        if(mBitmapCache != null){
            bitmap = mBitmapCache.getBitmap(path);
        }
        return bitmap;
    }

    public int getBitmapCacheCount(){
        int count = 0;
        if(mBitmapCache != null){
            count = mBitmapCache.getCount();
        }
        return count;
    }

    public Map<String, String> getThumbPathMap(){
        return thumbPathMap;
    }

    public int getThumbPathMapSize(){
        int size = 0;
        if(thumbPathMap != null){
            size =thumbPathMap.size();
        }
        return size;
    }

    public void addThumbPath(String key, String path){
        if(thumbPathMap != null){
            thumbPathMap.put(key, path);
            if(thumbPathMap.size() >= 512){
                releaseBitmapCache();
            }
        }
    }

    public String getThumbPath(String key){
        String path = null;
        if(thumbPathMap != null && thumbPathMap.size() > 0){
            path = thumbPathMap.get(key);
        }
        return path;
    }

    public boolean removeThumbPath(String key){
        String path = null;
        if(thumbPathMap != null && thumbPathMap.size() >  0){
            path = thumbPathMap.remove(key);
        }
        return TextUtils.isEmpty(path);
    }

    public void releaseBitmapCache(){
        if(mBitmapCache != null){
            mBitmapCache.clearCache();
        }
        if(thumbPathMap != null){
            thumbPathMap.clear();
        }
    }

    public void setRearLastState(boolean state){
        isRearLastTimePlaying = state;
    }
    public boolean getRearLastState(){
        return isRearLastTimePlaying;
    }

    public void setFrontLastState(boolean state){
        isFrontLastTimePlaying = state;
    }
    public boolean getFrontLastState(){
        return isFrontLastTimePlaying;
    }

    public void release(){
        if(videoInfoMap != null){
            videoInfoMap.clear();
            videoInfoMap = null;
        }
        if(!TextUtils.isEmpty(mDeviceUUID)){
            mDeviceUUID = null;
        }
//        if(mWorkHandlerThread != null){
//            if(mWorkHandlerThread.isAlive()){
//                mWorkHandlerThread.interrupt();
//            }
//            mWorkHandlerThread = null;
//        }
        if(!TextUtils.isEmpty(lastModifySSID)){
            lastModifySSID = null;
        }
        if(currentFragment != null){
            currentFragment = null;
        }
        if(serverVersionInfo != null){
            serverVersionInfo = null;
        }
        if(mBitmapCache != null){
            mBitmapCache.clearCache();
            mBitmapCache = null;
        }
        if(thumbPathMap != null){
            thumbPathMap.clear();
            thumbPathMap = null;
        }
    }
}
