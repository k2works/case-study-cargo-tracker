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
                address_city, address_street, contract_number, discount_rate)
            VALUES (
                #{id,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}, #{shipperCode}, #{shipperType}, #{name}, #{email}, #{phone},
                #{addressCountry}, #{addressPostalCode}, #{addressRegion},
                #{addressCity}, #{addressStreet}, #{contractNumber}, #{discountRate})
            """)
    int insert(ShipperRecord row);

    @Select("""
            SELECT id, shipper_code, shipper_type, name, email, phone,
                   address_country, address_postal_code, address_region,
                   address_city, address_street, contract_number, discount_rate, version
              FROM shipper WHERE id = #{id,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    ShipperRecord findById(@Param("id") UUID id);

    @Select("""
            SELECT id, shipper_code, shipper_type, name, email, phone,
                   address_country, address_postal_code, address_region,
                   address_city, address_street, contract_number, discount_rate, version
              FROM shipper WHERE email = #{email}
            """)
    ShipperRecord findByEmail(@Param("email") String email);

    @Select("""
            SELECT id, shipper_code, shipper_type, name, email, phone,
                   address_country, address_postal_code, address_region,
                   address_city, address_street, contract_number, discount_rate, version
              FROM shipper WHERE shipper_code = #{shipperCode}
            """)
    ShipperRecord findByShipperCode(@Param("shipperCode") String shipperCode);

    @Select("""
            SELECT id, shipper_code, shipper_type, name, email, phone,
                   address_country, address_postal_code, address_region,
                   address_city, address_street, contract_number, discount_rate, version
              FROM shipper ORDER BY shipper_code DESC
            """)
    List<ShipperRecord> findAll();

    /**
     * 荷主コードの採番。
     *
     * <p><strong>MAX + 1 では同時登録で重複する</strong>ため、シーケンスを使う
     * （V4。IT1 持ち越し C5）。シーケンスはトランザクションの外で進むため、
     * 同時に呼んでも同じ番号は返らない。
     */
    @Select("SELECT nextval('shipper_code_seq')")
    long nextSequence();

    /**
     * 訂正（US32）。楽観的ロック付き。
     *
     * <p><strong>WHERE 句の version が要である。</strong> これを外すと、2 人が同じ荷主を
     * 同時に訂正したとき後の更新が黙って前の訂正を消す。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE shipper
               SET name = #{name}, email = #{email}, phone = #{phone},
                   address_country = #{addressCountry},
                   address_postal_code = #{addressPostalCode},
                   address_region = #{addressRegion},
                   address_city = #{addressCity},
                   address_street = #{addressStreet},
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
               AND version = #{version}
            """)
    int update(ShipperRecord row);
}
