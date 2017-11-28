package hunuo.com.wifiuav.uity;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.jieli.lib.stream.beans.DeviceVersionInfo;
import com.jieli.lib.stream.tools.ParseHelper;
import com.jieli.lib.stream.util.ICommon;

import org.apache.commons.net.ftp.FTPClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hunuo.com.wifiuav.MainApplication;
import hunuo.com.wifiuav.R;
import hunuo.com.wifiuav.data.FileInfo;
import hunuo.com.wifiuav.data.SDFileInfo;

/**
 * class name: AppUtil
 * function : APP Util class
 * @author JL
 * create time : 2015-12-28 10:43
 * version : v1.0
 * ///////////////////////////////////////////////////////////////////////////
 *
 */
public class AppUtil implements IConstant, ICommon {

    private static String tag = "AppUtil";
    private static List<FileInfo> allLocalFile = new ArrayList<>();
    public static int failedNum = -1;
    public static final int UPGRADE_APK_TYPE = 0;
    public static final int UPGRADE_SDK_TYPE = 1;

    public static final String VERSION_JSON = "version.json";

    public static boolean isAppInBackground(Context context) {
        boolean isInBackground = true;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT > 20) {
            List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    for (String activeProcess : processInfo.pkgList) {
                        if (activeProcess.equals(context.getPackageName())) {
                            isInBackground = false;
                        }
                    }
                }
            }
        } else {
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
            ComponentName componentInfo = taskInfo.get(0).topActivity;
            if (componentInfo.getPackageName().equals(context.getPackageName())) {
                isInBackground = false;
            }
        }

        return isInBackground;
    }

    /**
     * 防止多次点击事件
     * **/
    private static long lastClickTime;
    public static boolean isFastDoubleClick(int delayTime) {
        long time = System.currentTimeMillis();
        if ( time - lastClickTime > delayTime) {
            return true;
        }
        lastClickTime = time;
        return false;
    }


    public static double getDeviceVersionInfo(String filename, int type, boolean outFtp){
        String result;
        String[] strs;
        int index = 0;
        try{
            if(filename != null){
                if(filename.contains(",")) {
                    switch (type){
                        case UPGRADE_APK_TYPE:
                            if(outFtp){
                                index = 1;
                            }else{
                                index = 2;
                            }
                            break;
                        case UPGRADE_SDK_TYPE:
                            if(outFtp){
                                index = 0;
                            }else{
                                index = 1;
                            }
                            break;
                    }
                    strs = filename.split(",");
                    if(strs[index].contains("[")){
                        result = strs[index].substring(strs[index].indexOf("[") + 1);
                        if(result.contains("]")){
                            result = result.substring(0,result.indexOf("]"));
                            return Double.parseDouble(result);
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return 0.0;
    }

    public static DeviceVersionInfo getLocalVersionInfo(String data){
        if(TextUtils.isEmpty(data)){
            return null;
        }
        DeviceVersionInfo deviceVersionInfo = new DeviceVersionInfo();
        JSONObject jsonObject;
        try{
            jsonObject = new JSONObject(data);
            //parse json data
            if(jsonObject.has(PRODUCT_TYPE)){
                String productTypes = jsonObject.getString(PRODUCT_TYPE);
                String[] products = parseDeviceVersionInfo(productTypes);
                if(products != null && products.length > 0){
                    deviceVersionInfo.setProductTypes(products);
                }
            }
            if(jsonObject.has(ANDROID_VERSION)){
                deviceVersionInfo.setLocalAndroidVersion(jsonObject.getString(ANDROID_VERSION));
            }
            String[] productTypes = deviceVersionInfo.getProductTypes();
            if(productTypes != null && productTypes.length > 0){
                Map<String, String[]> matchFirmwareVersions = new HashMap<>();
                for (String productType : productTypes){
                    if(jsonObject.has(productType)){
                        String matchDeviceVersion  = jsonObject.getString(productType);
                        String[] matchDeviceVersions = parseDeviceVersionInfo(matchDeviceVersion);
                        if(matchDeviceVersions != null && matchDeviceVersions.length > 0){
                            matchDeviceVersions = sort(matchDeviceVersions); //desc sort data
                            matchFirmwareVersions.put(productType, matchDeviceVersions);
                        }
                    }
                }
                deviceVersionInfo.setFirmwareVersions(matchFirmwareVersions);
            }
        }catch (JSONException e){
            e.printStackTrace();
            return null;
        }
        return deviceVersionInfo;
    }

    public static DeviceVersionInfo getServerVersionInfo(String data) {
        if (TextUtils.isEmpty(data)) {
            return null;
        }
        DeviceVersionInfo deviceVersionInfo = new DeviceVersionInfo();
        JSONObject jsonObject;
        try{
            jsonObject = new JSONObject(data);
            //parse json data
            if(jsonObject.has(FIRMWARE_DIR)){
                String serverFirmware  = jsonObject.getString(FIRMWARE_DIR);
                String[] serverFirmwareVersions = parseDeviceVersionInfo(serverFirmware);
                if(serverFirmwareVersions != null && serverFirmwareVersions.length > 0){
                    serverFirmwareVersions = sort(serverFirmwareVersions); //desc sort data
                    deviceVersionInfo.setServerFirmwareVersions(serverFirmwareVersions);
                }
            }
            if(jsonObject.has(ANDROID_DIR)){
                String serverAndroid  = jsonObject.getString(ANDROID_DIR);
                String[] serverAndroidVersions = parseDeviceVersionInfo(serverAndroid);
                if(serverAndroidVersions != null && serverAndroidVersions.length > 0){
                    serverAndroidVersions = sort(serverAndroidVersions); //desc sort data
                    deviceVersionInfo.setServerAndroidVersions(serverAndroidVersions);
                }
            }
        }catch (JSONException e){
            e.printStackTrace();
            return null;
        }
        return deviceVersionInfo;
    }

    private static String[] parseDeviceVersionInfo(String androidVersionString){
        if(TextUtils.isEmpty(androidVersionString)){
            return null;
        }
        if(androidVersionString.contains("[")){
            androidVersionString = androidVersionString.replace("[", "");
        }
        if(androidVersionString.contains("]")){
            androidVersionString = androidVersionString.replace("]", "");
        }
        if(androidVersionString.contains("\"")){
            androidVersionString = androidVersionString.replace("\"", "");
        }
        String[] resultStr;
        if(androidVersionString.contains(",")){
            resultStr = androidVersionString.split(",");
        }else{
            resultStr = new String[1];
            resultStr[0] = androidVersionString;
        }
        return resultStr;
    }

    public static String getAppStoragePath(MainApplication application, String dirType, boolean isRearView){
        if (application == null){
            Dbug.e(tag, "getAppStoragePath: application is null!");
            return null;
        }

        if (isRearView)
            return splicingFilePath(application.getAppName(), application.getDeviceUUID(), dirType, "RearView");
        else
            return splicingFilePath(application.getAppName(), application.getDeviceUUID(), dirType, null);
    }
    /**
     * 拼接目录路径
     * @param rootName        根路径名称
     * @param oneDirName      一级目录名称
     * @param twoDirName      二级目录名称
     * @param threeDirName    三级目录名称
     * @return                拼合的路径
     */
    public static String splicingFilePath(String rootName, String oneDirName, String twoDirName, String threeDirName){
        File file;
        String path;
        if(!TextUtils.isEmpty(rootName)){
            path = ROOT_PATH;
            if(rootName.contains(File.separator)){
                String[] dirNames = rootName.split(File.separator);
                for (String name : dirNames){
                    if(!TextUtils.isEmpty(name)){
                        path += File.separator + name;
                        file = new File(path);
                        if(!file.exists()){
                            if(file.mkdir()){
                                Dbug.w(tag, "create root dir success! path : " +path);
                            }
                        }
                    }
                }
            }else{
                path += File.separator + rootName;
                file = new File(path);
                if(!file.exists()){
                    if(file.mkdir()){
                        Dbug.w(tag, "create root dir success! path : " +path);
                    }
                }
            }
            if(TextUtils.isEmpty(oneDirName)){
                return  path;
            }
            path = path + File.separator + oneDirName;
            file = new File(path);
            if(!file.exists()){
                if(file.mkdir()){
                    Dbug.w(tag, "create one dir success!");
                }
            }
            if(TextUtils.isEmpty(twoDirName)){
               return path;
            }
            path = path + File.separator + twoDirName;
            file = new File(path);
            if(!file.exists()){
                if(file.mkdir()){
                    Dbug.w(tag, "create two dir success!");
                }
            }
            if(TextUtils.isEmpty(threeDirName)){
                return path;
            }
            path = path + File.separator + threeDirName;
            file = new File(path);
            if(!file.exists()){
                if(file.mkdir()){
                    Dbug.w(tag, "create three sub dir success!");
                }
            }
            return path;
        }else{
            return ROOT_PATH;
        }
    }


    /**
     * read text content from path
     * */
    public static String readTxtFile(String filePath){
        String textStr = "";
        if(filePath == null || filePath.isEmpty()){
            return textStr;
        }
        InputStreamReader read = null;
        try {
            String encoding="UTF-8";
            File file=new File(filePath);
            if(file.isFile() && file.exists()){ //判断文件是否存在
                read = new InputStreamReader(
                        new FileInputStream(file),encoding);//考虑到编码格式
                BufferedReader bufferedReader = new BufferedReader(read);
                String lineTxt;
                while((lineTxt = bufferedReader.readLine()) != null){
                    textStr = textStr + lineTxt + '\n';
                }
                read.close();
            }else{
                Dbug.e(tag, "Cannot find the specified file");
            }
        } catch (Exception e) {
            Dbug.e(tag, " err : " + e.getMessage());
            e.printStackTrace();
        }finally {
            if(read != null){
                try {
                    read.close();
                } catch (IOException e) {
                    Dbug.e(tag, " IOException : " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        return textStr;
    }

//    public static List<FileInfo> getAllLocalFile(String path, String subDirName, boolean isCustom){
//        if(allLocalFile == null){
//            allLocalFile = new ArrayList<>();
//        }else{
//            if(allLocalFile.size() > 0){
//                allLocalFile.clear();
//            }
//        }
//        if(path == null || path.isEmpty()){
//            return allLocalFile;
//        }
//        try{
//            File defaultFolder = new File(path);
//            if(defaultFolder.exists()){
//                if(defaultFolder.isDirectory()){
//                    File[] files = defaultFolder.listFiles();
//                    for (File file : files){
//                        if(file.isDirectory()){
//                            File[] subFiles = file.listFiles();
//                            for (File subFile : subFiles){
//                                if(subFile.getName().equals(subDirName)){
//                                    allLocalFile.addAll(getLocalFileInfo(subFile.getPath(), isCustom));
//                                }
//                            }
//                        }else{
//                            FileInfo info = new FileInfo();
//                            if(isCustom){
//                                info.setTitle(getFileName(file.getName()));
//                                info.setDirectory(false);
//                                info.setSize(file.length());
//                                String createTime = getFileCreateTime(file.getName());
//                                if(TextUtils.isEmpty(createTime)){
//                                    createTime = TimeFormater.formatYMDHMS(file.lastModified());
//                                }
//                                info.setCreateDate(createTime);
//                                info.setPath(file.getPath());
//                            }else{
//                                String modifyTime = TimeFormater.formatYMDHMS(file.lastModified());
//                                if(modifyTime == null){
//                                    modifyTime = "2015-08-07 15:34:26";
//                                }
//                                info.setTitle(file.getName());
//                                info.setDirectory(file.isDirectory());
//                                info.setSize(file.length());
//                                info.setCreateDate(modifyTime);
//                                info.setPath(file.getPath());
//                            }
//                            allLocalFile.add(info);
//                        }
//                    }
//                }
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//
//        return allLocalFile;
//    }

//    public static List<FileInfo> getLocalFileInfo(String path, boolean isCustom){
//        List<FileInfo> resultList = new ArrayList<>();
//        if(path == null || path.isEmpty()){
//            return resultList;
//        }
//        try{
//            File defaultFolder = new File(path);
//            if(defaultFolder.exists()){
//                if (defaultFolder.isDirectory()){
//                    File[] files = defaultFolder.listFiles();
//                    Map<String, File> filesMap = new HashMap<>();
//                    List<String> fileDatesList = new ArrayList<>();
//                    if(files != null && files.length > 0){
//                        for (File file : files){
//                            if(file.isFile()){
//                                if(isCustom){
//                                    String rawDate = file.getName();
//                                    if(!TextUtils.isEmpty(rawDate)){
//                                        fileDatesList.add(rawDate);
//                                        filesMap.put(rawDate, file);
//                                    }
//                                }else{
//                                    String date = file.lastModified()+"";
//                                    fileDatesList.add(date);
//                                    filesMap.put(date, file);
//                                }
//                            }
//                        }
//                        String[] fileDates = fileDatesList.toArray(new String[fileDatesList.size()]);
//                        fileDates = descSort(fileDates);
//                        for (String fileDate : fileDates){
//                            if(TextUtils.isEmpty(fileDate)){
//                                continue;
//                            }
//                            File file = filesMap.get(fileDate);
//                            if(file == null){
//                                continue;
//                            }
//                            FileInfo info = new FileInfo();
//                            if (isCustom) {
//                                if (file.isFile() && fileDate.equals(file.getName())) {
//                                    info.setTitle(getFileName(file.getName()));
//                                    info.setDirectory(false);
//                                    info.setSize(file.length());
//                                    String createTime = getFileCreateTime(file.getName());
//                                    if(TextUtils.isEmpty(createTime)){
////                                        createTime = TimeFormater.formatYMDHMS(file.lastModified());
//                                    }
//                                    info.setCreateDate(createTime);
//                                    info.setPath(file.getPath());
//                                }
//                            } else {
//                                if (file.isFile() && fileDate.equals(file.lastModified() + "")) {
//                                    String modifyTime = TimeFormater.getFormatedDateTime(TimeFormater.yyyyMMddHHmmss, file.lastModified());
//                                    if (modifyTime == null) {
//                                        modifyTime = "2015-08-07 15:34:26";
//                                    }
//                                    info.setTitle(file.getName());
//                                    info.setDirectory(false);
//                                    info.setSize(file.length());
//                                    info.setCreateDate(modifyTime);
//                                    info.setPath(file.getPath());
//                                }
//                            }
//                            resultList.add(info);
//                        }
//                    }
//                }
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return resultList;
//    }

    /**
     * desc sort
     */
    private static String[] descSort(String[] drs){
        if(drs == null || drs.length == 0){
            return drs;
        }
        for (int i = 0; i < drs.length; i++) {
            for (int j = 0; j < drs.length- i -1; j++) {
                if(drs[j].compareTo(drs[j+1]) < 0){
                    String temp = drs[j];
                    drs[j] = drs[j+1];
                    drs[j+1] = temp;
                }
            }
        }
        return drs;
    }

    private static String[] sort(String[] drs){
        if(drs == null || drs.length == 0){
            return drs;
        }
        double[] result = new double[drs.length];
        for (int i = 0; i < result.length; i++) {
            try{
                result[i] = Double.valueOf(drs[i]);
            }catch (NumberFormatException e){
                e.printStackTrace();
            }
        }
        drs = sort(result);
        return drs;
    }

    private static String[] sort(double[] drs){
        if(drs == null || drs.length == 0){
            return null;
        }
        for (int i = 0; i < drs.length; i++) {
            for (int j = 0; j < drs.length- i -1; j++) {
                if(drs[j] < (drs[j+1])){
                    double temp = drs[j];
                    drs[j] = drs[j+1];
                    drs[j+1] = temp;
                }
            }
        }
        String[] result = new String[drs.length];
        for (int i = 0; i < result.length; i++){
            result[i] = String.valueOf(drs[i]);
        }
        return result;
    }

    private static String getFileName(String drs){
        String result;
        if(drs == null || drs.isEmpty()){
            return null;
        }
        String fileType = "";
        int index = -1;
        if(drs.contains(".")){
            index = drs.indexOf(".");
            if(index != -1){
                fileType = drs.substring(index);
            }
        }
        if(drs.contains("_")){
            String[] strs = drs.split("_");
            result = strs[0];
        }else{
            if(index != -1){
                result = drs.substring(0, index);
            }else{
                result = drs;
            }
        }
        return result + fileType;
    }

    public static String getFileCreateTime(String drs){
        String result = null;
        if(drs == null || drs.isEmpty()){
            return null;
        }
        if(drs.contains("_")){
            String[] strs = drs.split("_");
            if(strs[1].length() >= 14){
                String time = strs[1];
                result = time.substring(0, 4) + "-" +time.substring(4, 6)+"-"+time.substring(6,8)
                        +" "+time.substring(8,10)+":"+time.substring(10,12)+":"+time.substring(12,14);
            }
        }
        return result;
    }


    public static void deleteFile(File file) {
        if(file == null || !file.exists()){
            return;
        }
        if (file.isFile()) {
            if(file.delete()){
                System.out.printf("delete file success!");
            }
            return;
        }
        if(file.isDirectory()){
            File[] childFiles = file.listFiles();
            if (childFiles == null || childFiles.length == 0) {
                if(file.delete()){
                    System.out.printf("delete empty file success!");
                }
                return;
            }
            for (File childFile :childFiles) {
                deleteFile(childFile);
            }
            if(file.delete()){
                System.out.printf("delete empty file success!");
            }
        }
    }

    /**
     * Get files and read data from the raw folder in resources
     * @param mContext  context
     * @param rawId resource ID
     * @return version message
     */
    public static String getFromRaw(Context mContext, int rawId){
        String result = "";
        if(mContext == null){
            return result;
        }
        InputStream in = null;
        try {
             in = mContext.getResources().openRawResource(rawId);
            //Number of bytes in the file
            int length = in.available();
            byte[]  buffer = new byte[length];
            //Read the data in the file to the byte array.
            in.read(buffer);
            result = new String(buffer, "GBK");
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(in != null){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static boolean compareVersionInfo(final Context context, final MainApplication mApplication, Handler handler){
        if(context == null || mApplication == null || handler == null){
            Dbug.e(tag, " compareAPKVersion parameters is empty!");
            return false;
        }
         /* Get version info */
        String versionInfo = AppUtil.getFromRaw(context, R.raw.local_version_info);
        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(context.getApplicationContext());
        String deviceVersionMsg = sharedPreferences.getString(DEVICE_VERSION_MSG, null);
        DeviceVersionInfo localVersionInfo = AppUtil.getLocalVersionInfo(versionInfo);
        DeviceVersionInfo deviceVersionInfo = ParseHelper.parseDeviceVersionText(deviceVersionMsg);
        DeviceVersionInfo serverVersionInfo;
        String appName = mApplication.getAppName();
        String serverVersionPath = AppUtil.splicingFilePath(mApplication.getAppName(), VERSION, null, null);
        // read data error
        if(localVersionInfo == null || TextUtils.isEmpty(localVersionInfo.getLocalAndroidVersion())
                || deviceVersionInfo == null || TextUtils.isEmpty(deviceVersionInfo.getFirmwareVersion())){
            Dbug.e(tag, " localVersionInfo " + localVersionInfo + " deviceVersionInfo " + deviceVersionInfo);
            Message msg = handler.obtainMessage();
            msg.what = IConstant.SHOW_NOTIFY_DIALOG;
            Bundle bundle = new Bundle();
            bundle.putInt(IConstant.DIALOG_TYPE, IConstant.READ_DATA_ERROR);
            bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
            msg.setData(bundle);
            handler.sendMessage(msg);
            return false;
        }
        double currentSDK = 0.0;
        double currentAPK = 0.0;
        // compare to product type
        boolean isSameProduct = false;
        boolean isMatchApp = false;
        String sameProductType = null;
        try{
            currentSDK = Double.valueOf(deviceVersionInfo.getFirmwareVersion());
            currentAPK = Double.valueOf(localVersionInfo.getLocalAndroidVersion());
        }catch (NumberFormatException e){
            e.printStackTrace();
        }
        if(null != localVersionInfo.getProductTypes()){
            for (String product : localVersionInfo.getProductTypes()){
                if(product != null){
                    if(product.equals(deviceVersionInfo.getProductType())){
                        Dbug.e(tag, " same product type : " + product);
                        sameProductType = product;
                        isSameProduct = true;
                        mApplication.setCurrentProductType(product);
                        break;
                    }
                }
            }
        }
        if(null != deviceVersionInfo.getAppTypes()) {
            for (String matchAppName : deviceVersionInfo.getAppTypes()) {
                if(!TextUtils.isEmpty(matchAppName)){
                    if(matchAppName.equals(appName)){
                        isMatchApp = true;
                        String deviceProduct = deviceVersionInfo.getProductType();
                        if(!TextUtils.isEmpty(deviceProduct)){
                            mApplication.setCurrentProductType(deviceProduct);
                        }
                        break;
                    }
                }
            }
        }
        if(!isSameProduct && !isMatchApp){
            Message msg = handler.obtainMessage();
            msg.what = IConstant.SHOW_NOTIFY_DIALOG;
            Bundle bundle = new Bundle();
            bundle.putInt(IConstant.DIALOG_TYPE, IConstant.PRODUCT_NOT_MATCH);
            bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
            msg.setData(bundle);
            handler.sendMessage(msg);
            return false;
        }
        String serverVersionName = null;
        if(!TextUtils.isEmpty(sameProductType)){
            File versionDir = new File(serverVersionPath);
            if(versionDir.exists()){
                String[] versionFiles = versionDir.list();
                if(versionFiles != null && versionFiles.length > 0){
                    for (String name : versionFiles){
                        if(name != null){
                            if(name.contains(sameProductType)){
                                serverVersionName = name;
                                break;
                            }
                        }
                    }
                }
            }
        }
        double serverAPKLatestVersion = 0.0;
        double serverSDKLatestVersion = 0.0;
        if(!TextUtils.isEmpty(serverVersionName)){
            String newPath = serverVersionPath + File.separator + serverVersionName;
            String serverInfo = AppUtil.readTxtFile(newPath);
            serverVersionInfo = AppUtil.getServerVersionInfo(serverInfo);
            if(serverVersionInfo != null){
                mApplication.setServerVersionInfo(serverVersionInfo);
                String[] serverAPKVersions = serverVersionInfo.getServerAndroidVersions();
                if( serverAPKVersions != null &&  serverAPKVersions.length > 0){
                    try{
                        serverAPKLatestVersion = Double.valueOf(serverAPKVersions[0]);
                    }catch (NumberFormatException e){
                        e.printStackTrace();
                    }
                    Dbug.e(tag, " serverAPKLatestVersion : " + serverAPKLatestVersion);
                }
                String[] serverSDKVersions = serverVersionInfo.getServerFirmwareVersions();
                if(serverSDKVersions != null && serverSDKVersions.length > 0){
                    try{
                        serverSDKLatestVersion = Double.valueOf(serverSDKVersions[0]);
                    }catch (NumberFormatException e){
                        e.printStackTrace();
                    }
                    Dbug.e(tag, " serverSDKLatestVersion : " + serverSDKLatestVersion);
                }
            }
        }
        boolean isSameAPKVersion = false;
        if(null != deviceVersionInfo.getAndroidVersions()){
            for (int i = 0; i < deviceVersionInfo.getAndroidVersions().length; i++){
                double deviceAPKVersion = 0.0;
                try{
                    String androidVersion = deviceVersionInfo.getAndroidVersions()[i];
                    if(TextUtils.isEmpty(androidVersion)) continue;
                    deviceAPKVersion = Double.valueOf(androidVersion);
                }catch (NumberFormatException e){
                    e.printStackTrace();
                }
                if(deviceAPKVersion > 0.0){
//                Dbug.e(tag, " deviceAPKVersion : "+deviceAPKVersion );
                    if(deviceAPKVersion == currentAPK){
                        Dbug.e(tag, " same apk version : " + deviceAPKVersion);
                        isSameAPKVersion = true;
                        break;
                    }
                }else{
                    Dbug.e(tag, " deviceAPKVersion is null");
                }
            }
        }
        boolean isSameSDKVersion = false;
        Map<String, String[]> matchFirmwareVersions = localVersionInfo.getFirmwareVersions();
        String[] firmwareVersions = matchFirmwareVersions.get(mApplication.getCurrentProductType());
        if(firmwareVersions != null){
            for (String localSDKVersion : firmwareVersions) {
                double sdkVersion = 0.0;
                try{
                    sdkVersion = Double.valueOf(localSDKVersion);
                }catch (NumberFormatException e){
                    e.printStackTrace();
                }
                if (sdkVersion > 0.0) {
//                    Dbug.e(tag, " localSDKVersion : " + localSDKVersion);
                    if (sdkVersion == currentSDK) {
                        Dbug.e(tag, " same sdk version : " + sdkVersion);
                        isSameSDKVersion = true;
                        break;
                    }
                } else {
                    Dbug.e(tag, " localSDKVersion is null");
                }
            }
        }
        Dbug.e(tag, " currentSDK " + currentSDK + " currentAPK " + currentAPK);
        //Mandatory update
        if(!isSameAPKVersion){
            if(!isSameSDKVersion){
                if(currentSDK > 0.0 && serverSDKLatestVersion > 0.0) {
                    if (serverSDKLatestVersion > currentSDK) {
                        Message msg = handler.obtainMessage();
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        Bundle bundle = new Bundle();
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.SDK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, true);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                        return false;
                    }
                }
                if(currentSDK > 0.0 && serverAPKLatestVersion > 0.0){
                    if(serverAPKLatestVersion > currentAPK){
                        Message msg = handler.obtainMessage();
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        Bundle bundle = new Bundle();
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.APK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, true);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                        return false;
                    }
                }
                if(serverSDKLatestVersion == 0.0 || serverAPKLatestVersion == 0.0){
                    boolean isCompareToLocal = false;
                    String[] androidVersions = deviceVersionInfo.getAndroidVersions();
//                    String localAndroidVersion = localVersionInfo.getLocalAndroidVersion();
                    for (String version : androidVersions){
                        if(!TextUtils.isEmpty(version)){
                            double dVersion = 0.0;
                            try{
                                dVersion = Double.valueOf(version);
                            }catch (NumberFormatException e){
                                e.printStackTrace();
                            }
                            if(dVersion > 0.0 && dVersion > currentAPK){
                                isCompareToLocal = true;
                                break;
                            }
                        }
                    }
                    Message msg = handler.obtainMessage();
                    Bundle bundle = new Bundle();
                    if(isCompareToLocal){
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.APK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, true);
                    }else{
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.SDK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, true);
                    }
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                    return false;
                }
                handler.sendEmptyMessage(IConstant.NO_UPDATE_FILE);
                return false;
            }else{ //Prompt update
                if(currentSDK > 0.0 && serverSDKLatestVersion > 0.0) {
                    if (serverSDKLatestVersion > currentSDK) {
                        Message msg = handler.obtainMessage();
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        Bundle bundle = new Bundle();
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.SDK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                        return true;
                    }
                }
                if(currentAPK > 0.0 && serverAPKLatestVersion > 0.0){
                    if(serverAPKLatestVersion > currentAPK){
                        Message msg = handler.obtainMessage();
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        Bundle bundle = new Bundle();
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.APK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                        return true;
                    }
                }
//                if(serverSDKLatestVersion == 0.0 || serverAPKLatestVersion == 0.0){
//                    Message msg = handler.obtainMessage();
//                    msg.what = IConstant.SHOW_NOTIFY_DIALOG;
//                    Bundle bundle = new Bundle();
//                    bundle.putInt(IConstant.DIALOG_TYPE, IConstant.SDK_NOT_MATCH);
//                    bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
//                    msg.setData(bundle);
//                    handler.sendMessage(msg);
//                }
            }
        }else{ //Prompt update
            if(isSameSDKVersion){
                if(currentSDK > 0.0 && serverSDKLatestVersion > 0.0) {
                    if (serverSDKLatestVersion > currentSDK) {
                        Message msg = handler.obtainMessage();
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        Bundle bundle = new Bundle();
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.SDK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                        return true;
                    }
                }
                if(currentAPK > 0.0 && serverAPKLatestVersion > 0.0){
                    if(serverAPKLatestVersion > currentAPK){
                        Message msg = handler.obtainMessage();
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        Bundle bundle = new Bundle();
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.APK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                    }
                }
            }else{
                if(currentSDK > 0.0 && serverSDKLatestVersion > 0.0) {
                    if (serverSDKLatestVersion > currentSDK) {
                        Message msg = handler.obtainMessage();
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        Bundle bundle = new Bundle();
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.SDK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                        return true;
                    }
                }
                if(currentAPK > 0.0 && serverAPKLatestVersion > 0.0){
                    if(serverAPKLatestVersion > currentAPK){
                        Message msg = handler.obtainMessage();
                        msg.what = IConstant.SHOW_NOTIFY_DIALOG;
                        Bundle bundle = new Bundle();
                        bundle.putInt(IConstant.DIALOG_TYPE, IConstant.APK_NOT_MATCH);
                        bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                        return true;
                    }
                }
//                if(serverSDKLatestVersion == 0.0 || serverAPKLatestVersion == 0.0){
//                    Message msg = handler.obtainMessage();
//                    msg.what = IConstant.SHOW_NOTIFY_DIALOG;
//                    Bundle bundle = new Bundle();
//                    bundle.putInt(IConstant.DIALOG_TYPE, IConstant.APK_NOT_MATCH);
//                    bundle.putBoolean(IConstant.MANDATORY_UPDATE, false);
//                    msg.setData(bundle);
//                    handler.sendMessage(msg);
//                }
            }
        }
        return true;
    }

    public static boolean getRecordVideoThumb(FileInfo fileInfo, String outPath){
        if(fileInfo == null || TextUtils.isEmpty(outPath)){
            Dbug.e(tag, "getRecordVideoThumb parameter is empty!");
            return false;
        }
        String inPath = fileInfo.getPath();
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;

        boolean result = false;
        byte[] data = new byte[1024]; //1KB
        byte[] headData = new byte[300 * 1024]; //300KB
        long picSize = 0;
        int currentSize = 0;
        int dataLength;
        byte[] secPerFrame = new byte[4];
        byte[] allFrameCount = new byte[4];
        byte[] width = new byte[4];
        byte[] height = new byte[4];
        byte[] thumbSize = new byte[4];
        long totalFrames;
        long microSecPerFrame;
        long videoWidth;
        long videoHeight;
        long duration = 0;
        int firstThumbPos = -1;
        File recordFile = new File(inPath);
        if(recordFile.exists()){
            try{
                fileInputStream = new FileInputStream(recordFile);
                fileOutputStream = new FileOutputStream(outPath, true);
                while ((dataLength = fileInputStream.read(data)) != -1) {
                    fileOutputStream.write(data, 0, dataLength);
                    fileOutputStream.flush();
                    if ((currentSize + dataLength) <= headData.length) {
                        System.arraycopy(data, 0, headData, currentSize, dataLength);
                    }
                    currentSize = currentSize + dataLength;
                    if (currentSize < (headData.length - 1024)) {
                        if (currentSize >= (30 * 1024)) {
                            if (picSize == 0) {
                                System.arraycopy(headData, 32, secPerFrame, 0, 4);
                                System.arraycopy(headData, 48, allFrameCount, 0, 4);
                                System.arraycopy(headData, 64, width, 0, 4);
                                System.arraycopy(headData, 68, height, 0, 4);
                                totalFrames = BufChangeHex.getLong(allFrameCount, true);
                                microSecPerFrame = BufChangeHex.getLong(secPerFrame, true);
                                videoWidth = BufChangeHex.getLong(width, true);
                                videoHeight = BufChangeHex.getLong(height, true);
                                if (microSecPerFrame > 0) {
                                    if ((1000000 / microSecPerFrame) > 0) {
                                        if ((totalFrames % (1000000 / microSecPerFrame)) == 0) {
                                            duration = totalFrames / (1000000 / microSecPerFrame);
                                        } else {
                                            duration = totalFrames / (1000000 / microSecPerFrame) + 1;
                                        }
                                    }
                                }
                                fileInfo.setWidth(videoWidth);
                                fileInfo.setHeight(videoHeight);
                                fileInfo.setTotalTime(duration);


                                for (int i = 3; i < headData.length; i++){
                                    if (headData[i-3] == 0x30 && headData[i-2] == 0x30 && headData[i-1] == 0x64 && headData[i] == 0x63){
                                        firstThumbPos = i + 1;
                                        break;
                                    }
                                }
                                if(-1 != firstThumbPos){
                                    System.arraycopy(headData, firstThumbPos, thumbSize, 0, 4);
                                    picSize = BufChangeHex.getLong(thumbSize, true);
                                }
                                Dbug.w(tag, "getRecordVideoThumb firstThumbPos ==> " + firstThumbPos);
                                Dbug.w(tag, "getRecordVideoThumb thumbSize ==> " + BufChangeHex.encodeHexStr(thumbSize) + " ===== " + picSize);
                                Dbug.w(tag, "getRecordVideoThumb allFrameCount ==> " + BufChangeHex.encodeHexStr(allFrameCount) + " ===== " + totalFrames);
                                Dbug.w(tag, "getRecordVideoThumb secPerFrame ==> " + BufChangeHex.encodeHexStr(secPerFrame) + " ===== " + microSecPerFrame);
                                Dbug.w(tag, "getRecordVideoThumb duration =====> " + duration);
                                Dbug.w(tag, "getRecordVideoThumb width ==> " + BufChangeHex.encodeHexStr(width) + " ===== " + videoWidth);
                                Dbug.w(tag, "getRecordVideoThumb height ==> " + BufChangeHex.encodeHexStr(height) + " ===== " + videoHeight);
                                if (picSize == 0 || duration == 0) {
//                                    if(recordFile.delete()){
//                                        Dbug.e(tag, "record Video read error");
//                                    }
                                    result = false;
                                    break;
                                }
                            }
                        }
                        if (picSize > 0 && (currentSize >= (firstThumbPos + picSize + 1024))) {
                            byte[] thumbData = new byte[(int) picSize];
                            if (picSize + firstThumbPos + 4 <= headData.length) {
                                System.arraycopy(headData, firstThumbPos + 4, thumbData, 0, thumbData.length);
                            }
                            String prefixName = "";
                            String title = fileInfo.getTitle();
                            if (!TextUtils.isEmpty(title) && title.contains(".")) {
                                prefixName = title.substring(0, title.lastIndexOf("."));
                            }
                            String saveThumbName = prefixName + "_" + duration + ".jpg";
                            String recordPath = "";
                            if (outPath.contains("/")) {
                                recordPath = outPath.substring(0, outPath.lastIndexOf("/"));
                            }
                            result = BufChangeHex.byte2File(thumbData, recordPath, saveThumbName);
                            if (!result) {
                                Dbug.d(tag, "save image failed!");
                                File deleteFile = new File(recordPath + "/" + saveThumbName);
                                if (deleteFile.exists() && deleteFile.isFile()) {
                                    if (deleteFile.delete()) {
                                        Dbug.w(tag, "delete file ok");
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }catch (IOException e){
                e.printStackTrace();
            }finally {
                try{
                    if(fileInputStream != null){
                        fileInputStream.close();
                    }
                    if(fileOutputStream != null){
                        fileOutputStream.close();
                    }
                    File outPutFile = new File(outPath);
                    if(outPutFile.exists() && outPutFile.isFile()){
                        if(outPutFile.delete()){
                            Dbug.w(tag, "delete local file!");
                        }
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

//    /**Create APPName/UUID/download directory
//     * @param context current implement context
//     */
//    public static void  createAppDownloadDirectory(Context context){
//        MainApplication mApplication = (MainApplication) context.getApplicationContext();
//        if(mApplication == null || mApplication.getDeviceUUID()== null){
//            return;
//        }
//        String path = Environment.getExternalStorageDirectory().getPath() + "/"+ mApplication.getAppName();
//        File file = new File(path);
//        if(!file.exists()){
//            if(file.mkdir()){
//                Dbug.e(tag, "mkdir folder success, the path : " + path);
//            }
//        }
//        String uuidPath = Environment.getExternalStorageDirectory().getPath() + "/"+ mApplication.getAppName() +"/"+ mApplication.getDeviceUUID();
//        File uuidFile = new File(uuidPath);
//        if(!uuidFile.exists()){
//            if(uuidFile.mkdir()){
//                Dbug.e(tag, "mkdir sub folder success, the path : " + uuidFile);
//            }
//        }
//        String downPath = Environment.getExternalStorageDirectory().getPath() + "/"+ mApplication.getAppName() +"/"+mApplication.getDeviceUUID()+"/download/";
//        File downFile  = new File(downPath);
//        if(!downFile.exists()){
//            if(downFile.mkdir()){
//                Dbug.e(tag, "download folder mkdir success!");
//            }
//        }
//    }


    public static void downloadTxt(List<String> changePaths, boolean isOk, final String appName){
        if(null == changePaths || changePaths.size() == 0 || appName == null){
            Dbug.e(tag, " downloadTxt parameters is empty!");
            return;
        }
        if(isOk){
            changePaths.remove(0);
            failedNum = 0;
        }else{
            failedNum++;
            if(failedNum > 1){
                failedNum = 0;
                changePaths.remove(0);
            }
        }
        if(changePaths.size() > 0){
            String changePath = changePaths.get(0);
            FTPClientUtil ftpClientUtil = new FTPClientUtil();
            FTPClient mFTPClient = ftpClientUtil.getFTPClient();
            String versionPath = AppUtil.splicingFilePath(appName, VERSION, null, null);
            File versionDir =  new File(versionPath);
            if(!versionDir.exists()){
                if(versionDir.mkdir()){
                    Dbug.e(tag, " Create version directory success !");
                }
            }
            FileOutputStream outputStream = null;
            InputStream inputStream = null;
            int length;
            byte[] buffer = new byte[44 *1460];
            try{
                if(mFTPClient == null){
                    mFTPClient = new FTPClient();
                }
                if(ftpClientUtil.connectAndLoginFTP(FTP_HOST_NAME, DEFAULT_FTP_PORT, FTP_USER_NAME, FTP_PASSWORD, true, changePath)){
                    String[] filesName = mFTPClient.listNames();
                    boolean isSameValue = false;
                    if(filesName != null && filesName.length > 0){
                        for (String name : filesName){
                            Dbug.e(tag, " ftp list name : " + name);
                            if(VERSION_JSON.equals(name)){
                                isSameValue = true;
                                break;
                            }
                        }
                        if(isSameValue){
                            try{
                                String outPath = versionDir + File.separator + changePath + "_" + VERSION_JSON;
                                outputStream = new FileOutputStream(outPath);
                            }catch (IOException e){
                                e.printStackTrace();
                                Dbug.e(tag, "downloadTxt open file error  : " + e.getMessage());
                                ftpClientUtil.disconnect();
                                return;
                            }
                            inputStream = mFTPClient.retrieveFileStream(VERSION_JSON);
                            if(inputStream == null){
                                Dbug.e(tag, "downloadTxt inputStream is empty !");
                                ftpClientUtil.disconnect();
                                downloadTxt(changePaths, false, appName);
                                return;
                            }
                            while ((length = inputStream.read(buffer)) != -1){
                                outputStream.write(buffer, 0, length);
                            }
                            boolean result = mFTPClient.completePendingCommand();
                            if(result){
                                Dbug.e(tag, " download VERSION_JSON success");
                            }else{
                                Dbug.e(tag, " download VERSION_JSON failed");
                            }
                            ftpClientUtil.disconnect();
                            downloadTxt(changePaths, result, appName);
                        }
                    }else{
                        Dbug.e(tag, "filesName == null!");
                    }
                }else{
                    Dbug.e(tag, " connectAndLoginFTP failed!");
                    downloadTxt(changePaths, false, appName);
                }
            }catch (IOException e){
                Dbug.e(tag, "downloadTxt IOException : " +e.getMessage());
                e.printStackTrace();
                ftpClientUtil.disconnect();
            }finally {
                ftpClientUtil.disconnect();
                try{
                    if(inputStream != null){
                        inputStream.close();
                    }
                    if(outputStream != null){
                        outputStream.close();
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }

    /** 获取一个文件夹下的所有文件 **/
    public static ArrayList<SDFileInfo> getFiles(String path) {
        File f = new File(path);
        File[] files = f.listFiles();
        if (files == null) {
            return null;
        }

        ArrayList<SDFileInfo> fileList = new ArrayList<>();
        // 获取文件列表
        for (File file : files) {
            SDFileInfo fileInfo = new SDFileInfo();
            fileInfo.Name = file.getName();
            fileInfo.IsDirectory = file.isDirectory();
            fileInfo.Path = file.getPath();
            fileInfo.Size = file.length();
            fileList.add(fileInfo);
        }

        // 排序
        Collections.sort(fileList, new FileComparator());

        return fileList;
    }

    /**
     * 检查文件是否空文件夹
     * @param path    文件路径
     */
    public static boolean checkIsEmptyFolder(String path){
        if(!TextUtils.isEmpty(path)){
            File file = new File(path);
            if(file.exists() && file.isDirectory()){
                File[] files = file.listFiles();
                if (files == null || files.length == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 删除文件
     * @param context    上下文
     * @param filePath   文件路径
     * @param fileType   文件类型{@link IConstant#FILE_TYPE_VIDEO,IConstant#FILE_TYPE_IMAGE}
     * @return -1 : 失败
     */
    public static int deleteFileWithResolver(Context context, String filePath, int fileType){
        if(context == null) return -1;
        ContentResolver mContentResolver = context.getContentResolver();
        Uri uri = null;
        String where = null;
        if(!TextUtils.isEmpty(filePath) && fileType >= 0){
            switch (fileType){
                case FILE_TYPE_VIDEO://删除视频
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    where = MediaStore.Video.Media.DATA + "='" + filePath + "'";
                    break;
                case FILE_TYPE_IMAGE: //删除图片
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    where = MediaStore.Images.Media.DATA + "='" + filePath + "'";
                    break;
            }
            if(mContentResolver != null && uri != null && !TextUtils.isEmpty(where)){
               return mContentResolver.delete(uri, where, null);
            }
        }
        return -1;
    }

    public static Locale getLanguage(String languageCode){
        Locale locale = null;
        switch (languageCode) {
            case ARGS_LANG_ZH_CN:
                locale= (Locale.SIMPLIFIED_CHINESE);
                break;
            case ARGS_LANG_ZH_TW:
                locale=(Locale.TRADITIONAL_CHINESE);
                break;
            case ARGS_LANG_EN_US:
                locale=(Locale.US);
                break;
            case ARGS_LANG_DE_DE:
                locale=(Locale.GERMANY);
                break;
            case ARGS_LANG_JA_JP:
                locale=(Locale.JAPAN);
                break;
            case ARGS_LANG_ES_ES:
                locale = new Locale("es", "ES");
                break;
            case ARGS_LANG_KO_KR:
                locale = new Locale("ko", "KR");
                break;
            case ARGS_LANG_FR_LU://7
                locale = new Locale("fr", "LU");
                break;
            case ARGS_LANG_IT_IT://8
                locale = new Locale("it", "IT");
                break;
            case ARGS_LANG_NL_NL://9
                locale = new Locale("nl", "KR");
                break;
            case ARGS_LANG_PT_PT://10
                locale = new Locale("pt", "PT");
                break;
            case ARGS_LANG_SV_SE://11
                locale = new Locale("sv", "SE");
                break;
            case ARGS_LANG_CS_CZ://12
                locale = new Locale("cs", "CZ");
                break;
            case ARGS_LANG_DA_DK://13
                locale = new Locale("da", "DK");
                break;
            case ARGS_LANG_PL_PL://14
                locale = new Locale("pl", "PL");
                break;
            case ARGS_LANG_RU_RU://15
                locale = new Locale("ru", "RU");
                break;
            case ARGS_LANG_TR_TR://16
                locale = new Locale("tr", "TR");
                break;
            case ARGS_LANG_HE_IL://17
                locale = new Locale("he", "IL");
                break;
            case ARGS_LANG_TH_TH://18
                locale = new Locale("th", "TH");
                break;
            case ARGS_LANG_HU_HU://19
                locale = new Locale("hu", "HU");
                break;
            case ARGS_LANG_RO_RO://20
                locale = new Locale("ro", "RO");
                break;
            case ARGS_LANG_AR_AR://21
                locale = new Locale("ar", "AR");
                break;
            default:
                Dbug.e(tag, "Unknown language code" + languageCode);
                break;
        }
        return locale;
    }

/*    public static final String[] LANGUAGES = {
            ARGS_LANG_ZH_CN,//0
            ARGS_LANG_ZH_TW,//1
            ARGS_LANG_EN_US,//2
            ARGS_LANG_DE_DE,//3
            ARGS_LANG_JA_JP,//4
            ARGS_LANG_ES_ES,//5
            ARGS_LANG_KO_KR,//6

            ARGS_LANG_FR_LU,//7
            ARGS_LANG_IT_IT,//8
            ARGS_LANG_NL_NL,//9
            ARGS_LANG_PT_PT,//10
            ARGS_LANG_SV_SE,//11
            ARGS_LANG_CS_CZ,//12
            ARGS_LANG_DA_DK,//13
            ARGS_LANG_PL_PL,//14
            ARGS_LANG_RU_RU,//15
            ARGS_LANG_TR_TR,//16
            ARGS_LANG_HE_IL,//17
            ARGS_LANG_TH_TH,//18
            ARGS_LANG_HU_HU,//19
            ARGS_LANG_RO_RO,//20
            ARGS_LANG_AR_AR//21
    };*/
}
