{
  description = "dp-rapportering development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            temurin-bin-21
#            gradle
          ];

          shellHook = ''
            echo "dp-rapportering development environment"
            echo "Java version:"
            java -version
            echo ""
          '';
        };
      }
    );
}
