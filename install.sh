#!/usr/bin/env bash
# jdp — Java Dependency Pilot · installer
# usage:
#   curl -fsSL https://raw.githubusercontent.com/Nandobez/jdp/main/install.sh | bash
#   bash install.sh --prefix=$HOME/.local  --pin=v0.1.0

set -euo pipefail

REPO="${JDP_REPO:-https://github.com/Nandobez/jdp.git}"
RAW="${JDP_RAW:-https://raw.githubusercontent.com/Nandobez/jdp}"
PREFIX="${JDP_PREFIX:-$HOME/.local}"
REF="${JDP_REF:-main}"
CACHE="${JDP_CACHE:-$HOME/.cache/jdp}"

for arg in "$@"; do
  case "$arg" in
    --prefix=*) PREFIX="${arg#--prefix=}" ;;
    --pin=*)    REF="${arg#--pin=}" ;;
    --cache=*)  CACHE="${arg#--cache=}" ;;
    -h|--help)
      sed -n '2,8p' "$0"; exit 0 ;;
  esac
done

bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
dim()   { printf '\033[2m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }

need() {
  command -v "$1" >/dev/null || { red "✗ falta '$1' no PATH"; return 1; }
}

bold "jdp installer"
dim  "  prefix : $PREFIX"
dim  "  ref    : $REF"
dim  "  cache  : $CACHE"
echo

# --- prerequisites ---
MISSING=0
need git  || MISSING=1
need mvn  || MISSING=1
need java || MISSING=1
[ "$MISSING" -eq 1 ] && { red "instale git + mvn + jdk17+ antes."; exit 1; }

JV=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [ "${JV:-0}" -lt 17 ]; then
  red "✗ JDK 17+ requerido (encontrado: $JV)"; exit 1
fi
dim "✓ jdk $JV · mvn $(mvn -v 2>/dev/null | head -1 | awk '{print $3}')"

# --- fetch source ---
mkdir -p "$CACHE"
SRC="$CACHE/src"
if [ -d "$SRC/.git" ]; then
  dim "↻ atualizando $SRC"
  git -C "$SRC" fetch --quiet origin "$REF"
  git -C "$SRC" checkout --quiet "$REF"
  git -C "$SRC" reset --quiet --hard "origin/$REF" 2>/dev/null || true
else
  dim "↓ clonando $REPO → $SRC"
  rm -rf "$SRC"
  git clone --quiet --depth=1 --branch "$REF" "$REPO" "$SRC" 2>/dev/null \
    || git clone --quiet --depth=1 "$REPO" "$SRC"
fi

# --- build ---
bold "compilando…"
(cd "$SRC" && mvn -q -DskipTests package)

JAR_SRC="$SRC/target/jdp.jar"
[ -f "$JAR_SRC" ] || { red "✗ build não produziu target/jdp.jar"; exit 1; }

# --- install ---
LIBDIR="$PREFIX/share/jdp"
BINDIR="$PREFIX/bin"
mkdir -p "$LIBDIR" "$BINDIR"

cp "$JAR_SRC" "$LIBDIR/jdp.jar"
cat > "$BINDIR/jdp" <<EOF
#!/usr/bin/env bash
exec java -jar "$LIBDIR/jdp.jar" "\$@"
EOF
chmod +x "$BINDIR/jdp"

echo
green "✓ jdp instalado em $BINDIR/jdp"
echo

case ":$PATH:" in
  *:"$BINDIR":*) : ;;
  *)
    dim "  $BINDIR não está no PATH — adicione ao seu shell rc:"
    echo "    export PATH=\"$BINDIR:\$PATH\""
    ;;
esac

dim "  experimente:  jdp --help"
