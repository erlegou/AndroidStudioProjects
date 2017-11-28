package com.jieli.stream.player.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.jieli.lib.stream.tools.ParseHelper;
import com.jieli.stream.player.R;
import com.jieli.stream.player.base.BaseDialogFragment;
import com.jieli.stream.player.util.Dbug;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: bob
 * Date: 16-12-12 10:20
 * Version: V1
 * Description:
 */

public class VideosChoiceDialog extends BaseDialogFragment{
	private final String tag = getClass().getSimpleName();
	private ListView mListView;
	private TextView mReturn;
	private MyAdapter mAdapter;
	private ParseHelper mParseHelper;
	//private List<String> mData;
	private String mCurrentMode;
	private OnDismissListener mOnDismissListener;
	private ImageView mSettingIcon;

	public void setOnDismissListener(OnDismissListener listener){
		mOnDismissListener = listener;
	}

	public interface OnDismissListener {
		void onDismiss(int listPosition);
	}

	private OnItemClickListener mOnItemClickListener;
	public void setOnItemClickListener(OnItemClickListener listener){
		mOnItemClickListener = listener;
	}
	public interface OnItemClickListener{
		void onItemClick(int position);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final Dialog dialog = new Dialog(getActivity(), android.R.style.Theme_Translucent_NoTitleBar);
		final View view = getActivity().getLayoutInflater().inflate(R.layout.videos_choice_dialog, null);
		if(dialog.getWindow() == null) return dialog;
		setCancelable(false);
		dialog.getWindow().setContentView(view);
		final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
		final WindowManager.LayoutParams params = dialog.getWindow().getAttributes();

		params.width = 500;
		params.height = 540;
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
			params.width = displayMetrics.heightPixels - 20;
			params.height = displayMetrics.heightPixels - 20;
		} else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
			params.width = displayMetrics.widthPixels - 20;
			params.height = displayMetrics.widthPixels - 20;
		}
		params.gravity = Gravity.CENTER;
		dialog.getWindow().setAttributes(params);

		mSettingIcon = (ImageView) view.findViewById(R.id.setting_icon);

		mReturn = (TextView) view.findViewById(R.id.cancel_action);
		mReturn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		mAdapter = new MyAdapter(getActivity(), 0);

		mListView = (ListView) view.findViewById(android.R.id.list);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (mOnItemClickListener != null){
					mOnItemClickListener.onItemClick(position);
				}
				dismiss();
			}
		});
		mAdapter.clear();
		List<String> data = new ArrayList<>();
		data.add("Front videos");
		data.add("Rear videos");
		mAdapter.addAll(data);
		mAdapter.notifyDataSetChanged();
		return dialog;
	}

	private class MyAdapter extends ArrayAdapter<String>{
		private final LayoutInflater mLayoutInflater;
		private ViewHolder holder;

		MyAdapter(Context context, int resource) {
			super(context, resource);
			mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		private class ViewHolder {
			private TextView textView;
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null){
				holder = new ViewHolder();
				convertView = mLayoutInflater.inflate(R.layout.text_image_item, parent, false);
				holder.textView = (TextView) convertView.findViewById(R.id.text);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			final String data = getItem(position);
			holder.textView.setText(data);
			return convertView;
		}
	}
}
