package com.jieli.stream.player.tool;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.*;
import android.os.Process;
import android.text.TextUtils;

import com.jieli.stream.player.MainApplication;
import com.jieli.stream.player.R;
import com.jieli.stream.player.data.beans.FTPLoginInfo;
import com.jieli.stream.player.data.beans.FileInfo;
import com.jieli.stream.player.util.AppUtil;
import com.jieli.stream.player.util.BufChangeHex;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IConstant;
import com.jieli.stream.player.util.ScanFilesHelper;
import com.jieli.stream.player.util.TimeFormater;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * class name: FtpHandlerThread
 * function : Manage FTP operations
 * @author JL
 * create time : 2015-11-07 16:30
 * version : v1.0
 * ///////////////////////////////////////////////////////////////////////////
 *
 */
public class FtpHandlerThread extends HandlerThread implements IConstant, Handler.Callback{
    private final String tag = getClass().getSimpleName();
    public static final int MSG_CONNECT_SERVER = 0x99;
    public static final int MSG_CHANGE_TO_SUBDIR = 0x100;
    public static final int MSG_CHANGE_TO_PARENT_DIR = 0x101;
    public static final int MSG_UPDATE_UI = 0x102;
    public static final int MSG_DELETE_SUCCESS = 0x103;
    public static final int MSG_SHOW_MESSAGES = 0x104;
    public static final int MSG_DOWNLOAD = 0x105;
    public static final int MSG_UPLOAD = 0x106;
    public static final int MSG_DELETE = 0x107;
    public static final int MSG_CREATE = 0x108;
    public static final int MSG_RENAME = 0x109;
    public static final int MSG_FTP_LOGOUT = 0x10A;
    public static final int MSG_VIDEO_MESSAGE = 0x10B;
    public static final int MSG_CANCEL_THREAD_POOL = 0x10C;
    public static final int CURRENT_DOWNLOAD_PROGRESS = 0x10D;

    private Handler mWorkerHandler;
    private Handler mUIHandler;
    private FTPClient mFTPClient;
    private ScanFilesHelper scanFilesHelper;
    private final Context context;
    private MainApplication mApplication;
    private String ftpAdd;
    private String userName;
    private String password;
    private String rootPath = "";
    private int port = -1;
    private boolean isLoading = false;
    private boolean isThumbLoading = false;
    private boolean isFileLoaded = false;

    private StringBuffer mCurrentPath = new StringBuffer();
    private List<FileInfo> mList = new ArrayList<>();
    private ExecutorService servie = null;
    private Future<String> future = null;
    private boolean isAVI = false;
    private boolean isStopDownLoadThread = false;
    private boolean isDestroyThread = false;
    private FTPFile[] ftpFiles = null;

	private static final String FTP_SERVER_DIR_FRONT = "/DCIMA";
	private static final String FTP_SERVER_DIR_REAR = "/DCIMB";
	private String mCurrBrowsingDevDir;

    public FtpHandlerThread(String name, Context mContext, MainApplication application) {
        super(name, Process.THREAD_PRIORITY_URGENT_AUDIO);
        this.context = mContext;
        this.mApplication = application;
        init();
    }

    @Override
    protected void onLooperPrepared() {
        mWorkerHandler = new Handler(getLooper(), this);
    }

    public Handler getWorkHandler(){
        return mWorkerHandler;
    }

    public void setUIHandler(Handler handler){
        mUIHandler = handler;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what){
            case MSG_CONNECT_SERVER:
                if(mFTPClient == null){
                    mFTPClient = new FTPClient();
                }
                FTPLoginInfo ftpLoginInfo = (FTPLoginInfo) msg.getData().getSerializable(IConstant.FTP_LOGIN_INFO);
                String newpath = (String)msg.obj;
                boolean openlist = msg.getData().getBoolean(IConstant.LIST_FTP_OPERATION);
	            String whichDir = msg.getData().getString("which_dir", IConstant.VIEW_FRONT);
	            switch (whichDir){
		            case IConstant.VIEW_FRONT:
			            mCurrBrowsingDevDir = FTP_SERVER_DIR_FRONT;
			            break;
		            case IConstant.VIEW_REAR:
			            mCurrBrowsingDevDir = FTP_SERVER_DIR_REAR;
			            break;
	            }
                if (ftpLoginInfo == null || TextUtils.isEmpty(ftpLoginInfo.getHostname())
                        || TextUtils.isEmpty(ftpLoginInfo.getUserName())|| TextUtils.isEmpty(ftpLoginInfo.getPassword())){
                    sendResult(context.getString(R.string.login_info_err));
                    return false;
                }
                ftpAdd = ftpLoginInfo.getHostname();
                userName = ftpLoginInfo.getUserName();
                password = ftpLoginInfo.getPassword();
                port = ftpLoginInfo.getPort();

                try {
                    boolean isSuccess = connectAndLoginFTP(ftpAdd, port, userName, password, false);
                    if (isSuccess){
                        Dbug.d(tag, "login success...ReplyCode=" + mFTPClient.getReplyCode());
                        if(newpath != null && !newpath.equals(mCurrentPath.toString())){
                            mCurrentPath.delete(0, mCurrentPath.length());
                            mCurrentPath = mCurrentPath.append(newpath);
                            if(!mFTPClient.changeWorkingDirectory(newpath)){
                                Dbug.d(tag, "changeWorkingDirectory failed!");
                                sendResult(context.getString(R.string.ftp_client_exception));
                                disconnect();
                                return false;
                            }
                        }
                        if(TextUtils.isEmpty(mCurrentPath.toString())){
                            sendResult(context.getString(R.string.ftp_client_exception));
                            disconnect();
                            return false;
                        }else{
                            if(mFTPClient != null){
                                if(openlist){
                                    sendResult(context.getString(R.string.read_data));
                                    mFTPClient.enterLocalPassiveMode();
                                    ftpFiles = mFTPClient.mlistDir(mCurrentPath.toString());
                                }else{
                                    sendResult(context.getString(R.string.read_data));
                                    if(ftpFiles != null && ftpFiles.length == 0){
                                        mFTPClient.enterLocalPassiveMode();
                                        ftpFiles = mFTPClient.mlistDir(mCurrentPath.toString());
                                    }
                                }
                            }
                        }
                        if(null == ftpFiles || ftpFiles.length == 0){
                            sendResult(context.getString(R.string.ftp_client_exception));
                            disconnect();
                            return false;
                        }
                        Dbug.i(tag, "root directory=" + rootPath + ", ftpFiles size="+ftpFiles.length + " time : " + TimeFormater.formatYMDHMS(Calendar.getInstance()));
                        if (mList != null && mList.size() > 0){
                            mList.clear();
                        }
                        for (int i = ftpFiles.length - 1; i >= 0 ; i--){
                            FTPFile f = ftpFiles[i];
                            FileInfo fileInfo = new FileInfo();
                            if (f.isDirectory()){
                                fileInfo.setDirectory(true);
                            } else {
                                fileInfo.setDirectory(false);
                            }
                            if(openlist){
                                Dbug.i(tag, "mlsd file format : " + f.getRawListing());
                            }
                            TimeFormater.getFormatedDateString(0);
                            if(openlist){
                                Dbug.i(tag, "mlsd file date : " + TimeFormater.formatYMDHMS(f.getTimestamp()));
                            }
                            fileInfo.setDateMes(getDateText(f.getRawListing()));
                            fileInfo.setSize(f.getSize());
                            fileInfo.setPath(mCurrentPath.toString());
                            fileInfo.setTitle(f.getName());
                            fileInfo.setCreateDate(TimeFormater.formatYMDHMS(f.getTimestamp()));
                            if(mList != null && fileInfo.getSize() > 1024 * 10){
                                mList.add(fileInfo);
                            }
                        }
                        if(mList != null){
                            String[] ftpFileDate =  new String[mList.size()];
                            for (int i = 0; i < mList.size(); i++){
                                FileInfo info = mList.get(i);
                                if(info == null) continue;
                                String fileDate = info.getDateMes();
                                if(!TextUtils.isEmpty(fileDate)){
                                    ftpFileDate[i] = fileDate;
                                }
                            }
                            ftpFileDate = sort(ftpFileDate);
                            List<FileInfo> temList = new ArrayList<>();
                            if(ftpFileDate != null && ftpFileDate.length > 0){
                                for(String fileDate : ftpFileDate){
                                    for (int j = 0; j < mList.size(); j++){
                                        FileInfo info = mList.get(j);
                                        if(info == null) continue;
                                        if(fileDate.equals(info.getDateMes())){
                                            temList.add(info);
                                            mList.remove(info);
                                            break;
                                        }
                                    }
                                }
                            }
                            mList = temList;
                        }

                        Message message = Message.obtain();
                        message.what = MSG_UPDATE_UI;
                        message.obj = mList;
                        mUIHandler.sendMessage(message);
                        TimeFormater.getFormatedDateString(8);
                        Dbug.e(tag, "MSG_UPDATE_UI is send!"+ " time : " + TimeFormater.formatYMDHMS(Calendar.getInstance()));
                        disconnect();
                    }
                } catch (IOException e) {
                    disconnect();
                    sendResult(context.getString(R.string.ftp_client_exception));
                    Dbug.e(tag, "IOException err =" + e.getMessage());
                    e.printStackTrace();
                }
                break;
            case MSG_CHANGE_TO_SUBDIR:
                if (mFTPClient != null){
                    String current;
                    Bundle bundle = msg.getData();
                    String fileName = bundle.getString(IConstant.FILE_NAME);
                    if(fileName == null || fileName.isEmpty()){
                        return false;
                    }
                    try {
                        current = mFTPClient.printWorkingDirectory();
                        mCurrentPath.delete(0, mCurrentPath.length());
                        if(current == null || current.equals("")){
                            Dbug.e(tag, "current is null!");
                            sendResult(context.getString(R.string.ftp_client_exception));
                            return false;
                        }
                        if (current.equals(rootPath)){
                            /*It is root directory*/
                            String content = current + fileName;
                            mCurrentPath.append(content);
                        } else {
                            String content = current + "/" + fileName;
                            mCurrentPath.append(content);
                        }
                        Dbug.e(tag, "printWorkingDirectory==" + current + ", Change path =" + mCurrentPath.toString());
                    } catch (IOException e) {
                        disconnect();
                        e.printStackTrace();
                    }

                    try {
                        mFTPClient.changeWorkingDirectory(mCurrentPath.toString());
                    } catch (IOException e) {
                        disconnect();
                        e.printStackTrace();
                    }

                    try {
                        ftpFiles = mFTPClient.listFiles(mCurrentPath.toString());
                        /*Working directory not empty*/
                        if (ftpFiles != null && ftpFiles.length >= 0){
                            mList.clear();
                            for (FTPFile f : ftpFiles){
                                FileInfo fileInfo = new FileInfo();
                                if (f.isDirectory()){
                                    fileInfo.setDirectory(true);
                                } else {
                                    fileInfo.setDirectory(false);
                                }
                                fileInfo.setPath(mCurrentPath.toString());
                                fileInfo.setTitle(f.getName());
                                if(!fileInfo.isDirectory()){
                                    mList.add(fileInfo);
                                }
                            }
                            Message message = Message.obtain();
                            message.what = MSG_UPDATE_UI;
                            message.obj = mList;
                            mUIHandler.sendMessage(message);
                        } else {
                            sendResult(context.getString(R.string.open_file_err));
                        }
                    } catch (IOException e) {
                        Dbug.e(tag, " error =" + e.getMessage());
                        sendResult(context.getString(R.string.ftp_client_exception));
                        disconnect();
                        e.printStackTrace();
                    }
                } else {
                    Dbug.e(tag, "FTPClient object is null");
                    mCurrentPath.delete(0, mCurrentPath.length());
                    try {
                        String current = mFTPClient.printWorkingDirectory();
                        mCurrentPath.append(current);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case MSG_CHANGE_TO_PARENT_DIR:
                if (mFTPClient != null){
                    try {
                        connectAndLoginFTP(ftpAdd, port, userName, password, false);
                        boolean ischange = mFTPClient.changeToParentDirectory();
                        if(!ischange){
                            Dbug.e(tag, "FTPClient changeToParentDirectory error! ");
                            sendResult(context.getString(R.string.ftp_client_exception));
                            return false;
                        }
                        String current = mFTPClient.printWorkingDirectory();
                        Dbug.e(tag, "FTPClient current= "+current);
                        if(null == current || current.isEmpty()){
                            Dbug.e(tag, "FTPClient current is null! ");
                            sendResult(context.getString(R.string.ftp_client_exception));
                            return false;
                        }
                        ftpFiles = mFTPClient.listFiles(current);
                        /*Working directory not empty*/
                        if (ftpFiles != null && ftpFiles.length > 0){
                            mList.clear();
                            for (FTPFile f : ftpFiles){
                                FileInfo fileInfo = new FileInfo();
                                if (f.isDirectory()){
                                    fileInfo.setDirectory(true);
                                } else {
                                    fileInfo.setDirectory(false);
                                }
                                fileInfo.setPath(current);
                                fileInfo.setTitle(f.getName());
                                mList.add(fileInfo);
                            }
                            Message message = Message.obtain();
                            message.what = MSG_UPDATE_UI;
                            message.obj = mList;
                            mUIHandler.sendMessage(message);
                        } else {
                            sendResult(context.getString(R.string.open_file_err));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Dbug.e(tag, "FTPClient object is null");
                    mCurrentPath.delete(0, mCurrentPath.length());
                }
                break;
            case MSG_DOWNLOAD:
                if(mFTPClient == null){
                    mFTPClient = new FTPClient();
                }
                mWorkerHandler.removeMessages(MSG_DOWNLOAD);
                Bundle bundle1 = msg.getData();
                String filename = bundle1.getString(IConstant.SELECTED_FILE_NAME, null);
                String localpath = bundle1.getString(IConstant.DOWNLOAD_LOCAL_PATH_NAME, null);
                Dbug.d(tag, " name == " + filename);
                downLoadFile(filename, localpath, msg.getData().getBoolean(VIEW_REAR, false));
                break;
            case MSG_UPLOAD:
                if(mFTPClient == null){
                    mFTPClient = new FTPClient();
                }
                FTPLoginInfo ftpInfo = (FTPLoginInfo) msg.getData().getSerializable(IConstant.FTP_LOGIN_INFO);
                if (ftpInfo == null){
                    sendResult(context.getString(R.string.upload_file_failed));
                    return false;
                }
                ftpAdd = ftpInfo.getHostname();
                userName = ftpInfo.getUserName();
                password = ftpInfo.getPassword();
                port = ftpInfo.getPort();

                if (mFTPClient != null){
                    Bundle bundle = msg.getData();
                    String fileName = bundle.getString(IConstant.REMOTE_FILE_NAME, null);
                    String localFile = bundle.getString(IConstant.SELECTED_FILE_NAME, null);
                    InputStream inputStream = null;
                    if(connectAndLoginFTP(ftpAdd, port, userName, password, true)){
                        Dbug.e("PersonalSettingActivity", "connectAndLoginFTP ==> true");
                        String remotePathName;
                        remotePathName = fileName;
//                        String current = mCurrentPath.toString();
//                        if (!TextUtils.isEmpty(current) && current.equals(rootPath)){
//                            /*It is root directory*/
//                            remotePathName = (current + fileName);
//                        } else {
//                            remotePathName = (current + File.separator + fileName);
//                        }
                        try {
                            try {
                                inputStream = new FileInputStream(localFile);
                            } catch (FileNotFoundException e) {
                                Dbug.e("PersonalSettingActivity", "FileNotFoundException ==> " +e.getMessage());
                                e.printStackTrace();
                            }
                            mFTPClient.enterLocalPassiveMode();
                            mFTPClient.setFileType(FTP.BINARY_FILE_TYPE);
                            mFTPClient.setBufferSize(1024 * 1024 * 5); //5M
                            Dbug.e("PersonalSettingActivity", "remotePathName = " + remotePathName +"  inputStream = " + localFile);
                            if(mFTPClient.storeFile(remotePathName, inputStream)){
                                sendResult(context.getString(R.string.upload_file_success));
                                Dbug.d("PersonalSettingActivity", "Upload success");
                            }else{
                                sendResult(context.getString(R.string.upload_file_failed));
                                Dbug.e("PersonalSettingActivity", "Upload failed");
                            }
                        } catch (IOException e) {
                            Dbug.e("PersonalSettingActivity", "IOException ==> " + e.getMessage());
                            e.printStackTrace();
                            sendResult(context.getString(R.string.upload_file_failed));
                        }finally {
                            disconnect();
                        }
                    }else{
                        sendResult(context.getString(R.string.upload_file_failed));
                    }
                } else {
                    Dbug.e(tag, "FTPClient object is null");
                    sendResult(context.getString(R.string.upload_file_failed));
                }
                break;
            case MSG_DELETE:
                if(mFTPClient == null){
                    mFTPClient = new FTPClient();
                }
                try {
                    int position = msg.arg1;
                    String fileName = (String) msg.obj;
                    String path = "";
                    if(!connectAndLoginFTP(ftpAdd, port, userName, password, false)){
                        sendResult(context.getString(R.string.delete_file_failed));
                        disconnect();
                        return false;
                    }
                    if (TextUtils.isEmpty(mCurrentPath.toString()) || mApplication.getDeviceUUID() == null) {
                        sendResult(context.getString(R.string.delete_file_failed));
                        disconnect();
                        return false;
                    }
                    path = path + "/" + fileName;
                    Dbug.d(tag, " delete file name : " + fileName + "  position = " + position + "  delete file path : " + path );
                    if (mFTPClient.deleteFile(fileName)){
                        String localPath;
                        boolean isRearView = msg.getData().getBoolean(VIEW_REAR, false);
                        if((fileName.contains(".mov") ||fileName.contains(".MOV") || fileName.contains(".mp4")
                                || fileName.contains(".MP4")|| fileName.contains(".avi") || fileName.contains(".AVI"))) {
                            String videoThumbPath = AppUtil.getAppStoragePath(mApplication, VIDEO, isRearView);
                            String oldName = BufChangeHex.getVideoThumb(fileName, videoThumbPath);
                            localPath = videoThumbPath + File.separator + oldName;
                        }else{
                            localPath = AppUtil.getAppStoragePath(mApplication, IMAGE, isRearView) + File.separator + fileName;
                        }
                        File file = new File(localPath);
                        if(file.exists() && file.isFile()){
                            if(file.delete()){
                                Dbug.d(tag, " delete ftp file and local thumb.");
                            }
                        }
                        sendOperation(MSG_DELETE_SUCCESS, position, fileName);
                        removeFtpFiles(fileName);
//                            mFTPClient.completePendingCommand();
                    } else {
                        sendResult(context.getString(R.string.delete_file_failed));
                    }
                } catch (IOException e) {
                    Dbug.e(tag, " MSG_DELETE IOException == "  + e.getMessage());
                    sendResult(context.getString(R.string.delete_file_failed));
                    e.printStackTrace();
                    disconnect();
                } catch (Exception e){
                    Dbug.e(tag, " MSG_DELETE Exception == "  + e.getMessage());
                    sendResult(context.getString(R.string.delete_file_failed));
                    e.printStackTrace();
                    disconnect();
                }finally {
                    disconnect();
                }
                break;
            case MSG_RENAME:
                if(mFTPClient == null){
                    mFTPClient = new FTPClient();
                }
                try {
                    Bundle bundle = msg.getData();
                    String newName = bundle.getString(IConstant.REMOTE_FILE_NAME, null);
                    String oldName = bundle.getString(IConstant.SELECTED_FILE_NAME, null);
                    if(TextUtils.isEmpty(newName) || TextUtils.isEmpty(oldName)){
                        sendResult(context.getString(R.string.rename_failed));
                        return false;
                    }

                    if(connectAndLoginFTP(ftpAdd, port, userName, password, false)){
                        if (TextUtils.isEmpty(mCurrentPath.toString()) || mApplication.getDeviceUUID() == null) {
                            sendResult(context.getString(R.string.rename_failed));
                            disconnect();
                            return false;
                        }
                        boolean isSuccess = mFTPClient.rename(oldName, newName);
                        if (isSuccess){
                            sendResult(context.getString(R.string.rename_success));
                            boolean isRearViewFile = msg.getData().getBoolean(VIEW_REAR, false);
                            String thumbPath = AppUtil.getAppStoragePath(mApplication, VIDEO, isRearViewFile);
                            String newThumbName = BufChangeHex.getVideoThumb(oldName, thumbPath);
                            if(!TextUtils.isEmpty(newThumbName)){
                                File deleteThumb = new File(thumbPath + File.separator + newThumbName);
                                if(deleteThumb.exists() && deleteThumb.isFile()){
                                    if(deleteThumb.delete()){
                                        Dbug.w(tag, " ReName ftp file success,so delete thumb!");
                                    }
                                }
                            }
                        } else {
                            sendResult(context.getString(R.string.rename_failed));
                        }
                        refreshFTPFiles(oldName, newName);
                    }else{
                        sendResult(context.getString(R.string.rename_failed));
                        Dbug.e(tag, "ReName ftp file success,so delete thumb!");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    sendResult(context.getString(R.string.ftp_client_exception));
                    disconnect();
                }finally {
                    disconnect();
                }
                break;
            case MSG_FTP_LOGOUT:
                if (mFTPClient != null && mFTPClient.isConnected()){
                    try {
                        mFTPClient.disconnect();
                    } catch (IOException e) {
                        sendResult(context.getString(R.string.ftp_client_exception));
                        Dbug.e(tag, "IOException 11--> " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                break;
            case MSG_CANCEL_THREAD_POOL:
                if(Thread.currentThread().isAlive()){
                    Thread.currentThread().interrupt();
                }
                if(future != null){
                    future.cancel(true);
                }
                if(servie != null){
                    servie.shutdownNow();
                }
                break;
        }
        return false;
    }

    private synchronized boolean connectAndLoginFTP(String hostName, int port, String user, String pwd, boolean root){
        if(mFTPClient == null || hostName == null || hostName.isEmpty() || user == null ||
                user.isEmpty() || pwd == null || pwd.isEmpty()){
            Dbug.e(tag, "Parameter is null!");
            return false;
        }
        try {
//            mFTPClient.setControlEncoding("UTF-8");
            mFTPClient.setDefaultPort(port);
            mFTPClient.connect(hostName);
            if(FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())){
                if(mFTPClient.login(user, password)){
                    mFTPClient.setConnectTimeout(3000);
                    mFTPClient.enterLocalPassiveMode();
                    rootPath = mFTPClient.printWorkingDirectory();
                    if(mCurrentPath != null){
                        mCurrentPath.delete(0, mCurrentPath.length());
                        mCurrentPath = mCurrentPath.append(rootPath);
                    }
                    if(!root){
                        String defaultPath = rootPath + mCurrBrowsingDevDir;
	                    Dbug.e(tag, "default path="+ defaultPath);
                        if(!mFTPClient.changeWorkingDirectory(defaultPath)){
                            Dbug.d(tag, "changeWorkingDirectory failed!");
                            sendResult(context.getString(R.string.ftp_client_exception));
                            disconnect();
                            return false;
                        }
                        if(mCurrentPath != null){
                            mCurrentPath.delete(0, mCurrentPath.length());
                            mCurrentPath = mCurrentPath.append(defaultPath);
                        }
                    }
                    return true;
                }
            }
            disconnect();
            return false;
        }catch (SocketException e){
            sendResult(context.getString(R.string.ftp_socket_err));
            Dbug.e(tag, "Socket SocketException = " +e.getMessage());
            e.printStackTrace();
        }catch (IOException e) {
            sendResult(context.getString(R.string.ftp_client_exception));
            Dbug.e(tag, "Socket IOException = " + e.getMessage());
            e.printStackTrace();
        }
        disconnect();
        return false;
    }

    private void downloadThreadPool(final String filename, final String filepath, final String date, final boolean isRearViewFile){
        if(filename == null || filename.equals("") || filepath == null || filepath.equals("") || isDestroyThread){
            Dbug.e(tag, "filename, localPath is null!");
            return;
        }
        try{
            future =  servie.submit(new Runnable() {
                @Override
                public void run() {
                    Dbug.e(tag, "current Thread name ：" + Thread.currentThread().getName() + " filename = " + filename);
                    try {
                        downloadFileThumb(filename, filepath, date, isRearViewFile);
                    } catch (Exception e) {
                        sendResult(context.getString(R.string.download_thumb_failed));
                        Dbug.e(tag, " downloadThreadPool err =" + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }, filename);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void downLoadFile(String filename, String localPath, boolean isRearViewFile){
        if(TextUtils.isEmpty(filename) || TextUtils.isEmpty(localPath)
                || isDestroyThread || mApplication.getDeviceUUID() == null){
            Dbug.e(tag, "filename, localPath is null!");
            isLoading = false;
            return;
        }
        OutputStream outputStream = null;
        InputStream inputStream;
        try{
            if(null == mFTPClient){
                mFTPClient = new FTPClient();
            }
            if(connectAndLoginFTP(ftpAdd, port, userName, password, false)){
                if(!TextUtils.isEmpty(mCurrentPath.toString())){
                    if(!mCurrentPath.toString().equals(mFTPClient.printWorkingDirectory())){
                        mCurrentPath.delete(0, mCurrentPath.length());
                        mCurrentPath.append(mFTPClient.printWorkingDirectory());
                    }
                }
                if(ftpFiles != null && ftpFiles.length == 0){
                    ftpFiles = mFTPClient.listFiles(mCurrentPath.toString());
                }
                FTPFile downloadFile = null;
                if(ftpFiles == null || ftpFiles.length == 0){
                    Dbug.e(tag, "files.length == 0");
                    sendResult(context.getString(R.string.download_file_err));
                    isLoading = false;
                    return;
                }
                for(FTPFile file : ftpFiles){
                    if(file.getName().equals(filename)){
                        Dbug.d(tag, "download file name : "+ filename+" , download file size : " + file.getSize());
                        downloadFile = file;
                        break;
                    }
                }
                if(downloadFile == null){
                    Dbug.e(tag, "downloadFile is null!");
                    sendResult(context.getString(R.string.download_file_err));
                    isLoading = false;
                    return;
                }
                long ftpFileSize = downloadFile.getSize();
                Dbug.d(tag, "ftp download file size : " + ftpFileSize + " phone free size : " + BufChangeHex.readSDCard());
                if(ftpFileSize > 0 && BufChangeHex.readSDCard() <= ftpFileSize + 5*1024*1024){
                    Dbug.e(tag, "Your phone storage space is insufficient!");
                    sendResultDelay(context.getString(R.string.phone_space_inefficient), 1000);
                    isLoading = false;
                    return;
                }
                File localFile = new File(localPath);
//            long localFileSize = 0;
                if(localFile.exists()){
                    isFileLoaded = true;
                    isLoading = false;
                    return;
                }
                //progress
                long step = ftpFileSize / 100;
                long progress = 0;
                long currentSize = 0;

                //prepare to download file
//            mFTPClient.enterLocalActiveMode();
//            mFTPClient.setReceieveDataSocketBufferSize(30 * 1460);
                mFTPClient.setFileType(FTP.BINARY_FILE_TYPE);
                mFTPClient.enterLocalPassiveMode();
//            mFTPClient.setBufferSize(40 * 1460); // 32KB
                outputStream = new FileOutputStream(localPath, true);
//            mFTPClient.setRestartOffset(localFileSize);
                inputStream = mFTPClient.retrieveFileStream(filename);
                byte[] data = new byte[44 * 1460];
//            byte[] data = new byte[64 * 1024]; //64KB
                int length;
                if(inputStream == null){
                    Dbug.e(tag, "inputStream is null filename = " + filename);
                    File file = new File(localPath);
                    if (file.exists() && file.isFile()) {
                        if(file.delete()){
                            Dbug.e(tag, "inputStream is null,so delete local file. localPath = " + localPath);
                        }
                    }
                    outputStream.flush();
//                outputStream.close();
                    isLoading = false;
                    sendResult(context.getString(R.string.download_file_err));
                    return;
                }

                sendResult(context.getString(R.string.download_task_start));
                isLoading = true;
                while((length = inputStream.read(data)) != -1) {

                    if (isStopDownLoadThread) {
                        File deleteFile = new File(localPath);
                        if(deleteFile.exists() && deleteFile.isFile()){
                            if(deleteFile.delete()){
                                Dbug.e(tag, "download task is aborted,so delete local file.");
                            }
                        }
                        break;
                    }

                    outputStream.write(data, 0, length);
                    outputStream.flush();

                    currentSize = currentSize + length;
                    if (currentSize / step != progress) {
                        progress = currentSize / step;
                        if (progress >= 0 && progress <= 100) {
                            sendOperation(CURRENT_DOWNLOAD_PROGRESS, (int) progress, filename);
//                            sendResult("current download file progress :" + progress);
                        }
                    }
                }

                if(!isStopDownLoadThread){
                    if(currentSize < ftpFileSize){
                        Dbug.e(tag, context.getString(R.string.incomplete_download) + "  currentSize : " + currentSize + " << >>  ftpFileSize : " +ftpFileSize);
                    }
                }
                inputStream.close();
                outputStream.flush();
//            outputStream.close();

            }else{
                isLoading = false;
                return;
            }
        }catch (IOException e){
            Dbug.e(tag, "IOException =====> 01 " + e.getMessage());
            e.printStackTrace();
            isLoading = false;
            disconnect();
        }catch (Exception ex){
            Dbug.e(tag, "Exception =====> 02 " + ex.getMessage());
            ex.printStackTrace();
            isLoading = false;
            disconnect();
        }finally {
            try{
                if(isLoading){
                    isLoading = false;
                    if(mFTPClient != null && mFTPClient.isConnected()){
                        if(mFTPClient.getReply() == 226){
                            Dbug.d(tag, "download == true ");
                            if(outputStream != null){
                                outputStream.close();
                            }
                            disconnect();
                            if(isStopDownLoadThread){
                                sendResult(context.getString(R.string.download_task_abort));
                                isStopDownLoadThread = false;
                            }else{
                                sendResult(context.getString(R.string.download_file_success));
                            }
                            try {
                                if(scanFilesHelper == null){
                                    scanFilesHelper = new ScanFilesHelper(context);
                                }
                                if(!TextUtils.isEmpty(localPath)){
                                    scanFilesHelper.scanFiles(localPath);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            String downFileThumbPath = localPath.substring(0, localPath.lastIndexOf("/")) + File.separator + SUB_THUMB;
                            File file = new File(downFileThumbPath);
                            if(!file.exists()){
                                if(file.mkdir()){
                                    Dbug.e(tag, "mkdir '../download/Thumb/' folder success!");
                                }
                            }
                            String oldFilePath = AppUtil.getAppStoragePath(mApplication, VIDEO, isRearViewFile);
                            String thumbFilePath = oldFilePath + File.separator + BufChangeHex.getVideoThumb(filename, oldFilePath);
                            Dbug.e(tag, " thumbFilePath : " + thumbFilePath);
                            String videoTime = BufChangeHex.getVideoDuration(thumbFilePath);
                            if(localPath.contains("/")){
                                String temp = localPath.substring(localPath.lastIndexOf("/")+1);
                                if(temp.contains(".")){
                                    String newName = temp.substring(0, temp.indexOf("."))+"_"+ videoTime + ".jpg";
                                    downFileThumbPath = downFileThumbPath + File.separator + newName;
                                    copyFile(thumbFilePath, downFileThumbPath);
                                }
                            }
                        }else{
                            Dbug.d(tag, "download == false ");
                            File deleteFile = new File(localPath);
                            if (deleteFile.exists() && deleteFile.isFile()) {
                                if(deleteFile.delete()){
                                    Dbug.e(tag, "download file failed or downloaded file, so delete local file!");
                                }
                            }
                            if(!isStopDownLoadThread){
                                sendResult(context.getString(R.string.download_file_failed));
                            }else{
                                sendResult(context.getString(R.string.download_task_abort));
                                isStopDownLoadThread = false;
                            }
                            if(outputStream != null){
                                outputStream.close();
                            }
                            disconnect();
                        }
                    }else{
                        sendResultAndMessage(context.getString(R.string.download_file_failed), filename);
                        if(outputStream != null){
                            outputStream.close();
                        }
                        File deleteFile = new File(localPath);
                        if (deleteFile.exists() && deleteFile.isFile()) {
                            if(deleteFile.delete()){
                                Dbug.e(tag, "download file failed, so delete local file!");
                            }
                        }
                        disconnect();
                    }
                }else{
                    if(!isFileLoaded){
                        File deleteFile = new File(localPath);
                        if (deleteFile.exists() && deleteFile.isFile()) {
                            if(deleteFile.delete()){
                                Dbug.e(tag, "download task failed, so delete local file!");
                            }
                        }
                        sendResult(context.getString(R.string.download_file_failed));
                    }else{
                        isFileLoaded = false;
                        sendResult(context.getString(R.string.download_file_downloaded));
                    }
                    if(outputStream != null){
                        outputStream.close();
                    }
                    disconnect();
                }
            }catch (IOException e){
                Dbug.e(tag, "Exception =====> 03 " + e.getMessage());
                sendResult(context.getString(R.string.download_file_err));
                disconnect();
                e.printStackTrace();
            }
        }
        isLoading = false;
        disconnect();
    }

    private synchronized void downloadFileThumb(String filename, String localPath, String date, final boolean isRearViewFile){
        if(TextUtils.isEmpty(filename) || TextUtils.isEmpty(localPath)
                || isDestroyThread || mApplication.getDeviceUUID() == null){
            Dbug.e(tag, "filename, localPath is null!");
            return;
        }
        FileInfo videoInfo = new FileInfo();
        OutputStream outputStream = null;
        InputStream inputStream;
        try {
            if(null == mFTPClient){
                mFTPClient = new FTPClient();
            }
            if(connectAndLoginFTP(ftpAdd, port, userName, password, false)){
                if(!TextUtils.isEmpty(mCurrentPath.toString())){
                    if (!mCurrentPath.toString().equals(mFTPClient.printWorkingDirectory())) {
                        mCurrentPath.delete(0, mCurrentPath.length());
                        mCurrentPath.append(mFTPClient.printWorkingDirectory());
                    }
                }
                if (ftpFiles != null && ftpFiles.length == 0) {
                    ftpFiles = mFTPClient.listFiles(mCurrentPath.toString());
                }
                FTPFile downloadFile = null;
                if (ftpFiles == null || ftpFiles.length == 0) {
                    Dbug.e(tag, "files.length == 0");
                    sendResult(context.getString(R.string.download_thumb_failed));
                    isThumbLoading = false;
                    return;
                }
                for (FTPFile file : ftpFiles) {
                    if (file.getName().equals(filename)) {
                        Dbug.d(tag, "download file name : "+ filename);
                        downloadFile = file;
                        break;
                    }
                }
                if (downloadFile == null) {
                    Dbug.e(tag, "downloadFile is null!");
                    sendResult(context.getString(R.string.download_thumb_failed));
                    isThumbLoading = false;
                    return;
                }
                long ftpFileSize = downloadFile.getSize();
                Dbug.d(tag, "ftp download file size : " + ftpFileSize + " phone free size : " + BufChangeHex.readSDCard());
                if(ftpFileSize > 0 && BufChangeHex.readSDCard() <= 512048 + 3*1024*1024){
                    sendResultDelay(context.getString(R.string.phone_space_inefficient), 1000);
                    Dbug.e(tag, "phone_space_inefficient!");
                    isThumbLoading = false;
                    return;
                }
                File localFile = new File(localPath);
                if (localFile.exists()) {
                    if(localFile.delete()){
                        Dbug.d(tag, "Download file is not complete!");
                    }
                }

                //progress
//            long step = ftpFileSize / 100;
//            long progress = 0;
                long currentSize = 0;

                //prepare to download file
//            mFTPClient.enterLocalActiveMode();
                mFTPClient.setFileType(FTP.BINARY_FILE_TYPE);
//            mFTPClient.setBufferSize(1024 * 1024 * 5); // 5M
                mFTPClient.enterLocalPassiveMode();
                outputStream = new FileOutputStream(localPath, true);
//            mFTPClient.setRestartOffset(localFileSize);
                inputStream = mFTPClient.retrieveFileStream(filename);
                byte[] data = new byte[1024];// 1KB
                byte[] headData = new byte[307200];//300KB
                long picSize = 0;
                int length;
                byte[] buf = new byte[4];
                byte[] rate = new byte[4];
                byte[] width = new byte[4];
                byte[] height = new byte[4];
                byte[] frameSize = new byte[4];
                long totalFrames;
                long microSecPerFrame;
                long videoWidth;
                long videoHeight;
                long videoTime = 0;
                int firstThumbPos = -1;
                if (inputStream == null) {
                    Dbug.e(tag, "inputStream is null filename = " + filename);
                    File file = new File(localPath);
                    if (file.exists() && file.isFile()) {
                        if(file.delete()){
                            Dbug.e(tag, "inputStream is null,so delete local file!");
                        }
                    }
                    outputStream.flush();
//                outputStream.close();
                    sendResultAndMessage(context.getString(R.string.download_thumb_failed), filename);
                    isThumbLoading = false;
                    return;
                }
                sendResult(context.getString(R.string.download_thumb_start));
                isThumbLoading = true;
                while ((length = inputStream.read(data)) != -1) {

                    if (isStopDownLoadThread) {
                        File deleteFile = new File(localPath);
                        if(deleteFile.exists() && deleteFile.isFile()){
                            if(deleteFile.delete()){
                                Dbug.e(tag,"download thumb task is aborted,so delete local file.");
                            }
                        }
                        isAVI = false;
                        break;
                    }

                    outputStream.write(data, 0, length);
                    outputStream.flush();

                    if (filename.contains(".AVI") || filename.contains(".avi")) {
                        if (currentSize < headData.length - 1024) {
                            if(currentSize + length  <= headData.length){
                                System.arraycopy(data, 0, headData, (int) currentSize, length);
                            }
                            if(currentSize >= 1024){
                                if(picSize == 0) {
                                    System.arraycopy(headData, 32, rate, 0, 4);
                                    System.arraycopy(headData, 48, buf, 0, 4);
                                    System.arraycopy(headData, 64, width, 0, 4);
                                    System.arraycopy(headData, 68, height, 0, 4);
                                    totalFrames = BufChangeHex.getLong(buf, true);
                                    microSecPerFrame = BufChangeHex.getLong(rate, true);
                                    videoWidth = BufChangeHex.getLong(width, true);
                                    videoHeight = BufChangeHex.getLong(height, true);

                                    if (microSecPerFrame > 0) {
                                        if ((1000000 / microSecPerFrame) > 0) {
                                            if ((totalFrames % (1000000 / microSecPerFrame)) == 0) {
                                                videoTime = totalFrames / (1000000 / microSecPerFrame);
                                            } else {
                                                videoTime = totalFrames / (1000000 / microSecPerFrame) + 1;
                                            }
                                        }
                                    }
                                    for (int i = 3; i < headData.length; i++){
                                        if (headData[i-3] == 0x30 && headData[i-2] == 0x30 && headData[i-1] == 0x64 && headData[i] == 0x63){
                                            firstThumbPos = i + 1;
                                            break;
                                        }
                                    }
                                    if(-1 != firstThumbPos){
                                        System.arraycopy(headData, firstThumbPos, frameSize, 0, 4);
                                        picSize = BufChangeHex.getLong(frameSize, true);
                                    }
                                    if(picSize == 0){
                                        isStopDownLoadThread = false;
                                        removeFtpFiles(filename);
                                        Dbug.e(tag, " picSize is 0, this video is null!");
                                        break;
                                    }
                                    Dbug.w(tag, "firstThumbPos ==> " + firstThumbPos);
                                    Dbug.w(tag, "frameSize ==> " + BufChangeHex.encodeHexStr(frameSize) + " ===== " + picSize);
                                    Dbug.w(tag, "totalFrames ==> " + BufChangeHex.encodeHexStr(buf) + " ===== " + totalFrames);
                                    Dbug.w(tag, "rate ==> " + BufChangeHex.encodeHexStr(rate) + " ===== " + microSecPerFrame);
                                    Dbug.w(tag, "time =====> " + videoTime);
                                    Dbug.w(tag, "width ==> " + BufChangeHex.encodeHexStr(width) + " ===== " + videoWidth);
                                    Dbug.w(tag, "height ==> " + BufChangeHex.encodeHexStr(height) + " ===== " + videoHeight);

                                    videoInfo.setTitle(filename);
                                    videoInfo.setSize((int) downloadFile.getSize());
                                    videoInfo.setPath(mCurrentPath.toString());
                                    videoInfo.setDirectory(false);
                                    videoInfo.setIsAVI(true);
                                    videoInfo.setTotalTime(videoTime);
                                    videoInfo.setWidth(videoWidth);
                                    videoInfo.setHeight(videoHeight);
                                }
                                if (picSize > 0 && (currentSize >= (firstThumbPos +picSize + 1024))) {
                                    byte[] frameData = new byte[(int) picSize];
                                    if (picSize + firstThumbPos+4 <= headData.length) {
                                        System.arraycopy(headData, firstThumbPos+4, frameData, 0, frameData.length);
                                    }
                                    String filePath = AppUtil.getAppStoragePath(mApplication, VIDEO, isRearViewFile);
                                    if(TextUtils.isEmpty(filePath)){
                                        isAVI = true;
                                        return;
                                    }
                                    File file2 = new File(filePath);
                                    if (!file2.exists()) {
                                        if (file2.mkdir()) {
                                            Dbug.d(tag, "mkdir '.../video' folder success.");
                                        }
                                    }

                                    String oldName = filename.substring(0, filename.indexOf(".")) + "_" + videoTime + ".jpg";
                                    if (!BufChangeHex.byte2File(frameData, filePath, oldName)) {
                                        Dbug.d(tag, "save image failed!");
                                        File deleteFile = new File(filePath + "/" + oldName);
                                        if (deleteFile.exists() && deleteFile.isFile()) {
                                            if (deleteFile.delete()) {
                                                Dbug.e(tag, "delete file ok");
                                            }
                                        }
                                    }
                                    isAVI = true;
                                    break;
                                }
                            }
                        }else{
                            break;
                        }
                    } else {
                        isAVI = false;
                    }
                    currentSize = currentSize + length;
                }
                inputStream.close();
                outputStream.flush();

            }else{
                isThumbLoading = false;
            }
        }catch (IOException e){
            Dbug.e(tag, "IOException =====> 01 " + e.getMessage());
            e.printStackTrace();
            sendResult(context.getString(R.string.ftp_client_exception));
            disconnect();
        }catch (Exception ex){
            Dbug.e(tag, "Exception =====> 02 " + ex.getMessage());
            ex.printStackTrace();
            sendResult(context.getString(R.string.ftp_client_exception));
            disconnect();
        }finally {
            try{
                if(isThumbLoading){
                    isThumbLoading = false;
                    if (!TextUtils.isEmpty(videoInfo.getTitle())) {
                        Message msg = Message.obtain();
                        msg.what = MSG_VIDEO_MESSAGE;
                        msg.obj = videoInfo;
                        mUIHandler.sendMessage(msg);
                    }
                    if(mFTPClient.isConnected()){
                        int rely = mFTPClient.getReply();
                        Dbug.d(tag, "getRely = " + rely);
                        if(rely == 226){
                            Dbug.d(tag, "download == true ");
                            saveThumbnail(filename, localPath, date, isRearViewFile);
                            sendResult(context.getString(R.string.download_thumb_success));
                            if(null != outputStream){
                                outputStream.close();
                            }
                            disconnect();
                        }else {
                            Dbug.d(tag, "download == false ");
                            File deleteFile = new File(localPath);
                            if (deleteFile.exists() && deleteFile.isFile()) {
                                if(deleteFile.delete()){
                                    Dbug.e(tag, " download thumb file failed, so delete local file.");
                                }
                            }
                            if (isAVI) {
                                sendResult(context.getString(R.string.download_thumb_success));
                            } else {
                                if(isStopDownLoadThread){
                                    isStopDownLoadThread = false;
                                    sendResult(context.getString(R.string.download_task_abort));
                                }else{
                                    sendResultAndMessage(context.getString(R.string.download_thumb_failed), filename);
                                }
                            }
                            if(null != outputStream){
                                outputStream.close();
                            }
                            disconnect();
                        }
                    }else{
                        sendResultAndMessage(context.getString(R.string.download_thumb_failed), filename);
                        if(null != outputStream){
                            outputStream.close();
                        }
                        File deleteFile = new File(localPath);
                        if (deleteFile.exists() && deleteFile.isFile()) {
                            if(deleteFile.delete()){
                                Dbug.e(tag,"download thumb task was disconnect,so delete file.");
                            }
                        }
                        disconnect();
                    }
                }else{
                    File deleteFile = new File(localPath);
                    if (deleteFile.exists()) {
                        if(deleteFile.delete()){
                            Dbug.e(tag,"download thumb task failed,so delete local file.");
                        }
                    }
                    sendResultAndMessage(context.getString(R.string.download_thumb_failed), filename);
                    if(outputStream != null){
                        outputStream.close();
                    }
                    disconnect();
                }
            }catch (IOException e){
                disconnect();
                Dbug.e(tag, "Exception =====> 03 " + e.getMessage());
                e.printStackTrace();
            }
        }
        isThumbLoading = false;
        disconnect();
    }

    private void saveThumbnail(String fileName, String localPath, String date, boolean isRearViewFile){
        if(mApplication.getDeviceUUID() == null || TextUtils.isEmpty(fileName)
                || TextUtils.isEmpty(localPath) || TextUtils.isEmpty(date) ){
            return;
        }
        if((fileName.contains(".png") || fileName.contains(".PNG")
                || fileName.contains(".JPEG") || fileName.contains(".jpeg")
                || fileName.contains(".jpg") || fileName.contains(".JPG"))){
            try{
                Bitmap bitmap = BitmapFactory.decodeFile(localPath);
                String path = AppUtil.getAppStoragePath(mApplication, IMAGE, isRearViewFile);
                if(TextUtils.isEmpty(path)) return;
                File file2 = new File(path);
                if(!file2.exists()){
                    if(file2.mkdir()){
                        Dbug.e(tag, "mkdir '.../image' folder success.");
                    }
                }
                String savePath = path + File.separator + fileName;
                if(bitmap == null){
                    File deleteFile = new File(localPath);
                    if (deleteFile.exists() && deleteFile.isFile()) {
                        if(deleteFile.delete()){
                            Dbug.e(tag, "open file err, thumb is null,so delete local file.");
                        }
                    }
                    return;
                }
                try{
                    File deleteFile = new File(localPath);
                    if(deleteFile.exists()){
                        copyFile(localPath, savePath);
                        if(deleteFile.delete()){
                            Dbug.w(tag, " thumb file delete success!");
                        }
                    }
                }catch (Exception e2){
                    Dbug.e(tag, "Exception err = " + e2.getMessage());
                    e2.printStackTrace();
                }
            }catch (Exception e){
                Dbug.e(tag, "Exception err = " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private boolean copyFile(String oldPath, String newPath){
        if(oldPath == null || oldPath.isEmpty() || newPath == null || newPath.isEmpty()){
            return false;
        }
//        int bytesum = 0;
        int byteRead;
        InputStream inStream = null;
        FileOutputStream fs = null;
        try {
            File oldFile = new File(oldPath);
            if (oldFile.exists()) {
                inStream = new FileInputStream(oldPath);
                fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[(int)oldFile.length()];
                while ((byteRead = inStream.read(buffer)) != -1) {
//                    bytesum += byteread;
                    fs.write(buffer, 0, byteRead);
                }
                inStream.close();
                fs.close();
                return true;
            }
            return false;
        }  catch (IOException e) {
            Dbug.e(tag , "--IOException -- : " + e.getMessage());
            e.printStackTrace();
        }finally {
           try{
               if(inStream != null){
                   inStream.close();
               }
           }catch (IOException e){
               Dbug.e(tag , "finally --IOException -- : " + e.getMessage());
           }
            if(fs != null){
                try {
                    fs.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /*Refresh under the path of data*/
    private boolean refreshFTPFiles(String oldFileName, String newFileName){
        if(TextUtils.isEmpty(oldFileName) || TextUtils.isEmpty(newFileName)){
            Dbug.e(tag, " Parameter is empty ");
            return false;
        }
        if(ftpFiles != null && ftpFiles.length > 0){
            for (FTPFile info : ftpFiles){
                if(info.getName().equals(oldFileName)){
                    info.setName(newFileName);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean removeFtpFiles(String fileName) {
        if(fileName == null || fileName.isEmpty()){
            return false;
        }
        if (ftpFiles != null && ftpFiles.length > 0) {
            FTPFile[] tempFtpFiles = ftpFiles.clone();
            FTPFile[] resultFtpFiles = new FTPFile[ftpFiles.length - 1];
            int n = 0;
            for (FTPFile ftpFile : tempFtpFiles) {
                if (!ftpFile.getName().equals(fileName)) {
                    if(n < ftpFiles.length - 1){
                        resultFtpFiles[n] = ftpFile;
                        n++;
                    }
                }
            }
            if (resultFtpFiles.length > 0) {
                ftpFiles = resultFtpFiles;
            }
            return true;
        } else {
            return false;
        }
    }

    public void tryToCancelThreadPool(){
        if(mWorkerHandler == null){
            if(null != getLooper()){
                mWorkerHandler = new Handler(getLooper(), this);
            }else{
                return;
            }
        }
        mWorkerHandler.sendEmptyMessage(MSG_CANCEL_THREAD_POOL);
    }

    public void tryToDeleteFile(String fileName, int selectedPosition, boolean isRearViewFile){
        if(mWorkerHandler == null){
            if(null != getLooper()){
                mWorkerHandler = new Handler(getLooper(), this);
            }else{
                return;
            }
        }
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putBoolean(VIEW_REAR, isRearViewFile);
        message.setData(bundle);
        message.what = MSG_DELETE;
        message.obj = fileName;
        message.arg1 = selectedPosition;
        mWorkerHandler.sendMessage(message);
    }

    public void tryToDownloadFile(String fileName, String localPathName, boolean isRearViewFile){
        if(mWorkerHandler == null){
            if(null != getLooper()){
                mWorkerHandler = new Handler(getLooper(), this);
            }else{
                return;
            }
        }
        Message message = Message.obtain();
        message.what = MSG_DOWNLOAD;
        Bundle b = new Bundle();
        b.putString(IConstant.SELECTED_FILE_NAME, fileName);
        b.putString(IConstant.DOWNLOAD_LOCAL_PATH_NAME, localPathName);
        b.putBoolean(IConstant.IS_DOWNLOAD_THUMBNAIL, false);
        b.putBoolean(VIEW_REAR, isRearViewFile);
        message.setData(b);
        mWorkerHandler.sendMessage(message);
    }

    public void tryToDownloadThumbnail(String fileName, String localPathName, String date, boolean isRearViewFile){

        downloadThreadPool(fileName, localPathName, date, isRearViewFile);
    }

    public void tryToUploadFile(String localPathName, String remoteFileName, FTPLoginInfo ftpLoginInfo){
        if(mWorkerHandler == null){
            if(null != getLooper()){
                mWorkerHandler = new Handler(getLooper(), this);
            }else{
                return;
            }
        }
        Message message = Message.obtain();
        message.what = MSG_UPLOAD;
        Bundle b = new Bundle();
        b.putString(IConstant.SELECTED_FILE_NAME, localPathName);
        b.putString(IConstant.REMOTE_FILE_NAME, remoteFileName);
        b.putSerializable(IConstant.FTP_LOGIN_INFO, ftpLoginInfo);
        message.setData(b);
        mWorkerHandler.sendMessage(message);
    }

    public void tryToRename(String fileName, String newFileName, boolean isRearViewFile){
        if(mWorkerHandler == null){
            if(null != getLooper()){
                mWorkerHandler = new Handler(getLooper(), this);
            }else{
                return;
            }
        }
        Message message = Message.obtain();
        message.what = MSG_RENAME;
        Bundle b = new Bundle();
        b.putBoolean(VIEW_REAR, isRearViewFile);
        b.putString(IConstant.SELECTED_FILE_NAME, fileName);
        b.putString(IConstant.REMOTE_FILE_NAME, newFileName);
        message.setData(b);
        mWorkerHandler.sendMessage(message);
    }

    private void tryToLogout(){
        Dbug.d(tag, "===tryToLogout===");
        if(mWorkerHandler == null){
            if(null != getLooper()){
                mWorkerHandler = new Handler(getLooper(), this);
            }else{
                return;
            }
        }
        mWorkerHandler.sendEmptyMessage(MSG_FTP_LOGOUT);
    }

    private void disconnect(){
        if (mFTPClient != null && mFTPClient.isConnected()){
            try {
                mFTPClient.disconnect();
            } catch (IOException e) {
                sendResult(context.getString(R.string.ftp_client_exception));
                Dbug.e(tag, "IOException 11--> " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String getDateText(String drs){
        String dataText = "";
        if(drs == null || drs.isEmpty()){
            return null;
        }
        try{
            String[] strs;
            if(drs.contains(";")){
                strs = drs.split(";");
                if(strs.length > 0 ){
                    for (String s : strs){
                        if(s.contains("modify")){
                            dataText = s.substring((s.indexOf("=")+1));
//                            Dbug.d(tag, " modify String = " + s + "  taget str = " +dataText);
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return dataText;
    }

    private void sendResult(String strings){
        if(mUIHandler == null){
            return;
        }
        Message message = Message.obtain();
        message.what = MSG_SHOW_MESSAGES;
        message.obj = strings;
        mUIHandler.sendMessage(message);
    }

    private void sendResultAndMessage(String strings, String filename){
        if(mUIHandler == null){
            return;
        }
        Bundle bundle = new Bundle();
        Message message = Message.obtain();
        message.what = MSG_SHOW_MESSAGES;
        message.obj = strings;
        bundle.putString("File_Name", filename);
        message.setData(bundle);
        mUIHandler.sendMessage(message);
    }

    private void sendResultDelay(String strings, int delay){
        if(mUIHandler == null){
            return;
        }
        Message message = Message.obtain();
        message.what = MSG_SHOW_MESSAGES;
        message.obj = strings;
        mUIHandler.sendMessageDelayed(message, delay);
    }

    private void sendOperation(int what, int arg1, String name){
        if(mUIHandler == null){
            return;
        }
        Message message = Message.obtain();
        message.what = what;
        message.arg1 = arg1;
        message.obj = name;
        mUIHandler.sendMessage(message);
    }

    public void setIsStopDownLoadThread(boolean bl){
        if(Thread.currentThread().isAlive()){
            this.isStopDownLoadThread = bl;
        }
    }

    private void init(){
        if(mList == null){
            mList = new ArrayList<>();
        }
        if(mCurrentPath == null){
            mCurrentPath = new StringBuffer();
        }
        if(servie == null){
            servie = Executors.newSingleThreadExecutor();
        }
        if(scanFilesHelper == null){
            scanFilesHelper = new ScanFilesHelper(context);
        }
    }

    private void release(){
        if(mList != null){
            if(mList.size() > 0){
                mList.clear();
            }
        }
        if(future != null){
            future.cancel(true);
        }
        if(servie != null){
            servie.shutdownNow();
        }
        if(scanFilesHelper != null){
            scanFilesHelper.release();
            scanFilesHelper = null;
        }
    }

    /**
     * desc sort
     */
    private String[] sort(String[] drs){
        if(drs == null || drs.length == 0){
            return drs;
        }
        for (int i = 0; i < drs.length-1; i++) {
            for (int j = i+1; j < drs.length; j++) {
                if(drs[j].compareTo(drs[i]) > 0){
                    String temp = drs[j];
                    drs[j] = drs[i];
                    drs[i] = temp;
                }
            }
        }
        return drs;
    }

    public void setIsDestoryThread(boolean bl){
        this.isDestroyThread = bl;
    }

    public boolean getIsDestoryThread(){
        return isDestroyThread;
    }

    @Override
    public boolean quit() {
        tryToLogout();
        release();
        return super.quit();
    }
}
