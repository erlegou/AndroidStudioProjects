package com.ihunuo.touying;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

public class MainActivity extends BaseActivity {

    ImageButton imageButton;
    TextView mtext;

    BlankFragment list1;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageButton = (ImageButton)findViewById(R.id.mian_back);

        mtext = (TextView)findViewById(R.id.maintext);

        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();


        list1 = new BlankFragment();
        transaction.replace(R.id.day_id_content, list1, "list");
        transaction.addToBackStack("list");

        transaction.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void GetPracticeCallback() {
        super.GetPracticeCallback();

        setDefaultFragment();
    }

    public void setDefaultFragment()
    {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_right_in, R.anim.slide_right_out);


            list1 = new BlankFragment();
            transaction.replace(R.id.day_id_content, list1, "list");
            transaction.addToBackStack("list");

        transaction.commit();


    }
}
