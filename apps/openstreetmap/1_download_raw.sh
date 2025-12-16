#!/usr/bin/env bash
set -euo pipefail

outputDir="./data/raw"

# Choose from https://download.geofabrik.de/<region>.html > raw directory index
# Date in YYMMDD format (as used by Geofabrik "raw" files)
date="251215"

regions=(
#   africa
#   antarctica
#   asia
  australia-oceania
  # central-america
#   europe
#   north-america
#   south-america
)

base="https://download.geofabrik.de"

# Ensure output directory exists
mkdir -p "$outputDir"

# Pick a downloader
if command -v curl >/dev/null 2>&1; then
  DL="curl -fL --retry 3 --retry-delay 2 -o"
elif command -v wget >/dev/null 2>&1; then
  DL="wget -O"
else
  echo "Error: need curl or wget" >&2
  exit 1
fi

# Compute MD5 in a cross-platform-ish way
md5_local() {
  local file="$1"
  if command -v md5sum >/dev/null 2>&1; then
    md5sum "$file" | awk '{print $1}'
  elif command -v md5 >/dev/null 2>&1; then
    # macOS
    md5 -q "$file"
  else
    echo "Error: need md5sum (Linux) or md5 (macOS)" >&2
    exit 1
  fi
}

# Extract MD5 hash from a .md5 file that might be in different formats
md5_remote_from_file() {
  local md5file="$1"
  # Common formats:
  # 1) "<hash>  filename"
  # 2) "MD5 (filename) = <hash>"
  # Grab the first 32-hex token we see.
  grep -Eo '[a-fA-F0-9]{32}' "$md5file" | head -n1 | tr 'A-F' 'a-f'
}

for region in "${regions[@]}"; do
  file="${region}-${date}.osm.pbf"
  url="${base}/${file}"
  md5url="${url}.md5"

  outputFile="${outputDir}/${file}"
  outputMd5file="${outputFile}.md5"

  echo "=== ${region} ==="
  echo "Downloading: ${url}"
  $DL "$outputFile" "$url"

  echo "Downloading MD5: ${md5url}"
  $DL "$outputMd5file" "$md5url"

  # Compute and compare hashes using the downloaded paths
  local_hash="$(md5_local "$outputFile" | tr 'A-F' 'a-f')"
  remote_hash="$(md5_remote_from_file "$outputMd5file")"

  if [[ -z "${remote_hash}" ]]; then
    echo "Could not parse remote MD5 from ${outputMd5file}" >&2
    echo "Local:  ${local_hash}"
    echo
    continue
  fi

  if [[ "${local_hash}" == "${remote_hash}" ]]; then
    echo "OK  md5 matches"
  else
    echo "FAIL md5 mismatch!"
    echo "Local:  ${local_hash}"
    echo "Remote: ${remote_hash}"
    # Uncomment next line if you want the script to stop on mismatch:
    # exit 2
  fi

  echo
done

echo "Done."
