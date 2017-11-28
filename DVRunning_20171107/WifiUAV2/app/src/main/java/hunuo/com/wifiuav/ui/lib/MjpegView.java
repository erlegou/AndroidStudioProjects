package hunuo.com.wifiuav.ui.lib;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.jieli.lib.stream.tools.ContrastCompress;
import com.jieli.lib.stream.util.ICommon;

import java.lang.ref.WeakReference;
import java.util.concurrent.LinkedBlockingQueue;

import hunuo.com.wifiuav.uity.Dbug;

public class MjpegView extends SurfaceView implements SurfaceHolder.Callback, ICommon {
    private static final String tag = MjpegView.class.getSimpleName();
    private boolean surfaceDone = false;
    private int mJpegWidth = 640;
    private int mJpegHeight = 480;
    private int mLightLevel = 0;
    private static BitmapFactory.Options bitmapOptions;

    private VideoThread mVideoThread;
    //private AudioTrack mAudioTrack = null;
    private AudioThread mAudioThread;
    public MjpegView(Context context) {
        super(context);
        init();
    }

    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Dbug.i(tag, "init.........................");
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);

        bitmapOptions = new BitmapFactory.Options();
//        bitmapOptions.inSampleSize = 2;
//        bitmapOptions.inScaled = false;
//        bitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        //noinspection deprecation
        bitmapOptions.inPurgeable = true;
    }

    public void clearCanvas() {
        synchronized (getHolder()) {
            Canvas canvas = getHolder().lockCanvas();
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            getHolder().unlockCanvasAndPost(canvas);
        }
    }

    public void drawThumbnail(byte[] data){
        if (mVideoThread != null && data != null) {
            mVideoThread.drawThumbnail(data);
        }
    }

    public void drawBitmap(byte[] data, boolean isOptimize){
        if (!surfaceDone) {
            Dbug.w(tag, "Surface not done");
            return;
        }

        if (mVideoThread != null && data != null) {
            mVideoThread.addData(data, isOptimize);
        }
    }

    public void writeAudioData(byte[] audioData) {
        if (mAudioThread != null && audioData != null) {
            mAudioThread.addData(audioData);
        }
    }

    public void updateLightLevel(int level) {
        if (mVideoThread != null) {
            mVideoThread.updateLightLevel(level);
        }
    }

    public void setJpegWidthAndHeightAndLevel(int width, int height, int level) {
        mJpegWidth = width;
        mJpegHeight = height;
        mLightLevel = level;
    }

    public int getContrastCompressWidth(){
        int width = mJpegWidth;
        if(mVideoThread != null){
            width = mVideoThread.getjWidth();
        }
        return width;
    }

    public int getContrastCompressHeight(){
        int height = mJpegHeight;
        if(mVideoThread != null){
            height = mVideoThread.getjHeight();
        }
        return height;
    }

    private static class VideoThread extends Thread {
        private boolean isVideoThreadRunning = false;
        private final LinkedBlockingQueue<byte[]> mBufList = new LinkedBlockingQueue<>(5);
        private int dispWidth;
        private int dispHeight;
        private boolean isVGA = true;
        private boolean isCompress = false;
        private volatile boolean isWaiting = false;
        private boolean isOptimize = false;
        private ContrastCompress mContrastCompress;
        private WeakReference<SurfaceHolder> mWeakRefSurfaceHolder;
        private int jWidth;
        private int jHeight;
        private Paint mPaint;

        public VideoThread (SurfaceHolder surfaceHolder, int jpegWidth, int jpegHeight, int lightLevel) {
            Dbug.e(tag, "==== VideoThread onCreate ====jpegWidth : " + jpegWidth +" ,jpegHeight = " +jpegHeight);
            jWidth = jpegWidth;
            jHeight = jpegHeight;
            mWeakRefSurfaceHolder = new WeakReference<>(surfaceHolder);
            mContrastCompress = new ContrastCompress(jpegWidth, jpegHeight, jpegWidth * 4, 9, lightLevel);
            if(!((jpegWidth == 640) && (jpegHeight == 480))){
                isVGA = false;
            }
            initPaint();
        }

        private void initPaint(){
            mPaint = new Paint();
            mPaint.setFilterBitmap(true);
            mPaint.setDither(true);
            mPaint.setAntiAlias(true);
        }

        public int getjWidth() {
            return jWidth;
        }

        public int getjHeight() {
            return jHeight;
        }

        public void updateLightLevel(int level) {
            if(level >= 0){
                isCompress = true;
                if (mContrastCompress != null) {
                    mContrastCompress.updateLightLevel(level);
                }
            }else{
                isCompress = false;
            }
            Dbug.e(tag, "isCompress = " +isCompress + " ,level = " +level);
        }

        public void addData(byte[] data, boolean isOptimize) {
            this.isOptimize = isOptimize;
            //Dbug.i(tag, "mBufList=" + mBufList.size() + ", remainingCapacity=" + mBufList.remainingCapacity());
            if (mBufList.remainingCapacity() <= 1) {
                mBufList.poll();
            }
            try {
                mBufList.put(data);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (isWaiting) {
                synchronized (mBufList) {
                    mBufList.notify();
                }
            }
        }

        public void drawThumbnail(byte[] data) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

            if (bitmap != null){
                Canvas canvas = null;
                final SurfaceHolder surfaceHolder = mWeakRefSurfaceHolder.get();
                try {
                    if (surfaceHolder != null) {
                        canvas = surfaceHolder.lockCanvas(null);
                        Rect destRect = resizeRect(bitmap.getWidth(), bitmap.getHeight());
                        if (canvas != null){
//                            Paint paint = new Paint();
//                            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
//                            canvas.drawPaint(paint);
//                            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
                            canvas.drawBitmap(bitmap, null, destRect, mPaint);
                        } else {
                            Dbug.e(tag, "drawThumbnail: resize destination rectangle fail. destRect=" + destRect);
                        }
                    }
                }finally {
                    if (canvas != null) {
                        if (null != surfaceHolder) {
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                        if (!bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    }
                }
            } else {
                Dbug.e(tag, "drawThumbnail: bitmap is null. data size=" + data.length);
            }
        }

        public void stopRunning (){
            isVideoThreadRunning = false;
            synchronized (mBufList) {
                mBufList.notify();
                mBufList.clear();
            }

            if (mContrastCompress != null) {
                mContrastCompress.release();
                mContrastCompress = null;
            }
        }

        public void setSurfaceSize(int width, int height) {
//            synchronized (mSurfaceHolder)
            {
                dispWidth = width;
                dispHeight = height;
            }
        }

        private Rect resizeRect(int bitmapWidth, int bitmapHeight) {
            int tempX;
            int tempY;
            float DEFAULT_VIDEO_WIDTH = 1280;
            float ratio_width = dispWidth/ DEFAULT_VIDEO_WIDTH;
            float DEFAULT_VIDEO_HEIGHT = 720;
            float ratio_height = dispHeight/ DEFAULT_VIDEO_HEIGHT;
            float aspectRatio = DEFAULT_VIDEO_WIDTH / DEFAULT_VIDEO_HEIGHT;

            int myWidth, myHeight;
            if (ratio_width > ratio_height){
                myWidth = (int) (dispHeight * aspectRatio);
                myHeight = dispHeight;
                tempX = (dispWidth / 2) - (bitmapWidth / 2);
                tempY = (dispHeight / 2) - (bitmapHeight / 2);
//                Dbug.i(tag, "tempX =" +tempX + ", tempY=" + tempY + ", myWidth + tempX=" + (myWidth + tempX) + ", myHeight + tempY=" +(myHeight + tempY) +
//                    ", bitmapWidth=" + bitmapWidth + ", bitmapHeight=" +bitmapHeight);
                return new Rect(tempX, tempY, myWidth + tempX, myHeight + tempY);
            }else{
                myWidth = dispWidth;
                myHeight = (int) (dispWidth / aspectRatio);
//                Dbug.w(tag, "myWidth =" +myWidth + ", myHeight=" + myHeight + ", aspectRatio=" + aspectRatio);
                if (dispWidth > dispHeight) {
                    return new Rect(0, 0, myWidth, myHeight);
                } else {
                    float ar = (float) bitmapWidth / (float) bitmapHeight;
                    bitmapWidth = dispWidth;
                    bitmapHeight = (int) (dispWidth / ar);
                    if (bitmapHeight > dispHeight) {
                        bitmapHeight = dispHeight;
                        bitmapWidth = (int) (dispHeight * ar);
                    }
                    tempX = (dispWidth / 2) - (bitmapWidth / 2);
                    tempY = (dispHeight / 2) - (bitmapHeight / 2);
                    return new Rect(tempX, tempY, bitmapWidth + tempX, bitmapHeight + tempY);
                }
            }
        }

        /*long lastTime = 0;
        long value = 0;
        int index = 0;*/
        @Override
        public void run() {
            super.run();
            isVideoThreadRunning = true;
            synchronized (mBufList) {
                while (isVideoThreadRunning) {
                    if (mBufList.isEmpty()) {
                        try {
                            isWaiting = true;
                            mBufList.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        //lastTime = System.currentTimeMillis();
                        isWaiting = false;
                        byte[] data = mBufList.remove();
                        Bitmap bitmap;
                        Canvas canvas = null;

                        if (isOptimize) {
//                            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, bitmapOptions);
                        } else {
                            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                           /* lastTime = System.currentTimeMillis();
                            mContrastCompress.compress(bitmap);
                            value += (System.currentTimeMillis() - lastTime);
                            if ((++index % 100) == 0){
                                Dbug.i(tag, "time value=" + value);
                                value = 0;
                            }*/
                        }

                        if(mContrastCompress != null && isVGA && isCompress){
//                            Dbug.i(tag, "ContrastCompress is starting.");
                            mContrastCompress.compress(bitmap);
                        }

                        if (bitmap != null){
                            final SurfaceHolder surfaceHolder = mWeakRefSurfaceHolder.get();
                            try {
                                if (surfaceHolder != null) {
                                    canvas = surfaceHolder.lockCanvas(null);
                                    Rect destRect = resizeRect(bitmap.getWidth(), bitmap.getHeight());
                                    if (canvas != null){
                                        canvas.drawBitmap(bitmap, null, destRect, mPaint);
                                    } else {
                                        Dbug.e(tag, "Resize destination rectangle fail. canvas is null" + ", destRect=" + destRect);
                                    }
                                }
                            }finally {
                                if (canvas != null) {
                                    if (null != surfaceHolder) {
                                        surfaceHolder.unlockCanvasAndPost(canvas);
                                    }
                                    if (!bitmap.isRecycled()) {
                                        bitmap.recycle();
                                    }
                                }
                            }
                        } else {
                            Dbug.e(tag, "bitmap is null. data size=" + data.length);
                        }
                        //Dbug.i(tag, "time value=" + (System.currentTimeMillis() - lastTime) + ", mBufList=" + mBufList.size());
                    }
                }
            }
        }
    }

    private static class AudioThread extends Thread {
        private AudioTrack mAudioTrack;
        private final LinkedBlockingQueue<byte[]> mQueue = new LinkedBlockingQueue<>(3);
        private boolean isRunning = false;

        public AudioThread() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            if (mAudioTrack == null){
                final int bufferSize = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

                mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);

                //Dbug.w(tag, "AudioThread: getState=" + mAudioTrack.getState() + ", getPlayState=" + mAudioTrack.getPlayState() +", bufferSize=" + bufferSize);
                if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING){
                    mAudioTrack.play();
                }
            }
        }

        public void addData(byte[] data) {
            if (mQueue.remainingCapacity() <= 0) {
                mQueue.poll();
            } else {
                try {
                    mQueue.put(data);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //Dbug.i(tag, "remainingCapacity=" + mQueue.remainingCapacity());
        }

        public void stopRunning() {
            isRunning =false;

            if (mAudioTrack != null)
                Dbug.i(tag, "stopRunning: getState=" + mAudioTrack.getState() + ", getPlayState=" + mAudioTrack.getPlayState());
            if (mAudioTrack != null && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED){
                mAudioTrack.stop();
                mAudioTrack.release();
                Dbug.i(tag, "stopRunning: release----getState=" + mAudioTrack.getState() + ", getPlayState=" + mAudioTrack.getPlayState());
                mAudioTrack = null;
            }
        }

        @Override
        public void run() {
            super.run();
            isRunning = true;
            while (isRunning) {
                byte[] data = new byte[0];
                try {
                    data = mQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (mAudioTrack != null && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED
                        && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    //Dbug.e(tag, "write: getState=" + mAudioTrack.getState() + ", getPlayState=" + mAudioTrack.getPlayState() + ", data.length=" + data.length);
                    mAudioTrack.write(data, 0, data.length);
                }
            }
        }
    }

    public void release() {
        mJpegWidth = 640;
        mJpegHeight = 480;
        if (mAudioThread != null){
            mAudioThread.stopRunning();
            mAudioThread = null;
        }

        if (mVideoThread != null) {
            mVideoThread.stopRunning();
            mVideoThread = null;
        }
       if(null != getHolder() && null != getHolder().getSurface()){
           getHolder().getSurface().release();
       }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceDone = true;
        Dbug.e(tag, "surfaceCreated-----------------------mJpegWidth=" + mJpegWidth+" ,mJpegHeight = " +mJpegHeight);
        if (mVideoThread == null) {
            mVideoThread = new VideoThread(holder, mJpegWidth, mJpegHeight, mLightLevel);
        }
        if (mVideoThread.getState() == Thread.State.NEW) {
            /**Start video playback task*/
            mVideoThread.start();
        }

        if (mAudioThread == null) {
            mAudioThread = new AudioThread();
        }
        if (mAudioThread.getState() == Thread.State.NEW) {
            /**Start audio playback task*/
            mAudioThread.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        Dbug.i(tag, "surfaceChanged--------------w=" + w + ", h=" + h + ", width=" + getWidth() + ", height=" + getHeight() + ", surfaceDone=" + surfaceDone);
        if (mVideoThread != null) {
            mVideoThread.setSurfaceSize(w, h);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Dbug.w(tag, "surfaceDestroyed------------------------surfaceDone="+ surfaceDone);
        surfaceDone = false;
        release();
    }
}