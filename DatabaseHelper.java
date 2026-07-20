package vn.edu.carapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "CarApp.db";
    private static final int DATABASE_VERSION = 1;

    // Tên bảng
    public static final String TABLE_DRIVERS = "drivers";

    // Các cột dữ liệu
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_VEHICLE_TYPE = "vehicle_type"; // Xe Máy, Xe 4 Chỗ, Xe 7 Chỗ
    public static final String COLUMN_VEHICLE_NAME = "vehicle_name"; // Toyota Vios, Vision...
    public static final String COLUMN_PLATE = "plate_number";

    // Câu lệnh tạo bảng
    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_DRIVERS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_NAME + " TEXT, " +
                    COLUMN_VEHICLE_TYPE + " TEXT, " +
                    COLUMN_VEHICLE_NAME + " TEXT, " +
                    COLUMN_PLATE + " TEXT" +
                    ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
        // Chèn sẵn dữ liệu mẫu khi tạo CSDL lần đầu tiên
        insertSampleData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DRIVERS);
        onCreate(db);
    }

    // Hàm nạp sẵn danh sách tài xế cố định vào CSDL
    private void insertSampleData(SQLiteDatabase db) {
        // Dữ liệu Xe Máy
        insertDriver(db, "Nguyễn Văn Nam", "Xe Máy", "Honda Wave Alpha", "59L3-123.45");
        insertDriver(db, "Lê Hoàng Hải", "Xe Máy", "Honda Vision", "29V1-456.78");

        // Dữ liệu Xe 4 Chỗ
        insertDriver(db, "Trần Minh Tuấn", "Xe 4 Chỗ", "Toyota Vios", "59A-789.21");
        insertDriver(db, "Phạm Quốc Bảo", "Xe 4 Chỗ", "Honda City", "43C-888.88");

        // Dữ liệu Xe 7 Chỗ
        insertDriver(db, "Vũ Hoàng Long", "Xe 7 Chỗ", "Mitsubishi Xpander", "60B2-999.12");
        insertDriver(db, "Nguyễn Thị Mai", "Xe 7 Chỗ", "Toyota Innova", "51G-555.55");
    }

    private void insertDriver(SQLiteDatabase db, String name, String type, String vName, String plate) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_VEHICLE_TYPE, type);
        values.put(COLUMN_VEHICLE_NAME, vName);
        values.put(COLUMN_PLATE, plate);
        db.insert(TABLE_DRIVERS, null, values);
    }

    /**
     * HÀM MỚI BỔ SUNG: Cho phép gọi từ DriverActivity để chèn tài xế mới vào CSDL
     * Hàm này tự mở kết nối writeable và dùng đúng các hằng số cột có sẵn.
     */
    public long insertDriverPublic(String name, String type, String vName, String plate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_VEHICLE_TYPE, type);
        values.put(COLUMN_VEHICLE_NAME, vName);
        values.put(COLUMN_PLATE, plate);

        long result = db.insert(TABLE_DRIVERS, null, values);
        db.close(); // Đóng kết nối để tránh rò rỉ bộ nhớ
        return result;
    }

    /**
     * Lấy ngẫu nhiên 1 tài xế dựa theo loại xe người dùng chọn từ CSDL SQLite
     */
    public Cursor getRandomDriverByVehicleType(String vehicleType) {
        SQLiteDatabase db = this.getReadableDatabase();
        // Câu lệnh SQL lấy ngẫu nhiên 1 dòng thỏa mãn điều kiện loại xe
        String query = "SELECT * FROM " + TABLE_DRIVERS +
                " WHERE " + COLUMN_VEHICLE_TYPE + " = ?" +
                " ORDER BY RANDOM() LIMIT 1";

        return db.rawQuery(query, new String[]{vehicleType});
    }

    // --- ĐÃ TÍCH HỢP HÀM MỚI VÀO ĐÂY VÀ CÓ THỂ GỌI TỪ BẤT CỨ ĐÂU ---
    /**
     * Hàm mã hóa Email để làm Key hợp lệ trên Firebase Realtime Database
     */
    public static String encodeEmail(String email) {
        if (email == null) return "unknown_driver";
        return email.replace(".", "_");
    }
}