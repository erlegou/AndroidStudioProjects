package com.jieli.stream.player.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Hashtable;

/**
 * class name: BitmapCache
 * function : Image cache to prevent memory overflow
 * @author JL
 * create time : 2016-01-18 11:11
 * version : v1.0
 * ///////////////////////////////////////////////////////////////////////////
 *
 */
public class BitmapCache {
    private static  BitmapCache cache;
    /** Storage for Cache memory*/
    private Hashtable bitmapRefs;
    /** Rubbish ReferenceQueue Queue*/
    private ReferenceQueue queue;

    /**
     *A soft reference to a Bitmap object and save it.
     */
    class BitmapRef extends SoftReference {
        String _key = "";
        public BitmapRef(Bitmap bitmap, ReferenceQueue q, String key){
            super(bitmap, q);
            _key = key;
        }
    }

    private BitmapCache(){
        bitmapRefs =  new Hashtable();
        queue = new ReferenceQueue();
    }

    /**
     *Get BitmapCache object
     */
    public static BitmapCache getInstance(){
        if(cache ==  null){
            cache = new BitmapCache();
        }
        return cache;
    }

    /**
     *Add pictures to cache
     */
    public void addCacheBitmap(Bitmap bmp, String key){
        cleanCache();
        BitmapRef ref = new BitmapRef(bmp, queue, key);
        bitmapRefs.put(key, ref);
    }

    /**
     *Get cached images
     */
    public Bitmap getBitmap(String path){
        Bitmap bmp = null;
        /** */
        if(bitmapRefs.contains(path)){
            BitmapRef ref = (BitmapRef) bitmapRefs.get(path);
            bmp = (Bitmap) ref.get();
        }
        /** */
        if(bmp == null){
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = false;
            options.inSampleSize = 10;
            bmp = BitmapFactory.decodeFile(path, options);
            this.addCacheBitmap(bmp, path);
        }
        return bmp;
    }

    public int getCount(){
        return bitmapRefs.size();
    }

    /**
     *清除cache缓存
     */
    private void cleanCache(){
        BitmapRef ref;
        while ((ref = (BitmapRef) queue.poll()) != null){
            bitmapRefs.remove(ref._key);
        }
    }

    /**
     * Clear all the contents of Cache
     */
    public void clearCache(){
        cleanCache();
        bitmapRefs.clear();
        System.gc();
        System.runFinalization();
    }
}
