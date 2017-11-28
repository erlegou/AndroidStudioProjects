package hunuo.com.wifiuav.uity;

import android.os.Environment;
import android.os.StatFs;

public class StorageUtil {
    private static final int JELLY_BEAN_MR2 = 18;

    /**获取外部存储可用空间大小
     * @return return value is in bytes
     */
    public static long getSdCardFreeBytes() {

        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
        long memory;

        if (android.os.Build.VERSION.SDK_INT >= JELLY_BEAN_MR2) {
            memory = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } else {
            long blockSize = stat.getBlockSize();
            memory = stat.getAvailableBlocks() * blockSize;
        }

        return memory;
    }

    /**获取外部存储已经使用大小
     * @return return value is in bytes
     */
    public static long getSdCardUsedBytes() {

        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
        long memory;

        if (android.os.Build.VERSION.SDK_INT >= JELLY_BEAN_MR2) {
            memory = (stat.getBlockCountLong() - stat.getAvailableBlocksLong()) * stat.getBlockSizeLong();
        } else {
            long blockSize = stat.getBlockSize();
            memory = (stat.getBlockCount() - stat.getAvailableBlocks()) * blockSize;
        }

        return memory;
    }

    /**获取外部存储总大小
     * @return return value is in bytes
     */
    public static long getSdCardTotalBytes() {

        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
        long memory;

        if (android.os.Build.VERSION.SDK_INT >= JELLY_BEAN_MR2) {
            memory = stat.getBlockCountLong() * stat.getBlockSizeLong();
        } else {
            long blockSize = stat.getBlockSize();
            memory = stat.getBlockCount() * blockSize;
        }

        return memory;
    }
}
