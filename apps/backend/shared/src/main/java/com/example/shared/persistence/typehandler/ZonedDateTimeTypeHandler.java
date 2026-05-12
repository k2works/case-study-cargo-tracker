package com.example.shared.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * ZonedDateTime と PostgreSQL の timestamptz / H2 の TIMESTAMP WITH TIME ZONE を相互変換する TypeHandler。
 *
 * <p>MyBatis 3.5 系標準の {@code ZonedDateTimeTypeHandler} は
 * {@code rs.getObject(col, ZonedDateTime.class)} を呼ぶが、PostgreSQL JDBC ドライバは
 * この変換をサポートしていない（{@code timestamptz から ZonedDateTime への変換はサポートされていません}
 * 例外が出る）。本ハンドラは一旦 {@link OffsetDateTime} で取得した上で
 * {@link ZoneId#systemDefault()} のゾーンに付け直して {@link ZonedDateTime} を構築する。
 *
 * <p>PostgreSQL の {@code timestamptz} は UTC 正規化して格納されるため、元のゾーン情報は失われる前提。
 *
 * <p>{@code @MappedJdbcTypes} は意図的に付けない。付けると登録が特定の JdbcType に限定され、
 * resultMap で {@code jdbcType} を指定していない自動マッピング経路で MyBatis が {@code UNDEFINED}
 * を引いた際に標準ハンドラがヒットしてしまうため、UNDEFINED にも登録される
 * {@code register(Class, TypeHandler)} 経由の登録に統一する。
 */
@MappedTypes(ZonedDateTime.class)
public class ZonedDateTimeTypeHandler extends BaseTypeHandler<ZonedDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ZonedDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter.toOffsetDateTime());
    }

    @Override
    public ZonedDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        OffsetDateTime odt = rs.getObject(columnName, OffsetDateTime.class);
        return toZonedDateTime(odt);
    }

    @Override
    public ZonedDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        OffsetDateTime odt = rs.getObject(columnIndex, OffsetDateTime.class);
        return toZonedDateTime(odt);
    }

    @Override
    public ZonedDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        OffsetDateTime odt = cs.getObject(columnIndex, OffsetDateTime.class);
        return toZonedDateTime(odt);
    }

    private ZonedDateTime toZonedDateTime(OffsetDateTime odt) {
        return odt == null ? null : odt.atZoneSameInstant(ZoneId.systemDefault());
    }
}
