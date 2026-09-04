-- 航海が受け入れる貨物種別。
--
-- 集約が空を GENERAL に決めてから書くので、この表に行が無い航海は存在しない
-- （不変条件 4）。読む側は「行が無い＝制限なし」と「一般貨物のみ」を区別しなくてよい。

CREATE TABLE voyage_accepted_cargo_type (
    voyage_number VARCHAR(20) NOT NULL REFERENCES voyage (voyage_number),
    cargo_type    VARCHAR(30) NOT NULL,
    PRIMARY KEY (voyage_number, cargo_type)
);
