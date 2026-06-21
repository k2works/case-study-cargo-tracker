package cargotracker.shipper.infrastructure.repositories

import cargotracker.shared.domain.{ShipperId, ShipperType}
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.shipper.domain.model.valueobjects.DiscountRate
import scalikejdbc.*

import javax.inject.Singleton
@Singleton
class ScalikeJdbcShipperRepository extends ShipperRepository:

  private def rowToShipper(rs: WrappedResultSet): Option[Shipper] =
    for
      shipperType <- ShipperType.fromName(rs.string("shipper_type"))
      shipper <- shipperType match
        case ShipperType.Individual =>
          Shipper
            .individual(
              ShipperId.unsafeFrom(rs.string("shipper_code")),
              rs.string("name"),
              rs.string("email"),
              rs.string("phone"),
              rs.string("address")
            )
            .toOption
        case ShipperType.Corporate =>
          for
            rate <- DiscountRate(rs.double("discount_rate")).toOption
            s <- Shipper
              .corporate(
                ShipperId.unsafeFrom(rs.string("shipper_code")),
                rs.string("name"),
                rs.string("email"),
                rs.string("phone"),
                rs.string("address"),
                rs.string("contract_number"),
                rate
              )
              .toOption
          yield s
    yield shipper

  override def findById(shipperId: ShipperId): Option[Shipper] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM shipper WHERE shipper_code = ${shipperId.value}"
        .map(rowToShipper)
        .single
        .apply()
        .flatten
    }

  override def findByEmail(email: String): Option[Shipper] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM shipper WHERE email = $email"
        .map(rowToShipper)
        .single
        .apply()
        .flatten
    }

  override def findAll(): Seq[Shipper] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM shipper ORDER BY shipper_code"
        .map(rowToShipper)
        .list
        .apply()
        .flatten
    }

  override def save(shipper: Shipper): Unit =
    DB.localTx { implicit session =>
      val existing =
        sql"SELECT id FROM shipper WHERE shipper_code = ${shipper.shipperId.value}"
          .map(_.long("id"))
          .single
          .apply()

      existing match
        case Some(_) =>
          sql"""
            UPDATE shipper
            SET name = ${shipper.name},
                email = ${shipper.email},
                phone = ${shipper.phone},
                address = ${shipper.address},
                shipper_type = ${shipper.shipperType.toString},
                contract_number = ${shipper.contractNumber.orNull},
                discount_rate = ${shipper.discountRate.value},
                updated_at = CURRENT_TIMESTAMP
            WHERE shipper_code = ${shipper.shipperId.value}
          """.update.apply()
        case None =>
          sql"""
            INSERT INTO shipper
              (shipper_code, name, email, phone, address, shipper_type, contract_number, discount_rate)
            VALUES
              (${shipper.shipperId.value}, ${shipper.name}, ${shipper.email},
               ${shipper.phone}, ${shipper.address}, ${shipper.shipperType.toString},
               ${shipper.contractNumber.orNull}, ${shipper.discountRate.value})
          """.update.apply()
    }

  override def nextIdentity(): ShipperId =
    DB.readOnly { implicit session =>
      val next = sql"""
        SELECT COALESCE(MAX(CAST(SUBSTRING(shipper_code FROM 4) AS INTEGER)), 0) + 1 AS next
        FROM shipper
      """.map(_.int("next")).single.apply().getOrElse(1)
      ShipperId.unsafeFrom(f"SH-${next}%06d")
    }
