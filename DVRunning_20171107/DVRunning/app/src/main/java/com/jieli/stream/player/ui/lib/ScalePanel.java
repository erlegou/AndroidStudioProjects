package com.jieli.stream.player.ui.lib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import com.jieli.lib.stream.beans.VideoInfo;
import com.jieli.stream.player.util.Dbug;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ScalePanel extends View {
    private final String tag = getClass().getSimpleName();

    public interface OnValueChangeListener {
        void onValueChange(float value);

        /**
         * value不再变化，终点
         *
         * @param mCalendar 刻度盘上当前时间
         */
        void onValueChangeEnd(Calendar mCalendar);
    }

    private static final int ITEM_HALF_DIVIDER = 60;

    private static final int ITEM_MAX_HEIGHT = 15;

    private static final int TEXT_SIZE = 14;

    /**
     * 像素密度
     */
    private final float mDensity;
    /**
     * 当前刻度值
     */
    private int mValue = 12;
    private final int mLineDivider = ITEM_HALF_DIVIDER;

    private float mLastX;
    /**
     * 记录刻度盘滑动的偏移量
     */
    private float mMove;

    /**
     * View的宽
     */
    private float mWidth;

    /**
     * View的高
     */
    private float mHeight;

    private final int mMinVelocity;
    private final Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    private OnValueChangeListener mListener;
    /**
     * 日期文字的宽度
     */
    private float textWidth = 0;
    private final TextPaint textPaint;
    private final TextPaint dateAndTimePaint;
    private final Paint linePaint;
    private final Paint minLinePaint;
    private boolean isNeedDrawableLeft, isNeedDrawableRight;
    private Calendar mCalendar;
    private final Paint middlePaint;
    private final Paint bgColorPaint;
    /**
     *
     */
    private boolean isChangeFromInSide;
    /** 为了画背景色，从左向右画，记录下屏幕最左，最右处的时间点*/
    private final Calendar leftCalendar;
    private final Calendar rightCalendar;
    private List<VideoInfo> data;
    private int hour, minute, second;
    private String dateStr;
    private String timeStr;

    private float offsetPercent;
    /**
     * 小时刻度单位
     */
    private final float hourScaleUnit;
    /**
     * 分钟刻度单位
     */
    private final float minScaleUnit;
    private boolean isChange = false;
    /**
     * 线条底部的位置
     */
    private float lineBottom;
    /**
     * 线条顶部得到位置
     */
    private float lineTop;


    public ScalePanel(Context context, AttributeSet attrs) {
        super(context, attrs);

        mScroller = new Scroller(getContext());
        mDensity = getContext().getResources().getDisplayMetrics().density;

        mMinVelocity = ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity();
        linePaint = new Paint();
        linePaint.setStrokeWidth(2);
        linePaint.setColor(getResources().getColor(android.R.color.white));

        minLinePaint = new Paint();
        minLinePaint.setStrokeWidth(1);
        minLinePaint.setColor(getResources().getColor(android.R.color.white));

        bgColorPaint = new Paint();
        bgColorPaint.setStrokeWidth(2);
//        bgColorPaint.setColor(Color.parseColor("#00a3dd"));
        bgColorPaint.setColor(getResources().getColor(android.R.color.holo_red_light));

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(getResources().getColor(android.R.color.white));
        textPaint.setTextSize(TEXT_SIZE * mDensity);

        dateAndTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        dateAndTimePaint.setColor(getResources().getColor(android.R.color.white));
        dateAndTimePaint.setTextSize(18 * mDensity);

        middlePaint = new Paint();
        middlePaint.setStrokeWidth(1);
        middlePaint.setColor(getResources().getColor(android.R.color.white));
        hourScaleUnit = mLineDivider * mDensity;//120
        minScaleUnit = mLineDivider;//60
        mCalendar = Calendar.getInstance();
        initDateAndTime(mCalendar);

        leftCalendar = Calendar.getInstance();
        rightCalendar = Calendar.getInstance();

        Log.w(tag, "mDensity=" + mDensity + ", mLineDivider=" + mLineDivider +", hourScaleUnit=" + hourScaleUnit);
    }

    /**
     * 根据时间来计算偏差，(minute*60+second)*hourScaleUnit/3600
     */
    private void initOffSet() {
        mMove = (minute * 60 + second) * hourScaleUnit / 3600;
        Log.e(tag, "mMove=" + mMove);
    }

    private void initDateAndTime(Calendar mCalendar) {
        this.mCalendar = mCalendar;
        hour = mCalendar.get(Calendar.HOUR_OF_DAY);
        minute = mCalendar.get(Calendar.MINUTE);
        second = mCalendar.get(Calendar.SECOND);
        mValue = hour;
        initOffSet();
    }

    /**
     * 通过设置calendar来设置刻度盘当前的时间
     *
     * @param mCalendar
     */
    public void setCalendar(Calendar mCalendar) {
        // 用户手指拖动刻度盘的时候，不接收外部的更新，以免冲突
        if (!isChangeFromInSide) {
            initDateAndTime(mCalendar);
            initOffSet();
            invalidate();
        }
    }

    /**
     * 设置用于接收结果的监听器
     *
     * @param listener
     */
    public void setValueChangeListener(OnValueChangeListener listener) {
        mListener = listener;
    }

    /**
     * 获取当前刻度值
     *
     * @return
     */
    public float getValue() {
        return mValue;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mWidth = getWidth();
        mHeight = getHeight();
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawScaleLine(canvas);
        drawMiddleLine(canvas);
    }

    /**
     * 从中间往两边开始画刻度线
     *
     * @param canvas 画布
     */
    private void drawScaleLine(Canvas canvas) {
        canvas.save();
        isNeedDrawableLeft = true;
        isNeedDrawableRight = true;
        float width = mWidth;
        float xPosition;
        lineBottom = mHeight - getPaddingBottom();
        lineTop = lineBottom - mDensity * ITEM_MAX_HEIGHT;
//        Log.d(tag, "mHeight=" + mHeight+ ", lineBottom=" + lineBottom + ", lineTop=" + lineTop + ", mValue=" + mValue);
        if (data != null && data.size() > 0) {
            calculateDrawPosition(canvas);
        } else {
            Dbug.w(tag, "data is null");
        }
        /** mValue的值控制在0~23之间*/
        if (mValue > 0) {
            mValue = mValue % 24;
        } else if (mValue < 0) {
            mValue = mValue % 24 + 24;
        }
        if (mMove < 0) {//向左滑动
            if (mValue == 0 && hour != 23) {
                mCalendar.set(Calendar.DAY_OF_MONTH, mCalendar.get(Calendar.DAY_OF_MONTH) - 1);
            }

            hour = mValue - 1;
            //滑到上一日23点
            if (hour == -1) {
                hour = 23;
            }
            offsetPercent = 1 + mMove / hourScaleUnit;
        } else if (mMove >= 0) {//向右滑动，
            offsetPercent = mMove / hourScaleUnit;
            hour = mValue;
            //滑到次日0点，
            if (hour == 0 && !isChange) {
                //如果没有isChange，那么在hour==0时，day会重复加一
                mCalendar.set(Calendar.DAY_OF_MONTH, mCalendar.get(Calendar.DAY_OF_MONTH) + 1);
                // 避免重复把day+1
                isChange = true;
            }
        }
        if (hour != 0) {
            /** 在hour切换成别的值的时候再把标志设为默认值*/
            isChange = false;
        }
        countMinAndSecond(offsetPercent);

        /**画时间文本*/
        drawTimeText(canvas);
        for (int i = 0; true; i++) {
            /** 往右边开始画*/
            xPosition = (width / 2 - mMove) + i * hourScaleUnit;
            if (isNeedDrawableRight && (xPosition + getPaddingRight()) < mWidth) {
//                Log.d(tag,  "xPosition=" + xPosition + ", mMove=" + mMove + ", i * hourScaleUnit=" + (i * hourScaleUnit) + ", mValue=" + mValue );
                /** 在view范围内画刻度*/
                canvas.drawLine(xPosition, lineTop, xPosition, lineBottom, linePaint);
                textWidth = Layout.getDesiredWidth(int2Str(mValue + i), textPaint);
                canvas.drawText(int2Str(mValue + i), xPosition - (textWidth / 2), lineTop - 5, textPaint);
                /** 在view范围内画分钟刻度*/
                for (int j = (int) (hourScaleUnit / 5); j < hourScaleUnit; j += hourScaleUnit / 5) {
                    canvas.drawLine(xPosition + j, lineTop + 10, xPosition + j, lineBottom, minLinePaint);
                }
            } else {
                isNeedDrawableRight = false;
            }
            /** 往左边开始画*/
            if (i > 0) {// 防止中间的刻度画两遍
                xPosition = (width / 2 - mMove) - i * hourScaleUnit;
                if (isNeedDrawableLeft && xPosition > getPaddingLeft()) {
//                    Log.i(tag,  "xPosition=" + xPosition + ", mMove=" + mMove + ", i * hourScaleUnit=" + (i * hourScaleUnit));
                    /** 在view范围内画刻度*/
                    canvas.drawLine(xPosition, lineTop, xPosition, lineBottom, linePaint);
                    textWidth = Layout.getDesiredWidth(int2Str(mValue - i), textPaint);
                    canvas.drawText(int2Str(mValue - i), xPosition - (textWidth / 2), lineTop - 5, textPaint);

                    /** 在view范围内画分钟刻度*/
                    for (int j = (int) (hourScaleUnit / 5); j < hourScaleUnit; j += hourScaleUnit / 5) {
                        canvas.drawLine(xPosition - j, lineTop + 10, xPosition - j, lineBottom, minLinePaint);
                    }
                } else {
                    isNeedDrawableLeft = false;
                }
            } else if (i == 0){/**往左边的第一格*/
                xPosition = (width / 2 - mMove) - i * hourScaleUnit;
                /** 在view范围内画分钟刻度*/
                for (int j = (int) (hourScaleUnit / 5); j < hourScaleUnit; j += hourScaleUnit / 5) {
                    canvas.drawLine(xPosition - j, lineTop + 10, xPosition - j, lineBottom, minLinePaint);
                }
            }

            /** 当不需要向左或者向右画的时候就退出循环，结束绘制操作*/
            if (!isNeedDrawableLeft && !isNeedDrawableRight) {
                break;
            }
        }
        canvas.restore();
    }

    /**
     * 还存在问题，如果data数据量过大，也就是用户搜索的时间跨度过大，这种方式肯定不行会卡死。
     * 所以以后得通过获得当前回放所处的位置，然后选择前后一天左右的时间，这样数据量就不会太大
     * 现在本着先做出来再优化的原则，记录下此问题，以后再做修改优化
     *
     * @param canvas 画布
     */
    private void calculateDrawPosition(Canvas canvas) {
        // 距离和时间对应起来 ((mWidth/2/hourScaleUnit)*3600*1000)
        long timeOffset = (long) ((mWidth / 2 / hourScaleUnit) * 3600 * 1000);
        long middleTime = mCalendar.getTimeInMillis();
        // 根据时间偏移算出左右的时间
        leftCalendar.setTimeInMillis(middleTime - timeOffset);
        rightCalendar.setTimeInMillis(middleTime + timeOffset);
        // 找到时间开始点，然后顺序向右画，直到画到屏幕最右侧，关键是找到时间开始点
        // 时间开始点就是从什么地方开始画背景色
        for (int position = 0; position < data.size(); position++) {
            VideoInfo tVideoFile = data.get(position);
            Calendar startCalendar = tVideoFile.getStartTime();
            Calendar endCalendar = tVideoFile.getEndTime();
            if (leftCalendar.before(startCalendar) && rightCalendar.after(startCalendar)) {
                /** 从start从开始画*/
                drawBgColor(canvas, startCalendar, endCalendar, position);
                break;
            } else if (leftCalendar.after(startCalendar) && leftCalendar.before(endCalendar)) {
                /** 从left从开始画*/
                drawBgColor(canvas, leftCalendar, endCalendar, position);
                break;
            }
        }
    }

    /**
     * 画布上，有内容的背景宽度
     */
    private final int mBackgroundWidth = 60;
    /**
     * 画布上，有内容的背景高度
     */
    private final int mBackgroundHeight = mBackgroundWidth + 60;
    /**
     * @param canvas 画布
     * @param startTime 第一块背景色开始的位置
     * @param endTime   第一块背景色的长度
     * @param position  第一块背景色所在时间片段在data中所处的position，下一块从position+1开始
     */
    private void drawBgColor(Canvas canvas, Calendar startTime, Calendar endTime, int position) {
        /** 根据时间获得在刻度盘上具体的位置*/
        float startPosition = getPositionByTime(startTime);
        float endPosition = getPositionByTime(endTime);

        /**画有内容的开始部分背景*/
        drawBgColorRect(startPosition, mBackgroundWidth, endPosition, mBackgroundHeight, canvas);

        for (int i = position + 1; i < data.size(); i++) {
            VideoInfo tVideoFile = data.get(i);
            Calendar startCalendar = tVideoFile.getStartTime();
            Calendar endCalendar = tVideoFile.getEndTime();
            startPosition = getPositionByTime(startCalendar);
            endPosition = getPositionByTime(endCalendar);
            if (startPosition <= mWidth) {
                /** 只画屏幕屏幕区域以内的*/
                drawBgColorRect(startPosition, mBackgroundWidth, endPosition, mBackgroundHeight, canvas);
            } else {
                break;
            }
        }
    }

    /**
     * 画背景色
     *
     * @param canvas
     */
    private void drawBgColorRect(float left, float top, float right, float bottom, Canvas canvas) {
//        Log.d(tag, "left=" + left+ ", top=" + top + ", right=" + right + ", bottom=" + bottom);
        canvas.drawRect(left, top, right, bottom, bgColorPaint);
    }

    /**
     * 根据时间获得在刻度盘上具体的位置
     *
     * @param calendar
     * @return
     */
    public float getPositionByTime(Calendar calendar) {
        long middleTime = mCalendar.getTimeInMillis();
        float position;
        long timeOffset = middleTime - calendar.getTimeInMillis();
        if (timeOffset >= 0) {
            position = (float) (mWidth / 2 - (1.0 * timeOffset / 3600 / 1000) * hourScaleUnit);
        } else {
            position = (float) (mWidth / 2 - (1.0 * timeOffset / 3600 / 1000) * hourScaleUnit);
        }
        return position;
    }

    /**
     * 准备画背景色的数据
     */
    public void setTimeData(List<VideoInfo> data) {
        this.data = data;
    }

    /**
     * 画日期时间的文字
     *
     * @param canvas
     */
    private void drawTimeText(Canvas canvas) {
        mCalendar.set(Calendar.HOUR_OF_DAY, hour);
        mCalendar.set(Calendar.MINUTE, minute);
        mCalendar.set(Calendar.SECOND, second);
        timeStr = date2timeStr(mCalendar.getTime());
        textWidth = Layout.getDesiredWidth(timeStr, textPaint);
        canvas.drawText(timeStr, mWidth / 2 + 15 * mDensity, 50, dateAndTimePaint);
        drawDateText(canvas);
    }

    private void drawDateText(Canvas canvas) {
        dateStr = date2DateStr(mCalendar.getTime());
        textWidth = Layout.getDesiredWidth(dateStr, textPaint);
        canvas.drawText(dateStr, mWidth / 2 - textWidth - 35 * mDensity, 50, dateAndTimePaint);
    }

    /**
     * 计算分钟和秒钟
     *
     * @param percent
     * @return
     */
    public int[] countMinAndSecond(float percent) {
        minute = (int) (3600 * percent / 60);
        second = (int) (3600 * percent % 60);
        return new int[]{minute, second};
    }

    /**
     * 画中间的红色指示线、阴影等。指示线两端简单的用了两个矩形代替
     *
     * @param canvas
     */
    private void drawMiddleLine(Canvas canvas) {
        canvas.save();

        canvas.drawLine(mWidth / 2, 0, mWidth / 2, mHeight, middlePaint);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        int xPosition = (int) event.getX();

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mScroller.forceFinished(true);
                mLastX = xPosition;
                isChangeFromInSide = true;
                Log.d(tag, "ACTION_DOWN=" + mLastX);
                break;
            case MotionEvent.ACTION_MOVE:
                mMove += (mLastX - xPosition);
                changeMoveAndValue();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                Log.i(tag, "ACTION_UP=" + mLastX);
                countMoveEnd();
                countVelocityTracker(event);
                return false;
            default:
                break;
        }
        mLastX = xPosition;
        return true;
    }

    private void changeMoveAndValue() {
        float fValue = mMove / hourScaleUnit;
        int tValue = (int) fValue;
        //滑动超过一格以后，记录下当前刻度盘上的值
        if (Math.abs(fValue) > 0) {
            mValue += tValue;
            //偏移量永远都小于一格
            mMove -= tValue * hourScaleUnit;
            notifyValueChange();
            postInvalidate();
        }
    }

    private void countVelocityTracker(MotionEvent event) {
        mVelocityTracker.computeCurrentVelocity(1000, 1500);
        float xVelocity = mVelocityTracker.getXVelocity();
//		Log.w(tag, "mMinVelocity="+mMinVelocity + ", Math.abs(xVelocity)="+Math.abs(xVelocity));
        if (Math.abs(xVelocity) > mMinVelocity) {
            mScroller.fling(0, 0, (int) xVelocity, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0);
        } else {
            notifyChangeOver();
        }
    }

    private void countMoveEnd() {
        mLastX = 0;
        notifyValueChange();
        postInvalidate();
    }

    private void notifyValueChange() {
        if (null != mListener) {
            mListener.onValueChange(mValue);
        }
    }

    private void notifyChangeOver() {
        if (null != mListener) {
            mListener.onValueChangeEnd(mCalendar);
        }
        isChangeFromInSide = false;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            if (mScroller.getCurrX() == mScroller.getFinalX()) { // over
                countMoveEnd();
                notifyChangeOver();
            } else {
                int xPosition = mScroller.getCurrX();
                mMove += (mLastX - xPosition);
                changeMoveAndValue();
                mLastX = xPosition;
            }
        }
    }

    public String int2Str(int i) {
        if (i > 0) {
            i = i % 24;
        } else if (i < 0) {
            i = i % 24 + 24;
        }
        String str = String.valueOf(i);
        if (str.length() == 1) {
            return "0" + str + ":00";
        } else if (str.length() == 2) {
            return str + ":00";
        }
        return "";
    }

    public String date2DateStr(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(date);
    }

    public String date2timeStr(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        return dateFormat.format(date);
    }
}