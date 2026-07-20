package vn.edu.carapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.gms.location.*;
import com.google.firebase.database.*;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class DriverActivity extends AppCompatActivity {

    // 1. Thành phần UI thuộc Header tài xế
    private TextView txtDriverNameHeader, txtDriverEmailHeader;
    private ImageView btnDriverProfile;
    private Button btnDriverLogout;

    // 2. Thành phần UI 2 trạng thái giao diện (Grid Menu & Map Scanning)
    private LinearLayout layoutDriverServiceGrid;
    private RelativeLayout layoutDriverMapAndScanning;
    private LinearLayout layoutDriverRadarContainer;

    // TextView động trong Radar để hiển thị trạng thái định vị/quét và địa chỉ thật
    private TextView txtRadarStatusTitle;
    private TextView txtRadarStatusSub;

    // 3. Các nút bấm chức năng trong Lưới ô vuông
    private LinearLayout btnGridAcceptTrip;
    private LinearLayout btnGridCheckFare;
    private LinearLayout btnGridHistory;
    private LinearLayout btnGridWallet;
    private Button btnCancelDriverScanning;

    // Thành phần điều khiển Hoàn tất chuyến đi và Đón khách
    private Button btnCompleteTrip;
    private Button btnPickedUp;

    // Quản lý trạng thái chuyến đi
    private enum TripStatus {
        GOING_TO_PICKUP,
        ON_TRIP
    }
    private TripStatus currentTripStatus = TripStatus.GOING_TO_PICKUP;

    // 4. Thành phần Bản đồ OSM và định vị hệ thống
    private MapView driverMapView;
    private IMapController mapController;
    private Marker pickupMarker;
    private Marker destinationMarker;
    private Marker driverMarker;
    private Polyline currentRoadOverlay; // CẬP NHẬT MỚI: Quản lý nét vẽ lộ trình tránh trùng lặp đè nét

    // Quản lý định vị của Google Play Services
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private boolean isFirstLocation = true;
    private boolean isScanningActive = false;

    private String selectedVehicleType = "Xe Máy";
    private String driverEmail = "";
    private String encodedEmail = "";

    // BIẾN QUẢN LÝ FIREBASE ĐỂ LẮNG NGHE REALTIME
    private DatabaseReference bookingsRef;
    private DatabaseReference currentDriverRef;
    private DatabaseReference driverLocationRef;
    private Query recentBookingsQuery;
    private ValueEventListener radarListener;
    private ValueEventListener driverInfoListener;
    private String currentBookingId = null;

    private final List<String> ignoredBookingIds = new ArrayList<>();

    // BIẾN LƯU TẠM THÔNG TIN CUỐC XE ĐỂ GHI LỊCH SỬ KHI BẤM HOÀN THÀNH
    private String tempCustomerName = "";
    private String tempPrice = "0";
    private String tempDistance = "0 km";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        setContentView(R.layout.activity_driver);

        initViews();
        loadDriverIdentity();
        setupMapViewConfiguration();
        setupLocationServices();
        setupClickListeners();
        observeDriverProfileRealtime();
    }

    private void initViews() {
        txtDriverNameHeader = findViewById(R.id.txtDriverNameHeader);
        txtDriverEmailHeader = findViewById(R.id.txtDriverEmailHeader);
        btnDriverProfile = findViewById(R.id.btnDriverProfile);
        btnDriverLogout = findViewById(R.id.btnDriverLogout);

        layoutDriverServiceGrid = findViewById(R.id.layoutDriverServiceGrid);
        layoutDriverMapAndScanning = findViewById(R.id.layoutDriverMapAndScanning);
        layoutDriverRadarContainer = findViewById(R.id.layoutDriverRadarContainer);

        txtRadarStatusTitle = findViewById(R.id.txtDriverRadarAddress);
        txtRadarStatusSub = findViewById(R.id.txtDriverRadarAddressSub);

        btnGridAcceptTrip = findViewById(R.id.btnGridAcceptTrip);
        btnGridCheckFare = findViewById(R.id.btnGridCheckFare);
        btnGridHistory = findViewById(R.id.btnGridHistory);
        btnGridWallet = findViewById(R.id.btnGridWallet);
        btnCancelDriverScanning = findViewById(R.id.btnCancelDriverScanning);
        btnCompleteTrip = findViewById(R.id.btnCompleteTrip);
        btnPickedUp = findViewById(R.id.btnPickedUp);

        driverMapView = findViewById(R.id.driverMapView);
        driverMapView.setBuiltInZoomControls(false);
        driverMapView.setMultiTouchControls(true);
    }

    private void loadDriverIdentity() {
        SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
        driverEmail = prefs.getString("CURRENT_LOGGED_IN_DRIVER_EMAIL", "");

        if (driverEmail.isEmpty()) {
            driverEmail = getIntent().getStringExtra("USER_EMAIL");
        }

        if (driverEmail == null || driverEmail.isEmpty()) {
            Toast.makeText(this, "⚠️ Phiên làm việc hết hạn, vui lòng đăng nhập lại!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        if (txtDriverEmailHeader != null) {
            txtDriverEmailHeader.setText(driverEmail);
        }

        encodedEmail = DatabaseHelper.encodeEmail(driverEmail);

        currentDriverRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("drivers").child(encodedEmail);

        driverLocationRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("drivers_working").child(encodedEmail);
    }

    private void observeDriverProfileRealtime() {
        if (currentDriverRef == null) return;

        driverInfoListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String dName = snapshot.child("dName").getValue(String.class);
                    String dVehicleType = snapshot.child("dVehicleType").getValue(String.class);

                    if (dName != null && !dName.isEmpty()) {
                        txtDriverNameHeader.setText("Xin chào, " + dName);
                    }
                    if (dVehicleType != null && !dVehicleType.isEmpty()) {
                        selectedVehicleType = dVehicleType;
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        currentDriverRef.addValueEventListener(driverInfoListener);
    }

    private void setupMapViewConfiguration() {
        if (driverMapView != null) {
            driverMapView.setTileSource(TileSourceFactory.MAPNIK);
            driverMapView.setMultiTouchControls(true);
            mapController = driverMapView.getController();
            mapController.setZoom(15.5);
            mapController.setCenter(new GeoPoint(10.762622, 106.660172));
        }
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null && isScanningActive) {
                        updateDriverLocationOnOSM(location);
                    }
                }
            }
        };
    }

    private void setupClickListeners() {
        if (btnGridAcceptTrip != null) {
            btnGridAcceptTrip.setOnClickListener(v -> startScanningWorkflow());
        }

        if (btnDriverProfile != null) {
            btnDriverProfile.setOnClickListener(v -> openPassengerProfileBottomSheet());
        }

        if (btnGridCheckFare != null) {
            btnGridCheckFare.setOnClickListener(v -> {
                String todayDateStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
                DatabaseReference historyRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                        .getReference("history_trips");

                Toast.makeText(DriverActivity.this, "⏳ Đang kiểm tra điều kiện...", Toast.LENGTH_SHORT).show();

                historyRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int todayTripCounter = 0;
                        for (DataSnapshot tripSnapshot : snapshot.getChildren()) {
                            String timeCompleted = tripSnapshot.child("timeCompleted").getValue(String.class);
                            String tripDriverEmail = tripSnapshot.child("driverEmail").getValue(String.class);

                            if (timeCompleted != null && timeCompleted.startsWith(todayDateStr) && driverEmail.equals(tripDriverEmail)) {
                                todayTripCounter++;
                            }
                        }

                        if (todayTripCounter >= 2) {
                            Intent intentCheckIn = new Intent(DriverActivity.this, CheckInActivity.class);
                            startActivity(intentCheckIn);
                        } else {
                            Toast.makeText(DriverActivity.this, "⚠️ Bạn mới hoàn thành " + todayTripCounter + "/2 cuốc xe cá nhân hôm nay.", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            });
        }

        if (btnGridHistory != null) {
            btnGridHistory.setOnClickListener(v -> startActivity(new Intent(DriverActivity.this, HistoryActivity.class)));
        }

        if (btnGridWallet != null) {
            btnGridWallet.setOnClickListener(v -> startActivity(new Intent(DriverActivity.this, WalletActivity.class)));
        }

        if (btnCancelDriverScanning != null) {
            btnCancelDriverScanning.setOnClickListener(v -> stopScanningWorkflow());
        }

        if (btnPickedUp != null) {
            btnPickedUp.setOnClickListener(v -> {
                currentTripStatus = TripStatus.ON_TRIP;
                btnPickedUp.setVisibility(View.GONE);

                if (btnCompleteTrip != null) {
                    btnCompleteTrip.setVisibility(View.VISIBLE);
                }

                if (currentBookingId != null) {
                    FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                            .getReference("bookings").child(currentBookingId)
                            .child("status").setValue("Đang thực hiện chuyến đi");
                }

                if (layoutDriverRadarContainer != null) {
                    layoutDriverRadarContainer.setVisibility(View.GONE);
                }

                if (destinationMarker != null && mapController != null) {
                    mapController.animateTo(destinationMarker.getPosition());
                }

                Toast.makeText(DriverActivity.this, "Đã đón khách! Bắt đầu hành trình đến điểm đến.", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnCompleteTrip != null) {
            btnCompleteTrip.setOnClickListener(v -> saveTripToFirebaseHistory());
        }

        if (btnDriverLogout != null) {
            btnDriverLogout.setOnClickListener(v -> {
                if (driverLocationRef != null) {
                    driverLocationRef.removeValue();
                }

                SharedPreferences.Editor editor = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE).edit();
                editor.remove("CURRENT_LOGGED_IN_DRIVER_EMAIL");
                editor.remove("dName");
                editor.apply();

                Toast.makeText(DriverActivity.this, "Đã đăng xuất tài khoản Đối tác!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(DriverActivity.this, AuthActivity.class));
                finish();
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutDriverMapAndScanning != null && layoutDriverMapAndScanning.getVisibility() == View.VISIBLE){
                    stopScanningWorkflow();
                } else {
                    finish();
                }
            }
        });
    }

    private void startScanningWorkflow() {
        if (layoutDriverServiceGrid != null) layoutDriverServiceGrid.setVisibility(View.GONE);
        if (layoutDriverMapAndScanning != null) layoutDriverMapAndScanning.setVisibility(View.VISIBLE);
        if (layoutDriverRadarContainer != null) layoutDriverRadarContainer.setVisibility(View.VISIBLE);

        if (btnCompleteTrip != null) btnCompleteTrip.setVisibility(View.GONE);
        if (btnPickedUp != null) btnPickedUp.setVisibility(View.GONE);

        if (txtRadarStatusTitle != null) {
            txtRadarStatusTitle.setText("ĐANG XÁC ĐỊNH VỊ TRÍ HIỆN TẠI...");
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        double driverLat = location.getLatitude();
                        double driverLng = location.getLongitude();

                        updateDriverLocationOnOSM(location);
                        String realAddress = getAddressFromLatLng(driverLat, driverLng);

                        if (txtRadarStatusTitle != null) txtRadarStatusTitle.setText("📍 " + realAddress);
                        if (txtRadarStatusSub != null) txtRadarStatusSub.setText("ĐANG QUÉT KHÁCH HÀNG XUNG QUANH TRONG BÁN KÍNH 2KM...");

                        isScanningActive = true;
                        startLocationUpdates();
                        startScanningForPassengerFlow(driverLat, driverLng);
                    } else {
                        Toast.makeText(this, "Không thể xác định vị trí GPS. Thử lại sau!", Toast.LENGTH_SHORT).show();
                        stopScanningWorkflow();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Toast.makeText(this, "Lỗi định vị: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    stopScanningWorkflow();
                });
    }

    private String getAddressFromLatLng(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                if (address.contains(", Việt Nam")) {
                    address = address.replace(", Việt Nam", "");
                }
                return address;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Tọa độ: " + lat + ", " + lng;
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void startScanningForPassengerFlow(double driverLat, double driverLng) {
        long tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000);
        bookingsRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("bookings");

        recentBookingsQuery = bookingsRef.orderByChild("timestamp").startAt(tenMinutesAgo);

        radarListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isScanningActive) return;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String status = data.child("status").getValue(String.class);
                    String bookingId = data.getKey();

                    if ("Đang tìm xe".equals(status) && bookingId != null) {
                        String requestedVehicleType = data.child("vehicleType").getValue(String.class);
                        if (requestedVehicleType == null || requestedVehicleType.isEmpty()) {
                            requestedVehicleType = "Xe 4 Chỗ";
                        }

                        if (!requestedVehicleType.equals(selectedVehicleType)) continue;
                        if (ignoredBookingIds.contains(bookingId)) continue;

                        String originAddress = data.child("pickupLocation").getValue(String.class);
                        if (originAddress == null || originAddress.isEmpty()) continue;

                        try {
                            Geocoder geocoder = new Geocoder(DriverActivity.this, new Locale("vi", "VN"));
                            List<Address> pickupAddresses = geocoder.getFromLocationName(originAddress, 1);
                            if (pickupAddresses != null && !pickupAddresses.isEmpty()) {
                                Address addr = pickupAddresses.get(0);
                                double passengerLat = addr.getLatitude();
                                double passengerLng = addr.getLongitude();

                                double distanceToPassenger = calculateDistanceBetweenPoints(driverLat, driverLng, passengerLat, passengerLng);

                                if (distanceToPassenger <= 2.0) {
                                    currentBookingId = bookingId;
                                    String customerName = data.child("passengerName").getValue(String.class);
                                    String destinationAddress = data.child("destinationLocation").getValue(String.class);

                                    Object priceObj = data.child("totalPrice").getValue();
                                    String priceText = (priceObj != null) ? String.valueOf(priceObj) : "0";

                                    if (recentBookingsQuery != null && radarListener != null) {
                                        recentBookingsQuery.removeEventListener(radarListener);
                                    }

                                    playNotificationEffects();
                                    displayBookingOnMap(customerName, originAddress, destinationAddress, priceText, requestedVehicleType);
                                    return;
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DriverActivity.this, "Lỗi kết nối Radar: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        recentBookingsQuery.addValueEventListener(radarListener);
    }

    private void playNotificationEffects() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(1000);

            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            if (r != null) r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void displayBookingOnMap(String customerName, String originAddress, String destinationAddress, String price, String requestedVehicleType) {
        if (layoutDriverRadarContainer != null) layoutDriverRadarContainer.setVisibility(View.GONE);

        Geocoder geocoder = new Geocoder(this, new Locale("vi", "VN"));
        try {
            List<Address> pickupAddresses = geocoder.getFromLocationName(originAddress, 1);
            List<Address> destAddresses = geocoder.getFromLocationName(destinationAddress, 1);

            GeoPoint pickupPoint = new GeoPoint(10.762622, 106.660172);
            GeoPoint destPoint = new GeoPoint(10.7715, 106.6984);

            if (pickupAddresses != null && !pickupAddresses.isEmpty()) {
                Address addr = pickupAddresses.get(0);
                pickupPoint = new GeoPoint(addr.getLatitude(), addr.getLongitude());
            }
            if (destAddresses != null && !destAddresses.isEmpty()) {
                Address addr = destAddresses.get(0);
                destPoint = new GeoPoint(addr.getLatitude(), addr.getLongitude());
            }

            if (driverMapView != null) {
                if (pickupMarker != null) driverMapView.getOverlays().remove(pickupMarker);
                if (destinationMarker != null) driverMapView.getOverlays().remove(destinationMarker);

                pickupMarker = new Marker(driverMapView);
                pickupMarker.setPosition(pickupPoint);
                pickupMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                pickupMarker.setTitle("ĐIỂM ĐÓN: " + originAddress);
                driverMapView.getOverlays().add(pickupMarker);

                destinationMarker = new Marker(driverMapView);
                destinationMarker.setPosition(destPoint);
                destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                destinationMarker.setTitle("ĐIỂM ĐẾN: " + destinationAddress);
                driverMapView.getOverlays().add(destinationMarker);

                driverMapView.invalidate();

                double distanceKm = calculateDistanceInKm(pickupPoint, destPoint);

                if (mapController != null) {
                    mapController.setZoom(14.5);
                    mapController.animateTo(pickupPoint);
                }

                currentDriverRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String realDriverName = snapshot.child("dName").getValue(String.class);
                        String realVehicleName = snapshot.child("dVehicleName").getValue(String.class);
                        String realPlateNumber = snapshot.child("dPlate").getValue(String.class);

                        AlertDialog.Builder builder = new AlertDialog.Builder(DriverActivity.this);
                        builder.setTitle("🎉 Có chuyến đi mới");
                        builder.setMessage(String.format("👤 Khách: %s\n📍 Đón: %s\n🏁 Đến: %s\n🛣️ Quãng đường: %.2f km\n💰 Tổng thu nhập: %s VNĐ\n🛵 Xe yêu cầu: %s",
                                customerName, originAddress, destinationAddress, distanceKm, price, requestedVehicleType));

                        builder.setPositiveButton("NHẬN CUỐC XE", (dialog, which) -> {
                            if (currentBookingId != null) {
                                DatabaseReference specificBookingRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                                        .getReference("bookings").child(currentBookingId);

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("status", "Tài xế đã nhận lịch");
                                updates.put("driverName", (realDriverName == null || realDriverName.isEmpty()) ? "Tài xế" : realDriverName);
                                updates.put("vehicleName", (realVehicleName == null || realVehicleName.isEmpty()) ? "Chưa cập nhật xe" : realVehicleName);
                                updates.put("plateNumber", (realPlateNumber == null || realPlateNumber.isEmpty()) ? "Chưa có biển" : realPlateNumber);
                                updates.put("vehicleType", selectedVehicleType);
                                updates.put("driverEmail", driverEmail);

                                specificBookingRef.updateChildren(updates).addOnSuccessListener(unused -> {
                                    Toast.makeText(DriverActivity.this, "Bạn đã nhận chuyến thành công!", Toast.LENGTH_LONG).show();

                                    tempCustomerName = customerName;
                                    tempPrice = price;
                                    tempDistance = String.format(Locale.US, "%.2f km", distanceKm);

                                    currentTripStatus = TripStatus.GOING_TO_PICKUP;

                                    if (layoutDriverRadarContainer != null) {
                                        layoutDriverRadarContainer.setVisibility(View.GONE);
                                    }

                                    if (btnPickedUp != null) btnPickedUp.setVisibility(View.VISIBLE);
                                    if (btnCompleteTrip != null) btnCompleteTrip.setVisibility(View.GONE);

                                }).addOnFailureListener(e -> {
                                    Toast.makeText(DriverActivity.this, "Lỗi kết nối Firebase, thử lại!", Toast.LENGTH_SHORT).show();
                                    restartScanningProcess();
                                });
                            }
                        });

                        builder.setNegativeButton("Bỏ qua cuốc này", (dialog, which) -> {
                            if (currentBookingId != null && !ignoredBookingIds.contains(currentBookingId)) {
                                ignoredBookingIds.add(currentBookingId);
                            }
                            if (layoutDriverRadarContainer != null) {
                                layoutDriverRadarContainer.setVisibility(View.VISIBLE);
                            }
                            restartScanningProcess();
                        });

                        builder.setCancelable(false);
                        builder.create().show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Không thể định vị địa chỉ!", Toast.LENGTH_SHORT).show();
            restartScanningProcess();
        }
    }

    private void restartScanningProcess() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    startScanningForPassengerFlow(location.getLatitude(), location.getLongitude());
                }
            });
        }
    }

    private void saveTripToFirebaseHistory() {
        if (recentBookingsQuery != null && radarListener != null) {
            recentBookingsQuery.removeEventListener(radarListener);
        }

        DatabaseReference globalHistoryRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("history_trips").push();

        String currentFormattedDateTime = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        Map<String, Object> historyData = new HashMap<>();
        historyData.put("customerName", tempCustomerName.isEmpty() ? "Khách hàng" : tempCustomerName);
        historyData.put("timeCompleted", currentFormattedDateTime);
        historyData.put("totalPrice", tempPrice);
        historyData.put("distance", tempDistance);
        historyData.put("driverEmail", driverEmail);

        globalHistoryRef.setValue(historyData).addOnSuccessListener(unused -> {
            runOnUiThread(() -> {
                try {
                    if (driverMapView != null) {
                        if (pickupMarker != null) driverMapView.getOverlays().remove(pickupMarker);
                        if (destinationMarker != null) driverMapView.getOverlays().remove(destinationMarker);
                        if (currentRoadOverlay != null) driverMapView.getOverlays().remove(currentRoadOverlay);
                        driverMapView.invalidate();
                    }

                    isScanningActive = false;

                    if (layoutDriverMapAndScanning != null) {
                        layoutDriverMapAndScanning.postDelayed(() -> {
                            if (btnCompleteTrip != null) btnCompleteTrip.setVisibility(View.GONE);
                            if (btnPickedUp != null) btnPickedUp.setVisibility(View.GONE);
                            if (layoutDriverMapAndScanning != null) layoutDriverMapAndScanning.setVisibility(View.GONE);
                            if (layoutDriverRadarContainer != null) layoutDriverRadarContainer.setVisibility(View.GONE);
                            if (layoutDriverServiceGrid != null) layoutDriverServiceGrid.setVisibility(View.VISIBLE);
                        }, 50);
                    }
                } catch (Exception e) {
                    if (layoutDriverMapAndScanning != null) layoutDriverMapAndScanning.setVisibility(View.GONE);
                    if (layoutDriverServiceGrid != null) layoutDriverServiceGrid.setVisibility(View.VISIBLE);
                }
            });

            if (currentBookingId != null) {
                FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                        .getReference("bookings").child(currentBookingId)
                        .child("status").setValue("Đã hoàn thành");
            }
            currentBookingId = null;
        }).addOnFailureListener(e -> {
            Toast.makeText(DriverActivity.this, "Lỗi kết nối lịch sử: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (layoutDriverMapAndScanning != null) layoutDriverMapAndScanning.setVisibility(View.GONE);
            if (layoutDriverServiceGrid != null) layoutDriverServiceGrid.setVisibility(View.VISIBLE);
        });
    }

    private void updateDriverLocationOnOSM(Location location) {
        if (driverMapView == null) return;
        GeoPoint currentPoint = new GeoPoint(location.getLatitude(), location.getLongitude());

        if (driverMarker == null) {
            driverMarker = new Marker(driverMapView);
            driverMarker.setTitle("Vị trí thực tế của bạn");
            driverMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            driverMapView.getOverlays().add(driverMarker);
        }

        driverMarker.setPosition(currentPoint);
        driverMapView.invalidate();

        // CẬP NHẬT MỚI: Tự động tính toán vẽ đường dựa trên Trạng thái cuốc xe thực tế
        if (currentTripStatus == TripStatus.GOING_TO_PICKUP && pickupMarker != null) {
            drawRoute(currentPoint, pickupMarker.getPosition());

            double distToPickup = calculateDistanceInKm(currentPoint, pickupMarker.getPosition());
            if (txtRadarStatusSub != null && layoutDriverRadarContainer != null && layoutDriverRadarContainer.getVisibility() == View.VISIBLE) {
                txtRadarStatusSub.setText(String.format(Locale.getDefault(), "Cách điểm đón khách: %.2f km", distToPickup));
            }
        } else if (currentTripStatus == TripStatus.ON_TRIP && destinationMarker != null) {
            drawRoute(currentPoint, destinationMarker.getPosition());
        }

        if (isFirstLocation) {
            if (mapController != null) {
                mapController.setZoom(17.5);
                mapController.setCenter(currentPoint);
            }
            isFirstLocation = false;
        }

        if (driverLocationRef != null) {
            driverLocationRef.child("latitude").setValue(location.getLatitude());
            driverLocationRef.child("longitude").setValue(location.getLongitude());
        }
    }

    private double calculateDistanceBetweenPoints(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private double calculateDistanceInKm(GeoPoint p1, GeoPoint p2) {
        return calculateDistanceBetweenPoints(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude());
    }

    private void stopScanningWorkflow() {
        isScanningActive = false;
        isFirstLocation = true;

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (recentBookingsQuery != null && radarListener != null) {
            recentBookingsQuery.removeEventListener(radarListener);
        }

        if (driverMapView != null) {
            driverMapView.getOverlays().clear();
            driverMarker = null;
            pickupMarker = null;
            destinationMarker = null;
            currentRoadOverlay = null; // CẬP NHẬT MỚI: Reset bộ nhớ đệm nét vẽ
            driverMapView.invalidate();
        }

        if (btnCompleteTrip != null) btnCompleteTrip.setVisibility(View.GONE);
        if (btnPickedUp != null) btnPickedUp.setVisibility(View.GONE);
        if (layoutDriverMapAndScanning != null) layoutDriverMapAndScanning.setVisibility(View.GONE);
        if (layoutDriverRadarContainer != null) layoutDriverRadarContainer.setVisibility(View.GONE);
        if (layoutDriverServiceGrid != null) layoutDriverServiceGrid.setVisibility(View.VISIBLE);

        currentBookingId = null;
        Toast.makeText(this, "Đã hủy quét và quay về màn hình điều khiển chính", Toast.LENGTH_SHORT).show();
    }

    private void openPassengerProfileBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(DriverActivity.this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_driver_profile, null);
        bottomSheetDialog.setContentView(sheetView);

        EditText edtName = sheetView.findViewById(R.id.edtDriverProfileName);
        EditText edtPhone = sheetView.findViewById(R.id.edtDriverProfilePhone);
        EditText edtVehicleName = sheetView.findViewById(R.id.edtDriverProfileVehicleName);
        EditText edtPlate = sheetView.findViewById(R.id.edtDriverProfilePlate);
        Spinner spinnerVehicleType = sheetView.findViewById(R.id.spinnerDriverVehicleType);
        Button btnSave = sheetView.findViewById(R.id.btnDriverProfileSave);

        String[] vehicleTypes = {"Xe Máy", "Xe 4 Chỗ", "Xe 7 Chỗ", "Xe Bus"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(DriverActivity.this, android.R.layout.simple_spinner_dropdown_item, vehicleTypes);
        if (spinnerVehicleType != null) {
            spinnerVehicleType.setAdapter(adapter);
        }

        if (edtPhone != null) {
            edtPhone.setText(driverEmail);
            edtPhone.setEnabled(false);
        }

        if (currentDriverRef != null) {
            currentDriverRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        if (edtName != null) edtName.setText(snapshot.child("dName").getValue(String.class));
                        if (edtVehicleName != null) edtVehicleName.setText(snapshot.child("dVehicleName").getValue(String.class));
                        if (edtPlate != null) edtPlate.setText(snapshot.child("dPlate").getValue(String.class));

                        if (spinnerVehicleType != null) {
                            String savedType = snapshot.child("dVehicleType").getValue(String.class);
                            if (savedType != null) {
                                for (int i = 0; i < vehicleTypes.length; i++) {
                                    if (vehicleTypes[i].equals(savedType)) {
                                        spinnerVehicleType.setSelection(i);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(view -> {
                String name = (edtName != null) ? edtName.getText().toString().trim() : "";
                String vehicleName = (edtVehicleName != null) ? edtVehicleName.getText().toString().trim() : "";
                String plate = (edtPlate != null) ? edtPlate.getText().toString().trim() : "";
                String vehicleType = (spinnerVehicleType != null) ? spinnerVehicleType.getSelectedItem().toString() : "Xe Máy";

                if (name.isEmpty()) {
                    Toast.makeText(DriverActivity.this, "Vui lòng nhập họ và tên tài xế!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Map<String, Object> profileUpdates = new HashMap<>();
                profileUpdates.put("dName", name);
                profileUpdates.put("dVehicleName", vehicleName);
                profileUpdates.put("dPlate", plate);
                profileUpdates.put("dVehicleType", vehicleType);

                if (currentDriverRef != null) {
                    currentDriverRef.updateChildren(profileUpdates).addOnSuccessListener(unused -> {
                        selectedVehicleType = vehicleType;
                        Toast.makeText(DriverActivity.this, "Cập nhật thông tin đối tác thành công!", Toast.LENGTH_SHORT).show();
                        bottomSheetDialog.dismiss();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(DriverActivity.this, "Cập nhật thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
        bottomSheetDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (driverMapView != null) driverMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (driverMapView != null) driverMapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (recentBookingsQuery != null && radarListener != null) {
            recentBookingsQuery.removeEventListener(radarListener);
        }
        if (currentDriverRef != null && driverInfoListener != null) {
            currentDriverRef.removeEventListener(driverInfoListener);
        }
        if (driverLocationRef != null) {
            driverLocationRef.removeValue();
        }
    }

    // CẬP NHẬT MỚI: Tối ưu hóa hàm vẽ đường, tự động xóa nét cũ đè lên bản đồ
    private void drawRoute(GeoPoint startPoint, GeoPoint endPoint) {
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(this, "JMartDriverApp/1.0");
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(startPoint);
                waypoints.add(endPoint);

                Road road = roadManager.getRoad(waypoints);

                if (road.mStatus == Road.STATUS_OK) {
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    runOnUiThread(() -> {
                        if (currentRoadOverlay != null) {
                            driverMapView.getOverlays().remove(currentRoadOverlay);
                        }
                        currentRoadOverlay = roadOverlay;
                        driverMapView.getOverlays().add(currentRoadOverlay);
                        driverMapView.invalidate();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}