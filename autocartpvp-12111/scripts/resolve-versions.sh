#!/bin/bash
# Writes the newest yarn / loader / fabric-api versions for MC $1 into gradle.properties
set -e
MC=${1:-1.21.11}
YARN=$(curl -fsS "https://meta.fabricmc.net/v2/versions/yarn/$MC" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['version'])")
LOADER=$(curl -fsS "https://meta.fabricmc.net/v2/versions/loader" | python3 -c "import sys,json;print([x for x in json.load(sys.stdin) if x['stable']][0]['version'])")
FAPI=$(curl -fsS "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml" | grep -o "<version>[^<]*+$MC</version>" | sed 's/<[^>]*>//g' | tail -1)
echo "yarn=$YARN loader=$LOADER fabric-api=$FAPI"
[ -n "$YARN" ] && [ -n "$LOADER" ] && [ -n "$FAPI" ]
sed -i "s|^yarn_mappings=.*|yarn_mappings=$YARN|;s|^loader_version=.*|loader_version=$LOADER|;s|^fabric_version=.*|fabric_version=$FAPI|" gradle.properties

# YACL and ModMenu versions from Modrinth for this Minecraft version
mr() { curl -fsS -A "sh4rps/autocartpvp-ci" "https://api.modrinth.com/v2/project/$1/version?game_versions=%5B%22$MC%22%5D&loaders=%5B%22fabric%22%5D" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['version_number'])"; }
YACL=$(mr yacl); MM=$(mr modmenu)
echo "yacl=$YACL modmenu=$MM"
[ -n "$YACL" ] && [ -n "$MM" ]
sed -i "s|^yacl_version=.*|yacl_version=$YACL|;s|^modmenu_version=.*|modmenu_version=$MM|" gradle.properties
