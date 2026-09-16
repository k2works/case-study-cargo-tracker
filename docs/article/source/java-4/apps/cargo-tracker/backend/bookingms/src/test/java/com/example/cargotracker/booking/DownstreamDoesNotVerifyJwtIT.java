package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 後段サービスは JWT の署名を再検証しない（ADR-0001 決定 4 の分担・タスク 4.6）。
 *
 * <p>検証を 2 か所に置くと、どちらが正かが曖昧になり、片方を直したときにもう片方が
 * 置き去りになる。ここで固定するのは「bookingms 単体では認証を要求しない」こと。
 * したがって <b>bookingms を直接外部に晒してはいけない</b>。晒すと認証を素通りする。</p>
 *
 * <p>この分担が壊れる方向は 2 つある。後段が検証を始めたら（このテストが赤になる）
 * 二重検証、Gateway が検証をやめたら（gatewayms のテストが赤になる）素通り。
 * 両側にテストを置いて初めて分担が固定される。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// クラスが終わったらコンテキストを閉じる。閉じないと複数のコンテキストが同時に
// 生きたまま同じ Axon Server にハンドラを登録し、二重登録で起動に失敗する
// （DuplicateQueryHandlerSubscriptionException）。落ちるテストが実行順で変わる。
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DownstreamDoesNotVerifyJwtIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private ResponseEntity<JsonMap> get(String authorization) {
        var request = rest.get().uri("http://localhost:" + port + "/api/v1/booking/shippers");
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("資格情報が無くても後段は 401 を返さない（検証は Gateway の担当）")
    void doesNotRequireAuthentication() {
        assertThat(get(null).getStatusCode())
                .as("後段でも検証すると、どちらが正かが曖昧になる。"
                        + "そのぶん bookingms は外部に晒してはいけない")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("壊れた JWT を付けても後段は判断に使わない")
    void ignoresInvalidToken() {
        assertThat(get("Bearer not-a-valid-jwt").getStatusCode())
                .as("後段が署名を見ていれば 401 になる。見ていないことを固定する")
                .isEqualTo(HttpStatus.OK);
    }
}
