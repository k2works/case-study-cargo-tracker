package com.example.cargotracker.shipper.infrastructure;

import com.example.cargotracker.shipper.domain.model.*;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ShipperRepository 統合テスト")
class ShipperRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private ShipperRepository shipperRepository;

    @Test
    @DisplayName("荷主を保存して ID で取得できる")
    void saveAndFindById() {
        ShipperId id = ShipperId.generate();
        ShipperName name = new ShipperName("田中 太郎");
        ContactInfo contact = new ContactInfo("tanaka@example.com", "03-0000-0000");
        Shipper shipper = Shipper.registerIndividual(id, name, contact);

        shipperRepository.save(shipper);

        Optional<Shipper> found = shipperRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
        assertThat(found.get().getName().value()).isEqualTo("田中 太郎");
        assertThat(found.get().getContactInfo().email()).isEqualTo("tanaka@example.com");
        assertThat(found.get().getCategory()).isEqualTo(CustomerCategory.INDIVIDUAL);
    }

    @Test
    @DisplayName("メールアドレスで荷主を検索できる")
    void findByEmail() {
        ShipperId id = ShipperId.generate();
        Shipper shipper = Shipper.registerIndividual(id, new ShipperName("鈴木 花子"),
                new ContactInfo("suzuki@example.com", null));

        shipperRepository.save(shipper);

        Optional<Shipper> found = shipperRepository.findByEmail("suzuki@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("存在しない ID の場合は空の Optional を返す")
    void findByIdNotFound() {
        Optional<Shipper> found = shipperRepository.findById(ShipperId.generate());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("存在しないメールアドレスの場合は空の Optional を返す")
    void findByEmailNotFound() {
        Optional<Shipper> found = shipperRepository.findByEmail("notfound@example.com");
        assertThat(found).isEmpty();
    }
}
