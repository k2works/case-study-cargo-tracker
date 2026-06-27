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
    # NOTE (IT2): CI は nixos-24.05 を使用 (GHC 9.10.2 互換性のため)。
    # channel 24.05 の fourmolu は 0.14 系列で、ローカル開発の 0.19/0.20
    # と diff が出るため CI 側 format/lint は continue-on-error: true で
    # warn-only 化している。push 前は pre-commit hook で format 強制。
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
