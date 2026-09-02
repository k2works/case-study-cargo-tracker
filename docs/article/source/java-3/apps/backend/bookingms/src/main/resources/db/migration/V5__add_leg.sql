-- 旅程の輸送区間（IT5 / US09）。適用済みの V1〜V4 は編集しない。
-- 編集すると checksum が変わり、既にデプロイ済みの環境が起動できなくなる。

-- 経路が決まった予約が持つ区間。1 つの航海で運ばれる 1 区間を 1 行にする。
--
-- 積込・荷降しの時刻は TIMESTAMPTZ にする（他テーブルと揃える）。港はそれぞれ別の
-- タイムゾーンにあり、DATE や素の TIMESTAMP では「現地の何時か」が決まらない。
--
-- 監査カラム（created_at / updated_at）は全テーブルに置く決定に従う。旅程は差し替えが
-- 起こるため、「いつ入れ直したか」が追えないと、遅延対応の経緯が残らない。
CREATE TABLE leg (
    id                     BIGSERIAL PRIMARY KEY,
    cargo_id               BIGINT      NOT NULL REFERENCES cargo (id),
    voyage_number          VARCHAR(20) NOT NULL,
    load_location_unlocode VARCHAR(5)  NOT NULL REFERENCES location (unlocode),
    unload_location_unlocode VARCHAR(5) NOT NULL REFERENCES location (unlocode),
    load_time              TIMESTAMP WITH TIME ZONE NOT NULL,
    unload_time            TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 区間の順序。旅程は「東京 → 釜山 → ロサンゼルス」のように順序に意味がある
    seq_number             INTEGER     NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    -- 消し忘れた行が混ざると旅程が二重になり、しかも順序は保たれるため
    -- 画面上は「区間が増えた」ようにしか見えない（IT3 で同型の事故があった）
    CONSTRAINT uk_leg_cargo_seq UNIQUE (cargo_id, seq_number)
);

-- 旅程は予約から辿る。予約詳細を開くたびに全件走査させない
CREATE INDEX idx_leg_cargo ON leg (cargo_id);
