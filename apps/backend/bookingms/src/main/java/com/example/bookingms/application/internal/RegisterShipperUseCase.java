package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.Shipper;
import java.util.Optional;

/**
 * 荷主を登録する。
 *
 * <p>重複したメールアドレスの扱いは、真偽値の引数ではなく呼ぶメソッドで表す。
 * {@code register(command, true)} という呼び出しは、呼び出し側を読んだだけでは
 * 何が true なのか分からない。
 */
public class RegisterShipperUseCase {

    private final ShipperRepository repository;

    public RegisterShipperUseCase(ShipperRepository repository) {
        this.repository = repository;
    }

    /** 登録する。同じメールアドレスの荷主が既にあれば、登録せずに問いかけを返す。 */
    public RegistrationOutcome register(RegisterShipperCommand command) {
        Optional<Shipper> existing = repository.findByEmail(command.email());
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
        Shipper shipper = Shipper.register(
                command.type(), command.name(), command.email(), command.address(), command.phone(),
                command.contract());
        return new RegistrationOutcome.Registered(repository.save(shipper));
    }
}
