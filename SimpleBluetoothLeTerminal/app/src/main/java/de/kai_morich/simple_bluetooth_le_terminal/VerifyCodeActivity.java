package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.content.Context;

import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import java.io.IOException;
import okhttp3.*;

public class VerifyCodeActivity extends AppCompatActivity {

    EditText etCode;
    Button btnVerify;

    private static final String BASE_URL = "http://192.168.1.4:5000";
    OkHttpClient client = new OkHttpClient();
    MediaType JSON = MediaType.get("application/json; charset=utf-8");

    String email;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_code);

        etCode = findViewById(R.id.etCode);
        btnVerify = findViewById(R.id.btnVerify);

        email = getIntent().getStringExtra("email");

        btnVerify.setOnClickListener(v -> verifyCode());
    }

    private void verifyCode() {
        String code = etCode.getText().toString().trim();
        if (code.length() != 6) {
            Toast.makeText(this, "Введите 6-значный код", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("code", code);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/verify")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() ->
                            Toast.makeText(VerifyCodeActivity.this, "Ошибка подключения", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    String resp = response.body().string();
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(VerifyCodeActivity.this, "Регистрация завершена", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(VerifyCodeActivity.this, LoginActivity.class));
                            finish();
                        } else {
                            Toast.makeText(VerifyCodeActivity.this, "Ошибка: " + resp, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
