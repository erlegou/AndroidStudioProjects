package hunuo.com.wifiuav.ui.lib;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * @author zqjasonZhong
 *         date : 2017/5/27
 */
public class CustomTextView extends TextView {
    public CustomTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CustomTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomTextView(Context context) {
        super(context);
    }

    @Override
    public boolean isFocused() {
        return true;
    }
}
