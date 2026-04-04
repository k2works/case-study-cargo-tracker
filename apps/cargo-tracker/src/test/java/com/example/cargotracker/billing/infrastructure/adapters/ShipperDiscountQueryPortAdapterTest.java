package com.example.cargotracker.billing.infrastructure.adapters;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContactInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.CorporateContractInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShipperDiscountQueryPortAdapter")
class ShipperDiscountQueryPortAdapterTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ShipperRepository shipperRepository;

    private ShipperDiscountQueryPortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ShipperDiscountQueryPortAdapter(bookingRepository, shipperRepository);
    }

    @Test
    @DisplayName("法人荷主の予約IDから割引率を返す")
    void findDiscountRateByBookingId_法人荷主の予約IDから割引率を返す() {
        // Given
        UUID bookingUuid = UUID.randomUUID();
        UUID shipperUuid = UUID.randomUUID();
        String bookingId = bookingUuid.toString();

        BookingId bId = new BookingId(bookingUuid);
        ShipperId sId = new ShipperId(shipperUuid);

        Booking booking = Booking.reconstitute(
                bId, sId,
                new CargoSpecification(CargoType.GENERAL_CARGO, new BigDecimal("100"), null, null, null, 1, null),
                new TransportCondition("JPTYO", "SGSIN", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)),
                BookingStatus.CONFIRMED);

        CorporateContractInfo contractInfo = new CorporateContractInfo("CONTRACT-001", new BigDecimal("15"));
        Shipper shipper = Shipper.registerCorporate(
                sId,
                new ShipperName("テスト法人"),
                new ContactInfo("test@example.com", "03-1234-5678"),
                contractInfo);

        when(bookingRepository.findById(bId)).thenReturn(Optional.of(booking));
        when(shipperRepository.findById(sId)).thenReturn(Optional.of(shipper));

        // When
        BigDecimal result = adapter.findDiscountRateByBookingId(bookingId);

        // Then
        assertThat(result).isEqualByComparingTo(new BigDecimal("15"));
    }

    @Test
    @DisplayName("個人荷主の場合はゼロを返す")
    void findDiscountRateByBookingId_個人荷主の場合はゼロを返す() {
        // Given
        UUID bookingUuid = UUID.randomUUID();
        UUID shipperUuid = UUID.randomUUID();
        String bookingId = bookingUuid.toString();

        BookingId bId = new BookingId(bookingUuid);
        ShipperId sId = new ShipperId(shipperUuid);

        Booking booking = Booking.reconstitute(
                bId, sId,
                new CargoSpecification(CargoType.GENERAL_CARGO, new BigDecimal("50"), null, null, null, 1, null),
                new TransportCondition("JPTYO", "USLAX", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)),
                BookingStatus.CONFIRMED);

        Shipper shipper = Shipper.registerIndividual(
                sId,
                new ShipperName("個人荷主"),
                new ContactInfo("individual@example.com", "090-1234-5678"));

        when(bookingRepository.findById(bId)).thenReturn(Optional.of(booking));
        when(shipperRepository.findById(sId)).thenReturn(Optional.of(shipper));

        // When
        BigDecimal result = adapter.findDiscountRateByBookingId(bookingId);

        // Then
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("予約が存在しない場合はゼロを返す")
    void findDiscountRateByBookingId_予約が存在しない場合はゼロを返す() {
        // Given
        String bookingId = UUID.randomUUID().toString();
        when(bookingRepository.findById(any())).thenReturn(Optional.empty());

        // When
        BigDecimal result = adapter.findDiscountRateByBookingId(bookingId);

        // Then
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        verify(shipperRepository, never()).findById(any());
    }
}
