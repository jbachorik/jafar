#!/usr/bin/env bash

HERE=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [ ! -f demo/src/test/resources/test-ap.jfr ]; then
  wget --no-check-certificate -O demo/src/test/resources/test-ap.jfr "https://www.dropbox.com/scl/fi/lp5bj8adi3l7jge9ykayr/test-ap.jfr?rlkey=28wghlmp7ge4bxnan9ccwarby&st=0kd2p1u1&dl=0"
fi
if [ ! -f demo/src/test/resources/test-jfr.jfr ]; then
  wget --no-check-certificate -O demo/src/test/resources/test-jfr.jfr "https://www.dropbox.com/scl/fi/5uhp13h9ltj38joyqmwo5/test-jfr.jfr?rlkey=p0wmznxgm7zud6xzaydled69c&st=ilfirsrg&dl=0"
fi

if [ ! -f parser/src/test/resource/test-ap.jfr ]; then
  ln -s ${HERE}/demo/src/test/resources/test-ap.jfr ${HERE}/parser/src/test/resources/test-ap.jfr
fi
