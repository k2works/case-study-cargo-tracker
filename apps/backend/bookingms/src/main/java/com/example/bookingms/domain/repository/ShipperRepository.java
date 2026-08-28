package com.example.bookingms.domain.repository;

import com.example.bookingms.domain.model.valueobjects.EmailAddress;
import com.example.bookingms.domain.model.aggregates.Shipper;
import java.util.List;
import java.util.Optional;

public interface ShipperRepository {

    Optional<Shipper> findByEmail(EmailAddress email);

    /** ID で 1 件取る。予約登録で荷主の実在を確かめるのに使う。 */
    Optional<Shipper> findById(Long id);

    /** 荷主コードを採番して保存し、採番後の荷主を返す。 */
    Shipper save(Shipper shipper);

    /** 氏名/社名・メールアドレスの部分一致で検索する。keyword が空なら全件。 */
    List<Shipper> search(String keyword);
}
