package cargotracker.shared.audit.infrastructure

import cargotracker.shared.audit.domain.*
import scalikejdbc.*

import javax.inject.Singleton

/** IT9 US30: AuditLogPort の ScalikeJDBC 実装。 audit_log テーブル (V30) に対する INSERT / SELECT のみを行う (UPDATE / DELETE は提供しない =
  * 不変記録)。
  */
@Singleton
class ScalikeJdbcAuditLogAdapter extends AuditLogPort:

  private def rowToAuditLog(rs: WrappedResultSet): Option[AuditLog] =
    for action <- AuditAction.fromName(rs.string("action"))
    yield AuditLog(
      id = Some(AuditLogId(rs.long("id"))),
      operator = rs.string("operator"),
      action = action,
      targetType = rs.string("target_type"),
      targetId = rs.string("target_id"),
      before = rs.stringOpt("before"),
      after = rs.stringOpt("after"),
      occurredAt = rs.zonedDateTime("occurred_at").toInstant
    )

  override def record(
      operator: String,
      action: AuditAction,
      targetType: String,
      targetId: String,
      before: Option[String],
      after: Option[String]
  ): Either[String, AuditLogId] =
    try
      DB.localTx { implicit session =>
        val id = sql"""
          INSERT INTO audit_log (operator, action, target_type, target_id, before, after)
          VALUES ($operator, ${action.toString}, $targetType, $targetId, ${before.orNull}, ${after.orNull})
        """.updateAndReturnGeneratedKey.apply()
        Right(AuditLogId(id))
      }
    catch case scala.util.control.NonFatal(e) => Left(s"audit_log の記録に失敗しました: ${e.getMessage}")

  override def findByFilter(filter: AuditLogFilter): Seq[AuditLog] =
    DB.readOnly { implicit session =>
      val conditions = scala.collection.mutable.ListBuffer.empty[SQLSyntax]
      filter.fromOccurredAt.foreach(t => conditions += sqls"occurred_at >= ${java.sql.Timestamp.from(t)}")
      filter.toOccurredAt.foreach(t => conditions += sqls"occurred_at <= ${java.sql.Timestamp.from(t)}")
      filter.operator.foreach(op => conditions += sqls"operator = $op")
      filter.action.foreach(a => conditions += sqls"action = ${a.toString}")
      val whereClause =
        if conditions.isEmpty then sqls""
        else sqls"WHERE ${SQLSyntax.join(conditions.toSeq, sqls" AND ")}"
      sql"""SELECT * FROM audit_log $whereClause ORDER BY occurred_at DESC LIMIT ${filter.limit}"""
        .map(rowToAuditLog)
        .list
        .apply()
        .flatten
    }

  override def findById(id: AuditLogId): Option[AuditLog] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM audit_log WHERE id = ${id.value}"
        .map(rowToAuditLog)
        .single
        .apply()
        .flatten
    }
