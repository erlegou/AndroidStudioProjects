package com.jieli.stream.player.ui.dialog;

import android.app.DialogFragment;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.jieli.lib.stream.tools.CommandHub;
import com.jieli.lib.stream.util.ICommon;
import com.jieli.stream.player.MainApplication;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseDialogFragment;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IConstant;
import com.jieli.stream.player.util.PreferencesHelper;

public class ModifyDevicePasswordDialog extends BaseDialogFragment implements IConstant {
    private final String tag = getClass().getSimpleName();
    private EditText mFirstContent, mSecondContent;
    private TextView mConfirm;
    private TextView mCancel;
    private TextView mFirstInputTips, mSecondInputTips;
    /**SSID范围:1-32个字符, 密码范围:8-63个字符,必须有设置密码*/
    private final int MAX_WIFI_PWD_LENGTH = 63;
    private final int MIN_WIFI_PWD_LENGTH = 8;
    private String mOldPassword;
    private NotifyDialog mNotifyDialog;

    public static ModifyDevicePasswordDialog newInstance() {
        return new ModifyDevicePasswordDialog();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.modify_device_password_dialog, container, false);
        mFirstContent = (EditText) v.findViewById(R.id.et_first_input);
        mSecondContent = (EditText) v.findViewById(R.id.et_second_input);
        mConfirm = (TextView) v.findViewById(R.id.tv_confirm);
        mCancel = (TextView) v.findViewById(R.id.tv_cancel);
        mFirstInputTips = (TextView) v.findViewById(R.id.et_first_input_count);
        mSecondInputTips = (TextView) v.findViewById(R.id.et_second_input_count);
        mConfirm.setOnClickListener(mOnClickListener);
        mCancel.setOnClickListener(mOnClickListener);
        mFirstContent.addTextChangedListener(mFirstInputTextWatcher);
        mSecondContent.addTextChangedListener(mSecondInputTextWatcher);

        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(getActivity().getApplicationContext());
//        String currentSSID = sharedPreferences.getString(CURRENT_SSID, null);
        mOldPassword = sharedPreferences.getString(CURRENT_PWD, null);

        if (!TextUtils.isEmpty(mOldPassword)){
            mFirstContent.setText(mOldPassword);
            mFirstContent.setSelection(0, mFirstContent.getText().length());
            mFirstContent.requestFocus();
        }

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getDialog() == null || getDialog().getWindow() == null) return;
        final WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();

        params.width = 100;
        params.height = 50;
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.width = displayMetrics.heightPixels - 20;
            params.height = displayMetrics.heightPixels * 3 / 5;
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.width = displayMetrics.widthPixels - 20;
            params.height = displayMetrics.widthPixels * 3 / 5;
        }
        params.gravity = Gravity.CENTER;
        getDialog().getWindow().setAttributes(params);
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mConfirm == v) {
                final String text = mFirstContent.getText().toString().trim();
                final String confirmPwd = mSecondContent.getText().toString().trim();

                Dbug.d(tag, "text:[" + text + "], confirmPwd=" + confirmPwd + ", length=" + confirmPwd.length());
                if (text.isEmpty() && confirmPwd.isEmpty()){

                    commitPassword(confirmPwd);

                } else if (text.length() >= 1 || confirmPwd.length() >= 1) {
//                    if (TextUtils.isEmpty(text) || TextUtils.isEmpty(confirmPwd)) {
//                        showToastLong(R.string.name_empty_error);
//                        return;
//                    }

                    if (text.length() < MIN_WIFI_PWD_LENGTH || confirmPwd.length() < MIN_WIFI_PWD_LENGTH
                            || text.length() > MAX_WIFI_PWD_LENGTH || confirmPwd.length() > MAX_WIFI_PWD_LENGTH){
                        showToastLong(R.string.input_pwd_error);
                        return;
                    }

                    if (!text.equals(confirmPwd)){
                        showToastLong(R.string.password_not_match_confirmation);
                        return;
                    }

                    commitPassword(confirmPwd);

                } else {
                    showToastLong(R.string.input_pwd_error);
                }

            } else if(mCancel == v) {
                dismiss();
            }
        }
    };

    private void commitPassword(final String password){
        Dbug.d(tag, "commitModifyPassword:[" + password + "], mOldPassword=[" + mOldPassword + "]");
        dismiss();
        if (mOldPassword.equals(password)) {
            Dbug.w(tag, "Password No Change!");
            return;
        }

        mNotifyDialog = new NotifyDialog(R.string.dialog_tip, R.string.restart_to_take_effect, R.string.cancel, R.string.confirm, new NotifyDialog.OnNegativeClickListener() {
            @Override
            public void onClick() {
                mNotifyDialog.dismiss();
            }
        }, new NotifyDialog.OnPositiveClickListener() {
            @Override
            public void onClick() {
                mNotifyDialog.dismiss();

                /**Remove current Wi-Fi & the old SSID*/
                SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                String currentSSID = sharedPreferences.getString(CURRENT_SSID, null);
                editor.remove(CURRENT_PWD);
                editor.remove(CURRENT_SSID);
                editor.remove(currentSSID);
                editor.apply();
                /**Send to device*/
                MainApplication.getApplication().setModifyPWD(true);
                if (password.isEmpty()){
                    Dbug.i(tag, "ARGS_AP_PWD_NONE.:");
                    CommandHub.getInstance().sendCommand(ICommon.CTP_ID_DEFAULT, ICommon.CMD_AP_PASSWORD, ICommon.ARGS_AP_PWD_NONE);
                } else {
                    Dbug.i(tag, "not ARGS_AP_PWD_NONE:");
                    CommandHub.getInstance().sendCommand(ICommon.CTP_ID_DEFAULT, ICommon.CMD_AP_PASSWORD, password);
                }
            }

        });

        mNotifyDialog.show(getFragmentManager(), "mNotifyDialog");
    }

    private final TextWatcher mFirstInputTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            int mInputLength = mFirstContent.getText().toString().trim().getBytes().length;
            String firstInputTip = mInputLength + "/" + MAX_WIFI_PWD_LENGTH;
            mFirstInputTips.setText(firstInputTip);
            if (mInputLength > MAX_WIFI_PWD_LENGTH) {
                showToastShort(R.string.limited_input);
            }
        }
    };

    private final TextWatcher mSecondInputTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            int mInputLength = mSecondContent.getText().toString().trim().getBytes().length;
            String secondInputTip = mInputLength + "/" + MAX_WIFI_PWD_LENGTH;
            mSecondInputTips.setText(secondInputTip);
            if (mInputLength > MAX_WIFI_PWD_LENGTH) {
                showToastShort(R.string.limited_input);
            }
        }
    };
}
