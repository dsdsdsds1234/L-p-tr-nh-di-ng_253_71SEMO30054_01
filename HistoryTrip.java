package vn.edu.carapp;

public class HistoryTrip {
    public String customerName;
    public String timeCompleted;
    public String totalPrice;
    public String distance;

    // Hàm dựng bắt buộc cho Firebase
    public HistoryTrip() {}

    public HistoryTrip(String customerName, String timeCompleted, String totalPrice, String distance) {
        this.customerName = customerName;
        this.timeCompleted = timeCompleted;
        this.totalPrice = totalPrice;
        this.distance = distance;
    }
}