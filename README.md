<div align="center">

<pre>
         ██╗██████╗ ██████╗
          ██║██╔══██╗██╔══██╗
          ██║██║  ██║██████╔╝
    ██   ██║██║  ██║██╔═══╝
╚█████╔╝██████╔╝██║
 ╚════╝ ╚═════╝ ╚═╝
</pre>

### Java Dependency Pilot
**Maven/Gradle dependency manager with CVE checks, role-conflict detection and a verify chain.**

[![JDK](https://img.shields.io/badge/JDK-17+-007396?style=for-the-badge&logo=openjdk)](https://openjdk.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](./LICENSE)

</div>

`jdp` does Maven/Gradle CRUD with the **didactic** angle: every action
walks the equivalence catalog (logger? jpa-provider? web-stack?), queries
OSV.dev for CVEs, prefers canonical groups (`org.springframework`,
`org.apache`, etc), and runs a **verify chain** (`resolve → clean+package
→ install`) with automatic rollback on failure. Interactive picker,
REPL autocomplete and tables that adapt to your terminal width.

## Install

```bash
curl -fsSL https://raw.githubusercontent.com/Nandobez/jdp/main/install.sh | bash
```

Customisation:

```bash
# version pin
curl -fsSL https://raw.githubusercontent.com/Nandobez/jdp/main/install.sh | bash -s -- --pin=v0.1.0

# custom prefix
curl -fsSL https://raw.githubusercontent.com/Nandobez/jdp/main/install.sh | bash -s -- --prefix=/usr/local
```

Prerequisites: **JDK 17+**, **mvn**, **git**.
Default installs to `~/.local/share/jdp/jdp.jar` with a wrapper at
`~/.local/bin/jdp`.

### Local build

```bash
git clone https://github.com/Nandobez/jdp.git
cd jdp
mvn -DskipTests package
./jdp --help
```

## Commands

```
CRUD
  list, ls         declared deps as a table
  search           Maven Central with ★ canonical highlight
  add              + verify chain + picker + role conflict
  rm, remove       remove with fuzzy suggestions when not found

ANALYSIS
  doctor           CVE (OSV.dev) + outdated + rules + score
                   --fix bumps CVE deps to the patched version
  why              transitive chain (mvn dependency:tree, prettified)
  weight           top jars by size (fat-jar / cold start impact)
  unused           declared deps without imports (heuristic + whitelist)
                   --clean prompts to remove each zombie

PROJECT
  init             scaffold: rest-api | batch | lib
  diff             release-notes / migration links between two versions
  migrate          maven → gradle (Kotlin DSL)
  repl             interactive shell with artifact tab-complete
```

Per-command help: `jdp <cmd> --help`.

## Quick tour

```bash
# scaffold
jdp init my-api -t rest-api --group io.acme

# add — picker for multiple matches, ★ canonical on top
jdp add jpa
jdp add starter-data-jpa
jdp add ch.qos.logback:logback-classic:1.5.6

# role-conflict detection
#   ⚠ role conflict
#   org.apache.logging.log4j:log4j-core already fills the same role: logger-impl
#   replace log4j-core with logback-classic? [Y/n/k]

# diagnostics
jdp doctor
jdp doctor --fix       # apply OSV patched versions

# analysis
jdp why tomcat
jdp weight -n 20
jdp unused --clean
```

## How it works

```
                   ┌─────────────┐
                   │  jdp <cmd>  │
                   └──────┬──────┘
                          │
         ┌────────────────┼───────────────┐
         ▼                ▼               ▼
   ┌──────────┐    ┌──────────────┐  ┌──────────┐
   │ pom.xml  │    │ Maven Central│  │  OSV.dev │
   │ (parser) │    │   solrsearch │  │   /query │
   └────┬─────┘    └──────┬───────┘  └────┬─────┘
        │                 │               │
        └────────┬────────┴───────────────┘
                 ▼
        ┌────────────────────┐
        │  Equivalence table │   ← role-conflict detection
        │  Canonical groups  │   ← ★ ranking
        │  Incompat rules    │   ← jjwt < 0.12, lombok < 1.18.30…
        └─────────┬──────────┘
                  ▼
        ┌────────────────────┐
        │  Mutate pom.xml    │
        │  Verify chain      │   ← resolve → package → install
        │  Rollback on fail  │
        └────────────────────┘
```

**Verify chain** (runs after every `add`/`rm`):
1. `mvn dependency:resolve` — confirms artifact + transitives exist
2. `mvn clean package -DskipTests` — confirms it still compiles
3. `mvn install -DskipTests` — populates `~/.m2` for next runs

If any step fails ⇒ **automatic pom rollback**. Skip it with `--no-build`
or `JDP_SKIP_BUILD=1`.

## Commands in detail

### `add`

- Short: `jdp add starter-web` → resolves via Central, prefers canonical groups
- GAV: `jdp add org.postgresql:postgresql:42.7.4`
- Multiple matches → **numbered picker** (Enter = canonical, q = cancel)
- Role conflict → **replace prompt** (Y/n/k, k = keep both)
- `--bom` omits the version (parent BOM decides)
- `-y` skips prompts (auto-canonical)

### `doctor`

- Queries **OSV.dev** for CVEs against each GAV+version
- Queries **Central** for the latest published version
- Four hand-curated incompat rules
- Score = `100 - 15·CVEs - 3·outdated - 10·incompat`
- `--fix` pulls patched versions from OSV and applies them

### `unused`

Heuristic: for each dep, look for `import <groupId>.*` or the artifactId
as substring in `src/main/java/**/*.java`. A **whitelist** prevents
false positives:
- starters (`spring-boot-starter-*`, autoconfig)
- JDBC drivers (mysql, postgresql, h2, mariadb, oracle, sqlserver)
- annotation processors (lombok, mapstruct)
- logging impls (logback, log4j) — used via the SLF4J facade
- test deps (junit, mockito, assertj, testcontainers)

`--clean` prompts for each zombie. `-y` removes everything silently.

### `why`

```
orders-api
└── spring-boot-starter-web         3.3.4   [springframework.boot]
    └── spring-boot-starter-tomcat  3.3.4   [springframework.boot]
        ├── tomcat-embed-core      10.1.30  [apache.tomcat.embed]
        ├── tomcat-embed-el        10.1.30  [apache.tomcat.embed]
        └── tomcat-embed-websocket 10.1.30  [apache.tomcat.embed]
```

### `weight`

Resolves `mvn dependency:build-classpath`, sums bytes, prints the top-N.
Useful for Lambda / slim containers.

### `repl`

```
jdp repl
jdp› add starter-data<TAB>   ← hits Central, suggests
jdp› doctor
jdp› exit
```

JLine + picocli + dynamic artifact autocomplete (debounce: 3 chars).

## Environment variables

| Var | Effect |
|---|---|
| `JDP_SKIP_BUILD=1` | skip the verify chain on add/rm |
| `JDP_NARROW=1` | force narrow layout (no borders) |
| `COLUMNS=80` | width cap — narrow renders when a table exceeds it |

## Project layout

```
jdp/
├── install.sh                    # installer; auto-detects JDK + mvn
├── pom.xml                       # build (shaded uber-jar)
├── src/main/java/dev/nandobez/jdp/
│   ├── Main.java                 # entry point + curated help
│   ├── core/
│   │   ├── Coord.java            # GAV + short-name helpers
│   │   ├── PomReader.java        # pom.xml parser (resolves ${…})
│   │   ├── PomWriter.java        # add/remove of <dependency>
│   │   ├── Equivalence.java      # role-equivalence catalog
│   │   └── Verify.java           # async verify chain + spinner
│   ├── sources/
│   │   ├── Central.java          # Maven Central solrsearch + fuzzy
│   │   └── Osv.java              # OSV.dev /v1/query
│   └── cmd/
│       ├── Tui.java              # adaptive table + colour helpers
│       ├── SubHelp.java          # per-subcommand help
│       └── *Cmd.java             # 12 commands
```

## Contributing

PRs welcome. Easy wins:
- more incompat rules in `DoctorCmd.RULES`
- more categories in `Equivalence.CLASSES`
- more canonical groups in the `CANON` lists (AddCmd / SearchCmd / DiffCmd)
- more project templates in `InitCmd`

## License

MIT — Fernando Bezerra · 2026
