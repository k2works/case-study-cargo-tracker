package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.internal.TrackingLookupUseCase;
import com.example.trackingms.application.port.TrackingNoticeRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 公開の追跡照会（US18・[ADR-024] 決定 5・6・7）。
 *
 * <p><strong>このシステムで唯一、認証を要さない業務経路である。</strong>
 * [ADR-007] のフィルタから除外する（{@code TrackingConfig} が接頭辞を渡す）。
 *
 * <p>接頭辞を {@code /api/v1/public/} と分けるのは、<strong>公開範囲が一目で分かる
 * 形にしておく</strong>ためである。業務 API に紛れていると、あとから「これは認証が
 * 要るのか」を毎回読み解くことになる。
 */
@RestController
@RequestMapping("/api/v1/public/tracking")
public class PublicTrackingController {

    /** 返すお知らせの上限。古いものまで全部出しても、荷主は読まない。 */
    private static final int NOTICE_LIMIT = 20;

    private final TrackingLookupUseCase lookUp;
    private final TrackingNoticeRepository notices;

    /** 表示の暦。荷主が読む日時は業務のタイムゾーンで出す（[ADR-010]）。 */
    private final java.time.ZoneId zone;

    public PublicTrackingController(TrackingLookupUseCase lookUp,
            TrackingNoticeRepository notices, java.time.Clock clock) {
        this.lookUp = lookUp;
        this.notices = notices;
        this.zone = clock.getZone();
    }

    /**
     * 追跡番号で照会する。
     *
     * <p><strong>形式が違っても「見つかりません」で返す。</strong>形式の誤りを別の答えに
     * すると、番号の形を総当たりの手がかりとして教えることになる。
     *
     * <p><strong>照会は成否に関わらず記録する</strong>（決定 7）。見つからなかった照会こそ、
     * 総当たりを見つける材料である。
     */
    @GetMapping("/{trackingNumber}")
    public PublicTrackingResponse byTrackingNumber(@PathVariable String trackingNumber,
            HttpServletRequest request) {
        TrackingActivity activity = lookUp
                .lookUp(trackingNumber, clientIpOf(request), request.getHeader("User-Agent"))
                .orElseThrow(PublicTrackingController::notFound);

        return PublicTrackingResponse.from(activity, lookUp.events(activity),
                notices.findByTrackingNumber(activity.trackingNumber(), NOTICE_LIMIT), zone);
    }

    /**
     * 呼び出し元の IP。
     *
     * <p><strong>{@code X-Forwarded-For} の先頭を採らない</strong>（決定 6）。
     * このヘッダは<strong>各ホップが末尾に追記する</strong>——先頭はクライアントが
     * 送った文字列そのものであり、誰でも好きな値を書ける。先頭を採ると、値を毎回
     * 変えるだけで<strong>IP ごとの上限をいくらでも回避できる</strong>。
     *
     * <p>採るのは<strong>末尾</strong>である。末尾を書き込むのは、こちらに最も近い
     * ホップ（Ingress / Gateway）であり、そこは詐称できない。
     *
     * <p>実装時に一度、先頭を採る形にしていた。javadoc は「そのまま信じない」と
     * 宣言していたのに、実装は最も信じてはいけない値を採っていた——<strong>しかも
     * 検査がその誤りを期待値として固定していた</strong>（IT8 のクローズレビュー）。
     */
    static String clientIpOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = forwarded.split(",");
        String nearest = hops[hops.length - 1].trim();
        return nearest.isEmpty() ? request.getRemoteAddr() : nearest;
    }

    /**
     * 見つからない（US18-4）。
     *
     * <p><strong>何を直せばよいかを伝える。</strong>打ち間違いが最も多い。
     */
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "追跡番号が見つかりません。番号をお確かめのうえ、もう一度入力してください");
    }
}
