package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 引取の取り消しを追跡に反映する（US36）。
 *
 * <p><strong>戻る先は記録しておいた値である</strong>（{@code status_before_claim}）。
 * 履歴から導き直すと、ユニットテストが緑でも
 * <strong>リクエストをまたいだときに誤った状態に復帰する</strong>。
 */
@Service
public class CancelClaimCommandService {

    private final TrackingActivityRepository trackingRepository;

    public CancelClaimCommandService(TrackingActivityRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    /**
     * 引取完了を引取前の状態に戻す。
     *
     * <p><strong>新しいトランザクションで動く</strong>（ADR-009）。購読側の失敗が
     * 承認そのものを巻き戻すと、承認できたのに待ち行列に残る形になる。
     *
     * @return 戻せたか。<strong>戻せなくても例外にしない</strong> — 取りこぼしは
     *         購読側が数える
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelClaim(String trackingNumber) {
        Optional<TrackingActivity> found =
                trackingRepository.findByTrackingNumber(new TrackingNumber(trackingNumber));
        if (found.isEmpty()) {
            return false;
        }
        TrackingActivity tracking = found.get();
        try {
            tracking.cancelClaim();
        } catch (IllegalStateException e) {
            return false;
        }
        return trackingRepository.update(tracking);
    }
}
