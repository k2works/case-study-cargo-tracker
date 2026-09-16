package com.example.cargotracker.routing;

import com.example.cargotracker.shared.archunit.AbstractServiceArchitectureTest;

/** Routing サービスに境界の規則を適用する。規則の本体は shared の testFixtures にある。 */
class RoutingArchitectureTest extends AbstractServiceArchitectureTest {

    @Override
    protected String servicePackage() {
        return "com.example.cargotracker.routing";
    }
}
