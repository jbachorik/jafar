#!/usr/bin/env bash

set -e

./gradlew :parser:publishToMavenLocal

(
  cd jafar-gradle-plugin || exit
  ./gradlew clean publishToMavenLocal
)