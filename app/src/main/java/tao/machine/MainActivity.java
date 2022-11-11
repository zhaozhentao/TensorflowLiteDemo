package tao.machine;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private DetectModel detectModel;

    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.detectModel = new DetectModel(this);
        this.detectModel.initialize();

        findViewById(R.id.take).setOnClickListener(v -> takePhoto());
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = null;
        try {
            imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageFile;
    }

    public void takePhoto() {
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File file = createImageFile();
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        imageUri = FileProvider.getUriForFile(this, "tao.machine.provider", file);
        it.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(it, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        try {
            Bitmap photo = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            photo = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), matrix, true);
            long start = System.currentTimeMillis();
            detectModel.classifyAsync(photo)
                .addOnSuccessListener(bitmap -> {
                    Toast.makeText(MainActivity.this, "耗时 " + (System.currentTimeMillis() - start), Toast.LENGTH_SHORT).show();
                    ((ImageView) findViewById(R.id.image)).setImageBitmap(bitmap) ;
                });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}