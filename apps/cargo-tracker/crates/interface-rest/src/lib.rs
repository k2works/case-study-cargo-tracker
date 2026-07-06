//! interface-rest クレート（プレースホルダ）。

/// クレート結線検証用のプレースホルダ関数。
#[must_use]
pub fn crate_name() -> &'static str {
    "interface-rest"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn クレート名を返す() {
        assert_eq!(crate_name(), "interface-rest");
    }
}
