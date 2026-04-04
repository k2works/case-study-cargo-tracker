package com.example.cargotracker.shipper.application;

import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommand;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;
import com.example.cargotracker.shipper.domain.model.repository.ShipperRepository;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterShipperCommandServiceTest {

    @Mock
    private ShipperRepository shipperRepository;

    @InjectMocks
    private RegisterShipperCommandService registerShipperCommandService;

    @Test
    void 個人荷主を登録するとShipperIdが返る() {
        RegisterShipperCommand command = new RegisterShipperCommand(
                "SHP-00000001",
                "山田 太郎",
                "taro@example.com",
                "090-1234-5678",
                ShipperType.INDIVIDUAL,
                null,
                null
        );
        when(shipperRepository.findByEmail(new Email(command.email()))).thenReturn(Optional.empty());

        ShipperId shipperId = registerShipperCommandService.registerShipper(command);

        assertNotNull(shipperId);
        verify(shipperRepository).save(any(Shipper.class));
    }

    @Test
    void 法人荷主を登録するとCorporateShipperが保存される() {
        RegisterShipperCommand command = new RegisterShipperCommand(
                "SHP-00000002",
                "株式会社テスト",
                "corp@example.com",
                "03-1234-5678",
                ShipperType.CORPORATE,
                "CN-001",
                new BigDecimal("0.10")
        );
        when(shipperRepository.findByEmail(new Email(command.email()))).thenReturn(Optional.empty());
        ArgumentCaptor<Shipper> shipperCaptor = ArgumentCaptor.forClass(Shipper.class);

        ShipperId shipperId = registerShipperCommandService.registerShipper(command);

        assertNotNull(shipperId);
        verify(shipperRepository).save(shipperCaptor.capture());
        assertInstanceOf(CorporateShipper.class, shipperCaptor.getValue());
    }

    @Test
    void 重複メールでIllegalStateExceptionが発生する() {
        RegisterShipperCommand command = new RegisterShipperCommand(
                "SHP-00000003",
                "山田 花子",
                "exists@example.com",
                null,
                ShipperType.INDIVIDUAL,
                null,
                null
        );
        Shipper existing = new Shipper(
                new ShipperId(java.util.UUID.randomUUID()),
                new com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode("SHP-EXISTS"),
                new com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName("既存ユーザー"),
                new Email(command.email()),
                null,
                ShipperType.INDIVIDUAL
        );
        when(shipperRepository.findByEmail(new Email(command.email()))).thenReturn(Optional.of(existing));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> registerShipperCommandService.registerShipper(command)
        );

        assertEquals("EMAIL_ALREADY_REGISTERED", exception.getMessage());
        verify(shipperRepository, never()).save(any(Shipper.class));
    }

    @Test
    void shipperTypeがCORPORATEのときcontractNumberを保存する() {
        RegisterShipperCommand command = new RegisterShipperCommand(
                "SHP-00000004",
                "株式会社契約",
                "contract@example.com",
                null,
                ShipperType.CORPORATE,
                "CN-999",
                new BigDecimal("0.05")
        );
        when(shipperRepository.findByEmail(new Email(command.email()))).thenReturn(Optional.empty());
        ArgumentCaptor<Shipper> shipperCaptor = ArgumentCaptor.forClass(Shipper.class);

        registerShipperCommandService.registerShipper(command);

        verify(shipperRepository).save(shipperCaptor.capture());
        CorporateShipper shipper = (CorporateShipper) shipperCaptor.getValue();
        assertEquals(command.contractNumber(), shipper.getContractNumber().getValue());
    }

    @Test
    void discountRateが015を超える場合IllegalArgumentExceptionが発生する() {
        RegisterShipperCommand command = new RegisterShipperCommand(
                "SHP-00000005",
                "株式会社割引",
                "discount@example.com",
                null,
                ShipperType.CORPORATE,
                "CN-150",
                new BigDecimal("0.16")
        );
        when(shipperRepository.findByEmail(new Email(command.email()))).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> registerShipperCommandService.registerShipper(command)
        );
        verify(shipperRepository, never()).save(any(Shipper.class));
    }
}
