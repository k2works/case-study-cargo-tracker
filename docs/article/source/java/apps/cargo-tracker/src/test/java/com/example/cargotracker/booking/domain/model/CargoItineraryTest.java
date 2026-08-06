package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.shared.domain.model.Location;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CargoItineraryTest {

    private static final Location JPTYO = new Location("JPTYO");
    private static final Location USNYC = new Location("USNYC");
    private static final Location CNSHA = new Location("CNSHA");

    private static final LocalDateTime LOAD_TIME = LocalDateTime.of(2026, 5, 10, 18, 0);
    private static final LocalDateTime UNLOAD_TIME = LocalDateTime.of(2026, 6, 10, 8, 0);

    // --- Leg テスト ---

    @Test
    void Legを有効なデータで作成できる() {
        assertDoesNotThrow(() -> new Leg("V001", JPTYO, USNYC, LOAD_TIME, UNLOAD_TIME));
    }

    @Test
    void Leg_voyageNumberがnullの場合に例外が発生する() {
        assertThrows(NullPointerException.class,
                () -> new Leg(null, JPTYO, USNYC, LOAD_TIME, UNLOAD_TIME));
    }

    @Test
    void Leg_voyageNumberが空文字の場合に例外が発生する() {
        assertThrows(IllegalArgumentException.class,
                () -> new Leg("", JPTYO, USNYC, LOAD_TIME, UNLOAD_TIME));
    }

    @Test
    void Leg_loadLocationがnullの場合に例外が発生する() {
        assertThrows(NullPointerException.class,
                () -> new Leg("V001", null, USNYC, LOAD_TIME, UNLOAD_TIME));
    }

    @Test
    void Leg_unloadLocationがnullの場合に例外が発生する() {
        assertThrows(NullPointerException.class,
                () -> new Leg("V001", JPTYO, null, LOAD_TIME, UNLOAD_TIME));
    }

    @Test
    void Leg_loadTimeがnullの場合に例外が発生する() {
        assertThrows(NullPointerException.class,
                () -> new Leg("V001", JPTYO, USNYC, null, UNLOAD_TIME));
    }

    @Test
    void Leg_unloadTimeがnullの場合に例外が発生する() {
        assertThrows(NullPointerException.class,
                () -> new Leg("V001", JPTYO, USNYC, LOAD_TIME, null));
    }

    @Test
    void Leg_loadTimeがunloadTime以降の場合に例外が発生する() {
        assertThrows(IllegalArgumentException.class,
                () -> new Leg("V001", JPTYO, USNYC, UNLOAD_TIME, LOAD_TIME));
    }

    @Test
    void Legのフィールドが正しく設定される() {
        Leg leg = new Leg("V001", JPTYO, USNYC, LOAD_TIME, UNLOAD_TIME);
        assertEquals("V001", leg.voyageNumber());
        assertEquals(JPTYO, leg.loadLocation());
        assertEquals(USNYC, leg.unloadLocation());
        assertEquals(LOAD_TIME, leg.loadTime());
        assertEquals(UNLOAD_TIME, leg.unloadTime());
    }

    // --- CargoItinerary テスト ---

    @Test
    void CargoItineraryを1件のLegで作成できる() {
        Leg leg = new Leg("V001", JPTYO, USNYC, LOAD_TIME, UNLOAD_TIME);
        assertDoesNotThrow(() -> new CargoItinerary(List.of(leg)));
    }

    @Test
    void CargoItineraryを複数のLegで作成できる() {
        LocalDateTime midTime = LocalDateTime.of(2026, 5, 25, 12, 0);
        Leg leg1 = new Leg("V001", JPTYO, CNSHA, LOAD_TIME, midTime);
        Leg leg2 = new Leg("V002", CNSHA, USNYC, midTime.plusHours(6), UNLOAD_TIME);
        assertDoesNotThrow(() -> new CargoItinerary(List.of(leg1, leg2)));
    }

    @Test
    void CargoItinerary_legsがnullの場合に例外が発生する() {
        assertThrows(NullPointerException.class, () -> new CargoItinerary(null));
    }

    @Test
    void CargoItinerary_legsが空の場合に例外が発生する() {
        assertThrows(IllegalArgumentException.class, () -> new CargoItinerary(List.of()));
    }

    @Test
    void expectedArrivalTimeは最後のLegのunloadTimeを返す() {
        LocalDateTime midTime = LocalDateTime.of(2026, 5, 25, 12, 0);
        Leg leg1 = new Leg("V001", JPTYO, CNSHA, LOAD_TIME, midTime);
        Leg leg2 = new Leg("V002", CNSHA, USNYC, midTime.plusHours(6), UNLOAD_TIME);
        CargoItinerary itinerary = new CargoItinerary(List.of(leg1, leg2));
        assertEquals(UNLOAD_TIME, itinerary.expectedArrivalTime());
    }

    @Test
    void CargoItineraryのlegsは変更不可である() {
        Leg leg = new Leg("V001", JPTYO, USNYC, LOAD_TIME, UNLOAD_TIME);
        CargoItinerary itinerary = new CargoItinerary(List.of(leg));
        assertThrows(UnsupportedOperationException.class, () -> itinerary.legs().add(leg));
    }
}
