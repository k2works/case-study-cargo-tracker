package com.example.cargotracker.security.infrastructure.acl;

import com.example.cargotracker.shipper.application.internal.outboundservices.acl.LinkedAccounts;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link LinkedAccounts} の実装（ACL のアダプタ。C11）。
 *
 * <p><strong>紐付けを持つのは Security である</strong>（ADR-013）。Shipper 側から
 * {@code users} を直接読むと、認証の表がどの BC からも読み書きされる形に近づく。
 *
 * <p><strong>読み取り専用である。</strong> 紐付けの設定は運用手順で行う
 * （ADR-013 が受け入れた代償）。ここに更新の口を作らない。
 */
@Component
public class LinkedAccountsAdapter implements LinkedAccounts {

    private final LinkedAccountMapper mapper;

    public LinkedAccountsAdapter(LinkedAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> findUsernames(UUID shipperId) {
        return shipperId == null ? List.of() : mapper.findUsernames(shipperId);
    }
}
