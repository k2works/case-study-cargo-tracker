//! booking コンテキストのアプリケーション層。

/// クレート結線検証用のプレースホルダ関数。
#[must_use]
pub fn service_context() -> &'static str {
    domain_booking::context_name()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ドメイン層のコンテキスト名を返す() {
        assert_eq!(service_context(), "booking");
    }
}
