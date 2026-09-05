package com.example.cargotracker.gateway;

import com.example.cargotracker.shared.archunit.AbstractServiceArchitectureTest;

/** Gateway サービスに境界の規則を適用する。規則の本体は shared の testFixtures にある。 */
class GatewayArchitectureTest extends AbstractServiceArchitectureTest {

    @Override
    protected String servicePackage() {
        return "com.example.cargotracker.gateway";
    }
}
