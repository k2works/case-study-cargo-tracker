-- 動作確認用の貨物予約データ。
--
-- **ユーザーマニュアル（docs/manual/04-貨物予約.md）の画面キャプチャと同じ内容にする。**
-- マニュアルの図と開発環境の画面が食い違うと、読者はどちらが正しいか判断できない。
--
-- V900 の動作確認用荷主（SHP-000001 山田商事）に紐づける。
-- db/demo 配下であり、demo プロファイル（local / dev）でのみ適用される。
--
-- **到着期限を固定日にしない。** 固定日にすると時間の経過とともに期限切れの予約になり、
-- 「期限が過ぎた予約が仮予約のまま並んでいる」という、業務上あり得ない画面が
-- マニュアルの図として残る。適用日からの相対日数で入れる。
INSERT INTO cargo (
    booking_id, shipper_id, cargo_type, weight,
    origin_unlocode, destination_unlocode, arrival_deadline, booking_status,
    dimension_length, dimension_width, dimension_height, quantity, description)
VALUES (
    '22222222-2222-4222-8222-222222222222',
    '11111111-1111-4111-8111-111111111111',
    'GENERAL', 1200.500,
    'JPOSA', 'USLAX', CURRENT_DATE + 30, 'PRELIMINARY',
    120.000, 80.000, 100.000, 10, '電子部品（コネクタ）'),
    (
    '33333333-3333-4333-8333-333333333333',
    '11111111-1111-4111-8111-111111111111',
    'REFRIGERATED', 800.000,
    'JPYOK', 'DEHAM', CURRENT_DATE + 45, 'PRELIMINARY',
    NULL, NULL, NULL, NULL, '冷凍水産物');
