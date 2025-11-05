package de.kai_morich.simple_bluetooth_le_terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.util.Log;


public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private static final int REQ_LOCATION = 1001;

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private EditText etLocation;
    private TextView tvTempValue, tvSalinityValue;
    private Button btnCollect, btnSave;

    // --- локация ---
    private FusedLocationProviderClient fusedLocationClient;
    private Double latitude = null, longitude = null;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    private final Handler handler = new Handler();
    private boolean waitingTemp = false;
    private boolean waitingTds  = false;
    private Double lastTemp = null;
    private Double lastSalinity = null;

    private static final String BASE_URL = "http://192.168.1.4:5000";
    private final OkHttpClient http = new OkHttpClient();
    private final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments() != null ? getArguments().getString("device") : null;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False) disconnect();
        requireActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null) service.attach(this);
        else requireActivity().startService(new Intent(getActivity(), SerialService.class));
    }

    @Override
    public void onStop() {
        if(service != null && !requireActivity().isChangingConfigurations()) service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        requireActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { requireActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            requireActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            requireActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        receiveText = view.findViewById(R.id.receive_text);
        sendText = view.findViewById(R.id.send_text);

        hexWatcher       = new TextUtil.HexWatcher(sendText);

        View btnForward = view.findViewById(R.id.btn_forward);
        View btnBackward = view.findViewById(R.id.btn_backward);
        View btnLeft = view.findViewById(R.id.btn_left);
        View btnRight = view.findViewById(R.id.btn_right);
        View btnStop = view.findViewById(R.id.btn_stop);

        btnForward.setOnClickListener(v -> send("5"));
        btnBackward.setOnClickListener(v -> send("6"));
        btnLeft.setOnClickListener(v -> send("7"));
        btnRight.setOnClickListener(v -> send("8"));
        btnStop.setOnClickListener(v -> send("0"));

        etLocation      = view.findViewById(R.id.et_location);
        tvTempValue     = view.findViewById(R.id.tv_temp_value);
        tvSalinityValue = view.findViewById(R.id.tv_salinity_value);
        btnCollect      = view.findViewById(R.id.btn_collect);
        btnSave         = view.findViewById(R.id.btn_save);

        btnCollect.setOnClickListener(v -> collectData());
        btnSave.setOnClickListener(v -> saveData());

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.hex).setChecked(hexEnabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.backgroundNotification).setChecked(service != null && service.areNotificationsEnabled());
        } else {
            menu.findItem(R.id.backgroundNotification).setChecked(true);
            menu.findItem(R.id.backgroundNotification).setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            new AlertDialog.Builder(getActivity())
                    .setTitle("Newline")
                    .setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                        newline = newlineValues[item1];
                        dialog.dismiss();
                    }).create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else if (id == R.id.backgroundNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!service.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                } else {
                    showNotificationSettings();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(requireContext().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            if (hexEnabled) {
                spn.append(TextUtil.toHexString(data)).append('\n');
            } else {
                String raw = new String(data);
                String msg = raw;
                if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                    if (pendingNewline && msg.charAt(0) == '\n') {
                        if(spn.length() >= 2) {
                            spn.delete(spn.length() - 2, spn.length());
                        } else {
                            Editable edt = receiveText.getEditableText();
                            if (edt != null && edt.length() >= 2)
                                edt.delete(edt.length() - 2, edt.length());
                        }
                    }
                    pendingNewline = msg.charAt(msg.length() - 1) == '\r';
                }
                parseIncomingForValues(msg);
                spn.append(TextUtil.toCaretString(msg, newline.length() != 0));
            }
        }
        receiveText.append(spn);
    }

    void status(String str) {
        Log.d("TerminalDebug", "STATUS: " + str);
        if (getActivity() == null || receiveText == null) {
            Log.w("TerminalFragment", "UI not ready, skipping status: " + str);
            return;
        }
        receiveText.append(str + "\n");
    }


    private void showNotificationSettings() {
        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("android.provider.extra.APP_PACKAGE", requireActivity().getPackageName());
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(Arrays.equals(permissions, new String[]{Manifest.permission.POST_NOTIFICATIONS}) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !service.areNotificationsEnabled()) {
            showNotificationSettings();
        }

        if (requestCode == REQ_LOCATION) {
            // Пользователь дал/не дал гео — просто сообщим, сохранить можно повторно
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getActivity(), "Разрешение на геолокацию получено", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), "Геолокация недоступна — координаты не будут сохранены", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }


    private void collectData() {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        lastTemp = null;
        lastSalinity = null;
        tvTempValue.setText("--");
        tvSalinityValue.setText("--");

        waitingTemp = true;
        send("temp");

        handler.postDelayed(() -> {
            waitingTds = true;
            send("tds");
        }, 1200);
    }

    private void parseIncomingForValues(String msg) {
        String lower = msg.toLowerCase();

        if (waitingTemp && (lower.contains("temp") || lower.contains("температ"))) {
            Double v = tryExtractNumber(lower);
            if (v != null) {
                lastTemp = v;
                waitingTemp = false;
                tvTempValue.setText(String.valueOf(v));
            }
        }

        if (waitingTds && (lower.contains("tds") || lower.contains("ppm"))) {
            Double v = tryExtractNumber(lower);
            if (v != null) {
                lastSalinity = v;
                waitingTds = false;
                tvSalinityValue.setText(String.valueOf(v));
            }
        }
    }

    private Double tryExtractNumber(String s) {
        Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(s);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); }
            catch (Exception ignored) {}
        }
        return null;
    }

    private void saveData() {
        if (lastTemp == null || lastSalinity == null) {
            Toast.makeText(getActivity(), "Сначала нажмите Collect data", Toast.LENGTH_SHORT).show();
            return;
        }

        String locationName = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        if (locationName.isEmpty()) locationName = "unknown";

        Context ctx = requireContext().getApplicationContext();
        String PREF_NAME = "UserPrefs";
        int userId = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt("user_id", -1);
        if (userId <= 0) {
            Toast.makeText(getActivity(), "user_id не найден. Сохраните user_id в SharedPreferences после логина.", Toast.LENGTH_LONG).show();
            return;
        }

        fetchLocationThenSave(userId, locationName);
    }

    private void fetchLocationThenSave(int userId, String locationName) {
        boolean fine = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            Toast.makeText(getActivity(), "Разрешите геолокацию и повторите сохранение", Toast.LENGTH_LONG).show();
            postMeasurement(userId, locationName, null, null);
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        latitude = loc.getLatitude();
                        longitude = loc.getLongitude();
                    } else {
                        latitude = null;
                        longitude = null;
                    }
                    postMeasurement(userId, locationName, latitude, longitude);
                })
                .addOnFailureListener(e -> {
                    latitude = null; longitude = null;
                    postMeasurement(userId, locationName, null, null);
                });
    }

    private void postMeasurement(int userId, String locationName, Double lat, Double lon) {
        try {
            // ISO 8601 с часовым поясом устройства
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            iso.setTimeZone(TimeZone.getDefault());
            String measuredAt = iso.format(new Date());

            org.json.JSONObject json = new org.json.JSONObject();
            json.put("user_id", userId);
            json.put("location", locationName);
            json.put("temperature", lastTemp);
            json.put("salinity", lastSalinity);
            if (lat != null) json.put("latitude", lat);
            if (lon != null) json.put("longitude", lon);
            json.put("measured_at", measuredAt);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/measurements")
                    .post(body)
                    .build();

            http.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (getActivity()==null) return;
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getActivity(), "Ошибка сети при сохранении", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String resp = response.body() != null ? response.body().string() : "";
                    if (getActivity()==null) return;
                    requireActivity().runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(getActivity(), "Данные сохранены", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getActivity(), "Ошибка сохранения: " + resp, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Ошибка формирования данных", Toast.LENGTH_SHORT).show();
        }
    }
}
