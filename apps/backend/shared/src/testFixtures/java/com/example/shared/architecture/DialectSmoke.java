package com.example.shared.architecture;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;

/**
 * SQL の方言差を検出するスモークの型。
 *
 * <p>本番は PostgreSQL、ローカルの手軽な起動は H2 という構成では、方言差が両方向に起きる。
 * 「本番で緑・ローカルで赤」も「ローカルで緑・本番で赤」も同じ頻度で起きるため、
 * 全クエリを両方の DB に投げて「解釈できるか」だけを確かめる。
 *
 * <p>実行はせず prepare だけを行う。結果の正しさではなく、構文・関数・型が
 * その DB で理解されるかを見るのが目的である。
 */
public final class DialectSmoke {

    private DialectSmoke() {
    }

    /**
     * MyBatis に登録された全ステートメントの SQL を取り出す。
     *
     * <p>MyBatis は完全修飾名と短い名前の両方でステートメントを登録する。マッパーが増えて
     * 短い名前（{@code findById} など）が衝突すると、その要素は {@code MappedStatement} では
     * なく曖昧さを表す内部オブジェクトになる。取り出す側は {@code Collection<?>} として
     * 受けてから絞り込む。宣言された型のまま stream を回すと、絞り込みより先に暗黙の
     * キャストが走り {@link ClassCastException} で落ちる。
     */
    public static List<String> statementsOf(Configuration configuration) {
        return statementsOf(configuration, java.util.Map.of());
    }

    /**
     * 動的 SQL に代表パラメータを与えて、全ステートメントの SQL を取り出す。
     *
     * <p>{@code <foreach>} を含むステートメントは、パラメータが無いと SQL を組み立てられない。
     * <strong>取り出せなかったものは黙って飛ばさず落とす。</strong>飛ばすと、動的 SQL を
     * 書いた分だけ検査の穴が広がり、方言差を見逃す範囲が増える。
     *
     * @param samples {@code @Param} の名前から代表値への対応。{@code <foreach>} が回す
     *     コレクションには、空でない値を与える（空だと {@code IN ()} になり構文が壊れる）
     */
    public static List<String> statementsOf(Configuration configuration,
            java.util.Map<String, Object> samples) {
        Collection<?> registered = configuration.getMappedStatements();
        List<String> sqls = new ArrayList<>();
        for (Object entry : registered) {
            if (entry instanceof MappedStatement statement) {
                sqls.add(sqlOf(statement, samples));
            }
        }
        return sqls;
    }

    private static String sqlOf(MappedStatement statement, java.util.Map<String, Object> samples) {
        try {
            return statement.getBoundSql(null).getSql();
        } catch (RuntimeException withoutParameters) {
            try {
                return statement.getBoundSql(samples).getSql();
            } catch (RuntimeException withSamples) {
                throw new IllegalStateException(statement.getId()
                        + " の SQL を組み立てられません。動的 SQL の代表パラメータを"
                        + " DialectSmokeTest の samples に足してください", withSamples);
            }
        }
    }

    /**
     * 与えられた SQL がその DataSource の DB で解釈できるかを確かめる。
     *
     * @return 解釈できなかった SQL とその理由。空であれば方言差は無い
     */
    public static List<String> unparseable(DataSource dataSource, List<String> sqls) {
        List<String> failures = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            for (String sql : sqls) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    // 構文だけでなくパラメータの型が決まることまで確かめる。
                    // `#{x} IS NULL` のような書き方は構文としては通るが、PostgreSQL は
                    // パラメータの型を決められず実行時に落ちる（H2 では通るため気づきにくい）
                    statement.getParameterMetaData().getParameterCount();
                } catch (SQLException e) {
                    failures.add(sql + " -> " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("方言スモークの接続に失敗しました", e);
        }
        return failures;
    }
}
