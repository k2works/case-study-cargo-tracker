package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.SearchShipperUseCase;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.ContractNumber;
import com.example.bookingms.domain.model.DiscountRate;
import java.math.BigDecimal;
import com.example.bookingms.domain.model.Shipper;
import com.example.bookingms.domain.model.ShipperType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 荷主の登録と検索が実際の DB で成立することを確認する。
 *
 * <p>採番・検索の絞り込み・重複検出は、いずれも DB の振る舞いに依存する。ユニットテストの
 * スタブが緑でも、ここが噛み合わなければ営業担当者は 1 件も登録できない。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("荷主の永続化")
class ShipperPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RegisterShipperUseCase useCase;

    @Autowired
    private SearchShipperUseCase searchUseCase;

    @Autowired
    private ShipperRepository repository;

    private RegisterShipperCommand command(String name, String email) {
        return new RegisterShipperCommand(
                ShipperType.INDIVIDUAL, name, email, "東京都千代田区 1-1-1", "03-1234-5678");
    }

    @Test
    @DisplayName("登録すると荷主コードが採番される")
    void assignsShipperCode() {
        RegistrationOutcome outcome = useCase.register(command("採番太郎", "saiban@example.com"));

        Shipper registered = ((RegistrationOutcome.Registered) outcome).shipper();
        // 採番は本番と同じ経路（シーケンス）を通す。自前採番だと他の登録が UNIQUE 制約で落ちる
        assertThat(registered.shipperCode()).matches("SHP-\\d{6}");
        assertThat(registered.id()).isNotNull();
    }

    @Test
    @DisplayName("続けて登録しても荷主コードが衝突しない")
    void assignsDistinctCodes() {
        Shipper first = ((RegistrationOutcome.Registered)
                useCase.register(command("連番一郎", "renban1@example.com"))).shipper();
        Shipper second = ((RegistrationOutcome.Registered)
                useCase.register(command("連番二郎", "renban2@example.com"))).shipper();

        assertThat(first.shipperCode()).isNotEqualTo(second.shipperCode());
    }

    @Test
    @DisplayName("同じメールアドレスは既存の荷主として提示する")
    void detectsDuplicate() {
        useCase.register(command("重複太郎", "duplicate@example.com"));

        RegistrationOutcome outcome =
                useCase.register(command("重複太郎（別入力）", "duplicate@example.com"));

        assertThat(outcome).isInstanceOf(RegistrationOutcome.DuplicateFound.class);
        assertThat(((RegistrationOutcome.DuplicateFound) outcome).existing().name())
                .isEqualTo("重複太郎");
    }

    @Test
    @DisplayName("それでも新規で登録すると 2 件目として保存される")
    void registersAnyway() {
        useCase.register(command("あえて太郎", "anyway@example.com"));

        useCase.registerAnyway(command("あえて太郎（本社）", "anyway@example.com"));

        assertThat(repository.search("あえて太郎")).hasSize(2);
    }

    @Test
    @DisplayName("氏名でもメールアドレスでも探せる")
    void searchesByNameOrEmail() {
        searchUseCase.search("");
        useCase.register(command("検索花子", "kensaku@example.com"));

        assertThat(repository.search("検索花子")).hasSize(1);
        assertThat(repository.search("kensaku@example.com")).hasSize(1);
        // 大文字小文字を区別すると、入力の揺れで見つからない荷主が出る
        assertThat(repository.search("KENSAKU@EXAMPLE.COM")).hasSize(1);
    }

    @Test
    @DisplayName("一覧は新しい順に返す")
    void listsNewestFirst() {
        useCase.register(command("並び順 一郎", "order1@example.com"));
        Shipper second = ((RegistrationOutcome.Registered)
                useCase.register(command("並び順 二郎", "order2@example.com"))).shipper();

        // 登録した直後に一覧へ戻って確かめるのが営業の使い方。最下部に沈むと誰も戻らなくなる
        assertThat(repository.search(null).get(0).shipperCode()).isEqualTo(second.shipperCode());
    }

    @Test
    @DisplayName("同一メールが複数あるとき、提示するのは最初に登録された荷主")
    void presentsOldestOnDuplicate() {
        Shipper first = ((RegistrationOutcome.Registered)
                useCase.register(command("先に登録", "same@example.com"))).shipper();
        useCase.registerAnyway(command("後から登録", "same@example.com"));

        RegistrationOutcome outcome = useCase.register(command("三番目", "same@example.com"));

        // 毎回違う「既存」が出ると、営業は何を基準に選べばよいか分からなくなる
        assertThat(((RegistrationOutcome.DuplicateFound) outcome).existing().shipperCode())
                .isEqualTo(first.shipperCode());
    }

    @Test
    @DisplayName("キーワードを指定しなければ全件を返す")
    void returnsAllWithoutKeyword() {
        useCase.register(command("全件太郎", "all@example.com"));

        List<Shipper> all = repository.search(null);

        assertThat(all).isNotEmpty();
    }

    @Test
    @DisplayName("法人の契約番号と割引率が保存され、読み戻せる")
    void persistsCorporateContract() {
        RegisterShipperCommand corporate = new RegisterShipperCommand(
                ShipperType.CORPORATE, "契約商事株式会社", "keiyaku@example.com", "東京都中央区", null,
                ContractNumber.of("CN-2026-0500"), DiscountRate.ofPercent(new BigDecimal("12.5")));

        Shipper saved = ((RegistrationOutcome.Registered) useCase.register(corporate)).shipper();
        Shipper reloaded = repository.search("契約商事").get(0);

        assertThat(saved.contractNumber()).contains(ContractNumber.of("CN-2026-0500"));
        assertThat(reloaded.contractNumber())
                .as("契約番号が保存されていない。US22 で全件の追加入力が発生する")
                .contains(ContractNumber.of("CN-2026-0500"));
        assertThat(reloaded.discountRate())
                .as("割合と百分率のどちらかで 100 倍ずれていないか")
                .contains(DiscountRate.ofPercent(new BigDecimal("12.5")));
    }

    @Test
    @DisplayName("割引率が未設定の法人は、読み戻しても未設定のまま（0% にしない）")
    void keepsUnsetDiscountRateUnset() {
        RegisterShipperCommand corporate = new RegisterShipperCommand(
                ShipperType.CORPORATE, "交渉中商事", "kosho@example.com", "東京都港区", null,
                ContractNumber.of("CN-2026-0501"), null);

        useCase.register(corporate);
        Shipper reloaded = repository.search("交渉中商事").get(0);

        // 0% にすると、設定漏れが「割引なしの契約」として通る
        assertThat(reloaded.discountRate()).isEmpty();
    }
}
