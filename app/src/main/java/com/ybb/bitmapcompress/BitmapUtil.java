package com.ybb.bitmapcompress;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by 闫斌斌 on 2016/7/21 14:15.
 * <p>
 * 图片工具类
 */
public class BitmapUtil {

    /**
     * 根据时间创建图片的文件名
     */
    public static String makeImageName() {
        long currentTimeMillis = System.currentTimeMillis();
        return String.valueOf(currentTimeMillis) + ".jpg";
    }

    /**
     * 根据originalUri获得一个不经过压缩的bitmap
     *
     * @param context     上下文
     * @param originalUri 要压缩的图片的Uri
     * @return 返回一个bitmap
     * @throws Exception
     */
    public static Bitmap getBitmapFromUri(Context context, Uri originalUri) {
        ContentResolver resolver = context.getContentResolver();
        InputStream is = null;
        Bitmap bitmap = null;
        try {
            is = resolver.openInputStream(originalUri);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeStream(is, null, opts);
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * 将一个图片文件转换为一个bitmap
     * 如果这个图片文件的图片的角度参数是错误的，那么就它的旋转角度调整过来。让它看起来不是旋转了90度的。
     *
     * @param photoFilePath 要旋转的目标图片的路径
     * @return 返回一个旋转角度正确的bitmap
     */
    public static Bitmap getCorrectDegreeBitmap(String photoFilePath) {
        int degree = getImageSpinAngle(photoFilePath);
        Bitmap bitmap = BitmapFactory.decodeFile(photoFilePath);
        return rotatingImage(degree, bitmap);
    }

    /**
     * 将一个bitmap保存为一个File文件
     *
     * @param context    上下文
     * @param bitmap     要保存的bitmap
     * @param storedPath 要存储的文件路径，如果这个路径为null那么bitmap会保存在 "/sdcard/"+你应用的包名+"Image/" 这个路径下
     * @return 返回保存的文件的绝对路径
     */
    public static String saveAsFile(Context context, Bitmap bitmap, String storedPath) {
        String fileName = null;
        if (storedPath == null) {
            String dir[] = context.getPackageName().split(".");
            File file = new File("/sdcard/" + dir[dir.length - 1] + "Image/");
            file.mkdirs();// 创建文件夹
            fileName = file.getAbsolutePath() + File.separator + BitmapUtil.makeImageName();
        } else {
            fileName = storedPath + BitmapUtil.makeImageName();
        }
        File photoFile = new File(fileName);
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(photoFile));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return fileName;
    }


    /**
     * 获取图片存储的路径
     *
     * @param context 上下文
     * @return 返回图片的存储路径。如果SD卡正常那么就存储在SD卡，如果不可用就存储在内部存储。
     */
    public static String getStoredPictureFilePath(Context context) {
        String filePath = null;
        String sdStatus = Environment.getExternalStorageState();
        if (sdStatus.equals(Environment.MEDIA_MOUNTED)) { // 如果sd卡可用
            filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + File.separator;
        } else {// 如果sd卡不可用
            Log.e("BitmapUtil", "SD card is not avaiable/writeable right now.");
            //获得手机内部的存储路径
            filePath = context.getCacheDir().getAbsolutePath() + File.separator + "image" + File.separator;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            file.mkdirs();
        }

        return file.getAbsolutePath() + File.separator + BitmapUtil.makeImageName();
    }

    /**
     * obtain the image rotation angle
     *
     * @param path path of target image
     */
    public static int getImageSpinAngle(String path) {
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
    private static Bitmap rotatingImage(int angle, Bitmap bitmap) {
        //rotate image
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        //create a new image
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * 获取图片的宽和高
     *
     * @param imagePath the path of image
     */
    public static int[] getImageSize(String imagePath) {
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
     * 将图片插入媒体库
     *
     * @param context       上下文
     * @param photoFilePath 要插入的图片文件的路径
     * @return 如果返回true则表明插入媒体库成功，否则返回false
     */
    public static boolean doInsertBitmap(final Context context, final String photoFilePath) {
        boolean b = false;
        Bitmap bitmap = BitmapFactory.decodeFile(photoFilePath);
        OutputStream os = null;
        //插入安卓系统的资源库
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, makeImageName());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATA, photoFilePath);

        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try {
            os = context.getContentResolver().openOutputStream(uri);
            b = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return b;
    }

    /**
     * * 根据Uri获取图片绝对路径，解决Android4.4以上版本Uri转换
     * @param context
     * @param imageUri
     * @return
     */
    public static String getAbsolutePathFromUri(Context context,Uri imageUri){
        if (context == null || imageUri == null)
            return null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, imageUri)) {
            if (isExternalStorageDocument(imageUri)) {
                String docId = DocumentsContract.getDocumentId(imageUri);
                String[] split = docId.split(":");
                String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(imageUri)) {
                String id = DocumentsContract.getDocumentId(imageUri);
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(imageUri)) {
                String docId = DocumentsContract.getDocumentId(imageUri);
                String[] split = docId.split(":");
                String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                String selection = MediaStore.Images.Media._ID + "=?";
                String[] selectionArgs = new String[] { split[1] };
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } // MediaStore (and general)
        else if ("content".equalsIgnoreCase(imageUri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(imageUri))
                return imageUri.getLastPathSegment();
            return getDataColumn(context, imageUri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(imageUri.getScheme())) {
            return imageUri.getPath();
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String column = MediaStore.Images.Media.DATA;
        String[] projection = { column };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}
