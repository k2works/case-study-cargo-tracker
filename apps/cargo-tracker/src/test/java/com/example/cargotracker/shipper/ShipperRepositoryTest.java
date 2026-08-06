package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.Address;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Phone;
import com.example.cargotracker.shipper.domain.model.Shipper;
import com.example.cargotracker.shipper.domain.model.ShipperCode;
import com.example.cargotracker.shipper.domain.model.ShipperName;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 荷主の永続化を実 PostgreSQL で検証する（ADR-003）。 */
class ShipperRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private ShipperRepository repository;

    private Shipper 登録する() {
        ShipperId id = ShipperId.generate();
        Shipper shipper = Shipper.registerIndividual(
                id,
                ShipperCode.of(repository.nextSequence()),
                new ShipperName("山田太朗"),
                new Email("repo-%s@example.com".formatted(id.value())),
                new Phone("06-1234-5678"),
                new Address("JP", "530-0001", "大阪府", "大阪市北区", "梅田 1-1-1"));
        repository.save(shipper);
        return shipper;
    }

    /**
     * 採番が同時登録で重複しないこと（IT1 持ち越し C5）。
     *
     * <p><strong>MAX + 1 の実装では、この検証が「同じトランザクション内で 2 回呼ぶ」だけでも
     * 同じ値を返して落ちる。</strong> シーケンスはトランザクションの外で進むため重複しない。
     */
    @Test
    void 荷主コードの採番は繰り返し呼んでも重複しない() {
        Set<Long> issued = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            assertThat(issued.add(repository.nextSequence()))
                    .as("同じ番号が 2 回採番されると、片方の登録が UNIQUE 制約で失敗する")
                    .isTrue();
        }
    }

    @Test
    void 訂正した内容が読み戻せる() {
        Shipper original = 登録する();
        Shipper loaded = repository.findById(original.id()).orElseThrow();

        assertThat(repository.update(loaded.rename(new ShipperName("山田太郎")))).isTrue();

        assertThat(repository.findById(original.id()).orElseThrow().name().value())
                .isEqualTo("山田太郎");
    }

    @Test
    void 訂正するとバージョンが進む() {
        Shipper original = 登録する();
        Shipper loaded = repository.findById(original.id()).orElseThrow();
        long before = loaded.version();

        repository.update(loaded.rename(new ShipperName("山田太郎")));

        assertThat(repository.findById(original.id()).orElseThrow().version())
                .isEqualTo(before + 1);
    }

    /**
     * 楽観的ロックが<strong>実際に競合を検出する</strong>ことを確認する（IT2 タスク 2-3）。
     *
     * <p>「version カラムがある」ことと「後勝ちを防げている」ことは別である。
     */
    @Test
    void 同時訂正の後勝ちを防ぐ() {
        Shipper original = 登録する();

        Shipper sessionA = repository.findById(original.id()).orElseThrow();
        Shipper sessionB = repository.findById(original.id()).orElseThrow();

        assertThat(repository.update(sessionA.rename(new ShipperName("Aの訂正")))).isTrue();
        assertThat(repository.update(sessionB.rename(new ShipperName("Bの訂正"))))
                .as("先行する訂正があったのに成功すると、B の内容が A の訂正を黙って消す")
                .isFalse();

        assertThat(repository.findById(original.id()).orElseThrow().name().value())
                .isEqualTo("Aの訂正");
    }
}
