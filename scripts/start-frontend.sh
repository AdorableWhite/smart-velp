#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
frontend_dir="$repo_root/frontend"

cd "$frontend_dir"
if [ ! -d "node_modules" ]; then
  npm install
fi
npm run dev
