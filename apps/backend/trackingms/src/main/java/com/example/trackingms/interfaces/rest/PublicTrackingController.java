package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.internal.TrackingLookupUseCase;
import com.example.trackingms.application.port.TrackingNoticeRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * <p><strong>何を直せばよいかを伝える。</strong>打ち間違いが最も多く、その次が
     * 予約番号（{@code BKG-}）で引こうとする誤りである。番号の形を添える。
     *
     * <p><strong>形式が違っても同じ答えを返す</strong>（[ADR-024] 決定 6）。この文言は
     * 「その番号は存在しない」ではなく「引けなかった」を伝えるものであり、
     * 実在するかどうかを区別して教えてはいない。
     */
    static final String NOT_FOUND_MESSAGE =
            "追跡番号が見つかりません。追跡番号は TRK- で始まります"
                    + "（予約番号 BKG- では引けません）。番号をお確かめのうえ、"
                    + "もう一度入力してください";

    private static TrackingNotFoundException notFound() {
        return new TrackingNotFoundException();
    }

    /**
     * 本文を返すための例外。
     *
     * <p><strong>{@code ResponseStatusException} では本文に文言が乗らない。</strong>
     * Spring の既定は {@code server.error.include-message=never} であり、添えた理由は
     * 落ちる。画面は本文の {@code message} を読むため、丁寧に書いた案内が誰にも
     * 届かないまま、モックだけが文言を返していた（IT9 返済枠 0.3）。
     */
    static class TrackingNotFoundException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        TrackingNotFoundException() {
            super(NOT_FOUND_MESSAGE);
        }
    }

    /** 見つからないときの本文。画面はこの {@code message} をそのまま出す。 */
    @ExceptionHandler(TrackingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TrackingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    /** 画面が読む形。他サービスの {@code ErrorResponse} と同じ形にそろえる。 */
    public record ErrorResponse(String message) {
    }
}
