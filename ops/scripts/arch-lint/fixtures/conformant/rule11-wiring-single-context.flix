/// 正例: 配線ファイルは 1 つの Bounded Context のアダプタを適用するだけ
mod EstimationWiring {
    use EstimateRepo.EstimateRepo
    use EstimationJdbcEstimateRepo.withJdbcEstimateRepo

    pub def withDb(f: Unit -> a): a = withJdbcEstimateRepo(f)
}
