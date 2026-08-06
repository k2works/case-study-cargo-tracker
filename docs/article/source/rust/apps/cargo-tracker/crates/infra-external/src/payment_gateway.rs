//! 決済機関連携 ACL の reqwest 実装（US23）。
//!
//! `app-billing::PaymentGatewayPort` を外部決済機関の REST API（HTTP/JSON）へ橋渡しする。
//! 入金確認を POST し、`CONFIRMED` 応答から取引参照番号・支払方法を取り出す。契約は wiremock で
//! 検証する（正常 CONFIRMED・決済失敗）。

use app_billing::{BillingServiceError, PaymentConfirmation, PaymentGatewayPort};
use async_trait::async_trait;
use rust_decimal::Decimal;
use serde::Deserialize;

/// 外部決済機関 API を叩く決済ゲートウェイアダプター。
pub struct ReqwestPaymentGateway {
    client: reqwest::Client,
    base_url: String,
}

impl ReqwestPaymentGateway {
    /// ベース URL を指定してアダプターを生成する。
    ///
    /// 外部決済機関のハングで無限待ちにならないよう接続/読み取り timeout を設定する。
    #[must_use]
    pub fn new(base_url: impl Into<String>) -> Self {
        let client = reqwest::Client::builder()
            .connect_timeout(std::time::Duration::from_secs(5))
            .timeout(std::time::Duration::from_secs(15))
            .build()
            .unwrap_or_default();
        Self {
            client,
            base_url: base_url.into(),
        }
    }
}

/// 決済機関の入金確認レスポンス（`{ "status": "CONFIRMED", "transactionId": "...", "method": "..." }`）。
#[derive(Debug, Deserialize)]
struct PaymentResponse {
    status: String,
    #[serde(rename = "transactionId")]
    transaction_id: Option<String>,
    method: Option<String>,
}

#[async_trait]
impl PaymentGatewayPort for ReqwestPaymentGateway {
    async fn confirm_payment(
        &self,
        invoice_number: &str,
        amount: Decimal,
    ) -> Result<PaymentConfirmation, BillingServiceError> {
        let url = format!("{}/payments", self.base_url);
        let body = serde_json::json!({
            "invoiceNumber": invoice_number,
            "amount": amount.to_string(),
        });
        let resp = self
            .client
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(|e| BillingServiceError::Backend(format!("決済機関への接続に失敗: {e}")))?;
        if !resp.status().is_success() {
            return Err(BillingServiceError::Backend(format!(
                "決済に失敗しました（HTTP {}）",
                resp.status()
            )));
        }
        let payload: PaymentResponse = resp
            .json()
            .await
            .map_err(|e| BillingServiceError::Backend(format!("決済応答の解析に失敗: {e}")))?;
        if payload.status != "CONFIRMED" {
            return Err(BillingServiceError::Backend(format!(
                "入金が確認できませんでした（status={}）",
                payload.status
            )));
        }
        Ok(PaymentConfirmation {
            transaction_reference: payload.transaction_id.unwrap_or_default(),
            payment_method: payload
                .method
                .unwrap_or_else(|| "BANK_TRANSFER".to_string()),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::{method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    #[tokio::test]
    async fn 入金確認が成功するとconfirmedと取引参照が返る() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/payments"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "status": "CONFIRMED",
                "transactionId": "TXN-12345",
                "method": "BANK_TRANSFER"
            })))
            .mount(&server)
            .await;
        let gateway = ReqwestPaymentGateway::new(server.uri());

        let result = gateway
            .confirm_payment("INV-0001", Decimal::from(220_000))
            .await
            .expect("入金確認できるはず");
        assert_eq!(result.transaction_reference, "TXN-12345");
        assert_eq!(result.payment_method, "BANK_TRANSFER");
    }

    #[tokio::test]
    async fn 決済失敗時はエラーになる() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/payments"))
            .respond_with(ResponseTemplate::new(402).set_body_json(serde_json::json!({
                "status": "FAILED",
                "errorCode": "INSUFFICIENT_FUNDS"
            })))
            .mount(&server)
            .await;
        let gateway = ReqwestPaymentGateway::new(server.uri());

        let result = gateway
            .confirm_payment("INV-0002", Decimal::from(500_000))
            .await;
        assert!(matches!(result, Err(BillingServiceError::Backend(_))));
    }

    #[tokio::test]
    async fn 未確認ステータス応答はエラーになる() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/payments"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "status": "PENDING"
            })))
            .mount(&server)
            .await;
        let gateway = ReqwestPaymentGateway::new(server.uri());

        let result = gateway
            .confirm_payment("INV-0003", Decimal::from(100_000))
            .await;
        assert!(matches!(result, Err(BillingServiceError::Backend(_))));
    }
}
