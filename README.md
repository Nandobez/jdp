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
**Gerente de dependências Maven/Gradle com CVE, conflito de função e verify chain.**

[![JDK](https://img.shields.io/badge/JDK-17+-007396?style=for-the-badge&logo=openjdk)](https://openjdk.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](./LICENSE)

</div>

`jdp` faz CRUD de dependências (Maven/Gradle) do lado **didático**: cada
ação atravessa a lista de equivalências (logger? jpa-provider? web-stack?),
consulta OSV.dev por CVE, prioriza grupos canônicos (`org.springframework`,
`org.apache`, etc), e roda uma **verify chain** (`resolve → clean+package →
install`) com revert automático se quebrar. Picker interativo, autocomplete
no REPL e tabelas que se adaptam ao terminal.

## Instalação

```bash
curl -fsSL https://raw.githubusercontent.com/Nandobez/jdp/main/install.sh | bash
```

Customização:

```bash
# pin de versão
curl -fsSL https://raw.githubusercontent.com/Nandobez/jdp/main/install.sh | bash -s -- --pin=v0.1.0

# prefix custom
curl -fsSL https://raw.githubusercontent.com/Nandobez/jdp/main/install.sh | bash -s -- --prefix=/usr/local
```

Pré-requisitos: **JDK 17+**, **mvn**, **git**.
Default instala em `~/.local/share/jdp/jdp.jar` + wrapper em `~/.local/bin/jdp`.

### Build local

```bash
git clone https://github.com/Nandobez/jdp.git
cd jdp
mvn -DskipTests package
./jdp --help
```

## Comandos

```
CRUD
  list, ls         tabela de deps declaradas
  search           Maven Central com ★ canonical
  add              + verify chain + picker + conflito de função
  rm, remove       remove com sugestão fuzzy se não achar

ANÁLISE
  doctor           CVE (OSV.dev) + outdated + regras + score
                   --fix sobe versões com CVE para a versão patched
  why              cadeia transitiva (`mvn dependency:tree` em árvore bonita)
  weight           top jars por tamanho (impacto fat-jar / cold start)
  unused           deps sem import (heurística com whitelist de starters/drivers/agents)
                   --clean prompta pra remover cada zumbi

PROJETO
  init             scaffold: rest-api | batch | lib
  diff             release notes / migration links entre 2 versões
  migrate          maven → gradle (Kotlin DSL)
  repl             shell interativo com tab-complete de artifacts
```

Ajuda detalhada: `jdp <cmd> --help`.

## Tour rápido

```bash
# scaffold
jdp init meu-api -t rest-api --group io.acme

# adicionar — picker se houver múltiplos, ★ canonical no topo
jdp add jpa
jdp add starter-data-jpa
jdp add ch.qos.logback:logback-classic:1.5.6

# detecta conflito de função
#   ⚠ conflito de função
#   org.apache.logging.log4j:log4j-core já cumpre o mesmo papel: logger-impl
#   substituir log4j-core por logback-classic? [S/n/m]

# diagnóstico
jdp doctor
jdp doctor --fix       # sobe pra versão patched do OSV

# análise
jdp why tomcat
jdp weight -n 20
jdp unused --clean
```

## Como funciona

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
        │  Equivalence table │   ← detecção de conflito
        │  Canonical groups  │   ← ranking ★
        │  Incompat rules    │   ← jjwt < 0.12, lombok < 1.18.30…
        └─────────┬──────────┘
                  ▼
        ┌────────────────────┐
        │  Mutate pom.xml    │
        │  Verify chain      │   ← resolve → package → install
        │  Rollback on fail  │
        └────────────────────┘
```

**Verify chain** (executada após cada `add`/`rm`):
1. `mvn dependency:resolve` — confirma que o artefato e suas transitivas existem
2. `mvn clean package -DskipTests` — confirma que ainda compila
3. `mvn install -DskipTests` — popula `~/.m2` pra próximas reuses

Falha em qualquer step → **revert automático do pom**. Pule com `--no-build`
ou `JDP_SKIP_BUILD=1`.

## Comandos em detalhe

### `add`

- Curto: `jdp add starter-web` → resolve via Central, prioriza canonical
- GAV: `jdp add org.postgresql:postgresql:42.7.4`
- Múltiplos hits → **picker numerado** (Enter = canonical, q = cancela)
- Conflito de função → **prompt de substituição** (S/n/m onde m = manter ambos)
- `--bom` omite a versão (parent BOM dita)
- `-y` pula prompts (auto-canonical)

### `doctor`

- Consulta **OSV.dev** por CVE em cada GAV+versão
- Consulta **Central** pela versão mais nova
- 4 regras hardcoded de incompatibilidade conhecida
- Score = `100 - 15·CVEs - 3·outdated - 10·incompat`
- `--fix` baixa versões patched do OSV e aplica

### `unused`

Heurística simples: para cada dep, procura `import <groupId>.*` ou
substring do artifactId em `src/main/java/**/*.java`. **Whitelist** evita
falsos positivos:
- starters (`spring-boot-starter-*`, autoconfig)
- JDBC drivers (mysql, postgresql, h2, mariadb, oracle, sqlserver)
- annotation processors (lombok, mapstruct)
- logging impls (logback, log4j) — usados via SLF4J facade
- test deps (junit, mockito, assertj, testcontainers)

`--clean` prompta por cada zumbi. `-y` remove tudo silenciosamente.

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

Resolve `mvn dependency:build-classpath`, soma bytes, mostra top-N. Útil
pra Lambda / container slim.

### `repl`

```
jdp repl
jdp› add starter-data<TAB>   ← bate no Central, sugere
jdp› doctor
jdp› exit
```

JLine + picocli + autocomplete dinâmico de artifacts (debounce 3 chars).

## Variáveis de ambiente

| Var | Efeito |
|---|---|
| `JDP_SKIP_BUILD=1` | pula verify chain em add/rm |
| `JDP_NARROW=1` | força layout narrow (sem bordas) |
| `COLUMNS=80` | limita largura — narrow se tabela passar disso |

## Layout do projeto

```
jdp/
├── install.sh                    # installer auto-detecta JDK + mvn
├── pom.xml                       # build (shaded uber-jar)
├── src/main/java/dev/nandobez/jdp/
│   ├── Main.java                 # entry point + help bonito
│   ├── core/
│   │   ├── Coord.java            # GAV + helpers de short name
│   │   ├── PomReader.java        # parser de pom.xml (resolve ${…})
│   │   ├── PomWriter.java        # add/remove de <dependency>
│   │   ├── Equivalence.java      # tabela de equivalência de função
│   │   └── Verify.java           # verify chain async com spinner
│   ├── sources/
│   │   ├── Central.java          # Maven Central solrsearch + fuzzy
│   │   └── Osv.java              # OSV.dev /v1/query
│   └── cmd/
│       ├── Tui.java              # tabela adaptativa + cores
│       ├── SubHelp.java          # help bonito por subcomando
│       └── *Cmd.java             # 12 comandos
```

## License

MIT — Fernando Bezerra · 2026
