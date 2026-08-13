package com.example.cargotracker.shipper.application.internal.outboundservices.acl;

import java.util.List;
import java.util.UUID;

/**
 * 荷主に紐付いた利用者アカウントを読む出力ポート（Shipper → Security の ACL。C11）。
 *
 * <p><strong>営業担当者が案内先を答えられなかった。</strong> ADR-013 は紐付けを
 * {@code users.shipper_id} に置いたが、設定されているかを画面から確かめる手段が
 * 無いまま 3 イテレーション繰り越した（IT9 レビュー M10）。
 *
 * <p><strong>運ぶのは利用者名だけである。</strong> メール・パスワード・ロールを
 * 運ばない。荷主詳細で答えたい問いは「この荷主で<strong>ログインできる人が
 * いるか</strong>」であり、その人の資格情報ではない。
 *
 * <p>実装は Security 側の {@code infrastructure/acl} が持つ。
 */
public interface LinkedAccounts {

    /**
     * 荷主に紐付いた利用者名を引く。
     *
     * @return 紐付けが無ければ空のリスト。<strong>「無い」は空欄ではなく
     *         画面で案内文に変える</strong>（空欄は「まだ調べていない」とも読める）
     */
    List<String> findUsernames(UUID shipperId);
}
