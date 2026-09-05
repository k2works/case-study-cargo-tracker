package com.example.cargotracker.archfixture.violating.domain.model;

import org.apache.ibatis.annotations.Mapper;

/** 違反フィクスチャ: ドメインが MyBatis を知っている。 */
@Mapper
public interface MyBatisDependentAggregate {
}
