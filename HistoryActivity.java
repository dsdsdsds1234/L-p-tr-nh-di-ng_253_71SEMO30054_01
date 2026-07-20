package vn.edu.carapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private ListView lvHistoryTrips;
    private List<HistoryTrip> tripList;
    private DatabaseReference historyRef;
    private HistoryAdapter adapter;
    private String currentDriverEmail = ""; // Biến lưu email tài xế hiện tại

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Lấy Email tài xế đang đăng nhập để phục vụ việc lọc dữ liệu
        SharedPreferences prefs = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);
        currentDriverEmail = prefs.getString("CURRENT_LOGGED_IN_DRIVER_EMAIL", "");

        ImageButton btnBack = findViewById(R.id.btnBackHistory);
        lvHistoryTrips = findViewById(R.id.lvHistoryTrips);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        tripList = new ArrayList<>();
        adapter = new HistoryAdapter();
        if (lvHistoryTrips != null) {
            lvHistoryTrips.setAdapter(adapter);
        }

        historyRef = FirebaseDatabase.getInstance("https://carapp-191b1-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("history_trips");

        historyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tripList.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot data : snapshot.getChildren()) {
                        try {
                            // ĐỌC THÊM TRƯỜNG driverEmail ĐỂ KIỂM TRA ĐIỀU KIỆN LỌC
                            String tripDriverEmail = data.child("driverEmail").getValue(String.class);

                            // Chỉ xử lý cuốc xe nếu nó thuộc về tài xế đang đăng nhập hiện tại
                            if (currentDriverEmail != null && currentDriverEmail.equals(tripDriverEmail)) {
                                String customer = data.child("customerName").getValue() != null ? String.valueOf(data.child("customerName").getValue()) : "Ẩn danh";
                                String time = data.child("timeCompleted").getValue() != null ? String.valueOf(data.child("timeCompleted").getValue()) : "--:--";
                                String price = data.child("totalPrice").getValue() != null ? String.valueOf(data.child("totalPrice").getValue()) : "0";
                                String distance = data.child("distance").getValue() != null ? String.valueOf(data.child("distance").getValue()) : "0.00 km";

                                if (!customer.equals("null") && !time.equals("null")) {
                                    HistoryTrip trip = new HistoryTrip(customer, time, price, distance);
                                    tripList.add(trip);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    Collections.reverse(tripList);
                }

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistoryActivity.this, "Lỗi tải dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class HistoryAdapter extends BaseAdapter {
        @Override
        public int getCount() { return tripList.size(); }
        @Override
        public Object getItem(int position) { return tripList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_history_trip, parent, false);
            }

            HistoryTrip currentTrip = tripList.get(position);

            TextView txtTime = convertView.findViewById(R.id.txtHistoryTime);
            TextView txtPrice = convertView.findViewById(R.id.txtHistoryPrice);
            TextView txtCustomer = convertView.findViewById(R.id.txtHistoryCustomer);
            TextView txtDistance = convertView.findViewById(R.id.txtHistoryDistance);

            if (txtTime != null) txtTime.setText(currentTrip.timeCompleted);
            if (txtPrice != null) txtPrice.setText("+" + currentTrip.totalPrice + "đ");
            if (txtCustomer != null) txtCustomer.setText(currentTrip.customerName);
            if (txtDistance != null) txtDistance.setText(currentTrip.distance);

            return convertView;
        }
    }
}