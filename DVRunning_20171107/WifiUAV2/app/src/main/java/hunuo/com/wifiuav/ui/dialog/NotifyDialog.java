package hunuo.com.wifiuav.ui.dialog;


import android.app.DialogFragment;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import hunuo.com.wifiuav.R;
import hunuo.com.wifiuav.base.BaseDialogFragment;
import hunuo.com.wifiuav.uity.Dbug;


public class NotifyDialog extends BaseDialogFragment {
    private final String tag = getClass().getSimpleName();
    private TextView mTitle;
    private TextView mContent;
    private ProgressBar mProgressBar;
    private TextView mNegativeButtonText;
    private TextView mConfirmButtonText;
    private TextView mPositiveButtonText;
    private View mDividerView;

    private Bundle bundle;
    private boolean isLeftGravity = false;

    private OnConfirmClickListener mOnConfirmClickListener;
    public interface OnConfirmClickListener{
        void onClick();
    }

    private OnNegativeClickListener mOnNegativeClickListener;
    public interface OnNegativeClickListener{
        void onClick();
    }

    private OnPositiveClickListener mOnPositiveClickListener;
    public interface OnPositiveClickListener{
        void onClick();
    }
    public NotifyDialog(){

    }

    public Bundle getBundle() {
        return bundle;
    }

    public void setBundle(Bundle bundle) {
        this.bundle = bundle;
    }

    private int mTitleId = 0, mContentId = 0, mNegativeButtonId = 0, mConfirmButtonId = 0, mPositiveButtonId = 0;
    private String title = "", content = "";
    private boolean mShowProgressBar = false;

    public NotifyDialog(boolean showProgressBar, int contentId){
//        Dbug.d(tag, "NotifyDialog:........00");
        mShowProgressBar = showProgressBar;
        mContentId = contentId;
    }

    public NotifyDialog(boolean showProgressBar, String content){
//        Dbug.d(tag, "NotifyDialog:........00");
        mShowProgressBar = showProgressBar;
        mContentId = -1;
        this.content = content;
    }

    public NotifyDialog(boolean showProgressBar, int contentId, int confirmTextId, OnConfirmClickListener listener){
//        Dbug.d(tag, "NotifyDialog:........11");
        mShowProgressBar = showProgressBar;
        mContentId = contentId;
        mConfirmButtonId = confirmTextId;
        mOnConfirmClickListener = listener;
    }

    public NotifyDialog(int titleId, int contentId, int confirmTextId, OnConfirmClickListener listener){
//        Dbug.d(tag, "NotifyDialog:........22");
        mTitleId = titleId;
        mContentId = contentId;
        mConfirmButtonId = confirmTextId;
        mOnConfirmClickListener = listener;
    }

    public NotifyDialog(String title, String content, int confirmTextId, OnConfirmClickListener listener){
        mTitleId = -1;
        this.title = title;
        mContentId = -1;
        this.content = content;
        mConfirmButtonId = confirmTextId;
        mOnConfirmClickListener = listener;
    }

    public NotifyDialog(int titleId, int contentId, int negativeTextId, int positiveTextId,
                        OnNegativeClickListener negativeListener, OnPositiveClickListener positiveListener){
//        Dbug.d(tag, "NotifyDialog:........33");
        mTitleId = titleId;
        mContentId = contentId;
        mPositiveButtonId = positiveTextId;
        mNegativeButtonId = negativeTextId;
        mOnNegativeClickListener = negativeListener;
        mOnPositiveClickListener = positiveListener;
    }

    public NotifyDialog(String title, String content, int negativeTextId, int positiveTextId,
                        OnNegativeClickListener negativeListener, OnPositiveClickListener positiveListener){
//        Dbug.d(tag, "NotifyDialog:........33");
        mTitleId = -1;
        this.title = title;
        mContentId = -1;
        this.content = content;
        mPositiveButtonId = positiveTextId;
        mNegativeButtonId = negativeTextId;
        mOnNegativeClickListener = negativeListener;
        mOnPositiveClickListener = positiveListener;
    }

    public void setContentTextLeft(boolean isLeftGravity){
       this.isLeftGravity = isLeftGravity;
    }

    public void setContent(int contentId){
        mContentId = contentId;
    }

    public void setContent(String content){
        mContentId = -1;
        this.content = content;
    }

    public void setNegativeText(int negativeTextId){
        this.mNegativeButtonId = negativeTextId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        setCancelable(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        Dbug.d(tag, "onCreateView:..........");
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.notify_dialog, container, false);
        mTitle = (TextView) view.findViewById(R.id.tv_title);
        mContent = (TextView) view.findViewById(R.id.tv_content);
        mProgressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        mNegativeButtonText = (TextView) view.findViewById(R.id.tv_left);
        mConfirmButtonText = (TextView) view.findViewById(R.id.tv_middle);
        mPositiveButtonText = (TextView) view.findViewById(R.id.tv_right);
        mDividerView = view.findViewById(R.id.divider_id);
        View line = view.findViewById(R.id.line_id);
        mContent.setMovementMethod(ScrollingMovementMethod.getInstance());

        if (mTitleId != 0){
            mProgressBar.setVisibility(View.GONE);
            if(mTitleId == -1){
                mTitle.setText(title);
            }else{
                mTitle.setText(getResources().getString(mTitleId));
            }
        } else {
            mTitle.setVisibility(View.GONE);

            if (mShowProgressBar){
                mProgressBar.setVisibility(View.VISIBLE);
            } else {
                mProgressBar.setVisibility(View.GONE);
            }
        }

        if (mContentId != 0){
            mContent.setVisibility(View.VISIBLE);
            if(mContentId == -1){
                mContent.setText(content);
            }else{
                mContent.setText(getResources().getString(mContentId));
            }
            if(isLeftGravity){
                mContent.setGravity(Gravity.LEFT);
            }else{
                mContent.setGravity(Gravity.CENTER);
            }
        } else {
            mContent.setVisibility(View.GONE);
        }

        if (mNegativeButtonId != 0){
            mNegativeButtonText.setVisibility(View.VISIBLE);
            mNegativeButtonText.setText(getResources().getString(mNegativeButtonId));
        } else {
            mNegativeButtonText.setVisibility(View.GONE);
        }
        if (mConfirmButtonId != 0){
            line.setVisibility(View.VISIBLE);
            mDividerView.setVisibility(View.GONE);
            mConfirmButtonText.setVisibility(View.VISIBLE);
            mConfirmButtonText.setText(getResources().getString(mConfirmButtonId));
        } else {
            mConfirmButtonText.setVisibility(View.GONE);

            if (mShowProgressBar){
                line.setVisibility(View.GONE);
                mDividerView.setVisibility(View.GONE);
            } else {
                line.setVisibility(View.VISIBLE);
                mDividerView.setVisibility(View.VISIBLE);
            }
        }

        if (mPositiveButtonId != 0){
            mPositiveButtonText.setVisibility(View.VISIBLE);
            mPositiveButtonText.setText(getResources().getString(mPositiveButtonId));
        } else {
            mPositiveButtonText.setVisibility(View.GONE);
        }

        mConfirmButtonText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnConfirmClickListener != null){
                    mOnConfirmClickListener.onClick();
                }
            }
        });

        mNegativeButtonText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnNegativeClickListener != null){
                    mOnNegativeClickListener.onClick();
                }
            }
        });

        mPositiveButtonText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnPositiveClickListener != null){
                    mOnPositiveClickListener.onClick();
                }
            }
        });
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Dbug.d(tag, "onActivityCreated.............:");
        if(getDialog() == null || getDialog().getWindow() == null) return;
        final WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();

        params.width = 100;
        params.height = 50;
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
            params.width = displayMetrics.heightPixels * 4 / 5;
            params.height = displayMetrics.heightPixels * 2 / 4;
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            params.width = displayMetrics.widthPixels * 4 / 5;
            params.height = displayMetrics.widthPixels * 2 / 4;
        }
        params.gravity = Gravity.CENTER;
        getDialog().getWindow().setAttributes(params);
    }
}
