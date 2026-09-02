package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.repository.ShipperRepository;
import com.example.bookingms.domain.model.valueobjects.EmailAddress;
import com.example.bookingms.domain.model.aggregates.Shipper;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.example.bookingms.domain.model.commands.RegisterShipperCommand;

/**
 * 荷主を登録する。
 *
 * <p>重複したメールアドレスの扱いは、真偽値の引数ではなく呼ぶメソッドで表す。
 * {@code register(command, true)} という呼び出しは、呼び出し側を読んだだけでは
 * 何が true なのか分からない。
 */
@Service
public class RegisterShipperUseCase {

    private final ShipperRepository repository;

    public RegisterShipperUseCase(ShipperRepository repository) {
        this.repository = repository;
    }

    /** 登録する。同じメールアドレスの荷主が既にあれば、登録せずに問いかけを返す。 */
    public RegistrationOutcome register(RegisterShipperCommand command) {
        Optional<Shipper> existing = repository.findByEmail(EmailAddress.of(command.email()));
        if (existing.isPresent()) {
            return new RegistrationOutcome.DuplicateFound(existing.get());
        }
        return registerAnyway(command);
    }

    /**
     * 重複を承知のうえで登録する。
     *
     * <p>同姓同名・同一メールの別部署のような実態があるため、営業担当者の判断を尊重する。
     */
    public RegistrationOutcome registerAnyway(RegisterShipperCommand command) {
        Shipper shipper = command.simulated()
                // **入口を分ける**（[ADR-030] 決定 3）。シミュレーション由来は荷主コードの
                // 帯で識別する。ここで分けておかないと、実データに混ざったまま締めに乗る
                ? Shipper.registerSimulated(command.type(), command.name(), command.email(),
                        command.address(), command.phone())
                : Shipper.register(
                        command.type(), command.name(), command.email(), command.address(),
                        command.phone(), command.contract());
        return new RegistrationOutcome.Registered(repository.save(shipper));
    }
}
