-- 動作確認用の荷主データ。
--
-- **ユーザーマニュアル（docs/manual/03-荷主管理.md）の画面キャプチャと同じ内容にする。**
-- マニュアルの図と開発環境の画面が食い違うと、読者はどちらが正しいか判断できない。
--
-- 本ファイルは db/demo 配下にあり、demo プロファイルを有効にした環境
-- （local / dev）でのみ適用される。common や {vendor} には置かない。
-- 置くと本番のマイグレーションに動作確認用データが混ざる。
--
-- バージョンを 900 番台にしているのは、業務スキーマのマイグレーション
-- （V1 から順に増える）と番号が衝突しないようにするためである。
INSERT INTO shipper (
    id, shipper_code, shipper_type, name, email, phone,
    address_country, address_postal_code, address_region,
    address_city, address_street)
VALUES (
    '11111111-1111-4111-8111-111111111111',
    'SHP-000001', 'INDIVIDUAL', '山田商事', 'shipper-sample@example.com', '03-0000-0000',
    'JP', '100-0001', '東京都', '千代田区', '千代田 1-1 サンプルビル 5F');
