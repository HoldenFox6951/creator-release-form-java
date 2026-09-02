#!/bin/sh
set -e
javac -d build $(find src/main/java -name '*.java')
java -cp build com.infrai.example.release.CreatorReleaseApp
