package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.Shipper;
import java.util.List;

/**
 * 荷主を探す。
 *
 * <p>登録（状態を変える）と検索（変えない）を同じクラスに置くと、片方の都合で
 * 依存が増えたときにもう片方まで巻き込まれる。
 */
public class SearchShipperUseCase {

    private final ShipperRepository repository;

    public SearchShipperUseCase(ShipperRepository repository) {
        this.repository = repository;
    }

    public List<Shipper> search(String keyword) {
        return repository.search(keyword);
    }
}
