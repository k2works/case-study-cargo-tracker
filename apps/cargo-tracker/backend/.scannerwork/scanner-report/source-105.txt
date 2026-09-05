package com.example.cargotracker.booking;

import com.example.cargotracker.shared.docs.SchemaErdGenerator;
import org.junit.jupiter.api.Test;

/**
 * bookingms の実スキーマから ER 図を生成する。
 *
 * <p><b>これはテストではなくドキュメント生成である。</b> 何も検証しないため通常の
 * {@code test} タスクからは除外し、{@code ./gradlew :bookingms:jigErd} でのみ実行する。
 * 実行には Docker（Testcontainers）と Graphviz（{@code dot}）が要る。</p>
 *
 * <p>出力先と接頭辞は {@code src/test/resources/jig.properties} が決める。</p>
 */
class SchemaErdDocument {

    @Test
    void 実スキーマからER図を生成する() {
        SchemaErdGenerator.generate("booking_read_db");
    }
}
