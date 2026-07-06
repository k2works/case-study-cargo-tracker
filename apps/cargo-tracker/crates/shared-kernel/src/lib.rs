//! 共有カーネル: Bounded Context 間で共有する値オブジェクト・trait を提供する。

use serde::{Deserialize, Serialize};

/// UN/LOCODE（5 文字の英大文字）で表す場所の識別子。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct Location(String);

/// 共有カーネルのエラー型。
#[derive(Debug, thiserror::Error)]
pub enum SharedKernelError {
    /// UN/LOCODE の形式が不正な場合。
    #[error("invalid UN/LOCODE: {0}")]
    InvalidUnLocode(String),
}

impl Location {
    /// UN/LOCODE 文字列から `Location` を生成する。
    ///
    /// # Errors
    ///
    /// 5 文字の英大文字でない場合は `SharedKernelError::InvalidUnLocode` を返す。
    pub fn new(code: &str) -> Result<Self, SharedKernelError> {
        if code.len() == 5 && code.chars().all(|c| c.is_ascii_uppercase()) {
            Ok(Self(code.to_string()))
        } else {
            Err(SharedKernelError::InvalidUnLocode(code.to_string()))
        }
    }

    /// UN/LOCODE 文字列を返す。
    #[must_use]
    pub fn code(&self) -> &str {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 正しい_un_locode_から_location_を生成できる() {
        let location = Location::new("JPTYO").expect("valid UN/LOCODE");
        assert_eq!(location.code(), "JPTYO");
    }

    #[test]
    fn 不正な_un_locode_はエラーになる() {
        assert!(Location::new("tokyo").is_err());
        assert!(Location::new("JP").is_err());
    }
}
