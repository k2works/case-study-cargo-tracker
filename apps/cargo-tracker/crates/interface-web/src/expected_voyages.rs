//! 経路確定（US09）の TOCTOU 照合で使う「期待航海番号列」のエンコード／デコードを
//! 一箇所に集約する。生成（テンプレートの hidden フィールド）と解釈（ハンドラ）が
//! 別箇所に分散すると、区切り文字の規約ずれで安全機構が壊れるため、両方向を本モジュールに集約し
//! round-trip テストで正しさを担保する（IT4 Try#5）。
//!
//! エンコード規約:
//! - 1 候補内の複数航海番号は `,`（カンマ）区切り
//! - 複数候補は `;`（セミコロン）区切り

/// 区切り文字（候補内・候補間）。
const VOYAGE_SEP: char = ',';
const CANDIDATE_SEP: char = ';';

/// 1 候補の航海番号列を `,` 区切りにエンコードする（テンプレートの `voyage_csv` 用）。
#[must_use]
pub fn encode_candidate(voyages: &[String]) -> String {
    voyages.join(&VOYAGE_SEP.to_string())
}

/// 候補ごとの `voyage_csv` を `;` で連結し、hidden フィールドの値を組み立てる。
#[must_use]
pub fn encode_list(candidate_csvs: &[String]) -> String {
    candidate_csvs.join(&CANDIDATE_SEP.to_string())
}

/// hidden フィールドの値から、指定インデックスの候補の航海番号列を復元する。
/// 該当インデックスが無い場合は空 `Vec` を返す。
#[must_use]
pub fn decode_selected(encoded_list: &str, index: usize) -> Vec<String> {
    encoded_list
        .split(CANDIDATE_SEP)
        .nth(index)
        .unwrap_or("")
        .split(VOYAGE_SEP)
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(String::from)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn v(items: &[&str]) -> Vec<String> {
        items.iter().map(|s| (*s).to_string()).collect()
    }

    #[test]
    fn 単一候補をエンコードしてデコードすると元に戻る() {
        let candidate = v(&["V001", "V002"]);
        let list = encode_list(&[encode_candidate(&candidate)]);
        assert_eq!(decode_selected(&list, 0), candidate);
    }

    #[test]
    fn 複数候補の各インデックスが正しく復元される() {
        let c0 = v(&["V001"]);
        let c1 = v(&["V010", "V011", "V012"]);
        let c2 = v(&["V020", "V021"]);
        let list = encode_list(&[
            encode_candidate(&c0),
            encode_candidate(&c1),
            encode_candidate(&c2),
        ]);
        assert_eq!(decode_selected(&list, 0), c0);
        assert_eq!(decode_selected(&list, 1), c1);
        assert_eq!(decode_selected(&list, 2), c2);
    }

    #[test]
    fn 範囲外インデックスは空を返す() {
        let list = encode_list(&[encode_candidate(&v(&["V001"]))]);
        assert!(decode_selected(&list, 5).is_empty());
    }

    #[test]
    fn 空文字列のデコードは空を返す() {
        assert!(decode_selected("", 0).is_empty());
    }
}
