package com.jieli.stream.player.ui.lib;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.jieli.lib.stream.util.ICommon;
import com.jieli.stream.player.util.Dbug;

import java.lang.ref.WeakReference;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoView extends SurfaceView implements SurfaceHolder.Callback, ICommon {
    private static final String tag = VideoView.class.getSimpleName();
    private boolean surfaceDone = false;

    private static BitmapFactory.Options bitmapOptions;

    private VideoThread mVideoThread;
    private AudioThread mAudioThread = null;

    public VideoView(Context context) {
        super(context);
        init();
    }

    public VideoView(Context context, AttributeSet attrs) {
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
        if (mVideoThread != null) {
            mVideoThread.clearCanvas();
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

    public void updateAudioTrackPlayState(boolean isBuffering){
        if (mAudioThread != null) {
            mAudioThread.updateAudioTrackPlayState(isBuffering);
        }
    }

    private static class AudioThread extends Thread {
        private boolean isAudioThreadRunning = false;
        private final LinkedBlockingQueue<byte[]> mBufList = new LinkedBlockingQueue<>(3);
        private AudioTrack mAudioTrack = null;

        public AudioThread () {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            /**Prepare audio track for playing audio*/
            if (mAudioTrack == null){
                final int bufferSize = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

                mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);

                if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING){
                    mAudioTrack.play();
                }
                //Dbug.i(tag, "getPlayState=" + mAudioTrack.getPlayState() + ", getState=" + mAudioTrack.getState());
            }
        }

        public void updateAudioTrackPlayState(boolean isBuffering){
            if (mAudioTrack != null) {
                if (isBuffering && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    mAudioTrack.pause();
                } else {
                    if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
                        mAudioTrack.play();
                    }
                }
            }
        }

        public void addData(byte[] audioData) {
            if (mBufList.remainingCapacity() <= 0) {
                mBufList.poll();
            } else {
                try {
                    mBufList.put(audioData);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //Dbug.w(tag, "audioData =" + audioData.length + ", mBufList=" + mBufList.size());
        }

        public void stopRunning (){
            isAudioThreadRunning = false;

            if (mAudioTrack != null && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED){
                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;
            }
        }

        @Override
        public void run() {
            super.run();
            isAudioThreadRunning = true;
            synchronized (mBufList) {
                while (isAudioThreadRunning) {
                    byte[] buf = new byte[0];
                    try {
                        buf = mBufList.take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (mAudioTrack != null && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED
                            && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        mAudioTrack.write(buf, 0, buf.length);
                        //Dbug.i(tag, "Remain mBufList size=" + mBufList.size() + ", remainingCapacity=" + mBufList.remainingCapacity());
                    }
                }
            }
        }
    }

    private static class VideoThread extends Thread {
        private boolean isVideoThreadRunning = false;
        private final LinkedBlockingQueue<byte[]> mBufList = new LinkedBlockingQueue<>(10);
        private int dispWidth;
        private int dispHeight;
        private boolean isOptimize = false;
        private final WeakReference<SurfaceHolder> mWeakRefSurfaceHolder;
        public VideoThread (SurfaceHolder surfaceHolder) {
            mWeakRefSurfaceHolder = new WeakReference<>(surfaceHolder);
        }

        public void addData(byte[] data, boolean isOptimize) {
            this.isOptimize = isOptimize;
            //Dbug.i(tag, "mBufList=" + mBufList.size() + ", remainingCapacity=" + mBufList.remainingCapacity());

            if (mBufList.remainingCapacity() <= 0) {
                mBufList.poll();
                mBufList.poll();
            } else {
                try {
                    mBufList.put(data);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stopRunning (){
            isVideoThreadRunning = false;
            mBufList.clear();
        }

        public void setSurfaceSize(int width, int height) {
            dispWidth = width;
            dispHeight = height;
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

        public void clearCanvas() {
            final SurfaceHolder surfaceHolder = mWeakRefSurfaceHolder.get();
            if (surfaceHolder != null) {
                Canvas canvas =surfaceHolder.lockCanvas(null);
                if (canvas != null) {
                    Dbug.i(tag, "clearCanvas.........................");
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    surfaceHolder.unlockCanvasAndPost(canvas);
                } else {
                    Dbug.e(tag, "canvas is null");
                }
            }
        }

        public void drawThumbnail(byte[] data) {
            Canvas canvas = null;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap != null){
                final SurfaceHolder surfaceHolder = mWeakRefSurfaceHolder.get();
                try {
                    if (surfaceHolder != null) {
                        canvas = surfaceHolder.lockCanvas(null);
                        Rect destRect = resizeRect(bitmap.getWidth(), bitmap.getHeight());
                        if (destRect != null && canvas != null){
                            canvas.drawBitmap(bitmap, null, destRect, null);
                        } else {
                            Dbug.e(tag, "drawThumbnail: Resize destination rectangle fail.");
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

        //long lastTime = 0;
        @Override
        public void run() {
            super.run();
            isVideoThreadRunning = true;
            synchronized (mBufList) {
                while (isVideoThreadRunning) {
                    //Dbug.w(tag, "VideoThread mBufList size=" + mBufList.size());
                    byte[] data = new byte[0];
                    try {
                        data = mBufList.take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Bitmap bitmap;
                    Canvas canvas = null;
                    //lastTime = System.currentTimeMillis();
                    if (isOptimize) {
                        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, bitmapOptions);
                    } else {
                        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    }
                    //Dbug.i(tag, "time value=" + (System.currentTimeMillis() - lastTime) + ", mBufList=" + mBufList.size());
                    if (bitmap != null){
                        final SurfaceHolder surfaceHolder = mWeakRefSurfaceHolder.get();
                        try {
                            if (surfaceHolder != null) {
                                canvas = surfaceHolder.lockCanvas(null);
                                Rect destRect = resizeRect(bitmap.getWidth(), bitmap.getHeight());
                                if (destRect != null && canvas != null){
                                    canvas.drawBitmap(bitmap, null, destRect, null);
                                } else {
                                    Dbug.e(tag, "Resize destination rectangle fail. canvas=" + canvas + ", destRect=" + destRect);
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
                }
            }
            Dbug.i(tag, "VideoThread stopping..." );
        }
    }

    private void release() {
        if (mAudioThread != null) {
            mAudioThread.stopRunning();
            mAudioThread = null;
        }

        if (mVideoThread != null) {
            mVideoThread.stopRunning();
            mVideoThread = null;
        }
        if(null != getHolder() && null != getHolder().getSurface()) {
            getHolder().getSurface().release();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceDone = true;
        //Dbug.e(tag, "surfaceCreated-----------------------surfaceDone=" + surfaceDone);
        if (mVideoThread == null) {
            mVideoThread = new VideoThread(holder);
        }
        if (mVideoThread.getState() == Thread.State.NEW) {
            /**Start video playback task*/
            mVideoThread.start();
        }

        if (mAudioThread == null) {
            mAudioThread = new AudioThread();
        }
        if (mAudioThread.getState() == Thread.State.NEW) {
            mAudioThread.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        //Dbug.i(tag, "surfaceChanged--------------w=" + w + ", h=" + h + ", width=" + getWidth() + ", height=" + getHeight() + ", surfaceDone=" + surfaceDone);
        if (mVideoThread != null) {
            mVideoThread.setSurfaceSize(w, h);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //Dbug.w(tag, "surfaceDestroyed------------------------surfaceDone="+ surfaceDone);
        surfaceDone = false;
        release();
    }
}