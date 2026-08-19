package com.example.shared.architecture;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
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

    /** MyBatis に登録された全ステートメントの SQL を取り出す。 */
    public static List<String> statementsOf(Configuration configuration) {
        List<String> sqls = new ArrayList<>();
        for (MappedStatement statement : configuration.getMappedStatements().stream()
                .filter(MappedStatement.class::isInstance)
                .map(MappedStatement.class::cast)
                .toList()) {
            sqls.add(statement.getBoundSql(null).getSql());
        }
        return sqls;
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
                try (PreparedStatement ignored = connection.prepareStatement(sql)) {
                    // prepare できれば十分。実行はしない
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
