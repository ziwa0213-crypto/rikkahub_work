#!/usr/bin/env bash
set -uo pipefail

if ! command -v python3 >/dev/null 2>&1 || ! python3 -m pip --version >/dev/null 2>&1; then
  apt-get update && apt-get install -y python3 python3-pip python3-venv
fi

venv=/workspace/.rikkahub/venv
if [ ! -x "$venv/bin/python" ]; then
  python3 -m venv "$venv"
fi
if [ -x "$venv/bin/python" ]; then
  "$venv/bin/python" -m pip install -r /workspace/.rikkahub/requirements.txt
  "$venv/bin/python" -c 'import numpy, dateutil, pytz; print("Character workspace dependencies: OK")'
else
  printf '%s\n' 'Could not create the virtual environment.' >&2
  exit 1
fi
