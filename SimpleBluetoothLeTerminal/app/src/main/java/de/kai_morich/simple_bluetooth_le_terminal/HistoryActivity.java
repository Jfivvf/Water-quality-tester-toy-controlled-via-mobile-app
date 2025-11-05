package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import android.widget.TextView;
import android.view.View;
import android.content.Context;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<Measurement> measurementList = new ArrayList<>();
    private OkHttpClient client = new OkHttpClient();
    private TextView textEmpty;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Toolbar toolbar = findViewById(R.id.toolbarHistory);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getText(R.string.history));
        }

        recyclerView = findViewById(R.id.recyclerViewHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Адаптер с обработчиком клика
        adapter = new HistoryAdapter(measurementList, measurement -> {
            Intent intent = new Intent(HistoryActivity.this, MeasurementDetailActivity.class);
            intent.putExtra("created_at", measurement.getCreatedAt());
            intent.putExtra("temperature", measurement.getTemperature());
            intent.putExtra("salinity", measurement.getSalinity());
            intent.putExtra("location", measurement.getLocation() != null ? measurement.getLocation() : "");
            if (measurement.getLatitude() != null) intent.putExtra("latitude", measurement.getLatitude());
            if (measurement.getLongitude() != null) intent.putExtra("longitude", measurement.getLongitude());
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);

        textEmpty = findViewById(R.id.textEmpty);

        loadMeasurements();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadMeasurements() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);
        if (userId == -1) {
            Toast.makeText(this, "user_id не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            String url = "http://192.168.1.4:5000/measurements?user_id=" + userId;

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    JSONArray arr = new JSONArray(body);

                    measurementList.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);

                        String createdAt = obj.optString("created_at", "");
                        double temperature = obj.optDouble("temperature", 0.0);
                        double salinity = obj.optDouble("salinity", 0.0);
                        String location = obj.optString("location", "");

                        Double lat = null;
                        Double lon = null;
                        if (obj.has("latitude") && !obj.isNull("latitude")) lat = obj.optDouble("latitude");
                        if (obj.has("longitude") && !obj.isNull("longitude")) lon = obj.optDouble("longitude");

                        Measurement m = new Measurement(createdAt, temperature, salinity, location, lat, lon);
                        measurementList.add(m);
                    }

                    runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        if (measurementList.isEmpty()) {
                            textEmpty.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            textEmpty.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Сетевая ошибка", Toast.LENGTH_SHORT).show());
            } catch (Exception ex) {
                ex.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Ошибка парсинга данных", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
