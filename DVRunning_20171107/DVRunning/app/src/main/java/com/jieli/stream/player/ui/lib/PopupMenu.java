package com.jieli.stream.player.ui.lib;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;

import com.jieli.stream.player.R;
import com.jieli.stream.player.util.Dbug;
import com.jieli.stream.player.util.IConstant;

import java.util.ArrayList;
import java.util.List;

public class PopupMenu {

    private String tag = getClass().getSimpleName();
    private Context mContext;
    private List<String> itemList = new ArrayList<>();
    private List<Integer> resIds = new ArrayList<>();
    private PopupWindow popupWindow;
    private ListView mListView;
    private View parentView;
    private OnPopItemClickListener listener;

    public interface OnPopItemClickListener {
        void onItemClick(List<String> data, List<Integer> resIds, int index);
    }

    public PopupMenu(Context context, List<String> list, List<Integer> ids){
        if(context == null){
            Dbug.e(tag, "PopupMenu context is null!");
            return;
        }
        this.mContext = context;
        this.itemList = list;
        this.resIds = ids;

        View view = LayoutInflater.from(mContext).inflate(R.layout.popup_menu_layout, null);

        //init listView
        mListView = (ListView) view.findViewById(R.id.pop_list_view);
        mListView.setAdapter(new PopupAdapter(mContext));
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (listener != null) {
                    listener.onItemClick(itemList, resIds, position);
                }
                dismiss();
            }
        });

        popupWindow = new PopupWindow(view, context.getResources().getDimensionPixelOffset(R.dimen.popup_menu_width),
                ViewGroup.LayoutParams.WRAP_CONTENT);

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    public void setOnPopItemClickListener(OnPopItemClickListener listener) {
        this.listener = listener;
    }

	public void showAtLocation(View parent, int gravity, int x, int y){
		if(popupWindow == null){
			Dbug.e(tag, "PopupMenu popupWindow is null!");
			return;
		}

		parentView = parent;

		popupWindow.showAtLocation(parent, gravity, x, y);
		popupWindow.setFocusable(true);

		popupWindow.setOutsideTouchable(true);

		popupWindow.update();
	}

	private static Rect locateView(View v)
	{
		int[] loc_int = new int[2];
		if (v == null) return null;
		try
		{
			v.getLocationOnScreen(loc_int);
		} catch (NullPointerException npe)
		{
			//Happens when the view doesn't exist on screen anymore.
			return null;
		}
		Rect location = new Rect();
		location.left = loc_int[0] + v.getWidth();
		location.top = 0;//loc_int[1] - v.getHeight()/2;
		location.right = location.left + v.getWidth();
		location.bottom = location.top + v.getHeight();
		return location;
	}

	public void showAsDropDown(View parent, int w, int h){
		if(popupWindow == null){
			Dbug.e(tag, "PopupMenu popupWindow is null!");
			return;
		}

		parentView = parent;

		Rect location = locateView(parent);
		if(location != null){
            popupWindow.showAtLocation(parent, Gravity.BOTTOM|Gravity.LEFT, location.left, location.top);
//		popupWindow.showAsDropDown(parent, 0, -parent.getHeight()-100);
            popupWindow.setFocusable(true);

            popupWindow.setOutsideTouchable(true);

            popupWindow.update();
        }
	}

    public void showAsDropDown(View parent){
        if(popupWindow == null){
            Dbug.e(tag, "PopupMenu popupWindow is null!");
            return;
        }

        parentView = parent;

        popupWindow.showAsDropDown(parent);

        popupWindow.setFocusable(true);

        popupWindow.setOutsideTouchable(true);

        popupWindow.update();
    }

    public void dismiss() {
        if(popupWindow != null){
            popupWindow.dismiss();
        }
    }

    public View getParentView(){
        return parentView;
    }

    public void addItems(List<String> items, boolean isAppend) {
        if(!isAppend){
            if(itemList.size() > 0){
                itemList.clear();
            }
            itemList = items;
        }else{
            itemList.addAll(items);
        }

        if(mListView != null){
            ((PopupAdapter)mListView.getAdapter()).notifyDataSetChanged();
        }
    }

    public int getResource(String imageName){
        if (TextUtils.isEmpty(imageName)){
            return 0;
        }
        return mContext.getResources().getIdentifier(imageName, IConstant.RESOURCE_DIR , mContext.getPackageName());
    }

    public void addItem(String item) {
        itemList.add(item);
        if(mListView != null){
            ((PopupAdapter)mListView.getAdapter()).notifyDataSetChanged();
        }
    }

    private class PopupAdapter extends BaseAdapter {
        private Context mContext;

        public PopupAdapter(Context context){
            this.mContext = context;
        }
        @Override
        public int getCount() {
            return itemList.size();
        }

        @Override
        public Object getItem(int position) {
            String item;
            if(position < itemList.size()){
                item = itemList.get(position);
            }else{
                item = null;
            }
            return item;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if(convertView == null){
                convertView = LayoutInflater.from(mContext).inflate(R.layout.popup_menu_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.imageView = (ImageView) convertView.findViewById(R.id.item_image);
                convertView.setTag(viewHolder);
            }else{
                viewHolder = (ViewHolder) convertView.getTag();
            }
            //set res id
            if(position < itemList.size()){
                viewHolder.imageView.setImageResource(getResource(itemList.get(position)));
            }else{
                viewHolder.imageView.setImageResource(0);
            }

            return convertView;
        }

        private final class ViewHolder {
            ImageView imageView;
        }
    }

}
