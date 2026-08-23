-- 貨物状態の手動更新・例外・履歴（US17・US18・US19・US20・ADR-024）。
--
-- IT7 までの trackingms は状態を 1 列持つだけで、履歴も例外も持っていなかった。

-- **例外が起きる前の状態を列に持つ**（ADR-024 決定 2）。
--
-- 履歴から再導出すると、1 リクエストの中では履歴が手元にあるので正しく見え、
-- 行に残っていないことに気づけない。ユニットが緑のままクロスリクエストで誤復帰する。
--
-- 列が無かったころの行が読めなくなるため、NULL を許す（ADR-009 の「不変条件の追加は
-- 既存行を壊す」）。未解決の例外が無ければ NULL である。
ALTER TABLE tracking_activity ADD COLUMN status_before VARCHAR(30);

-- いま貨物がある港。まだ動いていなければ出発港。
-- 既存行は出発港で埋める——空のままにすると、公開照会が現在地を出せない。
ALTER TABLE tracking_activity ADD COLUMN current_location_unlocode VARCHAR(5)
    REFERENCES location (unlocode);
UPDATE tracking_activity SET current_location_unlocode = origin_unlocode
 WHERE current_location_unlocode IS NULL;

-- 推定到着日（US18-2）。**到着期限とは別物である**。
-- 期限は「いつまでに届けるか」、こちらは「いつ届く見込みか」。
-- 経路が決まるまでは分からないため NULL を許す——**0 や今日で埋めない**。
ALTER TABLE tracking_activity ADD COLUMN estimated_arrival DATE;

-- 追跡の出来事（US17-3・US18-3）。
--
-- 荷役の記録と手動更新の**両方**が積まれる。別々のテーブルに分けると、
-- 荷主に見せる 1 本の経過を 2 つの表から組み立てることになる。
CREATE TABLE tracking_handling_event (
    id               BIGSERIAL PRIMARY KEY,
    tracking_number  VARCHAR(20) NOT NULL REFERENCES tracking_activity (tracking_number),
    -- 遷移した先の状態。「何が起きたか」ではなく「どうなったか」を残す
    -- ——荷主が読むのは状態であり、荷役の種別ではない
    tracking_status  VARCHAR(30) NOT NULL,
    location_unlocode VARCHAR(5) NOT NULL REFERENCES location (unlocode),
    -- 業務上の発生時刻。記録した時刻ではない
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 荷役から来たのか、人が手で入れたのか。運用の問い合わせで要る
    source           VARCHAR(20) NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_handling_event_number
    ON tracking_handling_event (tracking_number, occurred_at);

-- 起票された例外（US19・US20）。
--
-- **解決しても消さない。** 実際に起きたことの記録であり、あとから「無かったこと」には
-- できない（ADR-023 決定 3 と同じ立場）。解決したことを足す。
CREATE TABLE tracking_exception_event (
    id               BIGSERIAL PRIMARY KEY,
    tracking_number  VARCHAR(20) NOT NULL REFERENCES tracking_activity (tracking_number),
    exception_type   VARCHAR(30) NOT NULL,
    description      VARCHAR(500) NOT NULL,
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 未解決なら NULL。集約はこれを見て 2 件目の起票を断る
    resolved_at      TIMESTAMP WITH TIME ZONE,
    resolution_notes VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_exception_open
    ON tracking_exception_event (tracking_number, resolved_at);

-- 荷主へ通知した事実（ADR-024 決定 9）。
--
-- **メールは送っていない。** これは送信の記録ではなく、送信の代替である。
-- 荷主は追跡照会の画面でこれを読む。
CREATE TABLE tracking_notice (
    id               BIGSERIAL PRIMARY KEY,
    tracking_number  VARCHAR(20) NOT NULL REFERENCES tracking_activity (tracking_number),
    -- 荷主に見せる文言。**社内の手がかりを書かない**
    -- ——認証の外にある画面に出るため、作業者名や予約番号は載せない
    message          VARCHAR(500) NOT NULL,
    noticed_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_tracking_notice_number ON tracking_notice (tracking_number, noticed_at DESC);

-- 公開照会の記録（UC15 の最低保証・ADR-024 決定 7）。
--
-- 認証が無い経路なので「誰が」は IP と User-Agent である。
-- **見つからなかった照会こそ、総当たりを見つける材料である**——成否に関わらず残す。
CREATE TABLE tracking_lookup_log (
    id               BIGSERIAL PRIMARY KEY,
    tracking_number  VARCHAR(40) NOT NULL,
    client_ip        VARCHAR(45) NOT NULL,
    user_agent       VARCHAR(255),
    found            BOOLEAN NOT NULL,
    looked_up_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_lookup_log_ip ON tracking_lookup_log (client_ip, looked_up_at);
