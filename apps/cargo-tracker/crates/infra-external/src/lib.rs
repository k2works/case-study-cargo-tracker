//! infra-external クレート（プレースホルダ）。

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
