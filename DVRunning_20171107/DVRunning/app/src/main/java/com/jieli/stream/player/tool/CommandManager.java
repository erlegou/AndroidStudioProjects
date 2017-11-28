package com.jieli.stream.player.tool;

import com.jieli.lib.stream.util.ICommon;

import java.util.HashMap;

public class CommandManager {
    private final String tag = getClass().getSimpleName();
    private static CommandManager instance = null;
    private final static HashMap<String, String> mDeviceStatus = new HashMap<>();

    public static CommandManager getInstance(){
        if (instance == null){
            instance = new CommandManager();
        }
        return instance;
    }

    public String getDeviceStatus(String cmdNumber){
//        Dbug.w(tag, "get cmdNumber:" + cmdNumber);
        return mDeviceStatus.get(cmdNumber);
    }

    public void setDeviceStatus(String key, String value){
//        Dbug.w(tag, "get cmdNumber:" + cmdNumber);
        if(value.equals(ICommon.ARGS_CMD_NOT_REALIZE)){
            return;
        }
        mDeviceStatus.put(key, value);
    }

    public void clearDeviceStatus(){
        mDeviceStatus.clear();
    }
}
