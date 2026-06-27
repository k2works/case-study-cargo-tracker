{ packages ? import <nixpkgs> { } }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inputsFrom = [ baseShell ];
  buildInputs = with packages; [
    ghc
    stack
    cabal-install
    haskell-language-server
    # コード品質ツール (ADR 0002)
    # NOTE (IT2): nixos-25.05 で haskellPackages.fourmolu は 0.19 系列。
    # CI (.github/workflows/ci.yml) も同 channel を使用し、フォーマット
    # 結果の揺れを抑える。
    haskellPackages.hlint
    haskellPackages.fourmolu
    haskellPackages.weeder
    haskellPackages.ghcid
    # DB マイグレーション (言語非依存で運用)
    dbmate
    # ローカル DB / メールテスト
    postgresql
  ];

  shellHook = baseShell.shellHook + ''
    echo "Welcome to the Haskell development environment!"
    ghc --version
    cabal --version
    stack --version
    hlint --version
    fourmolu --version
    dbmate --version
  '';
}
