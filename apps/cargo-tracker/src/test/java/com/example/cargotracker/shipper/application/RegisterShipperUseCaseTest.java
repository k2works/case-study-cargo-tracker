package com.example.cargotracker.shipper.application;

import com.example.cargotracker.shipper.application.command.RegisterShipperCommand;
import com.example.cargotracker.shipper.application.command.RegisterShipperUseCase;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.*;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterShipperUseCase")
class RegisterShipperUseCaseTest {

    @Mock
    private ShipperRepository shipperRepository;

    private RegisterShipperUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterShipperUseCase(shipperRepository);
    }

    @Test
    @DisplayName("個人荷主を登録すると荷主 ID が返される")
    void registerIndividualShipperReturnsId() {
        when(shipperRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        RegisterShipperCommand command = new RegisterShipperCommand(
                "山田 太郎", "test@example.com", "090-0000-0000",
                CustomerCategory.INDIVIDUAL, null, null);

        ShipperId result = useCase.execute(command);

        assertThat(result).isNotNull();
        verify(shipperRepository).save(any(Shipper.class));
    }

    @Test
    @DisplayName("同一メールアドレスが既に登録されている場合は例外を投げる")
    void rejectDuplicateEmail() {
        ShipperId existingId = ShipperId.generate();
        Shipper existing = Shipper.registerIndividual(existingId,
                new ShipperName("既存 荷主"), new ContactInfo("dup@example.com", null));
        when(shipperRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(existing));

        RegisterShipperCommand command = new RegisterShipperCommand(
                "新規 荷主", "dup@example.com", null,
                CustomerCategory.INDIVIDUAL, null, null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DuplicateShipperException.class);
        verify(shipperRepository, never()).save(any());
    }

    @Test
    @DisplayName("登録時に重複チェックを行う")
    void checkDuplicateBeforeSave() {
        when(shipperRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        RegisterShipperCommand command = new RegisterShipperCommand(
                "テスト 太郎", "check@example.com", null,
                CustomerCategory.INDIVIDUAL, null, null);

        useCase.execute(command);

        verify(shipperRepository).findByEmail("check@example.com");
    }
}
