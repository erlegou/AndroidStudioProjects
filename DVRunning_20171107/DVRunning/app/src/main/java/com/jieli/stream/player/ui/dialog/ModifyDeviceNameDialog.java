package com.jieli.stream.player.ui.dialog;

import android.app.DialogFragment;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
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

public class ModifyDeviceNameDialog extends BaseDialogFragment implements IConstant, TextWatcher {
    private final String tag = getClass().getSimpleName();
    private EditText mContent;
    private TextView mConfirm;
    private TextView mCancel;
    private TextView mInputTips;
    /**SSID范围:1-32个字符, 密码范围:8-63个字符,必须有设置密码*/
    private final int MAX_WIFI_NAME_LENGTH = 32;
    private final int MAX_INPUT_NAME_LENGTH = MAX_WIFI_NAME_LENGTH - WIFI_PREFIX.length();
    private int mInputLength;
    private String mVariable;
    private String mOldName;
    private NotifyDialog mNotifyDialog;

    private final String FILTER_ASCII = "\\A\\p{ASCII}*\\z";
    public static ModifyDeviceNameDialog newInstance() {
        return new ModifyDeviceNameDialog();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.modify_device_name_dialog, container, false);
        mContent = (EditText) v.findViewById(R.id.et_content);
        mConfirm = (TextView) v.findViewById(R.id.tv_confirm);
        mCancel = (TextView) v.findViewById(R.id.tv_cancel);
        TextView prefixName = (TextView) v.findViewById(R.id.prefix_name);
        mInputTips = (TextView) v.findViewById(R.id.et_input_count);
        mConfirm.setOnClickListener(mOnClickListener);
        mCancel.setOnClickListener(mOnClickListener);
        mContent.addTextChangedListener(this);

        SharedPreferences sharedPreferences = PreferencesHelper.getSharedPreferences(MainApplication.getApplication());
        mOldName = sharedPreferences.getString(CURRENT_SSID, null);
        InputFilter filter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (!(source + "").matches(FILTER_ASCII)){
                    return "";
                }
                return null;
            }
        };
        mContent.setFilters(new InputFilter[] { filter });

        prefixName.setText(WIFI_PREFIX);
        mVariable = mOldName.substring(WIFI_PREFIX.length());
        mContent.setText(mVariable);
        mContent.setSelection(0, mContent.getText().length());
        mContent.requestFocus();

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
                final String text = mContent.getText().toString().trim();

                if (TextUtils.isEmpty(text) || text.length() == 0) {
                    showToastLong(R.string.name_empty_error);
                    return;
                }

                if (mInputLength > MAX_INPUT_NAME_LENGTH){
                    showToastLong(R.string.limited_input);
                    return;
                }
                commitModifyName(text);
            } else if(mCancel == v) {
                dismiss();
            }
        }
    };

    private void commitModifyName(final String changedName){
        dismiss();
        if (mVariable.equals(changedName)) {
            Dbug.w(tag, "No change!");
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
                editor.remove(CURRENT_PWD);
                editor.remove(CURRENT_SSID);
                editor.remove(mOldName);
                editor.apply();
                /**Send modified SSID to device*/
                MainApplication.getApplication().setModifySSID(true);
                CommandHub.getInstance().sendCommand(ICommon.CTP_ID_DEFAULT, ICommon.CMD_AP_SSID, WIFI_PREFIX + changedName);
            }
        });

        mNotifyDialog.show(getFragmentManager(), "mNotifyDialog");
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        mInputLength = mContent.getText().toString().trim().getBytes().length;
        String inputTip = mInputLength + "/" + MAX_INPUT_NAME_LENGTH;
        mInputTips.setText(inputTip);
        if (mInputLength > MAX_INPUT_NAME_LENGTH) {
            showToastShort(R.string.limited_input);
        }
    }
}
