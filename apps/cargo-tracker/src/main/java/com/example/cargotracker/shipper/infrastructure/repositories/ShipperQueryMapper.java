package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shipper.application.internal.queryservices.ShipperView;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 荷主の読み取り専用マッパー（CQRS のクエリ側）。
 *
 * <p>ドメインモデルを経由せず、表示用の {@link ShipperView} を SQL から直接組み立てる。
 * 住所の連結や種別の表示名は SQL 側で作る。**画面のテンプレートで組み立てると、
 * 一覧・詳細・検索結果で少しずつ違う表示になる。**
 */
@Mapper
public interface ShipperQueryMapper {

    String SELECT_VIEW = """
            SELECT CAST(id AS VARCHAR)      AS id,
                   shipper_code             AS shipperCode,
                   shipper_type             AS shipperType,
                   CASE shipper_type WHEN 'CORPORATE' THEN '法人' ELSE '個人' END AS typeLabel,
                   name                     AS name,
                   email                    AS email,
                   COALESCE(phone, '')      AS phone,
                   address_region || address_city || COALESCE(address_street, '') AS address,
                   address_country          AS addressCountry,
                   address_postal_code      AS addressPostalCode,
                   address_region           AS addressRegion,
                   address_city             AS addressCity,
                   COALESCE(address_street, '') AS addressStreet,
                   version                  AS version
              FROM shipper
            """;

    /**
     * 一覧。荷主名・荷主コード・メールアドレスの部分一致で絞り込む（US02 / C3）。
     *
     * <p>キーワードが未指定なら全件を返す。**絞り込みを画面側の filter で行うと、
     * 件数が増えたときに全件を読み込むことになる。**
     */
    @Select("""
            <script>
            """ + SELECT_VIEW + """
            <where>
              <if test="keyword != null and keyword != ''">
                (name LIKE '%' || #{keyword} || '%'
                 OR shipper_code LIKE '%' || #{keyword} || '%'
                 OR email LIKE '%' || #{keyword} || '%')
              </if>
            </where>
            ORDER BY shipper_code DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ShipperView> search(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 絞り込み後の総件数。ページ送りの総ページ数に使う。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM shipper
            <where>
              <if test="keyword != null and keyword != ''">
                (name LIKE '%' || #{keyword} || '%'
                 OR shipper_code LIKE '%' || #{keyword} || '%'
                 OR email LIKE '%' || #{keyword} || '%')
              </if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword);

    @Select(SELECT_VIEW + """
             WHERE id = #{id,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    ShipperView findById(@Param("id") UUID id);
}
