package com.example.trackingms.application.port;

import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingException;
import com.example.trackingms.domain.model.TrackingNumber;
import java.util.List;
import java.util.Optional;

/** 追跡の保存先（出力ポート）。 */
public interface TrackingActivityRepository {

    /**
     * まだ無ければ保存し、すでにあればそれを返す。<strong>1 回の書き込みで決める</strong>。
     *
     * <p>「探してから無ければ保存する」形にすると、2 つのイベントが同時に届いたときに
     * どちらも「無い」と読んでから書き、一意制約に当たった側が落ちる。単一の購読者では
     * 表面化しないが、<strong>並行化した瞬間に壊れる</strong>（[ADR-022] 決定 5）。
     *
     * <p>重複かどうかを決めるのは DB の一意制約であり、事前の読み出しではない。
     */
    TrackingActivity saveIfAbsent(TrackingActivity activity);

    /**
     * 追跡の状態を更新する（US15-4）。
     *
     * <p><strong>作成とは別のメソッドにする。</strong>「常に INSERT する save」で更新まで
     * 賄うと、最初の更新のときに行が増える。IT6 では作成しか起きなかったため、
     * 分岐が無いことを明記して先送りしていた。
     */
    void updateStatus(TrackingActivity activity);

    /** 追跡番号から探す。照会の入口であり、二重に作らないための確認にも使う。 */
    Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber);

    /**
     * 出来事を 1 件足す（US17-3・US18-3）。
     *
     * <p><strong>状態の更新と同じトランザクションで呼ぶ。</strong>別々になると、状態は
     * 動いたのに経過に出ない行ができ、荷主は「いつ変わったか」を読めない。
     */
    void appendEvent(TrackingNumber trackingNumber, TrackingEvent event);

    /** 1 つの貨物の経過を、起きた順に返す。 */
    List<TrackingEvent> findEvents(TrackingNumber trackingNumber, int limit);

    /**
     * 例外を保存する（起票・解決の両方）。
     *
     * <p>解決しても<strong>消さない</strong>。実際に起きたことの記録である。
     */
    void saveException(TrackingNumber trackingNumber, TrackingActivity activity);

    /** 未解決の例外がある追跡を返す。件数の遷移先である（横断規約）。 */
    List<TrackingActivity> findWithOpenExceptions(int limit);

    /**
     * 1 つの貨物に起きた例外を、解決済みも含めて古い順に返す（US19-5）。
     *
     * <p><strong>解決したら見えなくなる、では業務が回らない。</strong>「先週の遅れは
     * どうなったのか」と荷主から問い合わせが来たとき、担当者は解決の記録を読む。
     * 同じ貨物で 2 回目の遅延が起きたときに「前も同じ港で遅れた」と言えることも要る。
     */
    List<TrackingException> findExceptions(TrackingNumber trackingNumber, int limit);
}
