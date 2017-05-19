package com.ybb.bitmapcompress;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    Button btn;
    TextView tv1,tv2;
    ImageView iv1,iv2;

    protected boolean shouldAskPermissions() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    @TargetApi(Build.VERSION_CODES.M)
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (shouldAskPermissions()){
            askPermissions();
        }
        btn = (Button) findViewById(R.id.button);
        tv1 = (TextView) findViewById(R.id.tv_1);
        tv2 = (TextView) findViewById(R.id.tv_2);
        iv1 = (ImageView) findViewById(R.id.iv_1);
        iv2 = (ImageView) findViewById(R.id.iv_2);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
    }

    public void load(){
        Intent intent = new Intent();
        /* 开启Pictures画面Type设定为image */
        intent.setType("image/*");
        /* 使用Intent.ACTION_GET_CONTENT这个Action */
        intent.setAction(Intent.ACTION_GET_CONTENT);
        /* 取得相片后返回本画面 */
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            Log.e("uri", uri.toString());
            String imagePath = BitmapUtil.getAbsolutePathFromUri(this,uri);
            Log.e("图片路径：",imagePath);
            File file = new File(imagePath);
            Log.e("CreatorYan","文件存在"+file.exists()+"文件可读"+file.canRead()+"文件可写"+file.canWrite());
            Luban.get(this).load(file).putGear(Luban.FIRST_GEAR).setCompressListener(new OnCompressListener() {
                @Override
                public void onSuccess(File file) {
                    Log.e("CreatorYan", "压缩后的文件路径："+file.getAbsolutePath());
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    String info = "作为原图->大小:"+file.length() / 1024 + "K; 宽:"+w+"; 高:"+h;
                    Log.e("CreatorYan", info);
                    tv1.setText(info);
                    iv1.setImageBitmap(bitmap);
                }

                @Override
                public void onError(String e) {
                    Log.e("压缩出错：",e);
                }
            }).launch();

            Luban.get(this).load(file).putGear(Luban.FOURTH_GEAR).setCompressListener(new OnCompressListener() {
                @Override
                public void onSuccess(File file) {
                    Log.e("CreatorYan", "压缩后的文件路径："+file.getAbsolutePath());
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    String info = "作为缩略图->大小:"+file.length() / 1024 + "K; 宽:"+w+"; 高:"+h;
                    Log.e("CreatorYan：", info);
                    tv2.setText(info);
                    iv2.setImageBitmap(bitmap);
                }

                @Override
                public void onError(String e) {
                    Log.e("压缩出错：",e);
                }
            }).launch();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

}
