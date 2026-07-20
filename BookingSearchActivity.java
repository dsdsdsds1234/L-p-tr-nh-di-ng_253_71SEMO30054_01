package vn.edu.carapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class BookingSearchActivity extends AppCompatActivity {

    private EditText edtSearchDestination;
    private LinearLayout btnOpenMapHeader, itemLocation1, itemLocation2, itemLocation3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_search);

        edtSearchDestination = findViewById(R.id.edtSearchDestination);
        btnOpenMapHeader = findViewById(R.id.btnOpenMapHeader);
        itemLocation1 = findViewById(R.id.itemLocation1);
        itemLocation2 = findViewById(R.id.itemLocation2);
        itemLocation3 = findViewById(R.id.itemLocation3);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 1. Ấn nút "Bản đồ" phía trên góc phải
        btnOpenMapHeader.setOnClickListener(v -> {
            Intent intent = new Intent(BookingSearchActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // 2. Nhập trực tiếp địa điểm và nhấn Enter trên bàn phím điện thoại
        edtSearchDestination.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String inputDest = edtSearchDestination.getText().toString().trim();
                if (!inputDest.isEmpty()) {
                    chuyenDenBanDo(inputDest);
                }
                return true;
            }
            return false;
        });

        // 3. Click chọn nhanh các địa điểm lịch sử gợi ý
        itemLocation1.setOnClickListener(v -> chuyenDenBanDo("The Cafe Apartments"));
        itemLocation2.setOnClickListener(v -> chuyenDenBanDo("Crescent Mall"));
        itemLocation3.setOnClickListener(v -> chuyenDenBanDo("Chợ Bến Thành"));
    }

    private void chuyenDenBanDo(String destinationStr) {
        Intent intent = new Intent(BookingSearchActivity.this, MainActivity.class);
        // Gửi chuỗi ký tự địa điểm sang cho MainActivity xử lý Geocoder
        intent.putExtra("EXTRA_DESTINATION", destinationStr);
        startActivity(intent);
    }
}