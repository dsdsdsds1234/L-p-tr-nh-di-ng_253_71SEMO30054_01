package vn.edu.carapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.api.IMapController;
import org.osmdroid.views.overlay.Marker;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {

    private ImageButton btnZoomIn, btnZoomOut;
    private EditText edtPickupLocation;
    private EditText edtSearchLocation;

    private ImageView btnProfileHeader;
    private TextView txtHeaderUserName;
    private LinearLayout layoutServiceGrid;
    private RelativeLayout layoutMapAndBooking;
    private LinearLayout btnGridVehicleBike, btnGridVehicleCar4, btnGridVehicleCar7, btnGridVehicleBus;

    private Button btnConfirmBooking;
    private MapView mapView;
    private IMapController mapController;

    private Marker pickupMarker;
    private Marker destinationMarker;
    private String selectedVehicle = "Chưa chọn";
    private DatabaseHelper dbHelper;

    private androidx.activity.result.ActivityResultLauncher<String> pickImageLauncher;
    private Uri selectedAvatarUri = null;
    private ImageView currentSheetAvatar = null;

    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // Tạo một Thread Pool nhỏ để xử lý tác vụ Geocoder chạy ngầm, chống văng máy thật
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
        String lang = prefs.getString("uLang", "Tiếng Việt");
        String langCode = lang.equals("English") ? "en" : "vi";

        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);

        Resources res = newBase.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            Context context = newBase.createConfigurationContext(config);
            super.attachBaseContext(context);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
            super.attachBaseContext(newBase);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        org.osmdroid.config.Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Khởi tạo bộ chọn ảnh an toàn cho máy thật
        pickImageLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // Copy ảnh trực tiếp vào bộ nhớ Cache nội bộ của App để giữ quyền đọc vĩnh viễn
                        String localPath = saveImageToInternalStorage(uri);
                        if (localPath != null) {
                            selectedAvatarUri = Uri.fromFile(new File(localPath));
                            if (currentSheetAvatar != null) {
                                currentSheetAvatar.setPadding(0, 0, 0, 0);
                                currentSheetAvatar.setImageURI(selectedAvatarUri);
                            }
                            if (btnProfileHeader != null) {
                                btnProfileHeader.setImageURI(selectedAvatarUri);
                            }
                        }
                    }
                }
        );

        // Ánh xạ thành phần UI
        mapView = findViewById(R.id.mapView);
        edtPickupLocation = findViewById(R.id.edtPickupLocation);
        edtSearchLocation = findViewById(R.id.edtSearchLocation);
        btnConfirmBooking = findViewById(R.id.btnConfirmBooking);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById( R.id.btnZoomOut);

        btnProfileHeader = findViewById(R.id.btnProfileHeader);
        txtHeaderUserName = findViewById(R.id.txtHeaderUserName);
        layoutServiceGrid = findViewById(R.id.layoutServiceGrid);
        layoutMapAndBooking = findViewById(R.id.layoutMapAndBooking);

        btnGridVehicleBike = findViewById(R.id.btnGridVehicleBike);
        btnGridVehicleCar4 = findViewById(R.id.btnGridVehicleCar4);
        btnGridVehicleCar7 = findViewById(R.id.btnGridVehicleCar7);
        btnGridVehicleBus = findViewById(R.id.btnGridVehicleBus);

        updateHeaderProfileInfo();

        // Cấu hình bản đồ
        if (mapView != null) {
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setMultiTouchControls(true);
            mapView.setTilesScaledToDpi(true);
            mapView.setDestroyMode(false);
            mapView.setMinZoomLevel(4.0);
            mapView.setMaxZoomLevel(20.0);

            mapController = mapView.getController();
            mapController.setZoom(15.0);
            GeoPoint startPoint = new GeoPoint(10.762622, 106.660172);
            mapController.setCenter(startPoint);
        }

        checkLocationPermissionAndGetLocation();

        // Lắng nghe sự kiện click chọn loại xe
        if (btnGridVehicleBike != null) btnGridVehicleBike.setOnClickListener(v -> activateBookingMap("Xe Máy"));
        if (btnGridVehicleCar4 != null) btnGridVehicleCar4.setOnClickListener(v -> activateBookingMap("Xe 4 Chỗ"));
        if (btnGridVehicleCar7 != null) btnGridVehicleCar7.setOnClickListener(v -> activateBookingMap("Xe 7 Chỗ"));
        if (btnGridVehicleBus != null) btnGridVehicleBus.setOnClickListener(v -> activateBookingMap("Xe Bus"));

        if (edtPickupLocation != null) {
            edtPickupLocation.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    String locationName = edtPickupLocation.getText().toString().trim();
                    if (!locationName.isEmpty() && !locationName.startsWith("Vị trí của bạn")) {
                        searchAndMoveToLocation(locationName, true);
                    }
                }
            });
        }

        if (edtSearchLocation != null) {
            edtSearchLocation.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    String locationName = edtSearchLocation.getText().toString().trim();
                    if (!locationName.isEmpty()) {
                        searchAndMoveToLocation(locationName, false);
                    }
                }
            });
        }

        if (btnZoomIn != null) btnZoomIn.setOnClickListener(v -> { if (mapController != null) mapController.zoomIn(); });
        if (btnZoomOut != null) btnZoomOut.setOnClickListener(v -> { if (mapController != null) mapController.zoomOut(); });

        if (btnProfileHeader != null) {
            btnProfileHeader.setOnClickListener(v -> openUserProfileBottomSheet());
        }

        if (btnConfirmBooking != null) {
            btnConfirmBooking.setOnClickListener(v -> {
                String pickup = edtPickupLocation.getText().toString().trim();
                String destination = edtSearchLocation.getText().toString().trim();

                if (pickup.isEmpty()) {
                    pickup = "Vị trí hiện tại của bạn";
                    edtPickupLocation.setText(pickup);
                }
                if (destination.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập điểm đến!", Toast.LENGTH_SHORT).show();
                    return;
                }

                searchAndMoveToLocation(pickup, true);
                searchAndMoveToLocation(destination, false);

                String finalPickup = pickup;
                String finalDestination = destination;
                btnConfirmBooking.postDelayed(() -> {
                    showPriceEstimationDialog(finalPickup, finalDestination);
                }, 300);
            });
        }

        handleIntentData(getIntent());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutMapAndBooking != null && layoutMapAndBooking.getVisibility() == View.VISIBLE) {
                    layoutMapAndBooking.setVisibility(View.GONE);
                    if (layoutServiceGrid != null) {
                        layoutServiceGrid.setVisibility(View.VISIBLE);
                    }
                } else {
                    finish();
                }
            }
        });
    }

    // Hàm phụ trợ copy file tránh rớt quyền đọc URI trên điện thoại thật
    private String saveImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            File file = new File(getFilesDir(), "user_avatar.jpg");
            OutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentData(intent);
    }

    private void handleIntentData(Intent intent) {
        if (intent != null && intent.hasExtra("EXTRA_DESTINATION")) {
            String destinationFromSearch = intent.getStringExtra("EXTRA_DESTINATION");
            if (destinationFromSearch != null && !destinationFromSearch.isEmpty()) {
                activateBookingMap("Xe 4 Chỗ");
                if (edtSearchLocation != null) edtSearchLocation.setText(destinationFromSearch);
                searchAndMoveToLocation(destinationFromSearch, false);
            }
        }
    }

    private void showPriceEstimationDialog(String pickup, String destination) {
        double finalDistanceKm = 3.5;

        if (pickupMarker != null && destinationMarker != null) {
            GeoPoint startPoint = pickupMarker.getPosition();
            GeoPoint endPoint = destinationMarker.getPosition();

            double distanceInMeters = startPoint.distanceToAsDouble(endPoint);
            double actualDistance = distanceInMeters / 1000.0;
            finalDistanceKm = Math.round(actualDistance * 10.0) / 10.0;
        }

        if (finalDistanceKm < 1.0) {
            finalDistanceKm = 1.0;
        }

        long baseFare = 12000;
        if (selectedVehicle.contains("Xe Máy")) baseFare = 7000;
        else if (selectedVehicle.contains("7 Chỗ")) baseFare = 18000;
        else if (selectedVehicle.contains("Bus")) baseFare = 5000;

        long finalPrice = (long) (finalDistanceKm * baseFare);
        final double finalDistanceForFirebase = finalDistanceKm;

        CharSequence[] options = new CharSequence[]{
                String.format("Dịch vụ: %s\nQuãng đường thực tế: %.1f km\nTổng tiền: %,d VNĐ", selectedVehicle, finalDistanceKm, finalPrice)
        };

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this);
        builder.setTitle("💰 BẢNG GIÁ ƯỚC TÍNH CHUYẾN ĐI");
        builder.setItems(options, null);

        builder.setPositiveButton("Xác nhận đặt xe ngay", (dialog, which) -> {
            SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
            String userName = prefs.getString("uName", "Hành khách");
            String userPhone = prefs.getString("uPhone", "0000000000");

            FirebaseDatabase database = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app");
            DatabaseReference dbBookingsRef = database.getReference("bookings");
            String bookingId = dbBookingsRef.push().getKey();

            if (bookingId != null) {
                Map<String, Object> bookingData = new HashMap<>();
                bookingData.put("bookingId", bookingId);
                bookingData.put("passengerName", userName);
                bookingData.put("passengerPhone", userPhone);
                bookingData.put("pickupLocation", pickup);
                bookingData.put("destinationLocation", destination);
                bookingData.put("vehicleType", selectedVehicle);
                bookingData.put("distanceKm", finalDistanceForFirebase);
                bookingData.put("totalPrice", finalPrice);

                bookingData.put("driverName", "Đang tìm tài xế...");
                bookingData.put("vehicleName", "");
                bookingData.put("plateNumber", "");
                bookingData.put("status", "Đang tìm xe");

                // ĐÃ CẬP NHẬT: Thêm mốc thời gian hệ thống của server Firebase để đồng bộ với bộ lọc radar tài xế
                bookingData.put("timestamp", com.google.firebase.database.ServerValue.TIMESTAMP);

                dbBookingsRef.child(bookingId).setValue(bookingData);

                Intent intent = new Intent(MainActivity.this, BookingScanningActivity.class);
                intent.putExtra("booking_id", bookingId);
                intent.putExtra("vehicle_type", selectedVehicle);
                intent.putExtra("pickup_location", pickup);
                intent.putExtra("destination_location", destination);
                startActivity(intent);
            }
        });

        builder.setNegativeButton("Hủy bỏ", null);
        builder.create().show();
    }

    private void activateBookingMap(String vehicleType) {
        selectedVehicle = vehicleType;
        Toast.makeText(this, "Đã chọn dịch vụ: " + vehicleType, Toast.LENGTH_SHORT).show();
        if (layoutServiceGrid != null) layoutServiceGrid.setVisibility(View.GONE);
        if (layoutMapAndBooking != null) layoutMapAndBooking.setVisibility(View.VISIBLE);
    }

    private void updateHeaderProfileInfo() {
        SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
        if (txtHeaderUserName != null) {
            txtHeaderUserName.setText(prefs.getString("uName", "Hành khách"));
        }
        String savedAvatar = prefs.getString("uAvatar", "");
        if (!savedAvatar.isEmpty() && btnProfileHeader != null) {
            try {
                // Sử dụng khối try-catch bọc an toàn tránh sập máy thật khi phân quyền File thay đổi
                btnProfileHeader.setImageURI(Uri.parse(savedAvatar));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void openUserProfileBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(MainActivity.this);
        View sheetView = getLayoutInflater().inflate(R.layout.activity_profile, null);
        bottomSheetDialog.setContentView(sheetView);

        ImageView imgAvatar = sheetView.findViewById(R.id.imgProfileAvatar);
        EditText edtName = sheetView.findViewById(R.id.edtProfileName);
        EditText edtPhone = sheetView.findViewById(R.id.edtProfilePhone);
        Spinner spinnerLang = sheetView.findViewById(R.id.spinnerProfileLang);
        Button btnSave = sheetView.findViewById(R.id.btnProfileSave);
        Button btnProfileLogout = sheetView.findViewById(R.id.btnProfileLogout);

        currentSheetAvatar = imgAvatar;

        if (imgAvatar != null) {
            imgAvatar.setOnClickListener(view -> pickImageLauncher.launch("image/*"));
        }

        String[] languages = {"Tiếng Việt", "English"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, languages);
        if (spinnerLang != null) spinnerLang.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
        if (edtName != null) edtName.setText(prefs.getString("uName", "Hành khách"));
        if (edtPhone != null) edtPhone.setText(prefs.getString("uPhone", ""));
        if (spinnerLang != null) spinnerLang.setSelection(prefs.getString("uLang", "Tiếng Việt").equals("English") ? 1 : 0);

        String savedAvatar = prefs.getString("uAvatar", "");
        if (!savedAvatar.isEmpty() && imgAvatar != null) {
            try {
                imgAvatar.setPadding(0, 0, 0, 0);
                imgAvatar.setImageURI(Uri.parse(savedAvatar));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(view -> {
                String name = edtName.getText().toString().trim();
                String phone = edtPhone.getText().toString().trim();
                String lang = spinnerLang.getSelectedItem().toString();

                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Vui lòng điền đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String currentLang = prefs.getString("uLang", "Tiếng Việt");
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("uName", name);
                editor.putString("uPhone", phone);
                editor.putString("uLang", lang);

                if (selectedAvatarUri != null) {
                    editor.putString("uAvatar", selectedAvatarUri.toString());
                }
                editor.apply();

                Toast.makeText(MainActivity.this, "Đã cập nhật hồ sơ cá nhân!", Toast.LENGTH_SHORT).show();

                updateHeaderProfileInfo();
                bottomSheetDialog.dismiss();

                if (!lang.equals(currentLang)) {
                    setAppLocale(lang.equals("English") ? "en" : "vi");
                }
            });
        }

        if (btnProfileLogout != null) {
            btnProfileLogout.setOnClickListener(view -> {
                bottomSheetDialog.dismiss();
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isDriverAvailable", false);
                editor.apply();

                Intent intent = new Intent(MainActivity.this, AuthActivity.class);
                startActivity(intent);
                finish();
            });
        }

        bottomSheetDialog.setOnShowListener(dialog -> {
            BottomSheetDialog dialogCast = (BottomSheetDialog) dialog;
            FrameLayout bottomSheet = dialogCast.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.setLayoutParams(layoutParams);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        bottomSheetDialog.show();
    }

    private void checkLocationPermissionAndGetLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getUserCurrentLocation();
        }
    }

    private void getUserCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    GeoPoint myLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                    if (mapController != null) {
                        mapController.setZoom(17.5);
                        mapController.animateTo(myLocation);
                    }
                    if (pickupMarker != null) {
                        mapView.getOverlays().remove(pickupMarker);
                    }
                    pickupMarker = new Marker(mapView);
                    pickupMarker.setPosition(myLocation);
                    pickupMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    pickupMarker.setTitle("Điểm đón của bạn");
                    mapView.getOverlays().add(pickupMarker);
                    mapView.invalidate();

                    // Chuyển Geocoder chạy ngầm qua Executor để tránh NetworkOnMainThreadException sập máy thật
                    backgroundExecutor.execute(() -> {
                        Geocoder geocoder = new Geocoder(MainActivity.this, new Locale("vi", "VN"));
                        try {
                            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                            runOnUiThread(() -> {
                                if (addresses != null && !addresses.isEmpty()) {
                                    if (edtPickupLocation != null) edtPickupLocation.setText(addresses.get(0).getAddressLine(0));
                                } else {
                                    if (edtPickupLocation != null) edtPickupLocation.setText("Vị trí hiện tại của bạn");
                                }
                            });
                        } catch (IOException e) {
                            runOnUiThread(() -> {
                                if (edtPickupLocation != null) edtPickupLocation.setText("Vị trí hiện tại của bạn");
                            });
                        }
                    });
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getUserCurrentLocation();
        }
    }

    private void setAppLocale(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        resources.updateConfiguration(config, resources.getDisplayMetrics());
        recreate();
    }

    private void searchAndMoveToLocation(String locationName, boolean isPickup) {
        // Đưa toàn bộ tác vụ tìm kiếm Geocoder vào luồng chạy ngầm
        backgroundExecutor.execute(() -> {
            Geocoder geocoder = new Geocoder(this, new Locale("vi", "VN"));
            try {
                List<Address> addresses = geocoder.getFromLocationName(locationName, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    GeoPoint targetPoint = new GeoPoint(address.getLatitude(), address.getLongitude());

                    runOnUiThread(() -> {
                        if (mapController != null) {
                            mapController.setZoom(17.5);
                            mapController.animateTo(targetPoint);
                        }

                        if (isPickup) {
                            if (pickupMarker != null) { mapView.getOverlays().remove(pickupMarker); }
                            pickupMarker = new Marker(mapView);
                            pickupMarker.setPosition(targetPoint);
                            pickupMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            mapView.getOverlays().add(pickupMarker);
                        } else {
                            if (destinationMarker != null) { mapView.getOverlays().remove(destinationMarker); }
                            destinationMarker = new Marker(mapView);
                            destinationMarker.setPosition(targetPoint);
                            destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            mapView.getOverlays().add(destinationMarker);
                        }
                        mapView.invalidate();
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); if (mapView != null) mapView.onPause(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Tắt bộ luồng ngầm khi hủy ứng dụng để giải phóng bộ nhớ RAM
        backgroundExecutor.shutdown();
    }
}