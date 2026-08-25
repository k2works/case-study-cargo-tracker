package com.example.handlingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomsStatusHistoryMapper {

    @Insert("""
            INSERT INTO customs_status_history (
                customs_declaration_id, from_status, to_status, changed_by, changed_at, reason)
            VALUES (
                #{customsDeclarationId}, #{fromStatus}, #{toStatus}, #{changedBy},
                #{changedAt}, #{reason})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(CustomsStatusHistoryRecord row);

    /** 古い順に読む。監査の履歴は起きた順に並べる。 */
    @Select("""
            SELECT id, customs_declaration_id, from_status, to_status,
                   changed_by, changed_at, reason
              FROM customs_status_history
             WHERE customs_declaration_id = #{declarationId}
             ORDER BY changed_at, id
            """)
    @Result(column = "customs_declaration_id", property = "customsDeclarationId")
    @Result(column = "from_status", property = "fromStatus")
    @Result(column = "to_status", property = "toStatus")
    @Result(column = "changed_by", property = "changedBy")
    @Result(column = "changed_at", property = "changedAt")
    List<CustomsStatusHistoryRecord> findByDeclarationId(@Param("declarationId") long declarationId);
}
