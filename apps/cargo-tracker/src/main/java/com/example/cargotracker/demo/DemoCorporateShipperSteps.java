package com.example.cargotracker.demo;

import static com.example.cargotracker.demo.DemoActors.require;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices
        .RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 自動実行のたびに<strong>新しい法人荷主</strong>を登録する（US02 / US03）。
 *
 * <p><strong>法人であることは「種別」ではなく契約の有無で決まる</strong>
 * （{@code CorporateContract} の設計）。契約を渡さずに登録すると個人荷主になり、
 * 請求のときに契約割引が効かない。
 *
 * <p><strong>連絡先を毎回変える。</strong> 登録サービスは同じメールアドレスの荷主が
 * いれば<strong>登録せずに既存を返す</strong>。固定の連絡先にすると、2 回目以降の
 * 自動実行は「新しい荷主を登録した」と画面に出しながら、実際には
 * <strong>1 回目の荷主を使い回す</strong>ことになる。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
class DemoCorporateShipperSteps {

    private final RegisterShipperCommandService registerShipper;

    DemoCorporateShipperSteps(RegisterShipperCommandService registerShipper) {
        this.registerShipper = registerShipper;
    }

    /**
     * 法人荷主を登録する。
     *
     * @return 登録した荷主の ID
     */
    ShipperId register(DemoScenario scenario) {
        var result = registerShipper.register(
                new ShipperName(scenario.corporate().name()),
                new Email(email(scenario)),
                new Phone("03-5555-0100"),
                new Address("JP", "105-0011", "東京都", "港区", "芝公園 1-1 デモ商船ビル"),
                new CorporateContract(
                        new ContractNumber(scenario.contractNumber()),
                        new DiscountRate(scenario.corporate().discountRate())));
        // **既存を返されたら、新しく作れていない。** 契約番号に連番を入れているため
        // 通常は起きないが、黙って進むと画面の「登録しました」が嘘になる
        require(!result.duplicated(),
                "同じ連絡先の荷主が既にいます: " + scenario.corporate().name());
        return new ShipperId(result.shipper().id().value());
    }

    /** 契約番号ごとに変わる連絡先。<strong>重複判定に使われる。</strong> */
    private String email(DemoScenario scenario) {
        return "demo-%s@example.com".formatted(
                scenario.corporate().contractSuffix().toLowerCase(java.util.Locale.ROOT));
    }
}
