# Security Policy

## Supported versions

Security fixes land on `main` and ship in the next release from the current minor line. Older lines
are not patched retroactively: the three artifacts (`srcmorph`, `srcmorph-cli`,
`srcmorph-maven-plugin`) are released together from one parent pom at a single version, so upgrading
within a line is a version bump and nothing else.

| Version | Supported |
|---------|-----------|
| 1.2.x (current release line) | ✅ Security fixes released from `main` |
| 1.1.x and older | ❌ Upgrade to the current line |
| `main` (development) | ✅ Fixes land here first |

---

## Reporting a vulnerability — primary channel

**Please do not report security vulnerabilities through public GitHub issues.**

Use GitHub's built-in private vulnerability reporting mechanism:

https://github.com/bernardladenthin/srcmorph/security/advisories/new

This creates a private advisory that only the maintainers can see.

---

## Reporting a vulnerability — fallback

If GitHub Private Vulnerability Reporting is unavailable, contact the maintainer directly:

- **Bernard Ladenthin** — <bernard.ladenthin@gmail.com>

This address is the same maintainer contact published in [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md).

---

## Response SLA

We aim to:

- **Acknowledge** vulnerability reports within **14 days** of receipt.
- Provide a **remediation timeline** within **30 days** of acknowledgement.

---

## Coordinated disclosure

This project follows **coordinated disclosure** with a **90-day embargo** by default:

1. The reporter submits the vulnerability privately (see above).
2. The maintainer acknowledges receipt and investigates.
3. A fix is developed and tested privately.
4. A patched release is published.
5. A public security advisory is created **after** the fix is released.

Please do not disclose the vulnerability publicly until a fix has been released or until you and the maintainer have agreed on a disclosure date. If 90 days elapse without a remediation plan, the reporter may publish at their discretion after notifying the maintainer.

---

## Scope

**In scope:**

- Plugin source under `src/main/java/net/ladenthin/maven/llamacpp/aiindex/**`.
- Plugin Maven configuration (`pom.xml`) and CI workflow definitions in `.github/workflows/**`.

**Out of scope (report upstream):**

- Vulnerabilities in the bundled `net.ladenthin:llama` JNI binding — report at
  https://github.com/bernardladenthin/java-llama.cpp.
- Vulnerabilities in upstream `ggml-org/llama.cpp` — report at
  https://github.com/ggml-org/llama.cpp.
- Vulnerabilities in third-party Maven plugins or transitive dependencies — report to their respective maintainers; we will track and bump versions once an upstream fix lands.

---

## Security update notifications

Security fixes are published through:

- **GitHub Security Advisories** on this repository (subscribe via the *Watch → Custom → Security alerts* setting).
- The **CHANGELOG.md** `### Security` entry for the affected release.
- The **GitHub Releases** page, which is the canonical announcement channel.
