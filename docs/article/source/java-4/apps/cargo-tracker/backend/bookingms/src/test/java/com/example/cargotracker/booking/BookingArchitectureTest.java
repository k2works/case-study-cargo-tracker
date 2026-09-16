package com.example.cargotracker.booking;

import com.example.cargotracker.shared.archunit.AbstractServiceArchitectureTest;

/** Booking サービスに境界の規則を適用する。規則の本体は shared の testFixtures にある。 */
class BookingArchitectureTest extends AbstractServiceArchitectureTest {

    @Override
    protected String servicePackage() {
        return "com.example.cargotracker.booking";
    }
}
