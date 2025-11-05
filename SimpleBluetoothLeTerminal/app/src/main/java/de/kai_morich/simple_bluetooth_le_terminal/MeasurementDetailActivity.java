package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.ContentValues;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.widget.Toast;
import java.io.File;
import android.view.MenuItem;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MeasurementDetailActivity extends AppCompatActivity {

    private double salinity;
    private double temperature;
    private String createdAt;
    private String location;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement_detail);

        Toolbar toolbar = findViewById(R.id.toolbarDetail);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView textInfo = findViewById(R.id.textInfo);
        TextView textAlert = findViewById(R.id.textAlert);
        Button btnExport = findViewById(R.id.btnExport);
        Button btnCall = findViewById(R.id.btnCall);

        Intent intent = getIntent();
        createdAt = intent.getStringExtra("created_at");
        salinity = intent.getDoubleExtra("salinity", 0);
        temperature = intent.getDoubleExtra("temperature", 0);
        location = intent.getStringExtra("location");

        String info = getString(
                R.string.measurement_info_format,
                createdAt, location, temperature, salinity
        );
        textInfo.setText(info);

        if (salinity > 650) {
            double exceed = salinity - 650;
            textAlert.setVisibility(View.VISIBLE);
            textAlert.setText(getString(R.string.alert_exceeded_format, exceed));
            btnCall.setVisibility(View.VISIBLE);
            btnCall.setOnClickListener(v ->
                    Toast.makeText(this, getString(R.string.calling_service), Toast.LENGTH_SHORT).show());
        }

        btnExport.setOnClickListener(v -> exportData());
    }

    private void exportData() {
        String data = getString(
                R.string.export_data_format,
                createdAt, location, temperature, salinity
        );

        String filename = "measurement_" + createdAt.replace(" ", "_") + ".txt";

        try {
            Uri fileUri = null;
            OutputStream outputStream = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                fileUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (fileUri != null) {
                    outputStream = getContentResolver().openOutputStream(fileUri);
                }
            } else {
                java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File file = new java.io.File(downloadsDir, filename);
                outputStream = new java.io.FileOutputStream(file);
                fileUri = Uri.fromFile(file);
            }

            if (outputStream != null) {
                outputStream.write(data.getBytes());
                outputStream.flush();
                outputStream.close();

                Toast.makeText(this,
                        "Файл сохранён в Загрузки:\n" + filename,
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Ошибка при сохранении файла", Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.export_error), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


}
