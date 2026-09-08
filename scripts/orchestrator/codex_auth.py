#!/usr/bin/env python3
"""Check or establish ChatGPT authentication for the pinned Codex Python SDK."""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any

from openai_codex import Codex


def account_payload(value: Any) -> dict[str, Any]:
    if hasattr(value, "model_dump"):
        dumped = value.model_dump()
        return dumped if isinstance(dumped, dict) else {}
    if isinstance(value, dict):
        return value
    return {}


def account_present(value: Any) -> bool:
    payload = account_payload(value)
    return bool(payload.get("account"))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--device-login",
        action="store_true",
        help="Start ChatGPT device-code login before checking account state.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    with Codex() as codex:
        if args.device_login:
            login = codex.login_chatgpt_device_code()
            print("Open this URL on any device:", login.verification_url, flush=True)
            print("Enter code:", login.user_code, flush=True)
            login.wait()
        account = codex.account(refresh_token=True)
        payload = account_payload(account)
        if not account_present(account):
            print(
                "Codex ChatGPT authentication is not active for this service user.",
                file=sys.stderr,
            )
            return 2
        summary = {
            key: payload.get(key)
            for key in ("authMode", "requiresOpenaiAuth")
            if key in payload
        }
        print("Codex authentication active.", json.dumps(summary, sort_keys=True))
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
