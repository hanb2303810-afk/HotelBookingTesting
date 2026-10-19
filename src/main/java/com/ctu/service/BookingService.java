package com.ctu.service;

import com.ctu.exception.PaymentFailedException;
import com.ctu.exception.RoomNotAvailableException;
import com.ctu.model.BookingReceipt;
import com.ctu.model.Room;
import com.ctu.repository.RoomRepository;

import java.util.UUID;

public class BookingService {

    private final RoomRepository roomRepository;
    private final PaymentGateway paymentGateway;

    public BookingService(RoomRepository roomRepository,
                          PaymentGateway paymentGateway) {
        this.roomRepository = roomRepository;
        this.paymentGateway = paymentGateway;
    }

    public BookingReceipt bookRoom(String roomId, int nights, String creditCard) {

        // Kiểm tra tham số đầu vào
        if (roomId == null || nights <= 0
                || creditCard == null || creditCard.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tham so dat phong khong hop le!");
        }

        // Tìm phòng
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotAvailableException(
                        "Khong tim thay phong: " + roomId));

        // Kiểm tra trạng thái phòng
        if (!room.isAvailable()) {
            throw new RoomNotAvailableException(
                    "Phong da duoc dat truoc do: " + roomId);
        }

        // Tính tổng tiền
        double totalAmount = room.getPricePerNight() * nights;

        // Thực hiện thanh toán
        boolean paymentSuccess =
                paymentGateway.charge(creditCard, totalAmount);

        if (!paymentSuccess) {
            throw new PaymentFailedException(
                    "Thanh toan qua the credit card that bai!");
        }

        // Cập nhật trạng thái và lưu phòng
        room.setAvailable(false);
        roomRepository.save(room);

        // Trả về phiếu đặt phòng
        return new BookingReceipt(
                UUID.randomUUID().toString(),
                roomId,
                totalAmount);
    }
}
