package com.jieli.stream.player.tool;

import android.app.Activity;

import java.util.Stack;


public class ActivityStack {

    private static Stack<Activity> mActivityStack = new Stack<>();
    private static ActivityStack instance = null;

    public static ActivityStack getInstance(){
        if (instance == null){
            instance = new ActivityStack();
        }
        return instance;
    }

    public void popActivity(Activity activity) {
        if (activity != null) {
            activity.finish();
            mActivityStack.remove(activity);
        }
    }

    public void pushActivity(Activity activity) {
        if (activity != null){
            mActivityStack.add(activity);
        }
    }

    public void clearAllActivity() {
        while (!mActivityStack.isEmpty()) {
            Activity activity = mActivityStack.pop();
            if (activity != null) {
                activity.finish();
            }
        }
    }
}
