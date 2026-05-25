package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.ShipperCommandService;
import com.example.bookingms.application.ShipperQueryService;
import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.model.ShipperType;
import com.example.bookingms.domain.projections.ShipperProjection;
import com.example.bookingms.interfaces.rest.dto.PageRequest;
import com.example.bookingms.interfaces.rest.dto.PageResponse;
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

import java.math.BigDecimal;
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
                "03-1234-5678",
                null,
                null);

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
                "03-1234-5678",
                null,
                null);

        ResponseEntity<Map<String, String>> response = controller.register(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("shipperId")).isEqualTo("S-100");
    }

    @Test
    @DisplayName("US03: 法人荷主登録時に契約番号・割引率が Command に渡される")
    void 法人荷主登録時に契約情報がCommandに渡される() {
        when(commandService.register(any(RegisterShipperCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        RegisterShipperRequest request = new RegisterShipperRequest(
                null,
                ShipperType.CORPORATE,
                "株式会社グローバル商事",
                "東京都港区六本木 6-10-1",
                "ミッドタウンタワー 30F",
                "港区",
                "JP",
                "106-6130",
                "biz@global.example.com",
                "03-5555-0001",
                "CONTRACT-2026-001",
                new BigDecimal("0.150"));

        controller.register(request);

        ArgumentCaptor<RegisterShipperCommand> captor = ArgumentCaptor.forClass(RegisterShipperCommand.class);
        org.mockito.Mockito.verify(commandService).register(captor.capture());
        assertThat(captor.getValue().contractNumber()).isEqualTo("CONTRACT-2026-001");
        assertThat(captor.getValue().discountRate()).isEqualByComparingTo(new BigDecimal("0.150"));
    }

    @Test
    @DisplayName("US02: 荷主が存在しない場合 GET /{id} は 404 を返す")
    void 存在しない荷主は404() {
        when(queryService.findByShipperId("S-999")).thenReturn(null);

        ResponseEntity<ShipperResponse> response = controller.findByShipperId("S-999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US02: /search?email= で重複検出用の一覧を返す")
    void search_email指定で一覧を返す() {
        ShipperProjection p = new ShipperProjection();
        p.setShipperId("S-001");
        p.setEmail("yamada@example.com");
        p.setShipperType("INDIVIDUAL");
        p.setName("山田太郎");
        when(queryService.findByEmail("yamada@example.com")).thenReturn(List.of(p));

        ResponseEntity<List<ShipperResponse>> response = controller.searchByEmail("yamada@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).email()).isEqualTo("yamada@example.com");
    }

    @Test
    @DisplayName("IT2 ページネーション: GET /shippers は page=0, size=20 のページレスポンスを返す")
    void ページネーション付き荷主一覧を返す() {
        ShipperProjection p = new ShipperProjection();
        p.setShipperId("S-001");
        p.setShipperType("INDIVIDUAL");
        p.setName("山田太郎");
        when(queryService.findAll(new PageRequest(0, 20))).thenReturn(List.of(p));
        when(queryService.count()).thenReturn(1L);

        ResponseEntity<PageResponse<ShipperResponse>> response = controller.findAll(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageResponse<ShipperResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.items()).hasSize(1);
        assertThat(body.totalCount()).isEqualTo(1L);
        assertThat(body.page()).isEqualTo(0);
        assertThat(body.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("IT2 ページネーション: page=3, size=5 が QueryService に渡される")
    void ページパラメータが委譲される() {
        when(queryService.findAll(new PageRequest(3, 5))).thenReturn(List.of());
        when(queryService.count()).thenReturn(0L);

        ResponseEntity<PageResponse<ShipperResponse>> response = controller.findAll(3, 5);

        PageResponse<ShipperResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.page()).isEqualTo(3);
        assertThat(body.size()).isEqualTo(5);
        org.mockito.Mockito.verify(queryService).findAll(new PageRequest(3, 5));
    }
}
