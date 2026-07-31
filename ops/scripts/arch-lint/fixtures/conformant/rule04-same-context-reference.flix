/// 正例: 同一 Bounded Context 内の別モジュール参照は適法
mod TrackingSameContext {
    use TrackingView.TrackingDetailView

    pub def describe(v: TrackingDetailView): String =
        TrackingView.trackingNumber(v)
}
