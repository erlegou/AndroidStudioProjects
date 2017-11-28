package hunuo.com.wifiuav.data;

import java.io.Serializable;


public class FileInfo implements Serializable{
    private boolean isDirectory;
    private String mTitle;
    private long mSize;
    private String mPath;
    private String createDate;
    private String dateMes;
    private boolean isAVI = false;
    private long totalTime = 0;
    private long width = 0;
    private long height = 0;

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public long getSize() {
        return mSize;
    }

    public void setSize(long mSize) {
        this.mSize = mSize;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    public void setPath(String path){
        this.mPath = path;
    }

    public String getPath(){
        return this.mPath;
    }

    public void setIsAVI(boolean bl){
        this.isAVI = bl;
    }

    public boolean getIsAVI(){
        return isAVI;
    }

    public void setTotalTime(long time){
        this.totalTime = time;
    }

    public long getTotalTime(){
        return totalTime;
    }

    public void setWidth(long wid){
        this.width = wid;
    }

    public long getWidth(){
        return width;
    }

    public void setHeight(long heig){
        this.height = heig;
    }

    public long getHeight(){
        return height;
    }

    public void setCreateDate(String createDate){
        this.createDate = createDate;
    }

    public String getCreateDate(){
        return createDate;
    }

    public void setDateMes(String str){
        this.dateMes = str;
    }

    public String getDateMes(){
        return dateMes;
    }
}
