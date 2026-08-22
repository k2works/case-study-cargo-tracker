package com.example.bookingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.ContractNumber;
import com.example.bookingms.domain.model.CorporateContract;
import com.example.bookingms.domain.model.DiscountRate;
import com.example.bookingms.domain.model.EmailAddress;
import com.example.bookingms.domain.model.Shipper;
import com.example.bookingms.domain.model.ShipperProfile;
import com.example.bookingms.domain.model.ShipperType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 登録済みの荷主の内容を直す（US02 / #550）。 */
@DisplayName("荷主の編集")
class EditShipperUseCaseTest {

    private final List<Shipper> saved = new ArrayList<>();

    private Shipper stored = Shipper.restore(1L, "SHP-000001", ShipperType.CORPORATE,
            ShipperProfile.restore("丸紅商事", "corp@example.com", "東京都千代田区 1-1-1", null),
            new CorporateContract(ContractNumber.of("CN-0001"), null));

    private final ShipperRepository repository = new ShipperRepository() {
        @Override
        public Optional<Shipper> findByEmail(EmailAddress email) {
            return Optional.empty();
        }

        @Override
        public Optional<Shipper> findById(Long id) {
            return id.equals(1L) ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public Shipper save(Shipper shipper) {
            saved.add(shipper);
            return shipper;
        }

        @Override
        public List<Shipper> search(String keyword) {
            return List.of(stored);
        }
    };

    private final EditShipperUseCase useCase = new EditShipperUseCase(repository);

    @Test
    @DisplayName("連絡先と契約情報を直せる")
    void edits() {
        Shipper edited = useCase.edit(1L,
                ShipperProfile.of("丸紅商事株式会社", "sales@example.com", "東京都港区 2-2-2",
                        "03-1111-2222"),
                new CorporateContract(ContractNumber.of("CN-0002"),
                        DiscountRate.ofPercent(new BigDecimal("10")))).orElseThrow();

        assertThat(edited.name()).isEqualTo("丸紅商事株式会社");
        assertThat(edited.email().value()).isEqualTo("sales@example.com");
        // 荷主コードと id は変わらない。変わると、予約から見た荷主が別人になる
        assertThat(edited.shipperCode()).isEqualTo("SHP-000001");
        assertThat(edited.id()).isEqualTo(1L);
        assertThat(saved).hasSize(1);
    }

    @Test
    @DisplayName("居ない荷主を直そうとすると空を返す")
    void returnsEmptyForUnknownShipper() {
        assertThat(useCase.edit(999L,
                ShipperProfile.of("だれか", "who@example.com", "東京都", null), null)).isEmpty();
        assertThat(saved).isEmpty();
    }

    /** 検査は新規登録と同じものを通す。緩い入口から壊れた値が入らないようにする。 */
    @Test
    @DisplayName("法人の契約情報を外そうとすると拒む")
    void rejectsRemovingContractFromCorporate() {
        ShipperProfile profile = ShipperProfile.of("丸紅商事", "corp@example.com", "東京都", null);

        assertThatThrownBy(() -> useCase.edit(1L, profile, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(saved).as("拒んだのに保存している").isEmpty();
    }
}
