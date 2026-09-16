package com.example.cargotracker.archfixture.violating.domain.model;

import org.springframework.stereotype.Service;

/** 違反フィクスチャ: ドメインが Spring の型を持つ（@EventSourced 以外）。 */
@Service
public class SpringDependentAggregate {
}
