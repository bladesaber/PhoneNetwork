package com.example.phonenetwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PhotoPreview extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Handler handler = new Handler();

    public PhotoPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
//        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mHolder.setFormat(PixelFormat.TRANSPARENT);

        try {
            mCamera.setPreviewDisplay(mHolder);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] bytes, Camera camera) {
                Camera.Size size = camera.getParameters().getPreviewSize();
                try{
                    YuvImage image = new YuvImage(bytes, ImageFormat.NV21, size.width, size.height, null);
                    if(image!=null){
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);

                        Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                        bmp = rotateMyBitmap(bmp);
                        processImage(bmp);

                        stream.close();
                    }
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public Bitmap rotateMyBitmap(Bitmap bmp){
        //*****旋转一下
        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        Bitmap bitmap = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap nbmp2 = Bitmap.createBitmap(bmp, 0,0, bmp.getWidth(),  bmp.getHeight(), matrix, true);
        return nbmp2;
    };

    public void processImage(Bitmap bitmap){}

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

}
