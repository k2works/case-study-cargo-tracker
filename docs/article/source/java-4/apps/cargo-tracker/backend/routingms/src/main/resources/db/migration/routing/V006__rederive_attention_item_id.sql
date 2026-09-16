-- 要確認一覧の識別子を、共有カーネルの導出（AttentionItemId）に合わせ直す。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- IT2 で「同じ事実に毎回別の行が積み上がる」欠陥を直したとき、導出が bookingms と
-- routingms に 1 本ずつ書かれ、区切り文字が食い違い、しかも導出値をハイフンで区切って
-- UUID の見た目に整形していた（IT4 R.1・R.2）。導出を共有カーネルへ寄せたので、
-- すでに書かれている行も新しい導出に合わせる。合わせないと、同じ事実が
-- 「古い形の行」と「新しい形の行」の 2 行になって一覧に並ぶ。
--
-- attention_item は追記専用なので、行は消さずに識別子だけ付け替える。ただし採番していた
-- ころ（IT2 以前）の行は同じ事実に複数あるので、そこだけは最古の 1 行に畳む。

-- 1. 新しい導出で同じ識別子になる行のうち、最古の 1 行だけを残す。
DELETE FROM attention_item a
USING attention_item b
WHERE a.kind = b.kind
  AND a.target_type = b.target_type
  AND a.target_id = b.target_id
  AND a.reason = b.reason
  AND (b.occurred_at, b.item_id) < (a.occurred_at, a.item_id);

-- 2. 事実から導き直す。区切りは US（U+001F）。SHA-256 の先頭 128 ビットを 16 進で持つ。
UPDATE attention_item
SET item_id = substr(
        encode(
            sha256(convert_to(
                kind || chr(31) || target_type || chr(31) || target_id || chr(31) || reason,
                'UTF8')),
            'hex'),
        1, 32);
