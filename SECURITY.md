# Security Policy

## Supported versions

Security fixes are applied to the latest release and the `main` branch.

## Reporting a vulnerability

Please use the repository's **Security → Report a vulnerability** private
advisory flow:

<https://github.com/a268926268926-web/FluxDown-Bridge/security/advisories/new>

Do not open a public issue for exploitable behavior.

Include:

- affected version or commit;
- the security boundary involved;
- reproduction steps or a minimal proof of concept;
- expected and observed behavior;
- any relevant sanitized logs.

Never include bridge tokens, browser cookies, private URLs, Root credentials, or
personal data.

## Scope reminders

- The bridge is intended to bind only to `127.0.0.1`.
- A public or LAN listener is a vulnerability.
- Authentication bypass on `/download`, `/download/batch`, or `/api/v1/info` is
  a vulnerability.
- Shell injection through request fields is a vulnerability.
- Root is part of the documented design. Root access by itself is not a
  vulnerability, but an unnecessary Root invocation or unsafe command
  construction is in scope.

The project cannot protect against a compromised browser extension, a malicious
Root manager, an altered APK, or another app with equivalent local privileges.
