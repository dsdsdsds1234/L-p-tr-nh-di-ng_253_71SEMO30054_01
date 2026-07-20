package vn.edu.carapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class BookingResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_result);

        TextView txtResultDriver = findViewById(R.id.txtResultDriver);
        TextView txtResultVehicle = findViewById(R.id.txtResultVehicle);
        TextView txtResultPlate = findViewById(R.id.txtResultPlate);
        Button btnBackToMain = findViewById(R.id.btnBackToMain);

        // Hứng dữ liệu từ Intent gửi qua (đã đồng bộ chính xác Key từ máy tài xế)
        String driverName = getIntent().getStringExtra("driver_name");
        String vehicleName = getIntent().getStringExtra("vehicle_name");
        String plateNumber = getIntent().getStringExtra("plate_number");
        String vehicleType = getIntent().getStringExtra("vehicle_type");
        int duration = getIntent().getIntExtra("duration", 5);

        // Hiển thị lên giao diện công khai cho hành khách xem
        if (driverName == null || driverName.isEmpty()) driverName = "Chưa rõ";
        if (vehicleName == null || vehicleName.isEmpty()) vehicleName = "Chưa rõ";
        if (plateNumber == null || plateNumber.isEmpty()) plateNumber = "Chưa rõ";
        if (vehicleType == null || vehicleType.isEmpty()) vehicleType = "Xe Máy";

        txtResultDriver.setText("👤 Tài xế: " + driverName);
        txtResultVehicle.setText("🚗 Phương tiện: " + vehicleName + " (" + vehicleType + ")");
        txtResultPlate.setText("🔢 Biển số: " + plateNumber + "\n⏱️ Dự kiến đón: " + duration + " phút");

        // Nút bấm quay lại màn hình chính bản đồ
        btnBackToMain.setOnClickListener(v -> finish());
    }
}