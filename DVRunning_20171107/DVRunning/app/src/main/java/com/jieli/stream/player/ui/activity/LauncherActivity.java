package com.jieli.stream.player.ui.activity;

import android.os.Bundle;

import com.jieli.lib.stream.util.ICommon;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseActivity;
import com.jieli.stream.player.util.Dbug;

public class LauncherActivity extends BaseActivity implements ICommon {
    private final String tag = getClass().getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
    }

    @Override
    public void onBackPressed() {
        Dbug.i(tag, "onBackPressed:");
        release();
        super.onBackPressed();
    }
}
