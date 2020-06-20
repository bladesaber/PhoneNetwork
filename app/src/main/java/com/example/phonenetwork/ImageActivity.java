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
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.util.LinkedList;
import java.util.List;

public class ImageActivity extends AppCompatActivity {

    private Button inference;
    private ImageView image;

    AssetManager assetManager;
    String modelFilename = "yolov3_tiny.tflite";
    Yolo3Tiny classifier;

    Bitmap sourceBitmap;
    Bitmap cropBitmap;
    public static final int INPUT_SIZE = 416;

    long time_template = 0;

    final private int finsh_process = 200;
    Handler Looper = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case finsh_process:
                    System.out.println("time distance:"+ (System.currentTimeMillis()-time_template) );
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
        setContentView(R.layout.activity_image);

        inference = findViewById(R.id.ImageActivity_button);
        image = findViewById(R.id.ImageActivity_image);

        assetManager = getAssets();
        try {
            this.classifier = new Yolo3Tiny(assetManager, modelFilename);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

        this.sourceBitmap = Utils.getBitmapFromAsset(assetManager, "1.jpg");
        this.cropBitmap = Utils.processBitmap(sourceBitmap, INPUT_SIZE);
        this.image.setImageBitmap(cropBitmap);

        inference.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        time_template = System.currentTimeMillis();

                        final List<Recognition> results = classifier.recognizeImage(cropBitmap);
                        Message msg = new Message();
                        msg.what = finsh_process;
                        msg.obj = results;
                        Looper.sendMessage(msg);
                    }
                }).start();
            }
        });
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        classifier.close();
    }
}
