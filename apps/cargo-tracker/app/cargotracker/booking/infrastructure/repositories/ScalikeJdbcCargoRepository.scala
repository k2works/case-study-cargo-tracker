package cargotracker.booking.infrastructure.repositories

import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{
  BookingId,
  BookingStatus,
  CargoSpec,
  HazardousDeclaration,
  Itinerary,
  ItineraryLeg,
  RefrigerationSpec,
  RouteSpecification,
  TemperatureUnit
}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}
import scalikejdbc.*

import javax.inject.Singleton

@Singleton
class ScalikeJdbcCargoRepository extends CargoRepository:

  private def rowToCargoAndId(rs: WrappedResultSet): Option[(Long, Cargo.Snapshot)] =
    for
      ct <- CargoType.fromName(rs.string("cargo_type"))
      status <- BookingStatus.fromName(rs.string("booking_status"))
      routeSpec <- RouteSpecification(
        Location.unsafeFrom(rs.string("origin_unlocode")),
        Location.unsafeFrom(rs.string("destination_unlocode")),
        rs.localDate("arrival_deadline")
      ).toOption
    yield
      val hazardous = for
        hc <- rs.stringOpt("hazardous_class")
        un <- rs.stringOpt("hazardous_un_number")
        psn <- rs.stringOpt("hazardous_proper_name")
      yield HazardousDeclaration(hc, un, psn)

      val refrigeration = for
        minT <- rs.intOpt("refrigeration_min_temp")
        maxT <- rs.intOpt("refrigeration_max_temp")
        unit <- rs.stringOpt("refrigeration_unit").flatMap(TemperatureUnit.fromName)
        spec <- RefrigerationSpec(minT, maxT, unit).toOption
      yield spec

      val spec = CargoSpec(
        cargoType = ct,
        weight = Weight.unsafeFrom(rs.long("weight_kg")),
        description = rs.stringOpt("description"),
        quantity = rs.intOpt("quantity"),
        hazardous = hazardous,
        refrigeration = refrigeration
      )
      val csvItinerary = rs
        .stringOpt("itinerary_voyages")
        .filter(_.nonEmpty)
        .map(s => Itinerary.unsafeFrom(s.split(",").toList))
      val snapshot = Cargo.Snapshot(
        bookingId = BookingId.unsafeFrom(rs.string("tracking_id")),
        shipperId = ShipperId.unsafeFrom(rs.string("shipper_code")),
        routeSpecification = routeSpec,
        cargoSpec = spec,
        status = status,
        version = rs.int("version"),
        itinerary = csvItinerary,
        trackingNumber = rs.stringOpt("tracking_number")
      )
      (rs.long("id"), snapshot)

  private def loadLegs(cargoId: Long)(implicit session: DBSession): List[ItineraryLeg] =
    sql"""SELECT voyage_number, from_location_unlocode, to_location_unlocode
          FROM cargo_itinerary_leg
          WHERE cargo_id = $cargoId
          ORDER BY seq_number"""
      .map { rs =>
        ItineraryLeg(
          voyageNumber = rs.string("voyage_number"),
          from = Location.unsafeFrom(rs.string("from_location_unlocode")),
          to = Location.unsafeFrom(rs.string("to_location_unlocode"))
        )
      }
      .list
      .apply()

  private def reconstructWithLegs(
      cargoId: Long,
      snapshot: Cargo.Snapshot
  )(implicit session: DBSession): Cargo =
    val legs = loadLegs(cargoId)
    val enriched =
      if legs.isEmpty then snapshot
      else snapshot.copy(itinerary = Some(Itinerary.unsafeFromLegs(legs)))
    Cargo.reconstruct(enriched)

  override def findById(bookingId: BookingId): Option[Cargo] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM cargo WHERE tracking_id = ${bookingId.value}"
        .map(rowToCargoAndId)
        .single
        .apply()
        .flatten
        .map { case (id, snap) => reconstructWithLegs(id, snap) }
    }

  override def findAll(): Seq[Cargo] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM cargo ORDER BY tracking_id"
        .map(rowToCargoAndId)
        .list
        .apply()
        .flatten
        .map { case (id, snap) => reconstructWithLegs(id, snap) }
    }

  override def findByStatus(status: BookingStatus): Seq[Cargo] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM cargo WHERE booking_status = ${status.toString} ORDER BY tracking_id"
        .map(rowToCargoAndId)
        .list
        .apply()
        .flatten
        .map { case (id, snap) => reconstructWithLegs(id, snap) }
    }

  private def replaceLegs(cargoId: Long, legs: List[ItineraryLeg])(implicit session: DBSession): Unit =
    sql"DELETE FROM cargo_itinerary_leg WHERE cargo_id = $cargoId".update.apply()
    legs.zipWithIndex.foreach { case (leg, idx) =>
      sql"""INSERT INTO cargo_itinerary_leg
              (cargo_id, seq_number, voyage_number, from_location_unlocode, to_location_unlocode)
            VALUES ($cargoId, ${idx + 1}, ${leg.voyageNumber}, ${leg.from.unLocode}, ${leg.to.unLocode})""".update
        .apply()
    }

  override def save(cargo: Cargo): Unit =
    DB.localTx { implicit session =>
      val existing =
        sql"SELECT id FROM cargo WHERE tracking_id = ${cargo.bookingId.value}"
          .map(_.long("id"))
          .single
          .apply()

      val haz = cargo.cargoSpec.hazardous
      val refrig = cargo.cargoSpec.refrigeration
      val cargoId = existing match
        case Some(id) =>
          val updated = sql"""
            UPDATE cargo
            SET shipper_code = ${cargo.shipperId.value},
                origin_unlocode = ${cargo.routeSpecification.origin.unLocode},
                destination_unlocode = ${cargo.routeSpecification.destination.unLocode},
                arrival_deadline = ${cargo.routeSpecification.arrivalDeadline},
                cargo_type = ${cargo.cargoSpec.cargoType.toString},
                weight_kg = ${cargo.cargoSpec.weight.kg},
                description = ${cargo.cargoSpec.description.orNull},
                quantity = ${cargo.cargoSpec.quantity.orNull},
                hazardous_class = ${haz.map(_.hazardClass).orNull},
                hazardous_un_number = ${haz.map(_.unNumber).orNull},
                hazardous_proper_name = ${haz.map(_.properShippingName).orNull},
                refrigeration_min_temp = ${refrig.map(_.minTemperature).orNull},
                refrigeration_max_temp = ${refrig.map(_.maxTemperature).orNull},
                refrigeration_unit = ${refrig.map(_.unit.toString).orNull},
                booking_status = ${cargo.status.toString},
                itinerary_voyages = ${cargo.itinerary.map(_.voyageNumbers.mkString(",")).orNull},
                tracking_number = ${cargo.trackingNumber.orNull},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE tracking_id = ${cargo.bookingId.value} AND version = ${cargo.version}
          """.update.apply()
          if updated == 0 then
            throw cargotracker.shared.domain.OptimisticLockException(
              entityType = "Cargo",
              identifier = cargo.bookingId.value
            )
          id
        case None =>
          sql"""
            INSERT INTO cargo
              (tracking_id, shipper_code, origin_unlocode, destination_unlocode,
               arrival_deadline, cargo_type, weight_kg, description, quantity,
               hazardous_class, hazardous_un_number, hazardous_proper_name,
               refrigeration_min_temp, refrigeration_max_temp, refrigeration_unit,
               booking_status, itinerary_voyages, tracking_number)
            VALUES
              (${cargo.bookingId.value}, ${cargo.shipperId.value},
               ${cargo.routeSpecification.origin.unLocode},
               ${cargo.routeSpecification.destination.unLocode},
               ${cargo.routeSpecification.arrivalDeadline},
               ${cargo.cargoSpec.cargoType.toString},
               ${cargo.cargoSpec.weight.kg},
               ${cargo.cargoSpec.description.orNull},
               ${cargo.cargoSpec.quantity.orNull},
               ${haz.map(_.hazardClass).orNull},
               ${haz.map(_.unNumber).orNull},
               ${haz.map(_.properShippingName).orNull},
               ${refrig.map(_.minTemperature).orNull},
               ${refrig.map(_.maxTemperature).orNull},
               ${refrig.map(_.unit.toString).orNull},
               ${cargo.status.toString},
               ${cargo.itinerary.map(_.voyageNumbers.mkString(",")).orNull},
               ${cargo.trackingNumber.orNull})
          """.updateAndReturnGeneratedKey.apply()

      // IT7 0.14: 経路 leg 詳細を cargo_itinerary_leg テーブルに同期する
      cargo.itinerary.map(_.legs).foreach(legs => replaceLegs(cargoId, legs))
    }

  override def nextIdentity(): BookingId =
    DB.readOnly { implicit session =>
      val next = sql"""
        SELECT COALESCE(MAX(CAST(SUBSTRING(tracking_id FROM 4) AS INTEGER)), 0) + 1 AS next
        FROM cargo
      """.map(_.int("next")).single.apply().getOrElse(1)
      BookingId.unsafeFrom(f"BK-${next}%06d")
    }
