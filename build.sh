#!/bin/bash
set -exu -o pipefail
rm -Rf build src/main/gen src/main/resources/projectTemplates/*.zip
(cd templates && zip --recurse-paths "../src/main/resources/projectTemplates/Generic 6502 Project.zip" "Generic 6502 Project/")
./gradlew generateAsmLexer generateAsmParser verifyPlugin buildPlugin
./gradlew runPluginVerifier
