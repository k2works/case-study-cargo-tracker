-- キャンセル申請（US30・UC22）。
--
-- **data-model.md の定義をそのまま落とす。** IT9 で新しく決めたのではなく、設計に
-- すでに定義がある。突き合わせは SchemaDesignConsistencyTest が行う。

CREATE TABLE cancellation_request (
    id                        BIGSERIAL PRIMARY KEY,
    -- サロゲートキーで参照する（data-model.md の規約）。予約番号は業務キーであり、
    -- 参照に使うと採番の形式が変わったときに参照が外れる
    cargo_id                  BIGINT       NOT NULL REFERENCES cargo (id),
    -- **理由は必須。**承認する追跡管理者と、荷主に説明する担当者が読む
    reason                    VARCHAR(500) NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    requested_by              VARCHAR(100) NOT NULL,
    requested_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    -- **申請時点の予約状態。**キャンセル料の料率の根拠になる（US23・IT11）。
    -- あとから予約の状態を見ても、そのときどこまで進んでいたかは分からない
    booking_status_at_request VARCHAR(30)  NOT NULL,
    -- 承認したときだけ値が入る。却下では NULL のまま
    -- （却下は「キャンセルしない」決定であり、荷降しの手配は要らない）
    discharge_location_unlocode VARCHAR(5) REFERENCES location (unlocode),
    decided_by                VARCHAR(100),
    decided_at                TIMESTAMP WITH TIME ZONE,
    decision_reason           VARCHAR(500),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 承認待ちの一覧は古い順に読む。放っておくほど貨物は目的地へ近づく
CREATE INDEX idx_cancellation_request_pending
    ON cancellation_request (status, requested_at);

CREATE INDEX idx_cancellation_request_cargo ON cancellation_request (cargo_id);
