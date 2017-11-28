package com.ihunuo.touying;

import java.util.UUID;

/**
 * Created by tzy on 2017/11/15.
 */

public class AppConfig {
    public final static String TRANSFER_SERVICE_UUID_STR = "0000FFE5-0000-1000-8000-00805f9b34fb";
    public final static String TRANSFER_CHARACTERISTIC_UUID_STR = "0000AE01-0000-1000-8000-00805f9b34fb";

    public final static String TRANSFER_NOTITY_STR = "0000FFE9-0000-1000-8000-00805f9b34fb";


    public final static UUID TRANSFER_SERVICE_UUID = UUID.fromString(TRANSFER_SERVICE_UUID_STR);
    public final static UUID TRANSFER_CHARACTERISTIC_UUID = UUID.fromString(TRANSFER_CHARACTERISTIC_UUID_STR);
    public final static UUID TRANSFER_NOTITY_UUID = UUID.fromString(TRANSFER_NOTITY_STR);
}

