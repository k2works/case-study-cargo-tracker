package com.example.cargotracker.shipper.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 荷主の MyBatis マッパー。
 *
 * <p>UUID のパラメータには TypeHandler を明示する。MyBatis 標準に UUID の TypeHandler は無く、
 * アプリケーションは {@code mybatis.type-handlers-package} で登録しているが、
 * <strong>その設定を読まない解析ツール（JIG）では SQL の抽出に失敗し、CRUD 図から
 * このマッパーが丸ごと欠落する。</strong> 明示すれば設定への依存が消える。
 */
@Mapper
public interface ShipperMapper {

    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO shipper (
                id, shipper_code, shipper_type, name, email, phone,
                address_country, address_postal_code, address_region,
                address_city, address_street)
            VALUES (
                #{id,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}, #{shipperCode}, #{shipperType}, #{name}, #{email}, #{phone},
                #{addressCountry}, #{addressPostalCode}, #{addressRegion},
                #{addressCity}, #{addressStreet})
            """)
    int insert(ShipperRecord record);

    @Select("""
            SELECT id, shipper_code, shipper_type, name, email, phone,
                   address_country, address_postal_code, address_region,
                   address_city, address_street
              FROM shipper WHERE id = #{id,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    ShipperRecord findById(@Param("id") UUID id);

    @Select("""
            SELECT id, shipper_code, shipper_type, name, email, phone,
                   address_country, address_postal_code, address_region,
                   address_city, address_street
              FROM shipper WHERE email = #{email}
            """)
    ShipperRecord findByEmail(@Param("email") String email);

    @Select("""
            SELECT id, shipper_code, shipper_type, name, email, phone,
                   address_country, address_postal_code, address_region,
                   address_city, address_street
              FROM shipper ORDER BY shipper_code DESC
            """)
    List<ShipperRecord> findAll();

    /** 荷主コードの採番用。件数ではなく最大値を使う（削除で番号が重複しないため）。 */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(shipper_code, 5) AS INTEGER)), 0) FROM shipper")
    long maxSequence();
}
