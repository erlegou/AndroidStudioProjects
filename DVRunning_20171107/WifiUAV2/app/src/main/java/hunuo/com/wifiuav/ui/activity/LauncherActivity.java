package hunuo.com.wifiuav.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.jieli.lib.stream.util.ICommon;

import java.util.Timer;
import java.util.TimerTask;

import hunuo.com.wifiuav.R;
import hunuo.com.wifiuav.base.BaseActivity;
import hunuo.com.wifiuav.uity.Dbug;


public class LauncherActivity extends BaseActivity implements ICommon {
    private final String tag = getClass().getSimpleName();
    private int i = 0;
    private int TIME = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        Dbug.d("aaa","aaaaaaaaaaaa");
//        Intent intent=new Intent(getApplicationContext(),MainActivity.class);
//        startActivity(intent);

//        timer.schedule(task, 1000); // 1s后执行task
    }

    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                Intent intent=new Intent(getApplicationContext(),MainActivity.class);
                startActivity(intent);
            }
            super.handleMessage(msg);
        };
    };
    Timer timer = new Timer();
    TimerTask task = new TimerTask() {

        @Override
        public void run() {
            Message message = new Message();
            message.what = 1;
            handler.sendMessage(message);
        }
    };

    @Override
    public void onBackPressed() {
        Dbug.i(tag, "onBackPressed:");
        release();
        super.onBackPressed();
    }
}
