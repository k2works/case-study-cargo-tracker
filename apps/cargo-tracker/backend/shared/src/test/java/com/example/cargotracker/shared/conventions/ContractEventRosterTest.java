package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 契約イベントの名簿を固定する（[ADR-0018] 決定 2・3）。
 *
 * <p><b>本数だけでは守れない。</b> `architecture_backend.md` は「契約に置くイベントは
 * 11 本」と書き、「名簿は ArchUnit で固定する」とも書いていたが、<b>名簿の検査は
 * 実在しなかった</b>（IT16 の着手前に発見）。本数だけを数えると、1 本足して 1 本
 * 消せば通る——**うっかり契約へ移したイベント**が素通りする。</p>
 *
 * <p><b>契約は版を上げるのに Upcaster が要る。</b> 読む相手のいないイベントを
 * 契約に置くと、形を変えられない重荷になる。置くかどうかは 1 本ずつの判断なので、
 * 名前で固定する。</p>
 *
 * <p>足すときは、<b>読む相手（購読するサービス）を決めてから</b>この一覧に足す。</p>
 */
class ContractEventRosterTest {

    private static final Path CONTRACT_EVENTS = Path.of("src/main/java/com/example/"
            + "cargotracker/shared/contract/event");

    /**
     * 契約に置くイベント。<b>読む相手がいるものだけ</b>。
     *
     * <p>`TrackingClosedEvent` はここに<b>入らない</b>——閉じたことを読むサービスが
     * 無いので、trackingms の内部イベントのままにする（[ADR-0018] 決定 3）。</p>
     */
    private static final List<String> ROSTER = List.of(
            "CargoCancelledEvent",
            "CargoDeliveredEvent",
            "CargoDeliveryRevertedEvent",
            "CargoQuotedEvent",
            "CustomsStatusChangedEvent",
            "HandlingActivityRegisteredEvent",
            "HandlingActivityVoidedEvent",
            "PaymentRecordedEvent",
            "PaymentVoidedEvent",
            "ShipperRegisteredEvent",
            "TrackingInitializedEvent");

    @Test
    @DisplayName("[ADR-0018] 契約イベントは名簿どおり（本数ではなく名前で固定する）")
    void contractEventsMatchTheRoster() throws IOException {
        List<String> actual;
        try (Stream<Path> files = Files.list(CONTRACT_EVENTS)) {
            actual = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .filter(name -> !"package-info.java".equals(name))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .sorted()
                    .toList();
        }

        assertThat(actual)
                .as("契約イベントを 1 つも読めていない（検査が空振りしている）")
                .isNotEmpty();
        assertThat(actual)
                .as("契約に置くイベントは名簿どおりにする。**足すなら、読む相手を"
                        + "決めてから名簿に足す**——契約は版を上げるのに Upcaster が要る")
                .containsExactlyElementsOf(ROSTER.stream().sorted().toList());
    }
}
