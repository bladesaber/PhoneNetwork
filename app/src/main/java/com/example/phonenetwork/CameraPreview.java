package com.example.phonenetwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Handler handler = new Handler();

    boolean isProcessingFrame = false;
    int previewHeight = 0;
    int previewWidth = 0;
    int[] rgbBytes = null;
    private byte[][] yuvBytes = new byte[3][];
    private int yRowStride;

    private Runnable imageConverter;
    private Runnable postInferenceCallback;

    public CameraPreview(Context context, Camera camera) {
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
                System.out.println("fuck camera 1");
                if (isProcessingFrame) {
                    return;
                }

                try {
                    // Initialize the storage bitmaps once when the resolution is known.
                    if (rgbBytes == null) {
                        Camera.Size previewSize = camera.getParameters().getPreviewSize();
                        previewHeight = previewSize.height;
                        previewWidth = previewSize.width;
                        rgbBytes = new int[previewWidth * previewHeight];
                        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
                    }
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }

                isProcessingFrame = true;
                yuvBytes[0] = bytes;
                yRowStride = previewWidth;

                imageConverter = new Runnable() {
                            @Override
                            public void run() {
                                Utils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
                            }
                        };

                postInferenceCallback = new Runnable() {
                            @Override
                            public void run() {
                                camera.addCallbackBuffer(bytes);
                                isProcessingFrame = false;
                            }
                        };
                processImage();
            }
        });
    }

    public Bitmap cameraByte2Bitmap(byte[] data, int width, int height) {
        int frameSize = width * height;
        int[] rgba = new int[frameSize];
        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++) {
                int y = (0xff & ((int) data[i * width + j]));
                int u = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 0]));
                int v = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 1]));
                y = y < 16 ? 16 : y;
                int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
                int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));
                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);
                rgba[i * width + j] = 0xff000000 + (b << 16) + (g << 8) + r;
            }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bmp.setPixels(rgba, 0, width, 0, 0, width, height);
        return bmp;
    }

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

    public void onPreviewSizeChosen(final Size size, final int rotation){};

    public void processImage(){};

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }
}