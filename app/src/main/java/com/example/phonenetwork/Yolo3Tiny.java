package com.example.phonenetwork;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class Yolo3Tiny {

    private static final int INPUT_SIZE = 416;
    private static final int[] OUTPUT_WIDTH = new int[]{26, 13};
    private static final int NUM_BOXES_PER_BLOCK = 3;
    private Vector<String> labels = new Vector<String>();
    protected static final int BATCH_SIZE = 1;
    protected static final int PIXEL_SIZE = 3;

    private float MINIMUM_CONFIDENCE = 0.6f;
    private static final int[] ANCHORS = new int[]{10,14, 23,27, 37,58, 81,82, 135,169, 344,319};
    private float mNmsThresh = 0.2f;

    public Interpreter tfLite;
    GpuDelegate delegate;
    boolean isNNAPI = true;
    boolean isGPU = false;

    String labelFilename = "file:///android_asset/tiny.txt";

    private int NUM_THREADS = 4;

    public Yolo3Tiny(AssetManager assetManager, String modelFilename){

        try {
            String actualFilename = labelFilename.split("file:///android_asset/")[1];
            InputStream labelsInput = assetManager.open(actualFilename);
            BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
            String line;
            while ((line = br.readLine()) != null) {
                this.labels.add(line);
            }
            br.close();
        }catch (Exception e){
            throw new RuntimeException(e);
        }

        try {
            Interpreter.Options options = (new Interpreter.Options());
            options.setNumThreads(NUM_THREADS);
            if (isNNAPI) {
                NnApiDelegate nnApiDelegate = null;
                // Initialize interpreter with NNAPI delegate for Android Pie or above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    nnApiDelegate = new NnApiDelegate();
                    options.addDelegate(nnApiDelegate);
                    options.setNumThreads(NUM_THREADS);
//                    options.setUseNNAPI(false);
                    options.setAllowFp16PrecisionForFp32(true);
                    options.setAllowBufferHandleOutput(true);
                    options.setUseNNAPI(true);
                }
            }
            if (isGPU) {
                delegate = new GpuDelegate();
                options.addDelegate(delegate);
            }
            this.tfLite = new Interpreter(Utils.loadModelFile(assetManager, modelFilename), options);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    public ArrayList<Recognition> recognizeImage(Bitmap bitmap){
        ByteBuffer byteBuffer = Utils.convertBitmapToByteBuffer(bitmap, BATCH_SIZE, INPUT_SIZE, PIXEL_SIZE);

        Map<Integer, Object> outputMap = new HashMap<>();
        for (int i = 0; i < OUTPUT_WIDTH.length; i++) {
            float[][][][][] out = new float[1][OUTPUT_WIDTH[i]][OUTPUT_WIDTH[i]][3][5 + labels.size()];
            outputMap.put(i, out);
        }

        Object[] inputArray = {byteBuffer};
        this.tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        ArrayList<Recognition> detections = new ArrayList<Recognition>();

        for (int i = 0; i < OUTPUT_WIDTH.length; i++){
            int gridWidth = OUTPUT_WIDTH[i];
            float[][][][][] out = (float[][][][][]) outputMap.get(i);

            for (int y = 0; y < gridWidth; ++y){
                for (int x = 0; x < gridWidth; ++x){
                    for (int b = 0; b < NUM_BOXES_PER_BLOCK; ++b){
                        int offset = (gridWidth * (NUM_BOXES_PER_BLOCK * (labels.size() + 5))) * y
                                        + (NUM_BOXES_PER_BLOCK * (labels.size() + 5)) * x
                                        + (labels.size() + 5) * b;

                        float confidence = Utils.sigmoid(out[0][y][x][b][4]);
                        int detectedClass = -1;
                        float maxClass = 0;

                        float[] classes = new float[labels.size()];
                        for (int c = 0; c < labels.size(); ++c) {
                            classes[c] = out[0][y][x][b][5 + c];
                        }
                        for (int c = 0; c < labels.size(); ++c) {
                            if (classes[c] > maxClass) {
                                detectedClass = c;
                                maxClass = classes[c];
                            }
                        }

                        float confidenceInClass = maxClass * confidence;
                        if (confidenceInClass > MINIMUM_CONFIDENCE){
                            final float xPos = (x + Utils.sigmoid(out[0][y][x][b][0])) * (1.0f * INPUT_SIZE / gridWidth);
                            final float yPos = (y + Utils.sigmoid(out[0][y][x][b][1])) * (1.0f * INPUT_SIZE / gridWidth);

                            final float w = (float) (Math.exp(out[0][y][x][b][2]) * ANCHORS[i*6+b*2]);
                            final float h = (float) (Math.exp(out[0][y][x][b][3]) * ANCHORS[i*6+b*2+1]);

                            final RectF rect = new RectF(
                                    Math.max(0, xPos - w / 2),
                                    Math.max(0, yPos - h / 2),
                                    Math.min(bitmap.getWidth() - 1, xPos + w / 2),
                                    Math.min(bitmap.getHeight() - 1, yPos + h / 2));
                            detections.add(new Recognition("" + offset, labels.get(detectedClass), confidenceInClass, rect, detectedClass));
                        }
                    }
                }
            }
        }
        ArrayList<Recognition> recognitions = Utils.nms(detections, labels.size(), mNmsThresh);
        return recognitions;
    }

    public void close(){
        if (this.delegate!=null){
            this.delegate.close();
        }
        this.tfLite.close();
    }

}
