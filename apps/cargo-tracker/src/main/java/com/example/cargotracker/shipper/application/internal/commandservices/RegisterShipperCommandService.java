package com.example.cargotracker.shipper.application.internal.commandservices;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 荷主登録のユースケース（US02）。 */
@Service
public class RegisterShipperCommandService {

    /** 荷主コードの採番をやり直す回数。**無限には試さない。** */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final ShipperRepository repository;

    public RegisterShipperCommandService(ShipperRepository repository) {
        this.repository = repository;
    }

    /**
     * 個人荷主を登録する。
     *
     * <p>同一メールアドレスが登録済みの場合は登録せず、既存の荷主を返す（US02 の受入基準）。
     * どちらを使うかの判断は利用者に委ねるため、ここでは例外にしない。
     *
     * @return 登録結果。既存が見つかった場合は {@code existing} に値が入る
     */
    @Transactional
    public Result register(
            ShipperName name, Email email, Phone phone, Address address) {
        return register(name, email, phone, address, null);
    }

    /**
     * 荷主を登録する。
     *
     * @param contract 法人契約。<strong>{@code null} なら個人荷主として登録する</strong>
     *                 （US03）。種別を別の引数で受け取ると「法人なのに契約が無い」
     *                 組み合わせを渡せてしまうため、契約の有無が種別を決める
     */
    public Result register(
            ShipperName name, Email email, Phone phone, Address address,
            CorporateContract contract) {
        Optional<Shipper> existing = repository.findByEmail(email);
        if (existing.isPresent()) {
            return Result.duplicated(existing.get());
        }

        // **採番が既存のコードと衝突しても登録を落とさない。**
        // 投入済みのデータがシーケンスを進めずに書かれていると、採番は
        // 使用済みの番号を返す。そのとき利用者に見えるのは 500 であり、
        // **入力をやり直しても直らない**（同じ番号が返り続ける）。
        // 番号を進めながら数回試す。それでも取れなければ運用の問題として上げる。
        org.springframework.dao.DuplicateKeyException last = null;
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            ShipperId id = ShipperId.generate();
            ShipperCode code = ShipperCode.of(repository.nextSequence());
            Shipper shipper = contract == null
                    ? Shipper.registerIndividual(id, code, name, email, phone, address)
                    : Shipper.registerCorporate(id, code, name, email, phone, address, contract);
            try {
                repository.save(shipper);
                return Result.registered(shipper);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                last = e;
            }
        }
        // **握りつぶさない。** ここに来るのは採番が壊れているときであり、
        // 業務の結果ではなく運用の問題として上げる
        throw last == null
                ? new IllegalStateException("荷主コードを採番できませんでした")
                : last;
    }

    /**
     * 登録結果。
     *
     * @param shipper    登録した、または既存の荷主
     * @param duplicated 既存が見つかったため登録しなかった場合は true
     */
    public record Result(Shipper shipper, boolean duplicated) {

        static Result registered(Shipper shipper) {
            return new Result(shipper, false);
        }

        static Result duplicated(Shipper shipper) {
            return new Result(shipper, true);
        }
    }
}
