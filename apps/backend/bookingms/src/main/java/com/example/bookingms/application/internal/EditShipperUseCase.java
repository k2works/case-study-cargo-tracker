package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.CorporateContract;
import com.example.bookingms.domain.model.Shipper;
import com.example.bookingms.domain.model.ShipperProfile;
import java.util.Optional;

/**
 * 登録済みの荷主の内容を直す（US02 / #550）。
 *
 * <p>これまで荷主は登録したら直せなかった。転居・改称・担当者の変更は実際に起こるため、
 * 直せないと**同じ荷主をもう 1 件登録する**ことになり、予約がどちらに紐づくか分からなくなる。
 */
public class EditShipperUseCase {

    private final ShipperRepository repository;

    public EditShipperUseCase(ShipperRepository repository) {
        this.repository = repository;
    }

    /**
     * 直す。見つからなければ空を返す。
     *
     * <p><strong>メールアドレスの重複は、登録と違って問いかけにしない。</strong>登録では
     * 「同じお客様かもしれない」という判断が要るが、編集はすでにその荷主だと分かっている。
     * 別の荷主と同じアドレスになること自体は、代表アドレスを共有する部署で実在する。
     */
    public Optional<Shipper> edit(Long id, ShipperProfile profile, CorporateContract contract) {
        return repository.findById(id)
                .map(shipper -> repository.save(shipper.edit(profile, contract)));
    }
}
