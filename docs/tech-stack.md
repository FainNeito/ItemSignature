# ItemSignature technology stack
- Kotlin 2.2.21; JVM target 21; Maven 3.9+.
- Paper API 1.21.11-R0.1-SNAPSHOT, provided by Paper/Leaf.
- Adventure components supplied by Paper; optional Nexo reflection bridge.
- Kotlin stdlib bundled and relocated; SPEAR is development tooling only.
- JUnit 5.11.4, MockBukkit 4.110.0, Mockito 5.23.0.
- Konsist 0.17.3 (test scope) enforces SPEAR layer rules.
- Node 20+ validates EARS and operates the portable SPEAR state helper.
- Local builds use JDK 23 targeting Java 21; CI builds on Java 21.

Evidence: pom.xml; upstream SPEAR revision 2c91bae; https://raw.githubusercontent.com/LemonAppDev/konsist/main/README.md.
