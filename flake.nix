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
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            includeNDK = true;
            ndkVersions = [ "29.0.14206865" ];
            platformVersions = [ "37" ];
            buildToolsVersions = [ "37.0.0" ];
            includeEmulator = false;
            includeSystemImages = false;
            includeSources = false;
          };
          fhsEnv = pkgs.buildFHSEnv {
            name = "owenclave-fhs";
            targetPkgs = pkgs: with pkgs; [
              jdk21
              go_1_26
              golangci-lint
              gradle
              android-tools
              pkg-config
              git
              androidComposition.androidsdk
              androidComposition.platform-tools
              androidComposition.ndk-bundle
            ];
            multiPkgs = pkgs: with pkgs; [
              zlib
              ncurses5
              stdenv.cc.cc.lib
            ];
            runScript = "bash";
            profile = ''
              export JAVA_HOME="${pkgs.jdk21}"
              export ANDROID_HOME="${androidComposition.androidsdk}/libexec/android-sdk"
              export ANDROID_SDK_ROOT="${androidComposition.androidsdk}/libexec/android-sdk"
              export ANDROID_NDK_HOME="${androidComposition.androidsdk}/libexec/android-sdk/ndk-bundle"
              export ANDROID_NDK_ROOT="${androidComposition.androidsdk}/libexec/android-sdk/ndk-bundle"
              export GOCACHE="''${XDG_CACHE_HOME:-$HOME/.cache}/go-build"
              export GOMODCACHE="''${XDG_CACHE_HOME:-$HOME/.cache}/go-mod"
              export GOPATH="''${XDG_CACHE_HOME:-$HOME/.cache}/go"
              export PATH="$GOPATH/bin:$PATH"

              if ! command -v gomobile &>/dev/null; then
                echo "Installing gomobile and gobind..."
                go install golang.org/x/mobile/cmd/gomobile@latest 2>/dev/null
                go install golang.org/x/mobile/cmd/gobind@latest 2>/dev/null
              fi

              echo ""
              echo "owenclave dev shell (FHS)"
              echo "  ANDROID_HOME=$ANDROID_HOME"
              echo "  ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
              echo "  GOPATH=$GOPATH"
              echo ""
            '';
          };
        in
        {
          default = pkgs.mkShell {
            packages = [ fhsEnv ];
          };
        });
    };
}
