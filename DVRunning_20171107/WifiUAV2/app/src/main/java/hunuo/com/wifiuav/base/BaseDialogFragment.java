package hunuo.com.wifiuav.base;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class BaseDialogFragment extends DialogFragment {
    private Toast mToastShort;
    private Toast mToastLong;
    private boolean isShown = false;

    @Override public void onStart() {
        super.onStart();
        Window window = getDialog().getWindow();
        if(window == null) return;
        WindowManager.LayoutParams windowParams = window.getAttributes();
        windowParams.dimAmount = 0.5f;
        windowParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(windowParams);
    }

    public void showToastShort(String msg) {
        if (mToastShort != null) {
            mToastShort.setText(msg);
        } else {
            if (null != getActivity()) {
                mToastShort = Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT);
            }
        }
        if(mToastShort != null){
            mToastShort.show();
        }
    }

    public void showToastShort(int msg) {
        showToastShort(getResources().getString(msg));
    }

    public void showToastLong(String msg) {
        if (mToastLong != null) {
            mToastLong.setText(msg);
        } else {
            if (null != getActivity()) {
                mToastLong = Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG);
            }
        }
        if(mToastLong != null){
            mToastLong.show();
        }
    }

    public void showToastLong(int msg) {
        showToastLong(getResources().getString(msg));
    }

    @Override
    public void show(FragmentManager manager, String tag) {
//        super.show(manager, tag);
        if(isShowing()){
            return;
        }
        FragmentTransaction ft = manager.beginTransaction();
        ft.add(this, tag);
        ft.commitAllowingStateLoss();

        isShown = true;
    }

    @Override
    public void dismiss() {
        super.dismissAllowingStateLoss();
        isShown = false;
    }

    public boolean isShowing() {
        return isShown;
    }
}
