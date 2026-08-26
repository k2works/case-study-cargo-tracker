package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * {@code invoice.booking_id} の単独 UNIQUE を外す（[ADR-028] 決定 3）。
 *
 * <p><strong>SQL で書けないため Java にした。</strong>制約は列インラインの {@code UNIQUE} で
 * 作られており、<strong>名前が DB ごとに違う</strong>——PostgreSQL は
 * {@code invoice_booking_id_key}、H2 は {@code CONSTRAINT_9F} のような生成名である（実測）。
 * 名前を書いた {@code DROP CONSTRAINT IF EXISTS} は、H2 では素通りして制約が残り、
 * <strong>CI は緑のままローカル起動だけが「同じ予約に出し直せない」状態になる</strong>。
 *
 * <p>ここでは名簿を持たず、{@code information_schema} から
 * 「{@code booking_id} 1 列だけの UNIQUE 制約」を引いて落とす。
 *
 * <p>落としたあとの一意性は V6 が {@code (booking_id, void_marker)} で引き受ける。
 */
public class V5__drop_booking_unique extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement statement = context.getConnection().createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT tc.constraint_name
                          FROM information_schema.table_constraints tc
                          JOIN information_schema.key_column_usage kcu
                            ON kcu.constraint_name = tc.constraint_name
                           AND kcu.table_name = tc.table_name
                         WHERE LOWER(tc.table_name) = 'invoice'
                           AND tc.constraint_type = 'UNIQUE'
                           AND LOWER(kcu.column_name) = 'booking_id'
                        """)) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }

        try (Statement statement = context.getConnection().createStatement()) {
            for (String name : names) {
                // 複数列の UNIQUE は落とさない（booking_id を含むだけの制約を巻き込まない）
                if (columnCountOf(context, name) == 1) {
                    statement.execute("ALTER TABLE invoice DROP CONSTRAINT \"" + name + "\"");
                }
            }
        }
    }

    /**
     * その制約が何列で構成されているか。
     *
     * <p><strong>値は束縛する。</strong>制約名は DB から読んだ値であり、文字列連結で
     * SQL に混ぜると、名前に引用符を含む DB で壊れる（SpotBugs も止める）。
     */
    private int columnCountOf(Context context, String name) throws Exception {
        try (PreparedStatement statement = context.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM information_schema.key_column_usage"
                        + " WHERE constraint_name = ? AND LOWER(table_name) = 'invoice'")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }
}
