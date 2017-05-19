package com.ybb.bitmapcompress;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.ybb.bitmapcompress.Preconditions.checkNotNull;

/**
 * 图片压缩类（最接近微信的图片压缩方法）
 * 定义了四种图片的压缩模式:
 * 一：压缩结果比较稳定，返回的图片文件大致在200K~600K之间
 * 二：压缩结果比较稳定，返回的图片文件大致在60K~120K之间
 * 三：压缩结果不稳定，返回的图片文件大致在50K~300K之间
 * 四：压缩结果比较稳定，返回的图片文件大小大致在50~110K之间
 * 这四种模式图片都是以文件的类型传入，以文件的类型返回
 */
public class Luban {

    /**
     * 压缩的最轻（比较稳定，适用于作为原图上传到服务器）
     */
    public static final int FIRST_GEAR = 1;
    /**
     * 压缩的稍微厉害（比较稳定，适用于作为缩略图上传到服务器）
     */
    public static final int SECOND_GEAR = 2;
    /**
     * 压缩的最厉害（不稳定，在图片的宽或高小于1280或720时图片压缩出来的效果没有第二种好）
     */
    public static final int THIRD_GEAR = 3;
    /**
     * 压缩的比较厉害（比较稳定，比第二种看起来更加清晰，90%的情况下都比第二种压缩方法的压缩结果小，
     * 10%的情况下会比第二种方法大10K左右）当有清晰度要求的时候，这种作为缩略图的压缩方法最适合。
     */
    public static final int FOURTH_GEAR = 4;


    private static final String TAG = "Luban";
    public static String DEFAULT_DISK_CACHE_DIR = "luban_disk_cache";

    private static volatile Luban INSTANCE;

    private final File mCacheDir;
    private File tempFile;
    private static Context context;
    private String compressError = null;

    private OnCompressListener compressListener;
    private File mFile;
    private int gear = THIRD_GEAR;
    /**
     * 默认设置的要显示的目标缩略图的短边
     */
    private static final float TARGET_WIDTH = 500;
    /**
     * 默认设置的要显示的目标缩略图的长边
     */
    private static final float TARGET_HEIGHT = 889;
    private static final int mMaxWidth = 1280;
    private static final int mMaxHeight = 720;

    Luban(File cacheDir) {
        mCacheDir = cacheDir;
    }

    /**
     * Returns a directory with a default name in the private cache directory of the application to use to store
     * retrieved media and thumbnails.
     *
     * @param context A context.
     * @see #getPhotoCacheDir(Context, String)
     */
    public static File getPhotoCacheDir(Context context) {
        return getPhotoCacheDir(context, Luban.DEFAULT_DISK_CACHE_DIR);
    }

    /**
     * Returns a directory with the given name in the private cache directory of the application to use to store
     * retrieved media and thumbnails.
     *
     * @param context   A context.
     * @param cacheName The name of the subdirectory in which to store the cache.
     * @see #getPhotoCacheDir(Context)
     */
    public static File getPhotoCacheDir(Context context, String cacheName) {
        File cacheDir = context.getCacheDir();
        if (cacheDir != null) {
            File result = new File(cacheDir, cacheName);
            if (!result.mkdirs() && (!result.exists() || !result.isDirectory())) {
                // File wasn't able to create a directory, or the result exists but not a directory
                return null;
            }
            return result;
        }
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "default disk cache dir is null");
        }
        return null;
    }

    public static Luban get(Context mContext) {
        context = mContext;
        if (INSTANCE == null) INSTANCE = new Luban(Luban.getPhotoCacheDir(context));
        return INSTANCE;
    }

    public Luban launch() {
        checkNotNull(mFile, "the image file cannot be null, please call .load() before this method!");

        if (gear == Luban.FIRST_GEAR)
            firstCompress(mFile);
        else if (gear == Luban.SECOND_GEAR)
            secondCompress(mFile.getAbsolutePath());
        else if (gear == Luban.THIRD_GEAR)
            thirdCompress(mFile);
        else if (gear == Luban.FOURTH_GEAR)
            fourthCompress(mFile);
        return this;
    }

    /**
     * 第四种压缩方法，比较稳定，比较第二种压缩的很(最差的情况下会比第二种大13K)，但是比第二种清晰，
     * 但是缺点是比其他几种方法都耗时。
     *
     * @param mFile 要压缩的图片文件
     */
    private void fourthCompress(File mFile) {
        if (mFile.length() / 1024 < 40) {//小于40K就不压缩了
            compressListener.onSuccess(mFile);
            return;
        }
        int size[] = getImageSize(mFile.getAbsolutePath());
        int width = size[0];
        int height = size[1];
        float c = 0;
        if (width > TARGET_WIDTH && height > TARGET_HEIGHT) {
            //取一个大的缩放比例
            c = width / TARGET_WIDTH > height / TARGET_HEIGHT ? width / TARGET_WIDTH : height / TARGET_HEIGHT;
            c = 1 / c;
        } else if (width > TARGET_HEIGHT && height > TARGET_WIDTH) {
            c = width / TARGET_HEIGHT > height / TARGET_WIDTH ? width / TARGET_HEIGHT : height / TARGET_WIDTH;
            c = 1 / c;
        } else {
            c = 0.8f;
        }

        Matrix matrix = new Matrix();
        matrix.setScale(c, c);//缩小
        int degree = getImageSpinAngle(mFile.getAbsolutePath());
        matrix.postRotate(degree);

        Bitmap mbitmap = BitmapFactory.decodeFile(mFile.getAbsolutePath());
        if (mbitmap == null) {
            //有时候当应用程序把某个图片文件删除后，没有将媒体库数据库中的数据删除，导致虽然能够读取到图片的数据，
            //但是在加载的时候图片无法加载成功。
            compressError = "您选择了损坏的图片";
            compressListener.onError(compressError);
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(mbitmap, 0, 0, mbitmap.getWidth(), mbitmap.getHeight(), matrix, true);
        String storedPath = BitmapUtil.getStoredPictureFilePath(context);
        //-----------------------------------------
        if (bitmap != null) {
            FileOutputStream fos = null;
            try {
                tempFile = new File(storedPath);
                fos = new FileOutputStream(tempFile);
                fos.flush();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        bitmap.recycle();
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        //-----------------------------------------

        double scrale = (double) height / width;
        if (scrale < 0.5625) {
            secondCompress(tempFile.getAbsolutePath());
        } else {
            thirdCompress(tempFile);
        }
    }

    public Luban load(File file) {
        mFile = file;
        return this;
    }

    public Luban setCompressListener(OnCompressListener listener) {
        compressListener = listener;
        return this;
    }

    public Luban putGear(int gear) {
        this.gear = gear;
        return this;
    }

    /**
     * 压缩的程度稍大
     */
    private void secondCompress(@NonNull String filePath) {
        String thumb = BitmapUtil.getStoredPictureFilePath(context);

        double scale;

        int angle = getImageSpinAngle(filePath);
        int width = getImageSize(filePath)[0];
        int height = getImageSize(filePath)[1];
        int thumbW = width % 2 == 1 ? width + 1 : width;
        int thumbH = height % 2 == 1 ? height + 1 : height;

        width = thumbW > thumbH ? thumbH : thumbW;
        height = thumbW > thumbH ? thumbW : thumbH;

        double c = ((double) width / height);//计算短边除以长边的比例值

        if (c <= 1 && c > 0.5625) {//宽高比小于1，大于9/16的时候
            if (height < 1664) {
                scale = (width * height) / Math.pow(1664, 2) * 150;
                scale = scale < 60 ? 60 : scale;
            } else if (height >= 1664 && height < 4990) {
                thumbW = width / 2;
                thumbH = height / 2;
                scale = (thumbW * thumbH) / Math.pow(2495, 2) * 300;
                scale = scale < 60 ? 60 : scale;
            } else if (height >= 4990 && height < 10240) {
                thumbW = width / 4;
                thumbH = height / 4;
                scale = (thumbW * thumbH) / Math.pow(2560, 2) * 300;
                scale = scale < 100 ? 100 : scale;
            } else {
                int multiple = height / 1280 == 0 ? 1 : height / 1280;
                thumbW = width / multiple;
                thumbH = height / multiple;
                scale = (thumbW * thumbH) / Math.pow(2560, 2) * 300;
                scale = scale < 100 ? 100 : scale;
            }
        } else if (c <= 0.5625 && c > 0.5) {//宽高比小于9/16，大于1/2的时候
            int multiple = height / 1280 == 0 ? 1 : height / 1280;
            thumbW = width / multiple;
            thumbH = height / multiple;
            scale = (thumbW * thumbH) / (1440.0 * 2560.0) * 200;
            scale = scale < 100 ? 100 : scale;
        } else {//宽高比小于1/2
            int multiple = (int) Math.ceil(height / (1280.0 / c));
            thumbW = width / multiple;
            thumbH = height / multiple;
            scale = ((thumbW * thumbH) / (1280.0 * (1280 / c))) * 500;
            scale = scale < 100 ? 100 : scale;
        }

        compress(filePath, thumb, thumbW, thumbH, angle, (long) scale);


    }

    /**
     * 压缩的程度最轻
     * 如果原图的大小小于600K那么就不进行压缩直接返回
     *
     * @param file
     */
    private void firstCompress(@NonNull File file) {

        //如果图片是特别长或者特别宽的图片,则进行特殊压缩处理。(进行质量压缩)
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        int imgW = opts.outWidth;
        int imgH = opts.outHeight;
        float bi = (float) (imgW*1.0/imgH);
        if (bi < 0.1 || bi > 10){
            //压缩尺寸的方法不可行，因为会越压占用空间越大。
            Bitmap src = BitmapFactory.decodeFile(file.getAbsolutePath());
            String thumbFilePath = BitmapUtil.getStoredPictureFilePath(context);
            File targetFile = new File(thumbFilePath);
            int quality = 10;
            if (file.length()/1024 > 1024){//大于1M
                quality = 9;
            }else if (file.length()/1024 > 600){//大于600K
                quality = 70;
            }else if (file.length()/1024 > 300){//大于300K
                quality = 90;
            }else {
                quality = 100;
            }
            try {
                src.compress(Bitmap.CompressFormat.JPEG,quality,new FileOutputStream(targetFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            compressListener.onSuccess(targetFile);
            return;
        }else {
            //如果原图的大小小于600K那么就不进行压缩直接返回
            if (file.length() / 1024 < 600) {
                compressListener.onSuccess(file);
                return;
            }
            String thumbFilePath = BitmapUtil.getStoredPictureFilePath(context);
            try {
                Bitmap bitmap = getFixSizeBitmap(file.getAbsolutePath());
                saveImage(thumbFilePath, bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 压缩的程度最厉害
     *
     * @param file
     */
    private void thirdCompress(@NonNull File file) {
        int minSize = 60;
        int longSide = 720;
        int shortSide = 1280;

        String filePath = file.getAbsolutePath();
        String thumbFilePath = BitmapUtil.getStoredPictureFilePath(context);
        long size = 0;
        long maxSize = file.length() / 5;

        int angle = getImageSpinAngle(filePath);
        int[] imgSize = getImageSize(filePath);
        int width = 0, height = 0;
        if (imgSize[0] <= imgSize[1]) {
            double scale = (double) imgSize[0] / (double) imgSize[1];
            if (scale <= 1.0 && scale > 0.5625) {
                width = imgSize[0] > shortSide ? shortSide : imgSize[0];
                height = width * imgSize[1] / imgSize[0];
                size = minSize;
            } else if (scale <= 0.5625) {
                height = imgSize[1] > longSide ? longSide : imgSize[1];
                width = height * imgSize[0] / imgSize[1];
                size = maxSize;
            }
        } else {
            double scale = (double) imgSize[1] / (double) imgSize[0];
            if (scale <= 1.0 && scale > 0.5625) {
                height = imgSize[1] > shortSide ? shortSide : imgSize[1];
                width = height * imgSize[0] / imgSize[1];
                size = minSize;
            } else if (scale <= 0.5625) {
                width = imgSize[0] > longSide ? longSide : imgSize[0];
                height = width * imgSize[1] / imgSize[0];
                size = maxSize / 3 * 2;
            }
        }

        compress(filePath, thumbFilePath, width, height, angle, size);
    }

    private Bitmap getFixSizeBitmap(String path)
            throws FileNotFoundException {
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        Bitmap bitmap = null;
        if (mMaxWidth == 0 && mMaxHeight == 0) {
            decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeStream(new FileInputStream(new File(path)), null, decodeOptions);
        } else {
            int degress = getImageSpinAngle(path);
            decodeOptions.inJustDecodeBounds = true;
            if (new File(path).exists()){
                Log.e("cunzai","存在");
            }
            BitmapFactory.decodeFile(path, decodeOptions);

            int actualWidth = 0;
            int actualHeight = 0;

            if (degress == 0 || degress % 180 == 0) {
                //如果当前图片无旋转角度或则旋转角度为180 时 此时图片的高宽是正常的 其他情况比如旋转角度为90或270时，高宽是反着的
                actualWidth = decodeOptions.outWidth;
                actualHeight = decodeOptions.outHeight;
            } else {
                actualWidth = decodeOptions.outHeight;
                actualHeight = decodeOptions.outWidth;
            }

            int desiredWidth = getResizedDimension(mMaxWidth, mMaxHeight,
                    actualWidth, actualHeight);

            int desiredHeight = getResizedDimension(mMaxHeight, mMaxWidth,
                    actualHeight, actualWidth);

            decodeOptions.inJustDecodeBounds = false;
            decodeOptions.inSampleSize = findBestSampleSize(actualWidth,
                    actualHeight, desiredWidth, desiredHeight);


            Bitmap tempBitmap = BitmapFactory.decodeFile(path, decodeOptions);

            if (degress == 0 || degress % 180 == 0) {
                //如果当前图片无旋转角度或则旋转角度为180 时 此时图片的高宽是正常的 其他情况比如旋转角度为90或270时，高宽是反着的
                if (tempBitmap != null
                        && (tempBitmap.getHeight() > desiredHeight || tempBitmap
                        .getWidth() > desiredWidth)) {

                    bitmap = Bitmap.createScaledBitmap(tempBitmap, desiredWidth,
                            desiredHeight, true);
                    tempBitmap.recycle();
                } else {
                    bitmap = tempBitmap;
                }
            } else {
                if (tempBitmap != null
                        && (tempBitmap.getHeight() > desiredWidth || tempBitmap
                        .getWidth() > desiredHeight)) {

                    bitmap = Bitmap.createScaledBitmap(tempBitmap, desiredHeight,
                            desiredWidth, true);
                    tempBitmap.recycle();
                } else {
                    bitmap = tempBitmap;
                }
            }
            //确保图片的旋转角度是正确的
            bitmap = rotatingImage(degress, bitmap);
        }
        return bitmap;
    }

    private int getResizedDimension(int maxPrimary, int maxSecondary,
                                    int actualPrimary, int actualSecondary) {
        if (maxPrimary == 0 && maxSecondary == 0) {
            return actualPrimary;
        }
        if (maxPrimary == 0) {
            double ratio = (double) maxSecondary / (double) actualSecondary;
            return (int) (actualPrimary * ratio);
        }

        if (maxSecondary == 0) {
            return maxPrimary;
        }

        double ratio = (double) actualSecondary / (double) actualPrimary;
        int resized = maxPrimary;
        if (resized * ratio > maxSecondary) {
            resized = (int) (maxSecondary / ratio);
        }
        return resized;
    }

    private int findBestSampleSize(int actualWidth, int actualHeight,
                                   int desiredWidth, int desiredHeight) {
        double wr = (double) actualWidth / desiredWidth;
        double hr = (double) actualHeight / desiredHeight;
        double ratio = Math.min(wr, hr);
        float n = 1.0f;
        while ((n * 2) <= ratio) {
            n *= 2;
        }

        return (int) n;
    }

    /**
     * obtain the image's width and height
     *
     * @param imagePath the path of image
     */
    public int[] getImageSize(String imagePath) {
        int[] res = new int[2];

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;
        BitmapFactory.decodeFile(imagePath, options);

        res[0] = options.outWidth;
        res[1] = options.outHeight;

        return res;
    }

    /**
     * 指定参数压缩图片
     * create the thumbnail with the true rotate angle
     *
     * @param largeImagePath the big image path
     * @param thumbFilePath  the thumbnail path
     * @param width          width of thumbnail
     * @param height         height of thumbnail
     * @param angle          rotation angle of thumbnail
     * @param size           the file size of image
     */
    private void compress(String largeImagePath, String thumbFilePath, int width, int height, int angle, long size) {
        Bitmap thbBitmap = compress(largeImagePath, width, height);

        thbBitmap = rotatingImage(angle, thbBitmap);

        saveImage(thumbFilePath, thbBitmap, size);

    }

    /**
     * obtain the thumbnail that specify the size
     *
     * @param imagePath the target image path
     * @param width     the width of thumbnail
     * @param height    the height of thumbnail
     * @return {@link Bitmap}
     */
    private Bitmap compress(String imagePath, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);

        int outH = options.outHeight;
        int outW = options.outWidth;
        int inSampleSize = 1;

        if (outH > height || outW > width) {
            int halfH = outH / 2;
            int halfW = outW / 2;

            while ((halfH / inSampleSize) > height && (halfW / inSampleSize) > width) {
                inSampleSize *= 2;
            }
        }

        options.inSampleSize = inSampleSize;

        options.inJustDecodeBounds = false;

        int heightRatio = (int) Math.ceil(options.outHeight / (float) height);
        int widthRatio = (int) Math.ceil(options.outWidth / (float) width);

        if (heightRatio > 1 || widthRatio > 1) {
            if (heightRatio > widthRatio) {
                options.inSampleSize = heightRatio;
            } else {
                options.inSampleSize = widthRatio;
            }
        }
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(imagePath, options);
    }

    /**
     * obtain the image rotation angle
     *
     * @param path path of target image
     */
    private int getImageSpinAngle(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /**
     * 旋转图片
     * rotate the image with specified angle
     *
     * @param angle  the angle will be rotating 旋转的角度
     * @param bitmap target image               目标图片
     */
    private Bitmap rotatingImage(int angle, Bitmap bitmap) {
        //rotate image
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        //create a new image
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * 保存图片到指定路径
     * Save image with specified size
     *
     * @param filePath the image file save path 储存路径
     * @param bitmap   the image what be save   目标图片
     * @param size     the file size of image   期望大小
     */
    private void saveImage(String filePath, Bitmap bitmap, long size) {
        checkNotNull(bitmap, TAG + "bitmap cannot be null");

        File result = new File(filePath.substring(0, filePath.lastIndexOf("/")));

        if (!result.exists() && !result.mkdirs()) return;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int options = 100;
        bitmap.compress(Bitmap.CompressFormat.JPEG, options, stream);

        while (stream.toByteArray().length / 1024 > size) {
            stream.reset();
            options -= 6;
            if (options < 0) {
                break;
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, options, stream);
        }
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            fos.write(stream.toByteArray());
            fos.flush();
            fos.close();
            bitmap.recycle();

            if (compressListener != null) {
                compressListener.onSuccess(new File(filePath));
            } else {
                compressError = "compressListener为空";
                compressListener.onError(compressError);
            }
        } catch (Exception e) {
            if (compressListener != null) compressListener.onError(e.getMessage());
        } finally {
            //删除四级压缩过程中产生的多余文件。
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 缓存文件
     *
     * @param thumbFilePath 压缩完输出的图片文件路径
     * @param bitmap        要存储的图片
     */
    public String saveImage(String thumbFilePath, Bitmap bitmap) {
        if (bitmap != null) {
            FileOutputStream fos = null;
            try {
                File file = new File(thumbFilePath);
                fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                bitmap.recycle();
                if (compressListener != null) {
                    compressListener.onSuccess(new File(thumbFilePath));
                }

                return file.getAbsolutePath();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                if (compressListener != null) {
                    compressListener.onError(e.getMessage());
                }
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

}