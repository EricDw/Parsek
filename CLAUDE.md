# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Parsek is a Kotlin project (likely a parser library based on the name). It is currently in early/skeleton state with only a default `main()` entry point.

## Build System

This project uses **IntelliJ IDEA's built-in compiler** — there is no Gradle, Maven, or other build tool configured yet. The project module (`Parsek.iml`) is a standard IntelliJ Java module with the Kotlin runtime library.

- **Kotlin version:** 2.3 (language and API)
- **JVM target:** 1.8
- Source root: `src/`
- Test root: `test/` (directory does not exist yet)

To build and run from the command line, `kotlinc` must be used directly:

```bash
# Compile
kotlinc src/Main.kt -include-runtime -d out/parsek.jar

# Run
java -jar out/parsek.jar
```

If a build system (Gradle/Maven) is added later, update this file with the new commands.
