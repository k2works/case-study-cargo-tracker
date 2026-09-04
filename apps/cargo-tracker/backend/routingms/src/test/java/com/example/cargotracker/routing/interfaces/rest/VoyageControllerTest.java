package com.example.cargotracker.routing.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyagesQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageListView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageView;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.MovementRequest;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.PendingResponse;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.RegisterVoyageRequest;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 航海の入出力（S32・S33）。コマンドとクエリへの橋渡しだけを見る。 */
@SuppressWarnings("unchecked")
class VoyageControllerTest {

    private static final Instant DEPART = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant ARRIVE = Instant.parse("2026-09-24T18:00:00Z");

    private CommandGateway commands;
    private QueryGateway queries;
    private VoyageController controller;

    @BeforeEach
    void setUp() {
        commands = mock(CommandGateway.class);
        queries = mock(QueryGateway.class);
        controller = new VoyageController(commands, queries);
    }

    private static RegisterVoyageRequest request(List<String> cargoTypes) {
        return new RegisterVoyageRequest("V-MOL-001", "MOL", "商船三井", "MOL EXPRESS",
                List.of(new MovementRequest("JPTYO", "USNYC", DEPART, ARRIVE)), cargoTypes);
    }

    private static VoyageView view() {
        return new VoyageView("V-MOL-001", "MOL", "商船三井", "MOL EXPRESS", "JPTYO", "USNYC",
                DEPART, ARRIVE, false, List.of("GENERAL"), List.of());
    }

    @Test
    @DisplayName("登録は 201 と Location を返し、コマンドに値を載せる")
    void registers() {
        ResponseEntity<?> response = controller.register(
                request(List.of("GENERAL", "HAZARDOUS")), "routing01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation())
                .hasToString("/api/v1/routing/voyages/V-MOL-001");

        ArgumentCaptor<RegisterVoyageCommand> captor =
                ArgumentCaptor.forClass(RegisterVoyageCommand.class);
        org.mockito.Mockito.verify(commands).sendAndWait(captor.capture());
        RegisterVoyageCommand command = captor.getValue();
        // 表示のためだけに運ぶ値は、どこか一層で潰しても「登録できた」までは緑になる。
        assertThat(command.vesselName().value()).isEqualTo("MOL EXPRESS");
        assertThat(command.schedule().movements()).hasSize(1);
        assertThat(command.acceptedCargoTypes())
                .containsExactlyInAnyOrder(CargoType.GENERAL, CargoType.HAZARDOUS);
        assertThat(command.registeredBy()).isEqualTo("routing01");
    }

    @Test
    @DisplayName("対応貨物種別が未指定なら空で送る（既定は集約が決める）")
    void passesEmptyCargoTypes() {
        controller.register(request(null), null);

        ArgumentCaptor<RegisterVoyageCommand> captor =
                ArgumentCaptor.forClass(RegisterVoyageCommand.class);
        org.mockito.Mockito.verify(commands).sendAndWait(captor.capture());
        assertThat(captor.getValue().acceptedCargoTypes()).isEmpty();
    }

    @Test
    @DisplayName("知らない貨物種別は業務規則違反として断る")
    void rejectsUnknownCargoType() {
        // 黙って無視すると、選んだつもりの種別が落ちて候補から消える。
        assertThatThrownBy(() -> controller.register(request(List.of("UNKNOWN")), null))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("知らない貨物種別");
    }

    @Test
    @DisplayName("投影がまだなら 202 を返す（404 にしない）")
    void returnsPendingWhenNotProjected() {
        when(queries.query(any(), any(Class.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ResponseEntity<?> response = controller.find("V-MOL-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(((PendingResponse) response.getBody()).message()).contains("反映まで");
    }

    @Test
    @DisplayName("投影があれば 200 で返す")
    void returnsVoyage() {
        when(queries.query(any(), any(Class.class)))
                .thenReturn(CompletableFuture.completedFuture(view()));

        ResponseEntity<?> response = controller.find("V-MOL-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((VoyageView) response.getBody()).vesselName()).isEqualTo("MOL EXPRESS");
    }

    @Test
    @DisplayName("一覧の絞り込みは空文字を「絞り込まない」に寄せる")
    void normalizesBlankCargoType() {
        // 空文字のまま渡すと、どの航海にも一致せず一覧が黙って空になる。
        when(queries.query(any(), any(Class.class)))
                .thenReturn(CompletableFuture.completedFuture(new VoyageListView(List.of(), 0)));

        controller.list(0, 50, false, "  ");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(queries).query(captor.capture(), any(Class.class));
        assertThat(((FindVoyagesQuery) captor.getValue()).cargoType()).isNull();
    }

    @Test
    @DisplayName("一覧の絞り込みに知らない種別を渡すと断る")
    void rejectsUnknownFilter() {
        // 0 件は「無い」と読める。入力が誤っていることを伝える。
        assertThatThrownBy(() -> controller.list(0, 50, false, "UNKNOWN"))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
