package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.Shipper;
import java.util.List;
import java.util.Optional;

public class RegisterShipperUseCase {

    private final ShipperRepository repository;

    public RegisterShipperUseCase(ShipperRepository repository) {
        this.repository = repository;
    }

    /**
     * 荷主を登録する。
     *
     * @param registerAnyway 同じメールアドレスの荷主が既にあっても新規で登録するか。
     *     同姓同名・同一メールの別部署のような実態があるため、営業担当者の選択を尊重する
     */
    public RegistrationOutcome register(RegisterShipperCommand command, boolean registerAnyway) {
        if (!registerAnyway) {
            Optional<Shipper> existing = repository.findByEmail(command.email());
            if (existing.isPresent()) {
                return new RegistrationOutcome.DuplicateFound(existing.get());
            }
        }

        Shipper shipper = Shipper.register(
                command.type(), command.name(), command.email(), command.address(), command.phone());
        return new RegistrationOutcome.Registered(repository.save(shipper));
    }

    public List<Shipper> search(String keyword) {
        return repository.search(keyword);
    }
}
