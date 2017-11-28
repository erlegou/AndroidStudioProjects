package hunuo.com.wifiuav.uity;


public interface IAction {
    String ACTION_PREFIX = "com.jieli.stream.player_";

    String ACTION_REJECT_CONNECTION = ACTION_PREFIX + "reject_connection";
    String KEY_REJECT_CONNECTION = "reject_connection";

    String ACTION_DEVICE_WIFI_DISABLED = ACTION_PREFIX + "device_wifi_disabled";

    String ACTION_SPECIAL_DATA = ACTION_PREFIX + "special_data";
    String KEY_SPECIAL_STATE = "special_state";

    String ACTION_SDCARD_STATE = ACTION_PREFIX + "sdcard_state";
    String KEY_SDCARD_STATE = "sdcard_state";

    /*Generic data*/
    String ACTION_GENERIC_DATA = ACTION_PREFIX + "generic_data";
    String KEY_GENERIC_STATE = "generic_state";

    String ACTION_DEVICE_CONNECTION_ERROR = ACTION_PREFIX + "device_connection_error";
    String KEY_DEVICE_CONNECTION_ERROR = "device_connection_error";

    String ACTION_CHANGE_SSID_SUCCESS = ACTION_PREFIX + "change_ssid_success";
    String ACTION_CHANGE_PWD_SUCCESS = ACTION_PREFIX + "change_pwd_success";

    String ACTION_DEVICE_CONNECTION_SUCCESS = ACTION_PREFIX + "device_connection_success";

    String ACTION_REQUEST_UI_DESCRIPTION = ACTION_PREFIX + "request_ui_description";
    String KEY_REQUEST_UI_DESCRIPTION = "request_ui_description";

    String ACTION_DEVICE_LANG_CHANGED = ACTION_PREFIX + "device_language_changed";

    String ACTION_ALLOW_FIRMWARE_UPGRADE = ACTION_PREFIX + "allow_firmware_upgrade";

    String ACTION_QUIT_APP = ACTION_PREFIX + "quit_application";

    String ACTION_BROWSE_MODE_OPERATION = ACTION_PREFIX + "browse_mode_operation";

    String ACTION_SELECT_BROWSE_MODE = ACTION_PREFIX + "select_browse_mode";

    String ACTION_FORMAT_SDCARD = ACTION_PREFIX + "format_sdcard";
    String ACTION_RESET_DEVICE = ACTION_PREFIX + "reset_device";

    String ACTION_ENTER_OFFLINE_MODE = ACTION_PREFIX + "enter_offline_mode";

    String ACTION_UPDATE_LIST = ACTION_PREFIX + "update_list";
    String ACTION_CHANGE_FRAGMENT = ACTION_PREFIX + "change_fragment";

    String ACTION_RESPONDING_VIDEO_DESC_REQUEST = ACTION_PREFIX + "responding_video_desc_request";

    String ACTION_GET_VIDEO_INFO_ERROR = ACTION_PREFIX + "get_video_info_error";

    String ACTION_DEVICE_IN_USB_MODE = ACTION_PREFIX + "device_in_usb_mode";

    String ACTION_UPDATE_APK_SDK = ACTION_PREFIX + "update_apk_sdk";

    String ACTION_INIT_CTP_SOCKET_SUCCESS = ACTION_PREFIX + "init_ctp_socket_success";

    String ACTION_CLOSE_DEV_WIFI = ACTION_PREFIX + "close_dev_wifi";
    String CLOSE_DEV_WIFI = "close_dev_wifi";

    String ACTION_UPDATE_LOCAL_FILES_UI = ACTION_PREFIX + "update_local_files_ui";
    String ACTION_UPDATE_DEVICE_FILES_UI = ACTION_PREFIX + "update_device_files_ui";

    String ACTION_SDCARD_ONLINE = ACTION_PREFIX + "sdcard_online";

    String ACTION_CONNECT_OTHER_DEVICE = ACTION_PREFIX + "connect_other_device";

    String ACTION_MODIFY_FLASH_SETTING = ACTION_PREFIX + "modify_flash_setting";

    String ACTION_REAR_CAMERA_PLUG_STATE = ACTION_PREFIX + "rear_camera_plug_state";
    String KEY_REAR_CAMERA_PLUG_STATE = ACTION_PREFIX + "rear_camera_plug_state";
}
