package vn.edu.carapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CheckInActivity extends AppCompatActivity {

    private ImageButton btnBackCheckIn;
    private Button btnDoCheckIn;
    private ListView listViewCheckIn;

    private DatabaseReference historyTripsRef;
    private DatabaseReference checkInRef;
    private DatabaseReference driversRef; // Quản lý thông tin tài xế để lấy tên thật

    private ArrayList<String> checkInList;
    private ArrayAdapter<String> adapter;
    private int completedTripsTodayCount = 0; // Đổi tên biến để rõ nghĩa: Số cuốc trong ngày hôm nay
    private String currentDriverEmail = ""; // Email tài xế đang đăng nhập
    private boolean hasCheckedInToday = false; // Cờ kiểm tra trạng thái đã điểm danh hôm nay chưa

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkin);

        // Lấy Email của tài xế hiện tại từ SharedPreferences
        SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
        currentDriverEmail = prefs.getString("CURRENT_LOGGED_IN_DRIVER_EMAIL", "");

        btnBackCheckIn = findViewById(R.id.btnBackCheckIn);
        btnDoCheckIn = findViewById(R.id.btnDoCheckIn);
        listViewCheckIn = findViewById(R.id.listViewCheckIn);

        btnBackCheckIn.setOnClickListener(v -> finish());

        // Cấu hình ListView dữ liệu động
        checkInList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, checkInList);
        listViewCheckIn.setAdapter(adapter);

        // Khởi tạo các Node kết nối Database
        historyTripsRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("history_trips");
        checkInRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("checkin_history");
        driversRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("drivers");

        // 1. Kiểm tra số lượng chuyến xe CỦA RIÊNG TÀI XẾ NÀY trong ngày hôm nay
        checkTripsCondition();

        // 2. Tải và hiển thị lịch sử điểm danh Realtime CỦA RIÊNG TÀI XẾ NÀY
        loadCheckInHistory();

        // 3. Xử lý sự kiện bấm Check-in
        btnDoCheckIn.setOnClickListener(v -> performCheckIn());
    }

    private void checkTripsCondition() {
        historyTripsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                completedTripsTodayCount = 0;
                String todayDateStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

                if (snapshot.exists()) {
                    for (DataSnapshot data : snapshot.getChildren()) {
                        String timeCompleted = data.child("timeCompleted").getValue(String.class);
                        String tripDriverEmail = data.child("driverEmail").getValue(String.class);

                        // ĐỒNG BỘ ĐIỀU KIỆN: Phải chạy đúng ngày hôm nay VÀ đúng tài xế đang đăng nhập
                        if (timeCompleted != null && timeCompleted.startsWith(todayDateStr)
                                && currentDriverEmail != null && currentDriverEmail.equals(tripDriverEmail)) {
                            completedTripsTodayCount++;
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void performCheckIn() {
        // 1. Kiểm tra điều kiện ràng buộc tối thiểu 2 cuốc xe của riêng cá nhân trong ngày
        if (completedTripsTodayCount < 2) {
            Toast.makeText(this, "❌ Bạn mới hoàn thành " + completedTripsTodayCount + "/2 cuốc xe cá nhân hôm nay. Chưa đủ điều kiện check-in!", Toast.LENGTH_LONG).show();
            return;
        }

        // 2. Kiểm tra xem hôm nay đã điểm danh chưa
        if (hasCheckedInToday) {
            Toast.makeText(this, "⚠️ Bạn đã thực hiện điểm danh ngày hôm nay rồi!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Đồng bộ hóa phương thức mã hóa email sử dụng lớp Helper DatabaseHelper giống như DriverActivity
        String encodedEmail = DatabaseHelper.encodeEmail(currentDriverEmail);

        // Truy vấn trực tiếp lên Firebase để lấy Tên thật hiện tại của tài xế
        driversRef.child(encodedEmail).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String driverName = "Tài xế"; // Giá trị dự phòng nếu không tìm thấy node

                if (snapshot.exists()) {
                    if (snapshot.hasChild("dName")) {
                        driverName = snapshot.child("dName").getValue(String.class);
                    } else if (snapshot.hasChild("name")) {
                        driverName = snapshot.child("name").getValue(String.class);
                    }
                }

                // Thực hiện ghi dữ liệu điểm danh sau khi đã có tên thật chính xác
                saveCheckInToFirebase(driverName);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CheckInActivity.this, "Lỗi xác thực thông tin tài xế!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveCheckInToFirebase(String driverName) {
        String currentDateTime = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

        // Đóng gói dữ liệu đẩy lên Database kèm driverEmail để sau này lọc danh sách hiển thị
        String key = checkInRef.push().getKey();
        Map<String, Object> checkInData = new HashMap<>();
        checkInData.put("driverName", driverName);
        checkInData.put("checkInTime", currentDateTime);
        checkInData.put("driverEmail", currentDriverEmail); // Lưu thêm email để quản lý lọc

        if (key != null) {
            checkInRef.child(key).setValue(checkInData).addOnSuccessListener(unused -> {
                Toast.makeText(CheckInActivity.this, "🎉 Điểm danh ngày hôm nay thành công!", Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(e -> {
                Toast.makeText(CheckInActivity.this, "Lỗi kết nối database: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void loadCheckInHistory() {
        checkInRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                checkInList.clear();
                hasCheckedInToday = false; // Reset trạng thái trước khi duyệt danh sách
                String todayDateStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

                for (DataSnapshot data : snapshot.getChildren()) {
                    String name = data.child("driverName").getValue(String.class);
                    String time = data.child("checkInTime").getValue(String.class);
                    String email = data.child("driverEmail").getValue(String.class);

                    // CHỈ HIỂN THỊ: Lịch sử điểm danh của chính tài xế đang đăng nhập này
                    if (currentDriverEmail != null && currentDriverEmail.equals(email)) {
                        if (name != null && time != null) {
                            checkInList.add("👤 " + name + "\n⏰ Lịch trình: " + time);

                            // Kiểm tra xem đã có bản ghi điểm danh nào thuộc ngày hôm nay chưa
                            if (time.startsWith(todayDateStr)) {
                                hasCheckedInToday = true;
                            }
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}