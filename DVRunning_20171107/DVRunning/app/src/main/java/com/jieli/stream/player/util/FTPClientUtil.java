package com.jieli.stream.player.util;

import android.text.TextUtils;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.net.SocketException;


public class FTPClientUtil {

    private String tag = getClass().getSimpleName();
    private FTPClient mFTPClient;
    private String currentFTPPath;

    public FTPClientUtil(){
        mFTPClient = new FTPClient();
    }

    public FTPClient getFTPClient(){
        if(mFTPClient == null){
            mFTPClient = new FTPClient();
        }
        return mFTPClient;
    }

    /**
     * connect FTP
     */
    public boolean connectAndLoginFTP(String add, int port, String hostName, String password, boolean isChangeWorkPath, String changePath) {
        if (TextUtils.isEmpty(add) || TextUtils.isEmpty(hostName) || TextUtils.isEmpty(password)) {
            Dbug.e(tag, "-connectAndLoginFTP- parameter is empty!");
            throw new NullPointerException("ftp login message is empty!");
        }
        try {
            if(mFTPClient != null){
                mFTPClient.setDefaultPort(port);
                mFTPClient.connect(add);
                if (FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                    if (mFTPClient.login(hostName, password)) {
//                        mFTPClient.setControlEncoding("UTF-8");
                        mFTPClient.enterLocalPassiveMode();
                        currentFTPPath = mFTPClient.printWorkingDirectory();
                        if (isChangeWorkPath) {
                            if (!TextUtils.isEmpty(changePath)) {
                                String path = currentFTPPath + changePath;
                                if (mFTPClient.changeWorkingDirectory(path)) {
                                    Dbug.e(tag, "connect Ftp server success!  rootPath : " + path);
                                    currentFTPPath = path;
                                    return true;
                                }
                            }
                        }
                        if(isChangeWorkPath){
                            Dbug.e(tag, "connect Ftp server failed!");
                            return false;
                        }else{
                            Dbug.e(tag, "connect Ftp server success!");
                            return true;
                        }
                    }
                }
            }
            disconnect();
            Dbug.e(tag, "connect Ftp server failed! ");
            return false;
        } catch (SocketException e) {
            Dbug.e(tag, "connectFTP SocketException ===> " + e.getMessage());
            e.printStackTrace();
            disconnect();
        } catch (IOException e) {
            Dbug.e(tag, "connectFTP IOException ===> " + e.getMessage());
            e.printStackTrace();
            disconnect();
        }
        return false;
    }

    public String getCurrentFTPPath(){
        return currentFTPPath;
    }

    /**
     * change ftp server work path
     */
    public boolean changeWorkPath(String path) {
        if (TextUtils.isEmpty(path)) {
            Dbug.e(tag, "-connectAndLoginFTP- parameter is empty!");
            return false;
        }
        if (mFTPClient != null && mFTPClient.isConnected()) {
            try {
                boolean result = mFTPClient.changeWorkingDirectory(path);
                if(result){
                    currentFTPPath = path;
                }
                return result;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    /**
     * disconnect FTP
     */
    public void disconnect() {
        if (mFTPClient != null && mFTPClient.isConnected()) {
            try {
                mFTPClient.logout();
                mFTPClient.disconnect();
            } catch (IOException e) {
                Dbug.e(tag, "disconnect IOException --> " + e.getMessage());
                e.printStackTrace();
            }finally {
                currentFTPPath = null;
            }
        }
    }
}
