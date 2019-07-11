# Burstcoin Pool

[![Build Status](https://travis-ci.com/burst-apps-team/burstpool.svg?branch=master)](https://travis-ci.com/burst-apps-team/burstpool)

A Burstcoin Pool

Built by [Harry1453](https://github.com/harry1453) (Donation address [BURST-W5YR-ZZQC-KUBJ-G78KB](https://explorer.burstcoin.network/?action=account&account=16484518239061020631))

## Requirements

- MariaDB
- Java **8** (**Will not work on newer versions!!!**)

## Installation

- [Download The Latest Release](https://github.com/burst-apps-team/burstpool/releases/latest)
- Create a new MariaDB Database and create a user to access it.
- Extract the zip file. Configure `pool.properties` to suit your needs.
- You will need to wait some blocks before miners start to show their capacity.

## Configuration

You need to modify `pool.properties` to suit your needs. Properties are explained in that file.

## Customizing the Web UI

There are options to customize the site in the properties. Further modifications can be made by changing the contents of the JAR. Per the license terms, you must not remove the copyright notice at the bottom of the page, but you may make any other modifications you wish.

## Building

### Pre-requisites:

- Gradle
- JDK 8

### Building a release JAR

- `gradlew shadowJar` Will build a release JAR and place it under `build/libs/`
