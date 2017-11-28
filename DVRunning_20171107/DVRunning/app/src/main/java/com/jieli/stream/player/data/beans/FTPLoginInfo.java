package com.jieli.stream.player.data.beans;

import java.io.Serializable;


public class FTPLoginInfo implements Serializable{
    private String hostname;
    private int port;
    private String userName;
    private String password;

    public FTPLoginInfo(String hostname, int port, String userName, String password){
        this.hostname = hostname;
        this.port = port;
        this.userName = userName;
        this.password = password;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String toString() {
        return "hostname="+hostname+", port="+port +", userName" + userName;
    }
}
