package com.example.cargotracker.simulation.infrastructure.api;

import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.web.client.RestClient;

/**
 * 工程の検査が共有する段取り（本物の HTTP を通す）。
 *
 * <p><b>モックにしない。</b> 「本番の API を人と同じ順で叩く」という決定
 * （[ADR-0020] 決定 2）は、実際に HTTP を通さなければ確かめられない——
 * 経路の綴り違いも通ってしまう。</p>
 *
 * <p><b>段取りを 1 か所にする。</b> 検査のクラスは行の上限（500 行）で割れるが、
 * 割るたびに段取りを写すと、片方だけが正しくなる。</p>
 */
abstract class GatewayStepsTestSupport {

    private HttpServer server;
    final List<String> requests = new ArrayList<>();
    final List<String> bodies = new ArrayList<>();
    final Map<String, String> responses = new HashMap<>();
    final Map<String, Integer> statuses = new HashMap<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    GatewayBusinessApi start(Scenario scenario) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + path
                    + " auth=" + exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(exchange.getRequestMethod() + " " + path + " " + body);
            byte[] bytes = responses.getOrDefault(path, "{}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statuses.getOrDefault(path, 200), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
        responses.put("/api/v1/auth/login", "{\"token\":\"t-1\"}");
        return new GatewayBusinessApi(new GatewayCalls(client, new GatewayTokens(client)),
                ScenarioInput.standard(scenario),
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC), 1);
    }

    /** 起票した例外の URI（追跡番号と例外 ID は下の検査で固定する）。 */
    static final String TRACKING = "TRK-0000000001";
    static final String EXCEPTION = "exc-1";

    /** 便が通っている港（誤配の記録先はここから選ばれる）。 */
    void servedPorts(String... ports) {
        StringBuilder movements = new StringBuilder();
        for (int i = 0; i + 1 < ports.length; i++) {
            movements.append(i == 0 ? "" : ",")
                    .append("{\"departureUnLocode\":\"").append(ports[i])
                    .append("\",\"arrivalUnLocode\":\"").append(ports[i + 1]).append("\"}");
        }
        responses.put("/api/v1/routing/voyages",
                "{\"items\":[{\"movements\":[" + movements + "]}]}");
    }

    static Map<StepKind, String> inTransit() {
        var produced = new java.util.LinkedHashMap<StepKind, String>();
        produced.put(StepKind.REGISTER_SHIPPER, "SHP-1");
        produced.put(StepKind.REGISTER_BOOKING, "B-1");
        produced.put(StepKind.ISSUE_TRACKING_NUMBER, TRACKING);
        return produced;
    }
}
