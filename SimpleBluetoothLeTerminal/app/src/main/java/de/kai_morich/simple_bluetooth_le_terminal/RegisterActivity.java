package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.IOException;
import android.content.Context;
import okhttp3.*;

import android.widget.Spinner;
import android.widget.ArrayAdapter;

public class RegisterActivity extends AppCompatActivity {

    EditText etRegUsername, etRegEmail, etRegPassword;
    Button btnRegister;
    TextView tvLoginLink;
    Spinner spinnerRole;

    private static final String BASE_URL = "http://192.168.1.4:5000";
    OkHttpClient client = new OkHttpClient();
    MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etRegUsername = findViewById(R.id.etRegUsername);
        etRegEmail    = findViewById(R.id.etRegEmail);
        etRegPassword = findViewById(R.id.etRegPassword);
        spinnerRole   = findViewById(R.id.spinnerRole);
        btnRegister   = findViewById(R.id.btnRegister);
        tvLoginLink   = findViewById(R.id.tvLoginLink);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.roles_array_with_hint, android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);

        btnRegister.setOnClickListener(v -> registerUser());
        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String username = etRegUsername.getText().toString().trim();
        String email    = etRegEmail.getText().toString().trim();
        String password = etRegPassword.getText().toString().trim();
        String role     = spinnerRole.getSelectedItem().toString();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || role.equals("Выберите роль")) {
            Toast.makeText(this, "Заполните все поля и выберите роль", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("email", email);
            json.put("password", password);
            json.put("role", role);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/register")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() ->
                            Toast.makeText(RegisterActivity.this, "Ошибка подключения", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    String resp = response.body().string();
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, "Код отправлен на e-mail", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, VerifyCodeActivity.class).putExtra("email", email));
                            finish();
                        } else {
                            Toast.makeText(RegisterActivity.this, "Ошибка: " + resp, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
