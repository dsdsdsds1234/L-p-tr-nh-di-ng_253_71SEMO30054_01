package vn.edu.carapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 105;

    private ImageView imgProfileAvatar;
    private EditText edtProfileName, edtProfilePhone;
    private Spinner spinnerProfileLang;
    private Button btnProfileSave;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Ánh xạ thành phần giao diện
        imgProfileAvatar = findViewById(R.id.imgProfileAvatar);
        edtProfileName = findViewById(R.id.edtProfileName);
        edtProfilePhone = findViewById(R.id.edtProfilePhone);
        spinnerProfileLang = findViewById(R.id.spinnerProfileLang);
        btnProfileSave = findViewById(R.id.btnProfileSave);

        sharedPreferences = getSharedPreferences("GrabUserPrefs", MODE_PRIVATE);

        // Đổ dữ liệu Ngôn ngữ vào Spinner
        String[] languages = {"Tiếng Việt", "English"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        spinnerProfileLang.setAdapter(adapter);

        // Tải thông tin người dùng lên giao diện
        loadUserData();

        // Click đổi ảnh đại diện từ thư viện điện thoại
        imgProfileAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        // Click Lưu thay đổi thông tin
        btnProfileSave.setOnClickListener(v -> {
            String inputName = edtProfileName.getText().toString().trim();
            String inputPhone = edtProfilePhone.getText().toString().trim();
            String inputLang = spinnerProfileLang.getSelectedItem().toString();

            if (inputName.isEmpty() || inputPhone.isEmpty()) {
                Toast.makeText(this, "Hãy điền đầy đủ tên và SĐT!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Lưu dữ liệu vào SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("uName", inputName);
            editor.putString("uPhone", inputPhone);
            editor.putString("uLang", inputLang);
            editor.apply();

            Toast.makeText(this, "Đã cập nhật hồ sơ cá nhân!", Toast.LENGTH_SHORT).show();
            finish(); // Hoàn thành xong đóng Activity quay lại Bản đồ
            overridePendingTransition(0, 0); // Để Theme tự xử lý hiệu ứng trượt xuống
        });
    }

    private void loadUserData() {
        String savedName = sharedPreferences.getString("uName", "Hành khách");
        String savedPhone = sharedPreferences.getString("uPhone", "");
        String savedLang = sharedPreferences.getString("uLang", "Tiếng Việt");
        String savedAvatarStr = sharedPreferences.getString("uAvatar", "");

        edtProfileName.setText(savedName);
        edtProfilePhone.setText(savedPhone);
        spinnerProfileLang.setSelection(savedLang.equals("English") ? 1 : 0);

        if (!savedAvatarStr.isEmpty()) {
            imgProfileAvatar.setPadding(0, 0, 0, 0);
            imgProfileAvatar.setImageURI(Uri.parse(savedAvatarStr));
        }
    }

    // Nhận kết quả ảnh đại diện chọn từ thư viện máy
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            imgProfileAvatar.setPadding(0, 0, 0, 0);
            imgProfileAvatar.setImageURI(imageUri);

            // Lưu lâu dài đường dẫn ảnh đại diện
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("uAvatar", imageUri.toString());
            editor.apply();
        }
    }
}