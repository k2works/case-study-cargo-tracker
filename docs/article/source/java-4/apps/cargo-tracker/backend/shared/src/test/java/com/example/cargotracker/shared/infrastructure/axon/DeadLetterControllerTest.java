package com.example.cargotracker.shared.infrastructure.axon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 退避したイベントの読み口（S91 / IT15 引き継ぎ 1）。
 *
 * <p>実際の表から読む検査は {@code DeadLetterEndpointIT}（bookingms）にある。
 * ここで見るのは<b>行の読み方</b>——とくに<b>退避した時刻が読めない行で一覧
 * そのものを落とさない</b>ことである。1 行の書式で全体が見えなくなると、
 * 止まっていることに気づく手段まで失う。</p>
 */
class DeadLetterControllerTest {

    /** 行を 1 つ読ませる（列は Axon が書く形）。 */
    @SuppressWarnings("unchecked")
    private static DeadLetterController.DeadLetterView read(String enqueuedAt) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("dead_letter_id")).thenReturn("dl-1");
        when(row.getString("processing_group")).thenReturn("booking.projection");
        when(row.getString("sequence_identifier")).thenReturn("B-0001");
        when(row.getString("event_type")).thenReturn("HandlingActivityRegisteredEvent");
        when(row.getString("event_identifier")).thenReturn("evt-1");
        when(row.getString("enqueued_at")).thenReturn(enqueuedAt);
        when(row.getString("cause_type")).thenReturn("org.example.Boom");
        when(row.getString("cause_message")).thenReturn("値が長すぎます");

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });

        var controller = new DeadLetterController(jdbc, mock(DeadLetterRetryEndpoint.class));
        var items = (List<DeadLetterController.DeadLetterView>) controller.list().get("items");
        return items.getFirst();
    }

    @Test
    @DisplayName("止まった理由と列が読める")
    void readsWhatStopped() throws Exception {
        var item = read("2026-09-25T01:20:00Z");

        assertThat(item.causeMessage()).isEqualTo("値が長すぎます");
        assertThat(item.sequenceIdentifier()).isEqualTo("B-0001");
        assertThat(item.enqueuedAt()).isEqualTo(Instant.parse("2026-09-25T01:20:00Z"));
    }

    @Test
    @DisplayName("退避した時刻が無くても一覧は出る")
    void toleratesAMissingTimestamp() throws Exception {
        assertThat(read(null).enqueuedAt()).isNull();
    }

    @Test
    @DisplayName("退避した時刻が読めなくても一覧は出る（1 行で全体を落とさない）")
    void toleratesAnUnreadableTimestamp() throws Exception {
        // **止まっていることに気づく手段まで失わない。** 書式が変わったら
        // 時刻だけが空になる——一覧そのものが 500 になるより、そのほうがよい。
        assertThat(read("いつか").enqueuedAt()).isNull();
    }
}
