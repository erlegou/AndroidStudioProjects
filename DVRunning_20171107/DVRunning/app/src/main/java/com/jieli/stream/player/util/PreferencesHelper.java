package com.jieli.stream.player.util;

import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class PreferencesHelper {
	/** Shared Preferences name */
    private static final String PREFERENCES_NAME = "wifi_camera_pref";

    public static void putIntValue(Context context, String key, int value) {
        SharedPreferences sp = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public static void putLongValue(Context context, String key, long value) {
        SharedPreferences sp = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    public static void putStringValue(Context context, String key, String value) {
        SharedPreferences sp = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putString(key, value);
        editor.apply();
    }
    
    public static void putBooleanValue(Context context, String key, boolean value) {
        SharedPreferences sp = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }
    
    public static void putStringSetValue(Context context, String key, Set<String> value) {
        SharedPreferences sp = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putStringSet(key, value);
        editor.apply();
    }
    
    public static void remove(Context context, String key) {
        SharedPreferences sp = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.remove(key);
        editor.apply();
    }
    
    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
