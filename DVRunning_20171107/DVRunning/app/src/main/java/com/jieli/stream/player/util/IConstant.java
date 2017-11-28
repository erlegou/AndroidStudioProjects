package com.jieli.stream.player.util;


import android.os.Environment;

public interface IConstant {
    /**JSon constants*/
    String JS_MODE_CATEGORY = "mode_category";
    String JS_PHOTO_MODE = "photo_mode";
    String JS_VIDEO_MODE = "video_mode";
    String JS_SETTINGS_MODE = "settings_mode";
    String RESOURCE_DIR = "mipmap";

    /*App may creates following folders*/
    String DIR_RECORD = "record";
	String REAR_IMAGE = ".RearImage";
	String REAR_VIDEO = ".RearVideo";
	String REAR_DOWNLOAD = "RearDownload";

    String KEY_FILE_INFO = "file_info";

    String FILE_NAME = "file_name";

    /*FTP login info*/
    String FTP_LOGIN_INFO = "login_info";
    String LIST_FTP_OPERATION ="list_ftp_operation";

    /*Download & upload*/
    String SELECTED_FILE_NAME = "selected_file_name";
    String DOWNLOAD_LOCAL_PATH_NAME = "download_local_path_name";
    String REMOTE_FILE_NAME = "remote_file_name";
    String IS_DOWNLOAD_THUMBNAIL = "is_download_thumbnail";

    String SERVICE_CMD = "service_command";
    int SERVICE_CMD_INIT_SOCKET = 1;
    int SERVICE_CMD_CLOSE_SOCKET = 2;
    int SERVICE_CMD_CLEAR_DEVICE_STATUS = 3;

    String WIFI_PREFIX = "wifi_camera_";//过滤wifi名称
    String CURRENT_SSID = "cur_dev_ssid";
    String CURRENT_PWD = "cur_dev_pwd";

//    String DEVICE_VERSION_INFO = "device_version_info";

    /*Browse File operation*/
    String BROWSE_FILE_OPERATION_STYLE = "browse_file_operation_style";
    int SELECT_BROWSE_FILE = 0x01;
    int BACK_BROWSE_MODE = 0x02;
    int DELETE_BROWSE_FILE = 0x03;
    int DOWNLOAD_BROWSE_FILE = 0x04;

    String BROWSE_FRAGMENT_TYPE = "browse_fragment_type";

    int ACTIVITY_RESULT_OK = 2000; // TimelineActivity

    int BROWSE_ACTIVITY_RESULT_OK = 2001; // BrowseFileActivity

    String DEVICE_VERSION_INFO_NAME = "vermatch.txt";

    //ftp parameters
    int DEFAULT_FTP_PORT = 21;
    //outer net ftp
    String FTP_HOST_NAME = "cam.jieli.net";

    //内测账号
//    String FTP_USER_NAME = "www@baidu.com";
//    String FTP_PASSWORD = "pop123456";

//    //正式账号
    String FTP_USER_NAME = "your account";
    String FTP_PASSWORD = "your password";

    //intranet ftp
    String INSIDE_FTP_HOST_NAME = "192.168.1.1";
    String INSIDE_FTP_USER_NAME = "FTPX";
    String INSIDE_FTP_PASSWORD = "12345678";

    String UPDATE_TEXT = "update_text";
    String UPDATE_FILE = "update_file";
    String UPDATE_TYPE = "update_type";

    String PRODUCT_TYPE = "product_type";
    String ANDROID_VERSION = "android_version";
    String FIRMWARE_DIR = "firmware";
    String ANDROID_DIR = "android";

    String DIALOG_TYPE = "dialog_type";
    String MANDATORY_UPDATE = "mandatory_update";

    int SHOW_NOTIFY_DIALOG = 0xB016;
    int NO_UPDATE_FILE = 0xB017;
    int READ_DATA_ERROR = -1;
    int PRODUCT_NOT_MATCH = 0;
    int APK_NOT_MATCH = 1;
    int SDK_NOT_MATCH = 2;

    String DEVICE_VERSION_MSG = "device_version_msg";

    String APP_VERSION = "app_version";

    String LOCAL_FILES_UI = "local_files_ui";
    int ARGS_SHOW_DIALOG = 0;
    int ARGS_DISMISS_DIALOG = 1;
    String DEVICE_FILES_UI_TYPE = "device_files_ui_type";
    int WAITING_FOR_THUMB = 0;
    int WAITING_FOR_DELETE = 1;
    int WAITING_FOR_DATA = 2;
    int ALL_DIALOG_DISMISS = 3;
    String DEVICE_DIALOG_STATE = "device_dialog_state";

    String TAKE_PHOTO_FLASH_SETTING = "take_photo_flash_setting";

	String VIEW_FRONT = "front_view";
	String VIEW_REAR = "rear_view";

    /**
     * document
     */
    String ROOT_PATH = Environment.getExternalStorageDirectory().getPath(); //手机内存路径

    String IMAGE = "image";         //存储图片缩略图
    String VIDEO = "video";         //存储视频缩略图
    String THUMB = "thumb";         //缩略图保存跳转站
    String RECORD = "record";       //存储录制视频
    String UPLOAD = "Upload";       //存储上传固件sdk
    String DOWNLOAD = "download";   //存储下载的文件
    String SUB_THUMB = "Thumb";     //存储下载的视频的缩略图
    String VERSION = "Version";     //存储版本信息

    String KEY_ROOT_PATH_NAME = "key_root_path_name";
    String KEY_DIR_PATH = "key_dir_path";

    /**
     * Device type
     */
    int REC_FILE = 0;            //正常录像视频
    int SOS_FILE = 1;            //保护/紧急视频
    int DELAY_FILE = 2;          //延时拍摄视频

    /**File Type*/
    int FILE_TYPE_VIDEO = 0;
    int FILE_TYPE_IMAGE = 1;
}
