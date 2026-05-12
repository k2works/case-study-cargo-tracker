package com.example.shared.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * LocalDateTime と PostgreSQL の timestamptz / 通常 timestamp を相互変換する TypeHandler。
 *
 * <p>PostgreSQL JDBC ドライバは {@code rs.getObject(col, LocalDateTime.class)} を timestamptz カラムに対して
 * 呼ぶと {@code TIMESTAMPTZ 型のカラムの値を指定の型 java.time.LocalDateTime に変換できません}
 * 例外を投げる。本ハンドラは一旦 {@link OffsetDateTime} で取得した上で
 * {@link ZoneId#systemDefault()} に合わせて {@link LocalDateTime} に落とすことで両カラム型に対応する。
 *
 * <p>{@code @MappedJdbcTypes} は意図的に付けない。付けると登録が特定の JdbcType に限定され、
 * resultMap で {@code jdbcType} を指定していない自動マッピング経路で MyBatis が {@code UNDEFINED}
 * を引いた際に標準ハンドラがヒットしてしまうため、UNDEFINED にも登録される
 * {@code register(Class, TypeHandler)} 経由の登録に統一する。
 */
@MappedTypes(LocalDateTime.class)
public class LocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter.atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        OffsetDateTime odt = rs.getObject(columnName, OffsetDateTime.class);
        return toLocalDateTime(odt);
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        OffsetDateTime odt = rs.getObject(columnIndex, OffsetDateTime.class);
        return toLocalDateTime(odt);
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        OffsetDateTime odt = cs.getObject(columnIndex, OffsetDateTime.class);
        return toLocalDateTime(odt);
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt == null ? null : odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
