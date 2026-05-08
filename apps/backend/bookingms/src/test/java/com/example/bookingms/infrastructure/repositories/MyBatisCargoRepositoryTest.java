package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.Weight;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisCargoRepositoryTest {

    @Test
    void routeSpecificationがなくても保存できる() {
        StubCargoMapper cargoMapper = new StubCargoMapper();
        MyBatisCargoRepository repository = new MyBatisCargoRepository(cargoMapper, new StubLegMapper());
        Cargo cargo = new Cargo(new BookingId("BOOKING001"), 1L, CargoType.GENERAL,
                new Weight(BigDecimal.TEN), null);

        repository.save(cargo);

        assertThat(cargoMapper.insertedCargoRecord.getSpecOriginUnlocode()).isNull();
        assertThat(cargoMapper.insertedCargoRecord.getSpecDestinationUnlocode()).isNull();
    }

    @Test
    void routeSpecification付きのレコードを再構成できる() {
        StubCargoMapper cargoMapper = new StubCargoMapper();
        CargoRecord cargoRecord = new CargoRecord();
        cargoRecord.setId(10L);
        cargoRecord.setBookingId("BOOKING001");
        cargoRecord.setShipperId(1L);
        cargoRecord.setBookingStatus(BookingStatus.PRELIMINARY.name());
        cargoRecord.setCargoType(CargoType.GENERAL.name());
        cargoRecord.setWeightKg(BigDecimal.TEN);
        cargoRecord.setSpecOriginUnlocode("JPTYO");
        cargoRecord.setSpecDestinationUnlocode("CNSHA");
        cargoRecord.setSpecArrivalDeadline(LocalDate.of(2026, 6, 30));
        cargoMapper.byBookingId = Optional.of(cargoRecord);

        MyBatisCargoRepository repository = new MyBatisCargoRepository(cargoMapper, new StubLegMapper());

        Cargo cargo = repository.findByBookingId(new BookingId("BOOKING001")).orElseThrow();

        assertThat(cargo.getRouteSpecification()).isNotNull();
        assertThat(cargo.getRouteSpecification().getOriginUnlocode()).isEqualTo("JPTYO");
    }

    @Test
    void routeSpecificationの片側だけがある場合は再構成しない() {
        StubCargoMapper cargoMapper = new StubCargoMapper();
        CargoRecord cargoRecord = new CargoRecord();
        cargoRecord.setId(11L);
        cargoRecord.setBookingId("BOOKING002");
        cargoRecord.setShipperId(1L);
        cargoRecord.setBookingStatus(BookingStatus.PRELIMINARY.name());
        cargoRecord.setCargoType(CargoType.GENERAL.name());
        cargoRecord.setWeightKg(BigDecimal.TEN);
        cargoRecord.setSpecOriginUnlocode("JPTYO");
        cargoMapper.byBookingId = Optional.of(cargoRecord);

        MyBatisCargoRepository repository = new MyBatisCargoRepository(cargoMapper, new StubLegMapper());

        Cargo cargo = repository.findByBookingId(new BookingId("BOOKING002")).orElseThrow();

        assertThat(cargo.getRouteSpecification()).isNull();
    }

    private static final class StubCargoMapper implements CargoMapper {
        private CargoRecord insertedCargoRecord;
        private Optional<CargoRecord> byBookingId = Optional.empty();

        @Override
        public void insertCargo(CargoRecord cargoRecord) {
            this.insertedCargoRecord = cargoRecord;
        }

        @Override
        public Optional<CargoRecord> findByBookingId(String bookingId) {
            return byBookingId;
        }

        @Override
        public List<CargoRecord> findAll() {
            return List.of();
        }

        @Override
        public void updateCargo(CargoRecord cargoRecord) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubLegMapper implements LegMapper {
        @Override
        public void insertLeg(LegRecord legRecord) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LegRecord> findByCargoId(Long cargoId) {
            return List.of();
        }

        @Override
        public void deleteByCargoId(Long cargoId) {
            throw new UnsupportedOperationException();
        }
    }
}
