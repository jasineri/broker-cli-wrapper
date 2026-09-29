<div align="center">

# 🔧 Broker CLI Wrapper

**An unofficial, typed Java wrapper around the `sc` broker CLI.**

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)](.github/workflows/build.yml)

</div>

> **Disclaimer:** This project is **not affiliated with, endorsed by, or sponsored by**
> Scalable Capital GmbH. "Scalable Capital" and the `sc` CLI are trademarks of their
> respective owners. This is an independent, unofficial wrapper.
>
> Users are responsible for complying with Scalable Capital's terms of service.
> This software does not provide financial advice.

---

## Why?

The official `sc` CLI is great for interactive use, but calling it from Java
means dealing with `ProcessBuilder`, stream draining, ANSI escape codes, and
JSON parsing. This wrapper does the enchantment.

## Features

| Feature | Description                                                       |
|---|-------------------------------------------------------------------|
| `login()` | Runs `sc login`, opens the device-code URL in your browser        |
| `isLoggedIn()` | Checks `sc whoami` and returns a boolean                          |
| `run(...)` | Runs any `sc` subcommand and returns stdout                       |
| `runJson(...)` | Runs any `sc` subcommand and returns a JsonPath `DocumentContext` |
| Cross-platform browser opener | Opens default browser for login on Linux, Windows, macOS          |

## Quick Start

### Maven

```xml
<dependency>
    <groupId>de.jasineri.brokercli</groupId>
    <artifactId>broker-cli-wrapper</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
