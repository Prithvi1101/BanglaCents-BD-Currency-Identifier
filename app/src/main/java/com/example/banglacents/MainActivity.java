package com.example.banglacents;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {

    TextView result, confidence;
    ImageView imageView;
    Button picture;
    int imageSize = 224;

    private Interpreter tflite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        result = findViewById(R.id.result);
        confidence = findViewById(R.id.confidence);
        imageView = findViewById(R.id.imageView);
        picture = findViewById(R.id.button);

        // Load model once, but don't crash app if it fails
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (Throwable e) {   // catch ANY error here
            e.printStackTrace();
            Toast.makeText(this, "Error loading model: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }

        picture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkSelfPermission(Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    Intent cameraIntent =
                            new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, 1);
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
                }
            }
        });
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        // model.tflite must be exactly under app/src/main/assets/
        AssetFileDescriptor fileDescriptor = getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void classifyImage(Bitmap image) {
        if (tflite == null) {
            Toast.makeText(this, "Model not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // input [1, 224, 224, 3]
        float[][][][] input = new float[1][imageSize][imageSize][3];

        int[] intValues = new int[imageSize * imageSize];
        image.getPixels(intValues, 0, image.getWidth(), 0, 0,
                image.getWidth(), image.getHeight());

        int pixel = 0;
        for (int i = 0; i < imageSize; i++) {
            for (int j = 0; j < imageSize; j++) {
                int val = intValues[pixel++];

                float r = ((val >> 16) & 0xFF) / 255.0f;
                float g = ((val >> 8) & 0xFF) / 255.0f;
                float b = (val & 0xFF) / 255.0f;

                input[0][i][j][0] = r;
                input[0][i][j][1] = g;
                input[0][i][j][2] = b;
            }
        }

        // output [1, 16] (16 classes)
        float[][] output = new float[1][8];

        try {
            tflite.run(input, output);
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(this, "Error running model: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        float[] confidences = output[0];

        int maxPos = 0;
        float maxConfidence = 0f;
        for (int i = 0; i < confidences.length; i++) {
            if (confidences[i] > maxConfidence) {
                maxConfidence = confidences[i];
                maxPos = i;
            }
        }

        String[] classes = {
                "Crow", "Sparrow", "Magpie Robin", "Pigeon", "Parrot",
                "Eagle", "Kingfisher",
                "Not a bird"
        };

        result.setText(classes[maxPos]);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < classes.length; i++) {
            sb.append(String.format("%s: %.1f%%\n",
                    classes[i], confidences[i] * 100));
        }
        confidence.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            Bitmap image = (Bitmap) data.getExtras().get("data");
            int dimension = Math.min(image.getWidth(), image.getHeight());
            image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
            imageView.setImageBitmap(image);

            image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
            classifyImage(image);
        }
    }
}
