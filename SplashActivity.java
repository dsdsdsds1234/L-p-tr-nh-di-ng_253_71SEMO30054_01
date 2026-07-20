package vn.edu.carapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Sử dụng Handler của Java để hoãn 2500ms (2.5 giây) rồi chuyển màn hình
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // Chuyển từ SplashActivity sang MainActivity
                Intent intent = new Intent(SplashActivity.this, AuthActivity.class);
                startActivity(intent);
                finish(); // Đóng SplashActivity để người dùng không bấm Back quay lại được
            }
        }, 2500);
    }
}