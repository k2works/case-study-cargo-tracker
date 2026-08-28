package com.example.bookingms.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShipperMapper {

    String COLUMNS =
            "id, shipper_code, shipper_type, name, email, address, phone, contract_number, discount_rate";

    // 同一メールが複数あり得る（registerAnyway）。毎回違う荷主を提示すると営業の判断が揺れるため、
    // 最初に登録されたものに固定する
    @Select("SELECT " + COLUMNS + " FROM shipper WHERE email = #{email} ORDER BY id LIMIT 1")
    @Results(id = "shipperResult", value = {
        @Result(column = "shipper_code", property = "shipperCode"),
        @Result(column = "shipper_type", property = "shipperType"),
        @Result(column = "contract_number", property = "contractNumber"),
        @Result(column = "discount_rate", property = "discountRate")
    })
    ShipperRecord findByEmail(@Param("email") String email);

    @Select("SELECT " + COLUMNS + " FROM shipper WHERE id = #{id}")
    @Results(id = "shipperById", value = {
        @Result(column = "shipper_code", property = "shipperCode"),
        @Result(column = "shipper_type", property = "shipperType"),
        @Result(column = "contract_number", property = "contractNumber"),
        @Result(column = "discount_rate", property = "discountRate")
    })
    ShipperRecord findById(@Param("id") Long id);

    // 絞り込みの有無は動的 SQL で分ける。`#{keyword} IS NULL` のように書くと
    // PostgreSQL がパラメータの型を決められず落ちる（H2 では通るため気づきにくい）
    @Select("""
            <script>
            SELECT id, shipper_code, shipper_type, name, email, address, phone,
                   contract_number, discount_rate
            FROM shipper
            <if test="keyword != null">
            WHERE LOWER(name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
               OR LOWER(email) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            </if>
            -- 新しい順。営業の使い方は「登録した直後に一覧へ戻って入ったか確かめる」であり、
            -- 登録順だと今入れた 1 件が常に最下部に沈む
            ORDER BY id DESC
            </script>
            """)
    @Results(id = "shipperList", value = {
        @Result(column = "shipper_code", property = "shipperCode"),
        @Result(column = "shipper_type", property = "shipperType"),
        @Result(column = "contract_number", property = "contractNumber"),
        @Result(column = "discount_rate", property = "discountRate")
    })
    List<ShipperRecord> search(@Param("keyword") String keyword);

    /** 荷主コードはシーケンスから採番する。アプリ側で採番しない。 */
    @Select("SELECT NEXTVAL('shipper_code_seq')")
    long nextShipperCodeNumber();

    @Insert("""
            INSERT INTO shipper (shipper_code, shipper_type, name, email, address, phone,
                                 contract_number, discount_rate)
            VALUES (#{shipperCode}, #{shipperType}, #{name}, #{email}, #{address}, #{phone},
                    #{contractNumber}, #{discountRate})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(ShipperRecord row);

    /**
     * 登録済みの荷主を直す。
     *
     * <p><strong>荷主コードは更新しない。</strong>コードが変わると、予約から見た荷主が
     * 別人になる。採番し直すのは新規登録だけである。種別も変えない（個人と法人では
     * その後に成り立つ規則が違う）。
     */
    @Update("""
            UPDATE shipper
               SET name = #{name}, email = #{email}, address = #{address}, phone = #{phone},
                   contract_number = #{contractNumber}, discount_rate = #{discountRate},
                   updated_at = NOW()
             WHERE id = #{id}
            """)
    void update(ShipperRecord row);
}
