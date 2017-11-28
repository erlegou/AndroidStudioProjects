package com.jieli.stream.player.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jieli.stream.player.R;

public class WaitDialog extends Dialog {

	private Animation mAnimation;
	private ImageView progressImageView;
	private TextView notifyText;
	private Context mContext;
	private LinearInterpolator mInterpolator;
	private LinearLayout load_linear;

	private interface OnDialogClickListener {
		void onDialogClick(Dialog dialog, int which);
	}

	private WaitDialog(Context context) {
		super(context, R.style.MyDialog);
		setContentView(R.layout.wait_dialog_layout);

		mContext = context;
		load_linear = (LinearLayout) findViewById(R.id.load_linear);
		mAnimation = AnimationUtils.loadAnimation(mContext,
				R.anim.progress_animation);
		notifyText  = (TextView) findViewById(R.id.notify);
		mInterpolator = new LinearInterpolator();
		mAnimation.setInterpolator(mInterpolator);

	}


	public WaitDialog(Context context, String message1, String message2,
			OnDialogClickListener listener) {
		this(context);
		notifyText.setText(message1);
	}

	public WaitDialog(Context context, int message) {
		this(context, context.getString(message), null);
	}

	public WaitDialog(Context context, String message) {
		this(context, message, null);
	}

	public WaitDialog(Context context, String message,
			OnDialogClickListener listener) {
		this(context);
		notifyText.setText(message);

	}

	public WaitDialog(Context context, int message,
			OnDialogClickListener listener) {
		this(context, context.getString(message), listener);
	}

	public WaitDialog(Context context, int message, int check_text,
			boolean check, OnDialogClickListener listener) {
		this(context, context.getString(message),
				context.getString(check_text), check, listener);
	}

	public WaitDialog(Context context, int message, String check_text,
			boolean check, OnDialogClickListener listener) {
		this(context, context.getString(message), check_text, check, listener);
	}

	public WaitDialog(Context context, String message, int check_text,
			boolean check, OnDialogClickListener listener) {
		this(context, message, context.getString(check_text), check, listener);
	}

	public WaitDialog(Context context, String message, String check_text,
			boolean check, OnDialogClickListener listener) {
		this(context);
		notifyText.setText(message);
	}

}
