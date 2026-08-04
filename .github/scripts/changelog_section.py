#!/usr/bin/env python3
"""Imprime a secao do CHANGELOG.md correspondente a uma versao.

Usado pelo workflow de release: primeiro para validar que a versao fechada tem
entrada no changelog, depois para virar o corpo do GitHub Release.

Aceita os dois estilos de cabecalho do Keep a Changelog:
    ## [1.1.4] - 2026-08-04
    ## 1.1.4 - 2026-08-04
"""
import re
import sys

CHANGELOG = "CHANGELOG.md"


def main() -> int:
    if len(sys.argv) < 2:
        print("uso: changelog_section.py <versao>", file=sys.stderr)
        return 2
    version = sys.argv[1]

    try:
        text = open(CHANGELOG, encoding="utf-8").read()
    except FileNotFoundError:
        print(f"{CHANGELOG} nao encontrado na raiz do repositorio.", file=sys.stderr)
        return 1

    header = re.compile(r"^##\s*\[?" + re.escape(version) + r"\]?(?![0-9.]).*$", re.MULTILINE)
    match = header.search(text)
    if not match:
        print(
            f"{CHANGELOG} nao tem secao para a versao {version}. "
            f'Esperado um cabecalho como "## [{version}] - AAAA-MM-DD".',
            file=sys.stderr,
        )
        return 1

    following = re.compile(r"^##\s", re.MULTILINE).search(text, match.end())
    body = text[match.end():following.start() if following else len(text)].strip()
    if not body:
        print(f"A secao {version} do {CHANGELOG} esta vazia.", file=sys.stderr)
        return 1

    print(body)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
