
package com.ctu.service;

import com.ctu.exception.PaymentFailedException;
import com.ctu.exception.RoomNotAvailableException;
import com.ctu.model.BookingReceipt;
import com.ctu.model.Room;
import com.ctu.repository.RoomRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Test Suite")
class BookingServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private BookingService bookingService;

    private Room availableRoom;

    @BeforeEach
    void setUp() {
        availableRoom = new Room("R101", 500000, true);
    }

    @Test
    @DisplayName("Đặt phòng thành công")
    void testBookRoom_Success() {

        // Arrange
        when(roomRepository.findById("R101"))
                .thenReturn(Optional.of(availableRoom));

        when(paymentGateway.charge("123456789", 1500000))
                .thenReturn(true);

        // Act
        BookingReceipt receipt =
                bookingService.bookRoom("R101", 3, "123456789");

        // Assert
        assertNotNull(receipt);
        assertNotNull(receipt.getBookingId());
        assertEquals("R101", receipt.getRoomId());
        assertEquals(1500000, receipt.getTotalAmount(), 0.001);
        assertFalse(availableRoom.isAvailable());

        verify(roomRepository, times(1)).findById("R101");
        verify(paymentGateway, times(1))
                .charge("123456789", 1500000);
        verify(roomRepository, times(1)).save(availableRoom);
    }

    @Test
    @DisplayName("Không tìm thấy phòng")
    void testBookRoom_RoomNotFound_ThrowsException() {

        // Arrange
        when(roomRepository.findById("R999"))
                .thenReturn(Optional.empty());

        // Act & Assert
        RoomNotAvailableException exception = assertThrows(
                RoomNotAvailableException.class,
                () -> bookingService.bookRoom("R999", 1, "123456789")
        );

        assertEquals("Khong tim thay phong: R999",
                exception.getMessage());

        verify(roomRepository, times(1)).findById("R999");
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("Phòng đã được đặt trước")
    void testBookRoom_RoomAlreadyBooked_ThrowsException() {

        // Arrange
        Room bookedRoom = new Room("R102", 500000, false);

        when(roomRepository.findById("R102"))
                .thenReturn(Optional.of(bookedRoom));

        // Act & Assert
        RoomNotAvailableException exception = assertThrows(
                RoomNotAvailableException.class,
                () -> bookingService.bookRoom("R102", 1, "123456789")
        );

        assertEquals("Phong da duoc dat truoc do: R102",
                exception.getMessage());

        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any(Room.class));
        assertFalse(bookedRoom.isAvailable());
    }

    @Test
    @DisplayName("Thanh toán thất bại")
    void testBookRoom_PaymentFailed_ThrowsException() {

        // Arrange
        when(roomRepository.findById("R101"))
                .thenReturn(Optional.of(availableRoom));

        when(paymentGateway.charge("123456789", 500000))
                .thenReturn(false);

        // Act & Assert
        PaymentFailedException exception = assertThrows(
                PaymentFailedException.class,
                () -> bookingService.bookRoom("R101", 1, "123456789")
        );

        assertEquals(
                "Thanh toan qua the credit card that bai!",
                exception.getMessage()
        );

        verify(paymentGateway, times(1))
                .charge("123456789", 500000);
        verify(roomRepository, never()).save(any(Room.class));

        // Thanh toán thất bại thì phòng vẫn còn trống
        assertTrue(availableRoom.isAvailable());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("Số đêm không hợp lệ")
    void testBookRoom_InvalidNights_ThrowsException(int nights) {

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom("R101", nights, "123456789")
        );

        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @Test
    @DisplayName("Mã phòng null")
    void testBookRoom_NullRoomId_ThrowsException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom(null, 1, "123456789")
        );

        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @Test
    @DisplayName("Thẻ tín dụng null")
    void testBookRoom_NullCreditCard_ThrowsException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom("R101", 1, null)
        );

        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Thẻ tín dụng rỗng hoặc chỉ chứa khoảng trắng")
    void testBookRoom_EmptyCreditCard_ThrowsException(String creditCard) {

        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom("R101", 1, creditCard)
        );

        verifyNoInteractions(roomRepository, paymentGateway);
    }
}

