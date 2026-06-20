#!/bin/bash
set -exu -o pipefail
rm -Rf build src/main/gen src/main/resources/projectTemplates/*.zip
# buildPlugin runs the structure/configuration checks; verifyPlugin (2.x plugin)
# replaces the old runPluginVerifier task and runs the IntelliJ Plugin Verifier.
./gradlew generateAsmLexer generateAsmParser buildPlugin
./gradlew verifyPlugin
