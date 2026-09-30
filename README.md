<div align="center">

# 🔧 Broker CLI Wrapper

**An unofficial, typed Java wrapper around the `sc` broker CLI.**

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)](.github/workflows/build.yml)

</div>

> **Disclaimer**
> This project is not affiliated with Scalable Capital GmbH and is neither endorsed nor sponsored by Scalable Capital GmbH. “Scalable Capital” and any other referenced trademarks are trademarks of their respective owners. This project is an independent, unofficial third-party tool.
> Use of this software is entirely at your own risk and responsibility. Users are solely responsible for complying with the applicable terms of use, policies, rules, and requirements of Scalable Capital and for ensuring that their use of this software is permitted under those terms.
> Users are solely responsible for all actions, orders, transactions, and trading decisions made or supported through the use of this software. Users should independently verify all displayed information, order parameters, and settings before submitting any transaction.
> The developer and/or maintainer of this project assumes no responsibility or liability for trading decisions, financial losses, loss of profits, incorrect or unexecuted orders, technical issues, service interruptions, changes to broker interfaces, or any other damages arising from or related to the use of, or inability to use, this software.
> This software does not constitute financial, investment, legal, or tax advice. Use of this software does not create any advisory, fiduciary, or client relationship between the developer and the user.
> The user remains solely responsible for their decisions and actions when using this software.
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
