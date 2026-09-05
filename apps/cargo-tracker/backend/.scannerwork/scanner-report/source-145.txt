package com.example.cargotracker.handling;

import com.example.cargotracker.shared.archunit.AbstractServiceArchitectureTest;

/** Handling サービスに境界の規則を適用する。規則の本体は shared の testFixtures にある。 */
class HandlingArchitectureTest extends AbstractServiceArchitectureTest {

    @Override
    protected String servicePackage() {
        return "com.example.cargotracker.handling";
    }
}
