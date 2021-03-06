package hunuo.com.wifiuav.uity;



import java.util.Comparator;

import hunuo.com.wifiuav.data.SDFileInfo;

/**
 * 排序
 * @author zqjasonZhong
 * date : 2017/3/1
 */
public class FileComparator implements Comparator<SDFileInfo>{
    public int compare(SDFileInfo file1, SDFileInfo file2) {
        // 文件夹排在前面
        if (file1.IsDirectory && !file2.IsDirectory) {
            return -1000;
        } else if (!file1.IsDirectory && file2.IsDirectory) {
            return 1000;
        }
        // 相同类型按名称排序
        return file1.Name.compareTo(file2.Name);
    }
}
