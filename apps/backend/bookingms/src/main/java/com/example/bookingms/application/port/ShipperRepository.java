package com.example.bookingms.application.port;

import com.example.bookingms.domain.model.Shipper;
import java.util.List;
import java.util.Optional;

public interface ShipperRepository {

    Optional<Shipper> findByEmail(String email);

    /** 荷主コードを採番して保存し、採番後の荷主を返す。 */
    Shipper save(Shipper shipper);

    /** 氏名/社名・メールアドレスの部分一致で検索する。keyword が空なら全件。 */
    List<Shipper> search(String keyword);
}
