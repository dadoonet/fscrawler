#!/usr/bin/env python3
"""Send release announcement email via SMTP."""

from __future__ import annotations

import argparse
import os
import smtplib
import ssl
import sys
from email.message import EmailMessage
from pathlib import Path


def load_dotenv(env_path: Path) -> None:
    if not env_path.is_file():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip()
        if key and key not in os.environ:
            os.environ[key] = value


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        sys.exit(f"Missing required environment variable: {name}")
    return value


def send_announcement(notes_path: Path, subject: str) -> None:
    host = require_env("SMTP_HOST")
    port = int(require_env("SMTP_PORT"))
    user = require_env("SMTP_USER")
    password = require_env("SMTP_PASS")
    mail_from = os.environ.get("ANNOUNCE_FROM", user).strip() or user
    mail_to = require_env("ANNOUNCE_TO")

    if not notes_path.is_file():
        sys.exit(f"Release notes file not found: {notes_path}")

    body = notes_path.read_text(encoding="utf-8")
    message = EmailMessage()
    message["Subject"] = subject
    message["From"] = mail_from
    message["To"] = mail_to
    message.set_content(body)

    context = ssl.create_default_context()
    with smtplib.SMTP_SSL(host, port, context=context) as smtp:
        smtp.login(user, password)
        smtp.send_message(message)

    print(f"Announcement sent to {mail_to}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Send FSCrawler release announcement.")
    parser.add_argument(
        "notes_file",
        type=Path,
        help="Path to release notes Markdown file",
    )
    parser.add_argument(
        "--subject",
        required=True,
        help='Email subject (e.g. "FSCrawler 3.0 released")',
    )
    return parser.parse_args()


def main() -> None:
    load_dotenv(repo_root() / ".env")
    args = parse_args()
    notes_path = args.notes_file
    if not notes_path.is_absolute():
        notes_path = repo_root() / notes_path
    send_announcement(notes_path, args.subject)


if __name__ == "__main__":
    main()
