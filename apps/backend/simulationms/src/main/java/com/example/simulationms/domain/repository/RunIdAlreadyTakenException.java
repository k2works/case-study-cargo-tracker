package com.example.simulationms.domain.repository;

/**
 * その実行 ID は既に使われている。
 *
 * <p><strong>採番を裁くのは一意制約である。</strong>「今日の件数 + 1」で番号を決めると、
 * 数えてから書くまでの間に別の実行が入り込む。数え直しても同じことが起きるので、
 * <strong>数える側では防げない</strong>——実際に書いてみて、断られたら次の番号を採る。
 *
 * <p>永続化の都合をアプリケーション層へ持ち込まないために、DB の例外はここで
 * この型に変換する。アプリケーション層が Spring の例外型を知る必要はない。
 */
public class RunIdAlreadyTakenException extends RuntimeException {

    public RunIdAlreadyTakenException(String runId, Throwable cause) {
        super("実行 ID は既に使われています: " + runId, cause);
    }
}
