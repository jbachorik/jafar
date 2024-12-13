#!/usr/bin/env bash

./gradlew :parser:publishToMavenLocal

(
  cd gradle-plugin || exit
  ./gradlew publishToMavenLocal
)