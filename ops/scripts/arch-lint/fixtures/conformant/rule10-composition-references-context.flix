/// 正例: 合成ルートが Bounded Context を配線するのは適法
mod AppComposition {
    use TrackingReadDb.ReadDb
    use TrackingJdbcReadDb.withJdbcReadDb

    pub def routes(): List[String] = Nil
}
