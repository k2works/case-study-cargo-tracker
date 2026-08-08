package com.example.cargotracker.shipper.application.internal.commandservices;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shipper.domain.model.CorporateContract;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Shipper;
import com.example.cargotracker.shipper.domain.model.ShipperName;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 荷主訂正のユースケース（US32）。 */
@Service
public class UpdateShipperCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.shipper");

    private final ShipperRepository repository;

    public UpdateShipperCommandService(ShipperRepository repository) {
        this.repository = repository;
    }

    /**
     * 荷主情報を訂正する。
     *
     * <p>荷主コードと荷主種別は引数に取らない。**受け取らないことが「変更できない」の
     * 実装である。** 画面から欄を消すだけでは、リクエストを直接組み立てれば変更できてしまう。
     *
     * @param actor 操作者（監査ログ用）
     */
    @Transactional
    public Result update(
            ShipperId id, long version, ShipperCorrection correction, String actor) {

        ShipperName name = correction.name();
        Email email = correction.email();

        Optional<Shipper> found = repository.findById(id);
        if (found.isEmpty()) {
            return Result.notFound();
        }
        Shipper current = found.get();

        if (current.version() != version) {
            return Result.conflicted(current);
        }

        Optional<Shipper> sameEmail = repository.findByEmail(email);
        if (sameEmail.isPresent() && !sameEmail.get().id().equals(id)) {
            return Result.duplicatedEmail(sameEmail.get());
        }

        Shipper corrected = current
                .rename(name)
                .changeContact(email, correction.phone())
                .relocate(correction.address());
        CorporateContract contract = correction.contract();
        // **契約は法人荷主にだけ適用する。** 個人荷主に契約を渡す送信は無視する
        // （画面から欄を消すだけでは、リクエストを直接組み立てれば付けられてしまう）。
        // 種別と契約の整合は Shipper が守るため、ここでは種別だけを見る。
        if (contract != null && corrected.isCorporate()) {
            corrected = corrected.changeContract(contract);
        }

        boolean updated;
        try {
            updated = repository.update(corrected);
        } catch (DuplicateKeyException e) {
            // 上の重複チェックを通り抜けた同時更新。**UNIQUE 制約違反をそのまま
            // 伝播させると 500 になり、利用者には障害として見える**（IT1 持ち越し C6）。
            // 業務の結果（重複していた）に落とす。
            return repository.findByEmail(email)
                    .map(Result::duplicatedEmail)
                    .orElseGet(() -> Result.conflicted(current));
        }

        if (!updated) {
            // version は一致していたが、直前に他の訂正が入った
            return Result.conflicted(repository.findById(id).orElse(current));
        }

        // **利用者が入力した値をそのままログに書かない。** 改行を含めれば
        // ログの 1 行を割って偽の行を差し込める（ログインジェクション）。
        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("荷主訂正 shipperCode={} shipperId={} actor={} name={} email={}",
                    corrected.shipperCode().value(), id.value(), AuditValue.sanitize(actor),
                    AuditValue.sanitize(corrected.name().value()),
                    AuditValue.sanitize(corrected.email().value()));
        }

        return Result.updated(corrected);
    }

    /** 訂正の結果。 */
    public enum Outcome {
        /** 訂正した。 */
        UPDATED,
        /** 対象の荷主が見つからない。 */
        NOT_FOUND,
        /** 他の担当者が先に訂正していた（楽観的ロック）。 */
        CONFLICTED,
        /** 変更後のメールアドレスが他の荷主と重複している。 */
        DUPLICATED_EMAIL
    }

    /**
     * 訂正の結果。
     *
     * @param outcome 結果の種別
     * @param shipper 訂正後の荷主、競合時は最新の荷主、重複時は既存の荷主
     */
    public record Result(Outcome outcome, Shipper shipper) {

        static Result updated(Shipper shipper) {
            return new Result(Outcome.UPDATED, shipper);
        }

        static Result notFound() {
            return new Result(Outcome.NOT_FOUND, null);
        }

        static Result conflicted(Shipper latest) {
            return new Result(Outcome.CONFLICTED, latest);
        }

        static Result duplicatedEmail(Shipper existing) {
            return new Result(Outcome.DUPLICATED_EMAIL, existing);
        }

        public boolean isUpdated() {
            return outcome == Outcome.UPDATED;
        }
    }
}
