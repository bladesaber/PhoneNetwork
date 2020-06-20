package com.example.phonenetwork;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class PhotoActivity extends AppCompatActivity {

    private Camera.CameraInfo mFrontCameraInfo = null;
    private int mFrontCameraId = -1;

    private Camera.CameraInfo mBackCameraInfo = null;
    private int mBackCameraId = -1;
    private int mCameraId = -1;

    Camera mCamera;
    PhotoPreview mPreview;
    AssetManager assetManager;

    ImageView image;
    Button inference;
    public static Bitmap save_bitmap = null;
    public static Activity activity = null;
    public static boolean activity_free = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        activity = this;
        inference = findViewById(R.id.PhotoActivity_click);
        inference.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activity_free = true;
            }
        });

        assetManager = getAssets();

        int numberOfCameras = Camera.getNumberOfCameras();
        initCameraInfo();
        openCamera();

//        image = findViewById(R.id.PhotoActivity_image);
        mPreview = new PhotoPreview(this, mCamera){
            @Override
            public void processImage(Bitmap bitmap) {
                super.processImage(bitmap);
                save_bitmap = bitmap;
//                image.setImageBitmap(bitmap);

                if(activity_free){
                    startActivity(new Intent(PhotoActivity.this, ShowImageActivity.class));
                    activity_free = false;
                }
            }
        };
        FrameLayout preview = (FrameLayout) findViewById(R.id.PhotoActivity_preview);
        preview.addView(mPreview);

        mCamera.startPreview();

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
    }

    @Override
    protected void onResume() {
        super.onResume();
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
    }

}
