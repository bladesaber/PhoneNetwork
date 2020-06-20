package com.example.phonenetwork;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;

import java.util.List;

public class ShowImageActivity extends AppCompatActivity {

    ImageView image;
    Yolo3Tiny detector;
    AssetManager assetManager;
    String modelFilename = "yolov3_tiny.tflite";
    public static final int INPUT_SIZE = 416;

    Bitmap cropBitmap;

    final private int finsh_process = 200;
    Handler Looper = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case finsh_process:
                    List<Recognition> results = (List<Recognition>)msg.obj;
                    handleResult(cropBitmap, results);
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_image);

        image = findViewById(R.id.ShowImageActivity_image);
        if (PhotoActivity.save_bitmap != null) {

            assetManager = getAssets();
            try {
                this.detector = new Yolo3Tiny(assetManager, modelFilename);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            this.cropBitmap = Utils.processBitmap(PhotoActivity.save_bitmap, INPUT_SIZE);
            this.image.setImageBitmap(cropBitmap);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    final List<Recognition> results = detector.recognizeImage(cropBitmap);
                    Message msg = new Message();
                    msg.what = finsh_process;
                    msg.obj = results;
                    Looper.sendMessage(msg);
                }
            }).start();

        }
    }

    private void handleResult(Bitmap bitmap, List<Recognition> results) {
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

//        final List<Recognition> mappedRecognitions = new LinkedList<Recognition>();

        for (final Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null) {
                canvas.drawRect(location, paint);
            }
        }
        image.setImageBitmap(bitmap);
    }
}
