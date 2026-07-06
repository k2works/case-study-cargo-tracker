//! Cargo Tracker サーバーの composition root（プレースホルダ）。

/// 起動メッセージを返す。
fn startup_message() -> &'static str {
    "cargo-tracker-server"
}

#[tokio::main]
async fn main() {
    println!("{}", startup_message());
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 起動メッセージを返す() {
        assert_eq!(startup_message(), "cargo-tracker-server");
    }
}
