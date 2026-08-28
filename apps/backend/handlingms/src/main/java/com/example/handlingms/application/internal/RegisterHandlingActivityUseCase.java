package com.example.handlingms.application.internal;

import com.example.handlingms.application.port.CargoSnapshotFinder;
import com.example.handlingms.application.port.CustomsDeclarationRepository;
import com.example.handlingms.application.port.HandlingActivityRegistered;
import com.example.handlingms.application.port.HandlingActivityRepository;
import com.example.handlingms.application.port.HandlingEventNotifier;
import com.example.handlingms.application.port.LocationRepository;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.CargoSnapshot;
import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.ConsigneeConfirmation;
import com.example.handlingms.domain.model.HandlingActivity;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.model.HandlingVoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

/**
 * 荷役作業を記録する（US15・US16・[ADR-023]）。
 *
 * <p><strong>トランザクションの境目はここに置く。</strong>リポジトリの中に置くと、
 * 保存とイベントの発行が別のトランザクションになり、[ADR-022] 決定 6 の「コミット後に
 * 発行する」機構が素通りする（IT6 で実際に起きた形）。
 */
@Service
public class RegisterHandlingActivityUseCase {

    private final CargoSnapshotFinder cargoes;
    private final LocationRepository locations;
    private final HandlingActivityRepository activities;
    private final CustomsDeclarationRepository declarations;
    private final HandlingEventNotifier notifier;
    private final Clock clock;

    public RegisterHandlingActivityUseCase(CargoSnapshotFinder cargoes,
            LocationRepository locations, HandlingActivityRepository activities,
            CustomsDeclarationRepository declarations, HandlingEventNotifier notifier,
            Clock clock) {
        this.cargoes = cargoes;
        this.locations = locations;
        this.activities = activities;
        this.declarations = declarations;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * 記録する。
     *
     * @return 記録した荷役。追跡番号が見つからなければ空
     * @throws IllegalArgumentException 種別の要件を満たしていないとき、または作業場所が
     *     地点マスタに無いとき
     */
    @Transactional
    public Optional<HandlingActivity> register(RegisterHandlingActivityCommand command) {
        HandlingTrackingNumber trackingNumber =
                HandlingTrackingNumber.of(command.trackingNumber());
        Optional<CargoSnapshot> cargo = cargoes.findByTrackingNumber(trackingNumber);
        if (cargo.isEmpty()) {
            return Optional.empty();
        }

        CargoSnapshot snapshot = cargo.orElseThrow();
        HandlingType type = HandlingType.parse(command.type());
        Location location = requireLocation(command.locationUnLocode());
        requireNotAlreadyRecorded(snapshot, type, location, command.completionTime());
        requireCustomsCleared(snapshot, type);

        HandlingActivity registered = activities.register(HandlingActivity.register(
                snapshot,
                type,
                location,
                command.completionTime(),
                command.operatorName(),
                command.voyageNumber() == null || command.voyageNumber().isBlank()
                        ? null : HandlingVoyageNumber.of(command.voyageNumber()),
                command.consigneeConfirmation() == null
                        || command.consigneeConfirmation().isBlank()
                        ? null : ConsigneeConfirmation.of(command.consigneeConfirmation())));

        notifier.handlingActivityRegistered(new HandlingActivityRegistered(
                trackingNumber.value(),
                registered.bookingId().value(),
                registered.type().name(),
                registered.location().unLocode(),
                registered.completionTime(),
                registered.voyageNumber().map(HandlingVoyageNumber::value).orElse(null),
                registered.offRoute(),
                clock.instant()));

        return Optional.of(registered);
    }

    /**
     * <strong>同じ内容の記録は、2 回起きた作業ではない。</strong>
     *
     * <p>1 日数十件を打つ画面では、送信の二度押しや戻る操作で同じ内容がもう一度届く。
     * そのまま入れると履歴に同じ作業が 2 行並び、<strong>どちらが本物かを誰も判断
     * できない</strong>。予定外の作業を拒まない（[ADR-023] 決定 3）のとは別の話で、
     * ここで拒んでいるのは<strong>作業ではなく二重の記録</strong>である。
     *
     * <p>いまの検査は読み出しに頼っており、同時に 2 回届いた場合はすり抜ける。
     * <strong>本来は保存先の一意制約で決めるべき</strong>で、列の追加を伴うため
     * US17 の移行と同じ変更で入れる（それまでの間、すり抜けは二度押しの間隔より
     * 短い同時実行に限られる）。
     */
    private void requireNotAlreadyRecorded(CargoSnapshot cargo, HandlingType type,
            Location location, java.time.Instant completionTime) {
        if (activities.existsSameActivity(CargoBookingId.of(cargo.bookingId()), type,
                location.unLocode(), completionTime)) {
            throw new IllegalStateException("同じ作業がすでに記録されています。履歴を確認してください");
        }
    }

    /**
     * 通関が下りていない貨物は引き渡さない（US29-3・[ADR-023] 決定 4 の拡張点）。
     *
     * <p>通関が下りていない貨物を引き渡すと、税関との関係で会社が責任を負う。
     *
     * <p><strong>申告が 1 件も無い貨物も断る。</strong>名簿方式の検査は「載っていないもの」を
     * 通すと、載せ忘れたものほど漏れる（過去 take の教訓）。申告が無いのは「検査の対象外」
     * ではなく<strong>「通関済でない」</strong>である。
     *
     * <p><strong>引取以外は対象にしない。</strong>受領・積込・荷降しまで止めると、通関前の
     * 貨物が港で動かせなくなる。通関は<strong>引き渡しの前提</strong>であって、輸送の
     * 前提ではない。
     *
     * <p><strong>過去の記録はさかのぼって検査しない</strong>（[ADR-025] 決定 9）。
     * ガードが無かったあいだに記録された引取がある。ここで見るのは<strong>新しく
     * 受け付ける引取だけ</strong>である。
     *
     * @throws IllegalStateException 通関済でないとき。**いまの状態を添える**
     */
    private void requireCustomsCleared(CargoSnapshot cargo, HandlingType type) {
        if (type != HandlingType.CLAIM) {
            return;
        }
        CargoBookingId bookingId = CargoBookingId.of(cargo.bookingId());
        CustomsDeclaration latest = declarations.findLatestByBookingId(bookingId)
                .orElseThrow(() -> new IllegalStateException(
                        "この貨物には通関申告がありません。先に通関申告を登録してください"));
        if (!latest.isCleared()) {
            // 「できません」だけでは、次にすることが分からない。**いまの状態を伝える**
            throw new IllegalStateException(
                    "通関が完了していないため引き取りできません（現在: %s）"
                            .formatted(latest.status().label()));
        }
    }

    private Location requireLocation(String unLocode) {
        return locations.findByUnLocode(unLocode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "作業場所が見つかりません: " + unLocode));
    }
}
