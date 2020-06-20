package com.example.phonenetwork;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private Camera.CameraInfo mFrontCameraInfo = null;
    private int mFrontCameraId = -1;

    private Camera.CameraInfo mBackCameraInfo = null;
    private int mBackCameraId = -1;
    private int mCameraId = -1;

    final private int  TF_OD_API_INPUT_SIZE = 416;
    private static final boolean MAINTAIN_ASPECT = false;
    private Integer sensorOrientation;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private boolean computingDetection = false;

    Camera mCamera;
    CameraPreview mPreview;

    Yolo3Tiny detector;
    AssetManager assetManager;
    String modelFilename = "yolov3_tiny.tflite";

    ImageView image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        assetManager = getAssets();
        try {
            this.detector = new Yolo3Tiny(assetManager, modelFilename);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

        int numberOfCameras = Camera.getNumberOfCameras();
        initCameraInfo();
        openCamera();

        image = findViewById(R.id.CameraActivity_image);

        mPreview = new CameraPreview(this, mCamera){
            @Override
            public void onPreviewSizeChosen(Size size, int rotation) {
                super.onPreviewSizeChosen(size, rotation);

                int cropSize = TF_OD_API_INPUT_SIZE;

                previewWidth = size.getWidth();
                previewHeight = size.getHeight();

                sensorOrientation = rotation - getScreenOrientation();
                rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
                croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

                frameToCropTransform = Utils.getTransformationMatrix(
                                previewWidth, previewHeight,
                                cropSize, cropSize,
                                sensorOrientation, MAINTAIN_ASPECT);

                cropToFrameTransform = new Matrix();
                frameToCropTransform.invert(cropToFrameTransform);
            }

            @Override
            public void processImage() {
                super.processImage();
                if (computingDetection) {
                    readyForNextImage();
                    return;
                }
                System.out.println("fuck camera 2 processimage");
                computingDetection = true;

                rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

                readyForNextImage();

                final Canvas canvas = new Canvas(croppedBitmap);
                canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

                runInBackground(
                        new Runnable() {
                            @Override
                            public void run() {
                                final List<Recognition> results = detector.recognizeImage(croppedBitmap);

                                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                                final Canvas canvas = new Canvas(cropCopyBitmap);
                                final Paint paint = new Paint();
                                paint.setColor(Color.RED);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeWidth(2.0f);

                                System.out.println("fuck camera 3 please draw something");
//                                RectF test = new RectF(50, 50, 200, 200);
//                                canvas.drawRect(test, paint);
//                                image.setImageBitmap(cropCopyBitmap);

                                final List<Recognition> mappedRecognitions = new LinkedList<Recognition>();

                                for (final Recognition result : results) {
                                    final RectF location = result.getLocation();
                                    canvas.drawRect(location, paint);

                                    cropToFrameTransform.mapRect(location);
                                    result.setLocation(location);
                                    mappedRecognitions.add(result);
                                }
                                image.setImageBitmap(cropCopyBitmap);

                                computingDetection = false;
                            }
                        });
            }
        };

        FrameLayout preview = (FrameLayout) findViewById(R.id.CameraActivity_preview);
        preview.addView(mPreview);

        mCamera.startPreview();
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    private boolean hasBackCamera() {
        return mBackCameraId != -1;
    }

    private void openCamera() {
        if (mCamera != null) {
            throw new RuntimeException("相机已经被开启，无法同时开启多个相机实例！");
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (hasBackCamera()) {
                // 没有前置，就尝试开启后置摄像头
                mCamera = Camera.open(mBackCameraId);
                assert mCamera != null;
                mCamera.setDisplayOrientation(getCameraDisplayOrientation(mBackCameraInfo));
            } else {
                throw new RuntimeException("没有任何相机可以开启！");
            }
        }
    }

    private int getCameraDisplayOrientation(Camera.CameraInfo cameraInfo) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }
        return result;
    }

    private void initCameraInfo() {
        int numberOfCameras = Camera.getNumberOfCameras();// 获取摄像头个数
        for (int cameraId = 0; cameraId < numberOfCameras; cameraId++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                // 后置摄像头信息
                mBackCameraId = cameraId;
                mBackCameraInfo = cameraInfo;
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
                // 前置摄像头信息
                mFrontCameraId = cameraId;
                mFrontCameraInfo = cameraInfo;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCamera();
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        if(this.detector!=null){
            this.detector.close();
        }
    }

}
