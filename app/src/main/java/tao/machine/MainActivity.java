package tao.machine;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private DigitClassifier digitClassifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.digitClassifier = new DigitClassifier(this);
        this.digitClassifier.initialize().addOnSuccessListener(unused -> {
            Log.e("init", "finish");

            digitClassifier.classifyAsync(BitmapFactory.decodeResource(getResources(), R.mipmap.tesla))
                .addOnSuccessListener(bitmap -> {
                    ((ImageView) findViewById(R.id.image)).setImageBitmap(bitmap);
                });
        });
    }
}