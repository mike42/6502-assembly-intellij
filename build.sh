#!/bin/bash
set -exu -o pipefail
rm -Rf build src/main/gen src/main/resources/projectTemplates/*.zip
(cd "templates/Generic 6502 Project" && zip --recurse-paths "../../src/main/resources/projectTemplates/Generic 6502 Project.zip" root0/ .idea/)
./gradlew generateAsmLexer generateAsmParser verifyPlugin buildPlugin
./gradlew runPluginVerifier
