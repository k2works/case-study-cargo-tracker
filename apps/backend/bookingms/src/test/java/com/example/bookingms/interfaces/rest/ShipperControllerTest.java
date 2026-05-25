package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.ShipperCommandService;
import com.example.bookingms.application.ShipperQueryService;
import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.model.ShipperType;
import com.example.bookingms.domain.projections.ShipperProjection;
import com.example.bookingms.interfaces.rest.dto.RegisterShipperRequest;
import com.example.bookingms.interfaces.rest.dto.ShipperResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperControllerTest {

    @Mock
    private ShipperCommandService commandService;

    @Mock
    private ShipperQueryService queryService;

    @InjectMocks
    private ShipperController controller;

    @Test
    @DisplayName("US02: 荷主登録時に shipperId 未指定なら UUID が採番される")
    void shipperId未指定でUUIDが採番される() {
        when(commandService.register(any(RegisterShipperCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        RegisterShipperRequest request = new RegisterShipperRequest(
                null,
                ShipperType.INDIVIDUAL,
                "山田太郎",
                "東京都千代田区丸の内 1-1",
                null,
                "千代田区",
                "JP",
                "100-0005",
                "yamada@example.com",
                "03-1234-5678");

        ResponseEntity<Map<String, String>> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        String shipperId = response.getBody().get("shipperId");
        assertThat(shipperId).isNotBlank();

        ArgumentCaptor<RegisterShipperCommand> captor = ArgumentCaptor.forClass(RegisterShipperCommand.class);
        org.mockito.Mockito.verify(commandService).register(captor.capture());
        assertThat(captor.getValue().shipperId()).isEqualTo(shipperId);
    }

    @Test
    @DisplayName("US02: 荷主ID 指定時はそのまま採用される")
    void shipperId指定で同一IDが採用される() {
        when(commandService.register(any(RegisterShipperCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        RegisterShipperRequest request = new RegisterShipperRequest(
                "S-100",
                ShipperType.INDIVIDUAL,
                "山田太郎",
                "東京都千代田区丸の内 1-1",
                null,
                "千代田区",
                "JP",
                "100-0005",
                "yamada@example.com",
                "03-1234-5678");

        ResponseEntity<Map<String, String>> response = controller.register(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("shipperId")).isEqualTo("S-100");
    }

    @Test
    @DisplayName("US02: 荷主が存在しない場合 GET /{id} は 404 を返す")
    void 存在しない荷主は404() {
        when(queryService.findByShipperId("S-999")).thenReturn(null);

        ResponseEntity<ShipperResponse> response = controller.findByShipperId("S-999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US02: email パラメータ指定で重複検出用の一覧を返す")
    void email指定で一覧を返す() {
        ShipperProjection p = new ShipperProjection();
        p.setShipperId("S-001");
        p.setEmail("yamada@example.com");
        p.setShipperType("INDIVIDUAL");
        p.setName("山田太郎");
        when(queryService.findByEmail("yamada@example.com")).thenReturn(List.of(p));

        ResponseEntity<List<ShipperResponse>> response = controller.find("yamada@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).email()).isEqualTo("yamada@example.com");
    }
}
