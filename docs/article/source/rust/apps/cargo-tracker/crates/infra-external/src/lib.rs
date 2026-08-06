//! infra-external クレート: 外部システム連携アダプター（reqwest）。
//!
//! 外部システムとの連携は ACL ポート trait の背後に reqwest クライアント実装を隠蔽し、
//! テストでは wiremock でスタブする（test_strategy §4）。IT8 で決済機関連携を実装する。

mod payment_gateway;

pub use payment_gateway::ReqwestPaymentGateway;

/// クレート結線検証用のプレースホルダ関数。
#[must_use]
pub fn crate_name() -> &'static str {
    "infra-external"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn クレート名を返す() {
        assert_eq!(crate_name(), "infra-external");
    }
}
