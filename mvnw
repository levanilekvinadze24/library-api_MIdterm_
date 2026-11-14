#!/bin/sh
# Minimal Maven wrapper: downloads Apache Maven once into ~/.m2/wrapper and runs it.
set -e
MAVEN_VERSION=3.9.11
MAVEN_DIR="$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"
MVN="$MAVEN_DIR/apache-maven-$MAVEN_VERSION/bin/mvn"

if [ ! -x "$MVN" ]; then
  echo "Downloading Apache Maven $MAVEN_VERSION ..."
  mkdir -p "$MAVEN_DIR"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$MAVEN_DIR/maven.tar.gz"
  else
    wget -q "$URL" -O "$MAVEN_DIR/maven.tar.gz"
  fi
  tar -xzf "$MAVEN_DIR/maven.tar.gz" -C "$MAVEN_DIR"
  rm -f "$MAVEN_DIR/maven.tar.gz"
fi

exec "$MVN" "$@"
