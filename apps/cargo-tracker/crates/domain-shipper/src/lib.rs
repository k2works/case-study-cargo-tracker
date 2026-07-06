//! shipper コンテキストのドメイン層。

use shared_kernel::Location;

/// クレート結線検証用のプレースホルダ関数。
#[must_use]
pub fn context_name() -> &'static str {
    "shipper"
}

/// shared-kernel への依存を検証するプレースホルダ関数。
///
/// # Errors
///
/// UN/LOCODE の形式が不正な場合はエラーを返す。
pub fn parse_location(code: &str) -> Result<Location, shared_kernel::SharedKernelError> {
    Location::new(code)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn コンテキスト名を返す() {
        assert_eq!(context_name(), "shipper");
    }

    #[test]
    fn shared_kernel_の_location_を利用できる() {
        assert!(parse_location("USNYC").is_ok());
    }
}
