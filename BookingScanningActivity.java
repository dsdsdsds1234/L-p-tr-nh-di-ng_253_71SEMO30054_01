package vn.edu.carapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

// IMPORT THƯ VIỆN FIREBASE THỜI GIAN THỰC
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class BookingScanningActivity extends AppCompatActivity {

    private DatabaseReference currentBookingRef;
    private ValueEventListener bookingStatusListener;
    private String bookingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_scanning);

        // Nút hủy tìm xe
        ImageButton btnCancelScanning = findViewById(R.id.btnCancelScanning);

        // Nhận ID cuốc xe được truyền sang từ MainActivity
        Intent intentData = getIntent();
        bookingId = intentData.getStringExtra("booking_id");
        Bundle extras = intentData.getExtras();

        if (bookingId != null) {
            // Trỏ thẳng đến vị trí cuốc xe hiện tại trên cơ sở dữ liệu Firebase
            FirebaseDatabase database = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app");
            currentBookingRef = database.getReference("bookings").child(bookingId);

            // Bắt đầu lắng nghe sự thay đổi trạng thái từ phía tài xế
            startListeningForDriverAcceptance(extras);
        } else {
            Toast.makeText(this, "Lỗi: Không tìm thấy mã chuyến đi!", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Nếu khách bấm nút Hủy, cập nhật trạng thái sang "Đã hủy" trên Firebase trước khi thoát
        if (btnCancelScanning != null) {
            btnCancelScanning.setOnClickListener(v -> {
                if (currentBookingRef != null) {
                    currentBookingRef.child("status").setValue("Hành khách đã hủy");
                }
                finish();
            });
        }
    }

    /**
     * Lắng nghe Realtime: Khi tài xế đổi trạng thái thành "Tài xế đã nhận lịch"
     */
    private void startListeningForDriverAcceptance(Bundle oldExtras) {
        bookingStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String status = snapshot.child("status").getValue(String.class);

                if (status != null && status.equals("Tài xế đã nhận lịch")) {

                    // SỬA TẠI ĐÂY: Gỡ trình lắng nghe ngay lập tức để tránh xung đột vòng lặp sự kiện
                    if (currentBookingRef != null) {
                        currentBookingRef.removeEventListener(this);
                    }

                    // Lấy thông tin tài xế thực tế vừa nhận cuốc từ Firebase (Khớp chuẩn CamelCase của tài xế)
                    String realDriverName = snapshot.child("driverName").getValue(String.class);
                    String realVehicleName = snapshot.child("vehicleName").getValue(String.class);
                    String realPlateNumber = snapshot.child("plateNumber").getValue(String.class);
                    String realVehicleType = snapshot.child("vehicleType").getValue(String.class); // Thêm dòng này để đồng bộ loại xe

                    // Chuyển tiếp dữ liệu sang màn hình hiển thị kết quả thành công
                    Intent intent = new Intent(BookingScanningActivity.this, BookingResultActivity.class);

                    if (oldExtras != null) {
                        intent.putExtras(oldExtras); // Giữ lại thông tin điểm đón, điểm đến cũ
                    }

                    // ĐỒNG BỘ CHÍNH XÁC KEY SANG MÀN HÌNH BOOKING RESULT
                    intent.putExtra("driver_name", realDriverName);
                    intent.putExtra("vehicle_name", realVehicleName);
                    intent.putExtra("plate_number", realPlateNumber);
                    intent.putExtra("vehicle_type", realVehicleType); // Truyền chính xác loại xe (Xe máy/Xe 4 chỗ...) sang
                    intent.putExtra("duration", 5); // Ước tính thời gian tài xế đến (phút)

                    // Điều hướng màn hình chuyển động
                    startActivity(intent);
                    finish(); // Giải phóng hoàn toàn màn hình quét radar tìm kiếm
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(BookingScanningActivity.this, "Lỗi kết nối Firebase!", Toast.LENGTH_SHORT).show();
            }
        };

        currentBookingRef.addValueEventListener(bookingStatusListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Gỡ bỏ bộ lắng nghe Firebase khi thoát hẳn activity để tránh tràn bộ nhớ (Memory Leak)
        if (currentBookingRef != null && bookingStatusListener != null) {
            currentBookingRef.removeEventListener(bookingStatusListener);
        }
    }
}