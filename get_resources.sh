#!/usr/bin/env bash

if [ ! -f demo/src/test/resources/test-ap.jfr ]; then
  wget --no-check-certificate -O demo/src/test/resources/test-ap.jfr "https://drive.usercontent.google.com/download?id=1lebqWl27wAF1Q59cziTvVcz3osVx6iSn&export=download&authuser=0&confirm=t&uuid=c1f0f74f-4905-440a-acf0-bd90c628d6db&at=AN_67v2uViTVsyMesAoxPq7yUFr4%3A1728386424683"
fi
if [ ! -f demo/src/test/resources/test-async.jfr ]; then
  wget --no-check-certificate -O demo/src/test/resources/test-async.jfr "https://drive.usercontent.google.com/download?id=13eC3Rcapd9mWqZ_FmTDMsEe_naNshtmn&export=download&authuser=0&confirm=t&uuid=93d70a93-aba0-485f-a981-242866014e12&at=AN_67v3ONmsOLbP3EsuhrLRZgiig%3A1728386466678"
fi
if [ ! -f demo/src/test/resources/test-jfr.jfr ]; then
  wget --no-check-certificate -O demo/src/test/resources/test-jfr.jfr "https://drive.usercontent.google.com/download?id=1UqqpUQwVSyIYMEGP4uEr_pA-A-Gmbpo9&export=download&authuser=0&confirm=t&uuid=25b88cbd-7493-47db-b9db-a223d62a66c2&at=AN_67v0u2i7M4LFf3jmjZqlI6vZU%3A1728386510351"
fi

ln -s demo/src/test/resources/test-ap.jfr parser/src/test/resources/test-ap.jfr