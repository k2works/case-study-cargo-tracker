package com.example.cargotracker.auth;

import com.example.cargotracker.shared.archunit.AbstractServiceArchitectureTest;

/** Auth サービスに境界の規則を適用する。規則の本体は shared の testFixtures にある。 */
class AuthArchitectureTest extends AbstractServiceArchitectureTest {

    @Override
    protected String servicePackage() {
        return "com.example.cargotracker.auth";
    }
}
