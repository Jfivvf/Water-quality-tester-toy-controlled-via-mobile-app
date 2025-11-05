package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class LanguagesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_languages);

        Button btnEnglish = findViewById(R.id.btnEnglish);
        Button btnRussian = findViewById(R.id.btnRussian);
        Button btnGerman = findViewById(R.id.btnGerman);
        Button btnSpanish = findViewById(R.id.btnSpanish);
        Button btnItalian = findViewById(R.id.btnItalian);

        btnEnglish.setOnClickListener(v -> setLocale("en"));
        btnRussian.setOnClickListener(v -> setLocale("ru"));
        btnGerman.setOnClickListener(v -> setLocale("de"));
        btnSpanish.setOnClickListener(v -> setLocale("es"));
        btnItalian.setOnClickListener(v -> setLocale("it"));
    }

    private void setLocale(String langCode) {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        prefs.edit().putString("app_lang", langCode).apply();

        LocaleHelper.setLocale(this, langCode);

        Intent intent = new Intent(this, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
