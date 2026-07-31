/// 負例: 共有カーネルが Bounded Context のモジュールを参照している
/// 合成ルートを shared 配下に置いたときに実際に生じる形（IT2 レビュー H9）
mod SharedHtmlDashboard {
    use TrackingView.TrackingDetailView

    pub def summary(v: TrackingDetailView): String =
        TrackingView.trackingNumber(v)
}
