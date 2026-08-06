package com.example.cargotracker.shipper.application.internal.commandservices;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.Address;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Phone;
import com.example.cargotracker.shipper.domain.model.Shipper;
import com.example.cargotracker.shipper.domain.model.ShipperCode;
import com.example.cargotracker.shipper.domain.model.ShipperName;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 荷主登録のユースケース（US02）。 */
@Service
public class RegisterShipperCommandService {

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
        Optional<Shipper> existing = repository.findByEmail(email);
        if (existing.isPresent()) {
            return Result.duplicated(existing.get());
        }

        Shipper shipper = Shipper.registerIndividual(
                ShipperId.generate(),
                ShipperCode.of(repository.nextSequence()),
                name, email, phone, address);
        repository.save(shipper);
        return Result.registered(shipper);
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
