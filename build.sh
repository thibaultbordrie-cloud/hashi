#!/usr/bin/env sh
# Builds Hashi with the official Kotlin compiler only (no Gradle, no downloaded library).
#   KOTLINC=/path/to/kotlinc ./build.sh        build/hashi.jar, then run the tests
set -eu
cd "$(dirname "$0")"
mkdir -p build
"$KOTLINC" src/main/kotlin -jvm-target 21 -Werror -include-runtime -d build/hashi.jar
"$KOTLINC" src/main/kotlin src/test/kotlin -jvm-target 21 -Werror -include-runtime -d build/hashi-tests.jar
java -cp build/hashi-tests.jar hashi.CheckKt
