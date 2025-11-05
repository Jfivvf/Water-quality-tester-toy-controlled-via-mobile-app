package de.kai_morich.simple_bluetooth_le_terminal;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AccountActivity extends AppCompatActivity {

    private ImageView imgAvatar, btnEditUsername, btnEditPassword;
    private TextView tvUsername, tvPassword, tvRoleValue, tvCreatedValue;
    private static final int REQ_PERMISSION = 100;
    private int userId;
    private String serverUrl = "http://192.168.1.4:5000";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        imgAvatar = findViewById(R.id.imgAvatar);
        btnEditUsername = findViewById(R.id.btnEditUsername);
        btnEditPassword = findViewById(R.id.btnEditPassword);
        tvUsername = findViewById(R.id.tvUsername);
        tvPassword = findViewById(R.id.tvPassword);
        tvRoleValue = findViewById(R.id.tvRoleValue);
        tvCreatedValue = findViewById(R.id.tvCreatedValue);

        userId = getSharedPreferences("UserPrefs", MODE_PRIVATE).getInt("user_id", -1);
        if (userId == -1) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadLocalAvatar();
        loadUserInfo();

        imgAvatar.setOnClickListener(v -> selectImage());
        TextView tvChangePhoto = findViewById(R.id.tvChangePhoto);
        tvChangePhoto.setOnClickListener(v -> selectImage());

        btnEditUsername.setOnClickListener(v -> showChangeUsernameDialog());
        btnEditPassword.setOnClickListener(v -> showChangePasswordDialog());
    }

    private void loadUserInfo() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                String response = NetworkUtils.postJson(serverUrl + "/get_user_info", json);
                JSONObject obj = new JSONObject(response);

                runOnUiThread(() -> {
                    try {
                        tvUsername.setText(obj.getString("username"));
                        if (obj.has("role")) tvRoleValue.setText(obj.getString("role"));
                        if (obj.has("created_at")) tvCreatedValue.setText(obj.getString("created_at"));

                        if (obj.has("avatar") && !obj.isNull("avatar")) {
                            byte[] decoded = Base64.decode(obj.getString("avatar"), Base64.DEFAULT);
                            Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                            imgAvatar.setImageBitmap(bmp);
                            saveLocalAvatar(bmp);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void selectImage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_PERMISSION);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMISSION);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            pickImageLauncher.launch(intent);
        } else {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
        }
    }

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                        bitmap = rotateImageIfRequired(this, selectedImage, bitmap);
                        imgAvatar.setImageBitmap(bitmap);
                        saveLocalAvatar(bitmap);
                        uploadAvatar(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

    private Bitmap rotateImageIfRequired(Context context, Uri uri, Bitmap bitmap) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(uri);
        ExifInterface ei = new ExifInterface(input);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        input.close();

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateBitmap(bitmap, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateBitmap(bitmap, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateBitmap(bitmap, 270);
            default:
                return bitmap;
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void uploadAvatar(Bitmap bitmap) {
        new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                String encoded = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("avatar", encoded);

                NetworkUtils.postJson(serverUrl + "/update_avatar", json);
                runOnUiThread(() -> Toast.makeText(this, "Avatar updated", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void saveLocalAvatar(Bitmap bitmap) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        String encoded = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        editor.putString("profile_image", encoded);
        editor.apply();
    }

    private void loadLocalAvatar() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String encoded = prefs.getString("profile_image", null);
        if (encoded != null) {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            Bitmap savedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            imgAvatar.setImageBitmap(savedBitmap);
        }
    }

    private void showChangeUsernameDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter new username");

        new AlertDialog.Builder(this)
                .setTitle("Change Username")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) updateUsername(newName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateUsername(String newName) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("new_username", newName);
                NetworkUtils.postJson(serverUrl + "/update_username", json);
                runOnUiThread(() -> {
                    tvUsername.setText(newName);
                    Toast.makeText(this, "Username updated", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showChangePasswordDialog() {
        Context ctx = this;
        EditText current = new EditText(ctx);
        current.setHint("Current password");
        EditText newPass = new EditText(ctx);
        newPass.setHint("New password");

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(current);
        layout.addView(newPass);

        new AlertDialog.Builder(ctx)
                .setTitle("Change Password")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String cur = current.getText().toString().trim();
                    String newP = newPass.getText().toString().trim();
                    if (!cur.isEmpty() && !newP.isEmpty()) updatePassword(cur, newP);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updatePassword(String current, String newP) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("current_password", current);
                json.put("new_password", newP);

                String response = NetworkUtils.postJson(serverUrl + "/update_password", json);
                JSONObject obj = new JSONObject(response);

                runOnUiThread(() -> {
                    try {
                        if (obj.has("success") && obj.getBoolean("success")) {
                            Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            String msg = obj.has("message") ? obj.getString("message") : "Invalid password";
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error parsing response", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            selectImage();
        }
    }
}
