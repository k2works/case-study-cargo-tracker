package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.Shipper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 荷主を探す。
 *
 * <p>登録（状態を変える）と検索（変えない）を同じクラスに置くと、片方の都合で
 * 依存が増えたときにもう片方まで巻き込まれる。
 */
@Service
public class SearchShipperUseCase {

    private final ShipperRepository repository;

    public SearchShipperUseCase(ShipperRepository repository) {
        this.repository = repository;
    }

    public List<Shipper> search(String keyword) {
        return repository.search(keyword);
    }

    /**
     * 荷主 1 件を取る。
     *
     * <p>編集画面を URL で直接開いた（再読み込みした）ときに、一覧を経由せずに
     * 対象を復元できるようにするため。一覧の検索結果に頼ると、再読み込みで白紙になる。
     */
    public Optional<Shipper> findById(Long id) {
        return repository.findById(id);
    }
}
