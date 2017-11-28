package com.jieli.stream.player.util;

import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BufChangeHex {
    private static final char[] DIGITS_LOWER = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final char[] DIGITS_UPPER = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public BufChangeHex() {
    }

    public static char[] encodeHex(byte[] data) {
        return encodeHex(data, true);
    }

    public static char[] encodeHex(byte[] data, boolean toLowerCase) {
        return encodeHex(data, toLowerCase?DIGITS_LOWER:DIGITS_UPPER);
    }

    protected static char[] encodeHex(byte[] data, char[] toDigits) {
        int l = data.length;
        char[] out = new char[(l << 1) + l];
        int i = 0;

        for(int j = 0; i < l; ++i) {
            out[j++] = toDigits[(240 & data[i]) >>> 4];
            out[j++] = toDigits[15 & data[i]];
            out[j++] = 32;
        }

        return out;
    }

    protected static char[] encodeHex2(byte[] data, char[] toDigits) {
        int l = data.length;
        char[] out = new char[l << 1];
        int i = 0;

        for(int j = 0; i < l; ++i) {
            out[j++] = toDigits[(240 & data[i]) >>> 4];
            out[j++] = toDigits[15 & data[i]];
        }

        return out;
    }

    public static String encodeHexStr(byte[] data) {
        return encodeHexStr(data, true);
    }

    public static String encodeHexStr(byte[] data, boolean toLowerCase) {
        return encodeHexStr(data, toLowerCase?DIGITS_LOWER:DIGITS_UPPER);
    }

    protected static String encodeHexStr(byte[] data, char[] toDigits) {
        return new String(encodeHex(data, toDigits));
    }

    public static byte[] decodeHex(char[] data) {
        int len = data.length;
        if((len & 1) != 0) {
            throw new RuntimeException("Odd number of characters.");
        } else {
            byte[] out = new byte[len >> 1];
            int i = 0;

            for(int j = 0; j < len; ++i) {
                int f = toDigit(data[j], j) << 4;
                ++j;
                f |= toDigit(data[j], j);
                ++j;
                out[i] = (byte)(f & 255);
            }

            return out;
        }
    }

    public static int bytesToInt(byte[] bytes) {
        int addr;
        if (bytes.length == 2) {
            addr = bytes[1] & 0xFF | ((bytes[0] << 8) & 0xFF00);
        } else {
            addr = bytes[3] & 0xFF | ((bytes[2] << 8) & 0xFF00)
                    | ((bytes[1] << 16) & 0xFF0000)
                    | ((bytes[0] << 24) & 0xFF000000);
        }

        return addr;
    }

    /* byte[] -> long */
    public final static long getLong(byte[] buf, boolean asc) {
        if (buf == null) {
             throw new IllegalArgumentException("byte array is null!");
        }
        if (buf.length > 8) {
             throw new IllegalArgumentException("byte array size > 8 !");
         }
        long r = 0;
       if (asc)
           for (int i = buf.length - 1; i >= 0; i--) {
             r <<= 8;
             r |= (buf[i] & 0x00000000000000ff);
           }
        else
         for (int i = 0; i < buf.length; i++) {
            r <<= 8;
            r |= (buf[i] & 0x00000000000000ff);
            }
        return r;
    }

    protected static int toDigit(char ch, int index) {
        int digit = Character.digit(ch, 16);
        if(digit == -1) {
            throw new RuntimeException("Illegal hexadecimal character " + ch + " at index " + index);
        } else {
            return digit;
        }
    }

    public static boolean byte2File(byte[] buf, String filePath, String fileName) {
        BufferedOutputStream bos = null;
        FileOutputStream fos = null;
        File file;
        try {
            File dir = new File(filePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            file = new File(filePath + File.separator + fileName);
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(buf);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String getVideoDuration(String drs){
        String result = null;
        if(TextUtils.isEmpty(drs)){
            return null;
        }
        if(drs.contains("_")){
            String[] strs = drs.split("_");
            for (String str : strs){
                if(str.contains(".")){
                    result = str.split("\\.")[0];
                }
            }
        }
        return result;
    }


    public static String getVideoThumb(String filename, String path){
        String result = null;
        if(filename == null || filename.isEmpty() || path == null || path.isEmpty()){
            return null;
        }
        File[] files;
        String flagFileName;
        if(filename.contains(".")){
            flagFileName = filename.substring(0, filename.lastIndexOf("."));
        }else{
            return null;
        }
        File folderFile = new File(path);
        if(folderFile.exists()){
            files = folderFile.listFiles();
            if(files != null && files.length > 0){
                for (File file : files){
                    if(file.getName().contains(flagFileName)){
                        result = file.getName();
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static String combinDataStr(String drs, String newstr){
        String newFileName = "";
        if(drs == null || drs.isEmpty()){
            return newFileName;
        }else if(newstr == null || newstr.isEmpty()){
            newFileName = drs;
            return newFileName;
        }
        try{
            String[] strs = drs.split("\\.", 2);
            newFileName =  strs[0]+"_"+ newstr +"."+strs[1];
        }catch (Exception e){
            e.printStackTrace();
        }
        return newFileName;
    }

    /**
     *  获取android当前可用内存大小
     */
    public static long  readSDCard() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File sdcardDir = Environment.getExternalStorageDirectory();
            StatFs sf = new  StatFs(sdcardDir.getPath());
            long  blockSize = sf.getBlockSize();
            long  blockCount = sf.getBlockCount();
            long  availCount = sf.getAvailableBlocks();
            Dbug.e("BufChangeHex", "block大小:" + blockSize + ",block数目:" + blockCount + ",总大小:" + blockSize * blockCount / 1024 + "KB");
            Dbug.d("BufChangeHex" ,  "可用的block数目：:" + availCount+ ",剩余空间:" + availCount*blockSize/ 1024 + "KB" );
            return availCount*blockSize;
        }
        return -1;
    }

    private static long lastClickTime;
    public static boolean isFastDoubleClick(int delayTime) {
        long time = System.currentTimeMillis();
        if(lastClickTime == 0){
            lastClickTime = time;
            return false;
        }else{
            if ( time - lastClickTime >= delayTime) {
                lastClickTime = time;
                return false;
            }else{
                lastClickTime = time;
                return true;
            }
        }
    }

    public static char convertIntToAscii(int a) {
        return a >= 0 && a <= 255?(char)a:'\u0000';
    }
}