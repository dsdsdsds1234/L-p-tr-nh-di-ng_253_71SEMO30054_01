package vn.edu.carapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private TextView txtAuthTitle, txtAuthSubtitle, txtSwitchPrompt, txtSwitchAction;
    private EditText edtName, edtEmail, edtPassword;
    private Button btnSubmit;

    private RadioGroup rgRole;
    private RadioButton rbPassenger, rbDriver;

    private boolean isLoginMode = true;

    // URL Firebase Database cấu hình của hệ thống
    private final String FIREBASE_URL = "https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // 1. Ánh xạ các thành phần giao diện
        txtAuthTitle = findViewById(R.id.txtAuthTitle);
        txtAuthSubtitle = findViewById(R.id.txtAuthSubtitle);
        txtSwitchPrompt = findViewById(R.id.txtSwitchPrompt);
        txtSwitchAction = findViewById(R.id.txtSwitchAction);
        edtName = findViewById(R.id.edtName);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnSubmit = findViewById(R.id.btnSubmit);

        rgRole = findViewById(R.id.rgRole);
        rbPassenger = findViewById(R.id.rbPassenger);
        rbDriver = findViewById(R.id.rbDriver);

        // 2. Sự kiện chuyển đổi trạng thái giữa Đăng nhập <-> Đăng ký
        if (txtSwitchAction != null) {
            txtSwitchAction.setOnClickListener(v -> {
                isLoginMode = !isLoginMode;
                switchMode(isLoginMode);
            });
        }

        // 3. Sự kiện bấm nút Đăng nhập / Đăng ký
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> handleFormSubmit());
        }

        // Đặt trạng thái giao diện ban đầu
        switchMode(isLoginMode);
    }

    private void switchMode(boolean isLogin) {
        if (txtAuthTitle == null || txtAuthSubtitle == null || edtName == null ||
                btnSubmit == null || txtSwitchPrompt == null || txtSwitchAction == null) {
            return;
        }

        if (isLogin) {
            txtAuthTitle.setText("Xin chào!");
            txtAuthSubtitle.setText("Đăng nhập để tiếp tục trải nghiệm ứng dụng");
            edtName.setVisibility(View.GONE);
            btnSubmit.setText("ĐĂNG NHẬP");
            txtSwitchPrompt.setText("Bạn chưa có tài khoản? ");
            txtSwitchAction.setText("Đăng ký ngay");
            if (rgRole != null) rgRole.setVisibility(View.VISIBLE); // Hiện chọn vai trò khi đăng nhập
        } else {
            txtAuthTitle.setText("Tạo tài khoản");
            txtAuthSubtitle.setText("Đăng ký rất nhanh chóng và hoàn toàn miễn phí");
            edtName.setVisibility(View.VISIBLE); // Hiện ô điền tên khi đăng ký
            btnSubmit.setText("ĐĂNG KÝ");
            txtSwitchPrompt.setText("Bạn đã có tài khoản? ");
            txtSwitchAction.setText("Đăng nhập");
            if (rgRole != null) rgRole.setVisibility(View.VISIBLE); // Hiện chọn vai trò để phân loại khi Đăng ký thực tế
        }
    }

    private void handleFormSubmit() {
        if (edtEmail == null || edtPassword == null) return;

        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra phân quyền dựa trên RadioButton
        boolean isDriverSelected = (rbDriver != null && rbDriver.isChecked());

        if (isLoginMode) {
            // --- THỰC HIỆN LOGIC XÁC THỰC ĐĂNG NHẬP ---
            if (isDriverSelected) {
                loginDriverFlow(email, password);
            } else {
                loginPassengerFlow(email, password);
            }
        } else {
            // --- THỰC HIỆN LOGIC ĐĂNG KÝ ---
            if (edtName == null) return;
            String name = edtName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Vui lòng điền họ tên để đăng ký", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isDriverSelected) {
                registerDriverFlow(name, email, password);
            } else {
                registerPassengerFlow(name, email, password);
            }
        }
    }

    /**
     * THỨ 1: Xử lý Đăng nhập cho Tài xế - Có xác thực tài khoản & mật khẩu từ Firebase
     */
    private void loginDriverFlow(String email, String password) {
        String encodedEmail = DatabaseHelper.encodeEmail(email);
        DatabaseReference driverNodeRef = FirebaseDatabase.getInstance(FIREBASE_URL)
                .getReference("drivers").child(encodedEmail);

        Toast.makeText(this, "⏳ Đang xác thực tài xế...", Toast.LENGTH_SHORT).show();

        driverNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String dbPassword = snapshot.child("password").getValue(String.class);

                    if (password.equals(dbPassword)) {
                        // Đăng nhập thành công -> Lưu định danh Email hiện hành tránh xung đột giữa các tài xế
                        SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
                        prefs.edit().putString("CURRENT_LOGGED_IN_DRIVER_EMAIL", email).apply();

                        Toast.makeText(AuthActivity.this, "🔓 Đăng nhập Đối tác thành công!", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(AuthActivity.this, DriverActivity.class);
                        intent.putExtra("USER_EMAIL", email);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(AuthActivity.this, "❌ Sai mật khẩu, vui lòng kiểm tra lại!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(AuthActivity.this, "❌ Tài khoản tài xế không tồn tại trên hệ thống!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AuthActivity.this, "Lỗi Firebase: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * THỨ 2: Xử lý Đăng ký cho Tài xế - Tạo tài khoản thực và thiết lập Ví tiền 0đ, Lịch sử trống
     */
    private void registerDriverFlow(String name, String email, String password) {
        String encodedEmail = DatabaseHelper.encodeEmail(email);
        DatabaseReference driverNodeRef = FirebaseDatabase.getInstance(FIREBASE_URL)
                .getReference("drivers").child(encodedEmail);

        driverNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(AuthActivity.this, "❌ Email tài xế này đã được đăng ký trước đó!", Toast.LENGTH_SHORT).show();
                } else {
                    // THỨ 3: Khởi tạo cấu trúc thông tin trống cho tài xế mới hoàn toàn
                    Map<String, Object> driverMap = new HashMap<>();
                    driverMap.put("email", email);
                    driverMap.put("password", password);
                    driverMap.put("dName", name); // Lưu tên gốc đăng ký
                    driverMap.put("dVehicleName", ""); // Mới đăng ký: chưa có phương tiện cụ thể
                    driverMap.put("dPlate", ""); // Mới đăng ký: chưa có biển số
                    driverMap.put("dVehicleType", "Xe Máy"); // Mặc định phân loại ban đầu

                    // Khởi tạo các thông số nghiệp vụ tài chính và công việc về bằng 0
                    driverMap.put("walletBalance", 0);
                    driverMap.put("checkInCount", 0);

                    // Đồng bộ lưu lên Server Realtime Database
                    driverNodeRef.setValue(driverMap).addOnSuccessListener(unused -> {
                        Toast.makeText(AuthActivity.this, "🎉 Đăng ký tài xế mới thành công! Vui lòng đăng nhập.", Toast.LENGTH_LONG).show();
                        isLoginMode = true;
                        switchMode(isLoginMode);
                    }).addOnFailureListener(e -> {
                        Toast.makeText(AuthActivity.this, "Lỗi đăng ký: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    /**
     * Mở rộng: Xử lý Đăng nhập cho Hành khách tương tự qua Node "passengers"
     */
    private void loginPassengerFlow(String email, String password) {
        String encodedEmail = DatabaseHelper.encodeEmail(email);
        DatabaseReference passengerNodeRef = FirebaseDatabase.getInstance(FIREBASE_URL)
                .getReference("passengers").child(encodedEmail);

        passengerNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String dbPassword = snapshot.child("password").getValue(String.class);
                    if (password.equals(dbPassword)) {
                        String dbName = snapshot.child("name").getValue(String.class);

                        SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("uPhone", email);
                        editor.putString("uName", (dbName == null || dbName.isEmpty()) ? "Hành khách" : dbName);
                        editor.apply();

                        Toast.makeText(AuthActivity.this, "👋 Đăng nhập Hành khách thành công!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(AuthActivity.this, "❌ Sai mật khẩu Hành khách!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(AuthActivity.this, "❌ Tài khoản hành khách chưa tồn tại!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    /**
     * Mở rộng: Xử lý Đăng ký cho Hành khách
     */
    private void registerPassengerFlow(String name, String email, String password) {
        String encodedEmail = DatabaseHelper.encodeEmail(email);
        DatabaseReference passengerNodeRef = FirebaseDatabase.getInstance(FIREBASE_URL)
                .getReference("passengers").child(encodedEmail);

        passengerNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(AuthActivity.this, "❌ Email hành khách này đã được sử dụng!", Toast.LENGTH_SHORT).show();
                } else {
                    Map<String, Object> passengerMap = new HashMap<>();
                    passengerMap.put("name", name);
                    passengerMap.put("email", email);
                    passengerMap.put("password", password);

                    passengerNodeRef.setValue(passengerMap).addOnSuccessListener(unused -> {
                        Toast.makeText(AuthActivity.this, "🎉 Đăng ký hành khách thành công!", Toast.LENGTH_SHORT).show();
                        isLoginMode = true;
                        switchMode(isLoginMode);
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}