{
  description = "owenclave development environment - android proxy client";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = import nixpkgs { inherit system; };
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              jdk21
              go_1_26
              golangci-lint
              gradle
              android-tools
              androidsdk
              androidsdk-tools
              android-ndk
              pkg-config
              git
              gomobile
            ];

            ANDROID_HOME = "${pkgs.androidsdk}/share/android-sdk";
            ANDROID_SDK_ROOT = "${pkgs.androidsdk}/share/android-sdk";
            ANDROID_NDK_HOME = "${pkgs.android-ndk}/share/android-ndk";
            ANDROID_NDK_ROOT = "${pkgs.android-ndk}/share/android-ndk";

            shellHook = ''
              export GOCACHE="''${XDG_CACHE_HOME:-$HOME/.cache}/go-build"
              export GOMODCACHE="''${XDG_CACHE_HOME:-$HOME/.cache}/go-mod"
              echo "owenclave dev shell"
              echo "  ANDROID_HOME=$ANDROID_HOME"
              echo "  ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
              echo ""
              echo "  build: ./run lib core && ./gradlew :app:assembleOssRelease"
            '';
          };
        });
    };
}
