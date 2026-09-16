package com.example.cargotracker.simulation;

import com.example.cargotracker.shared.archunit.AbstractServiceArchitectureTest;

/**
 * Simulation サービスに境界の規則を適用する。規則の本体は shared の testFixtures にある。
 *
 * <p><b>業務サービスと同じ規則を当てる。</b> 業務ではなく「業務が成立していることを
 * 確かめる手段」だが（[ADR-0020]）、層の分け方まで別にすると読み方が 2 つになる。</p>
 */
class SimulationArchitectureTest extends AbstractServiceArchitectureTest {

    @Override
    protected String servicePackage() {
        return "com.example.cargotracker.simulation";
    }
}
