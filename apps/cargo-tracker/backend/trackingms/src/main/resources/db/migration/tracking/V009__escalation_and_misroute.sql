-- 緊急の escalation・S42 の宛先情報・誤配の印を投影に足す（IT11 / US20・US28）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_exception」「tracking_summary」
--
-- **記録と読み口は対で出す。** escalated_at はイベント
-- （ExceptionEscalatedEvent）を写す先である。写す先が無ければ、US20 §受入基準 3 は
-- 「Event Store に積んだだけで誰も読めない」——IT10 で同じ形の未達を 2 件出した。
--
-- **misrouted は data-model.md に定義済みの列**（tracking_summary）。V002 の
-- コメントが「後の IT で足す」と書いたまま残っていたので、実装が正典に追いつく。
-- 誤配のバナー（S22・S41）と一覧の絞り込みが読む。
--
-- **booking_id を例外一覧に持つ**（IT10 レビュー N9 の半分）。追跡管理者の電話は
-- 「A 社の予約の件で」から始まるが、S42 は追跡番号しか出せなかった。一覧の 1 行
-- ごとに他サービスへ問い合わせないための写しである。
--
-- **荷主名はここに持てない。** trackingms が受け取る契約
-- （TrackingInitializedEvent）は shipperId しか運ばず、名前は bookingms にある。
-- 足すには**既に本番に出ている契約の形を変える**ことになり、Upcaster が要る。
-- 判断は IT12（通関で S42 をもう一度触るとき）。それまで S42 は予約番号を出す。

ALTER TABLE tracking_exception
    ADD COLUMN escalated_at TIMESTAMPTZ,
    ADD COLUMN booking_id   VARCHAR(36);

ALTER TABLE tracking_summary
    ADD COLUMN misrouted BOOLEAN NOT NULL DEFAULT FALSE;

-- 緊急で未対応のものを先に読む（S42 / ダッシュボード）。
-- **部分索引にする。** 決着した例外のほうが時間とともに増えるので、
-- 全行に索引を張ると「まだ手を入れる場所」を引くのに関係ない行を辿る。
CREATE INDEX idx_tracking_exception_escalated
    ON tracking_exception (escalated_at DESC)
    WHERE escalated_at IS NOT NULL AND response_status <> 'RESOLVED';
