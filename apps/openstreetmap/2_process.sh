sudo apt update
sudo apt install osmium-tool

brew install osmium-too

# List all key in file
osmium tags-count australia-oceania-251215.osm.pbf > australia-oceania-251215-tag.txt

# planet.osm.pbf -> only highways + administrative boundaries
osmium tags-filter australia-oceania-251215.osm.pbf \
  w/highway \
  r/boundary=administrative \
  w/boundary=administrative \
  -o planet-roads-admin-australia-oceania-251215.osm.pbf

# 1) Filter administrative boundaries
INPUT="australia-oceania-251215.osm.pbf"
osmium tags-filter "./data/raw/$INPUT" \
  r/boundary=administrative \
  w/boundary=administrative \
  -o "./data/filtered/admin-$INPUT"

# 2) Filter roads (highways)
INPUT="australia-oceania-251215.osm.pbf"
osmium tags-filter "./data/raw/$INPUT" \
  w/highway \
  -o "./data/filtered/road-$INPUT"

# 3) sort
mkdir -p ./data/sorted

for f in ./data/filtered/admin-*.osm.pbf; do
  base=$(basename "$f")
  osmium sort "$f" -o "./data/sorted/$base"
done

# 4) merge
osmium merge ./data/sorted/admin-*.osm.pbf \
  -o ./data/filtered/admin-ALL.osm.pbf

