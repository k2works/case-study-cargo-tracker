{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    # プロジェクトは .NET 10 (LTS) 固定（docs/design/tech_stack.md）
    dotnet-sdk_10
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo ".NET development environment activated"
    echo "  - .NET SDK: $(dotnet --version)"
  '';
}
