#!/usr/bin/env bash
# Build and launch Canban (SWT/GTK, Linux x86_64).
set -euo pipefail
cd "$(dirname "$0")"

SWT="$HOME/.m2/repository/org/eclipse/platform/org.eclipse.swt.gtk.linux.x86_64/3.128.0/org.eclipse.swt.gtk.linux.x86_64-3.128.0.jar"

rm -rf bin && mkdir -p bin
javac -cp "$SWT" -d bin $(find src -name '*.java')
exec java --enable-native-access=ALL-UNNAMED -cp "bin:$SWT" se.canban.app.Main
