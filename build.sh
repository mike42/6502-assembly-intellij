#!/bin/bash
set -exu -o pipefail
rm -Rf build src/main/gen
./gradlew generateAsmLexer generateAsmParser verifyPlugin buildPlugin

