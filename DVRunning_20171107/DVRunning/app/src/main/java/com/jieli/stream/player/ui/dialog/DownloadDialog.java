package com.jieli.stream.player.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.jieli.stream.player.R;
import com.jieli.stream.player.ui.lib.NumberProgressBar;


public class DownloadDialog extends Dialog{

    private TextView dialogTitle;
    private TextView dialogContent;
    private TextView dialogTask;
    private NumberProgressBar numberProgressBar;
    private Button cancelBtn;

    private  OnCancelBtnClickListener mClickListener;
    public interface OnCancelBtnClickListener{
        void onClick();
    }

    public DownloadDialog(Context mContext){
        super(mContext, R.style.MyDialog);
        setContentView(R.layout.download_dialog_layout);

        dialogTitle = (TextView) findViewById(R.id.load_dialog_title);
        dialogContent = (TextView) findViewById(R.id.load_dialog_message);
        dialogTask = (TextView) findViewById(R.id.load_dialog_task);
        numberProgressBar = (NumberProgressBar) findViewById(R.id.load_dialog_progress);
        cancelBtn = (Button) findViewById(R.id.cancel_btn);

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mClickListener != null){
                    mClickListener.onClick();
                }
            }
        });
    }

    public void setDialogTilte(String text){
        dialogTitle.setText(text);
    }

    public String getDialogTitle(){
        return dialogTitle.getText().toString();
    }

    public void setDialogTask(String text){
        dialogTask.setText(text);
    }

    public String getDialogTask(){
        return dialogTask.getText().toString();
    }

    public void setDialogContent(String text){
        dialogContent.setText(text);
    }

    public String getDialogContent(){
        return dialogContent.getText().toString();
    }

    public void setOnCancelClickListener(OnCancelBtnClickListener listener){
        mClickListener = listener;
    }

    public NumberProgressBar getNumberProgressBar(){
        return numberProgressBar;
    }

}
