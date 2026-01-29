#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
backend_dir="$repo_root/backend"
tools_dir="$repo_root/.tools"
maven_version="3.9.6"
maven_base="apache-maven-$maven_version"
maven_dir="$tools_dir/$maven_base"
tar_path="$repo_root/$maven_base-bin.tar.gz"
download_url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$maven_version/$maven_base-bin.tar.gz"
yt_dlp_url="https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
yt_dlp_path="$tools_dir/yt-dlp"

resolve_maven_cmd() {
  if command -v mvn >/dev/null 2>&1; then
    echo "mvn"
    return
  fi

  mkdir -p "$tools_dir"

  if [ ! -x "$maven_dir/bin/mvn" ]; then
    rm -rf "$maven_dir"
    if [ -f "$tar_path" ]; then
      tar -xzf "$tar_path" -C "$tools_dir" || true
    fi
  fi

  if [ ! -x "$maven_dir/bin/mvn" ]; then
    rm -rf "$maven_dir"
    if command -v curl >/dev/null 2>&1; then
      curl -L "$download_url" -o "$tar_path"
    elif command -v wget >/dev/null 2>&1; then
      wget "$download_url" -O "$tar_path"
    else
      echo "Neither curl nor wget found for Maven download." >&2
      exit 1
    fi
    tar -xzf "$tar_path" -C "$tools_dir"
  fi

  if [ -x "$maven_dir/bin/mvn" ]; then
    if ! "$maven_dir/bin/mvn" -v >/dev/null 2>&1; then
      rm -rf "$maven_dir"
      if command -v curl >/dev/null 2>&1; then
        curl -L "$download_url" -o "$tar_path"
      elif command -v wget >/dev/null 2>&1; then
        wget "$download_url" -O "$tar_path"
      else
        echo "Neither curl nor wget found for Maven download." >&2
        exit 1
      fi
      tar -xzf "$tar_path" -C "$tools_dir"
    fi
  fi

  if [ ! -x "$maven_dir/bin/mvn" ]; then
    echo "Maven command not found at: $maven_dir/bin/mvn" >&2
    exit 1
  fi

  echo "$maven_dir/bin/mvn"
}

mvn_cmd="$(resolve_maven_cmd)"

if command -v yt-dlp >/dev/null 2>&1; then
  export VELP_YTDLP_PATH="$(command -v yt-dlp)"
else
  if [ ! -x "$yt_dlp_path" ]; then
    mkdir -p "$tools_dir"
    if command -v curl >/dev/null 2>&1; then
      curl -L "$yt_dlp_url" -o "$yt_dlp_path"
    elif command -v wget >/dev/null 2>&1; then
      wget "$yt_dlp_url" -O "$yt_dlp_path"
    else
      echo "Neither curl nor wget found for yt-dlp download." >&2
      exit 1
    fi
    chmod +x "$yt_dlp_path"
  fi
  export VELP_YTDLP_PATH="$yt_dlp_path"
fi

cd "$backend_dir"
"$mvn_cmd" clean spring-boot:run
