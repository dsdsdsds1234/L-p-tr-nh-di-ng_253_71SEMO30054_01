package vn.edu.carapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class WalletActivity extends AppCompatActivity {

    private ImageButton btnBackWallet, btnPreviousMonth, btnNextMonth;
    private TextView txtCurrentMonthDisplay;
    private TextView txtNetIncome, txtTotalGross, txtAppFee, txtTaxFee, txtNetIncomeSub;

    private DatabaseReference historyRef;
    private ValueEventListener historyListener;

    private Calendar currentSelectedCalendar;
    private DataSnapshot latestFirebaseSnapshot = null;
    private String currentDriverEmail = ""; // Biến lưu email tài xế hiện tại

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        // Lấy Email tài xế đang đăng nhập từ bộ nhớ tạm để phục vụ tính toán doanh thu cá nhân
        SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
        currentDriverEmail = prefs.getString("CURRENT_LOGGED_IN_DRIVER_EMAIL", "");

        currentSelectedCalendar = Calendar.getInstance();

        btnBackWallet = findViewById(R.id.btnBackWallet);
        btnPreviousMonth = findViewById(R.id.btnPreviousMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        txtCurrentMonthDisplay = findViewById(R.id.txtCurrentMonthDisplay);

        txtNetIncome = findViewById(R.id.txtNetIncome);
        txtTotalGross = findViewById(R.id.txtTotalGross);
        txtAppFee = findViewById(R.id.txtAppFee);
        txtTaxFee = findViewById(R.id.txtTaxFee);
        txtNetIncomeSub = findViewById(R.id.txtNetIncomeSub);

        updateMonthDisplayLabel();

        if (btnBackWallet != null) btnBackWallet.setOnClickListener(v -> finish());

        if (btnPreviousMonth != null) {
            btnPreviousMonth.setOnClickListener(v -> {
                currentSelectedCalendar.add(Calendar.MONTH, -1);
                updateMonthDisplayLabel();
                filterAndDisplayEarnings();
            });
        }

        if (btnNextMonth != null) {
            btnNextMonth.setOnClickListener(v -> {
                currentSelectedCalendar.add(Calendar.MONTH, 1);
                updateMonthDisplayLabel();
                filterAndDisplayEarnings();
            });
        }

        historyRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("history_trips");

        startFirebaseListening();
    }

    private void updateMonthDisplayLabel() {
        if (txtCurrentMonthDisplay != null) {
            SimpleDateFormat monthYearFormat = new SimpleDateFormat("'Tháng' MM / yyyy", Locale.getDefault());
            txtCurrentMonthDisplay.setText(monthYearFormat.format(currentSelectedCalendar.getTime()));
        }
    }

    private void startFirebaseListening() {
        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                latestFirebaseSnapshot = snapshot;
                filterAndDisplayEarnings();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(WalletActivity.this, "Lỗi kết nối lịch sử: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        historyRef.addValueEventListener(historyListener);
    }

    private void filterAndDisplayEarnings() {
        if (latestFirebaseSnapshot == null) return;

        double totalGross = 0;

        String targetMonthYear = new SimpleDateFormat("MM/yyyy", Locale.getDefault())
                .format(currentSelectedCalendar.getTime());

        for (DataSnapshot data : latestFirebaseSnapshot.getChildren()) {
            String timeCompleted = data.child("timeCompleted").getValue(String.class);
            Object priceObj = data.child("totalPrice").getValue();
            // ĐỌC EMAIL ĐỂ LỌC DOANH THU BIÊN PHÂN PHỐI
            String tripDriverEmail = data.child("driverEmail").getValue(String.class);

            // THÊM ĐIỀU KIỆN ĐÚNG TÀI XẾ: Phải trùng khớp driverEmail thì mới tính tiền vào tổng
            if (currentDriverEmail != null && currentDriverEmail.equals(tripDriverEmail)
                    && timeCompleted != null && timeCompleted.contains(targetMonthYear) && priceObj != null) {
                try {
                    String priceStr = String.valueOf(priceObj).replaceAll("[^0-9]", "");
                    if (!priceStr.isEmpty()) {
                        totalGross += Double.parseDouble(priceStr);
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }

        double appFee = totalGross * 0.20;
        double taxFee = totalGross * 0.015;
        double netIncome = totalGross - appFee - taxFee;

        DecimalFormat formatter = new DecimalFormat("#,###");

        if (totalGross == 0) {
            txtTotalGross.setText("0đ");
            txtAppFee.setText("-0đ");
            txtTaxFee.setText("-0đ");
            txtNetIncome.setText("0đ");
            txtNetIncomeSub.setText("0đ");
        } else {
            txtTotalGross.setText(formatter.format(totalGross) + "đ");
            txtAppFee.setText("-" + formatter.format(appFee) + "đ");
            txtTaxFee.setText("-" + formatter.format(taxFee) + "đ");
            txtNetIncome.setText(formatter.format(netIncome) + "đ");
            txtNetIncomeSub.setText(formatter.format(netIncome) + "đ");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (historyRef != null && historyListener != null) {
            historyRef.removeEventListener(historyListener);
        }
    }
}