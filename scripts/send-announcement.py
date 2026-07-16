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

from dotenv_util import load_dotenv


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        sys.exit(f"Missing required environment variable: {name}")
    return value


def smtp_security_mode(port: int) -> str:
    configured = os.environ.get("SMTP_SECURITY", "").strip().lower()
    if configured in {"ssl", "starttls"}:
        return configured
    return "starttls" if port == 587 else "ssl"


def smtp_login(host: str, port: int, user: str, password: str) -> smtplib.SMTP:
    security = smtp_security_mode(port)
    context = ssl.create_default_context()

    try:
        if security == "ssl":
            smtp = smtplib.SMTP_SSL(host, port, context=context, timeout=30)
            smtp.login(user, password)
            return smtp

        smtp = smtplib.SMTP(host, port, timeout=30)
        smtp.ehlo()
        smtp.starttls(context=context)
        smtp.ehlo()
        smtp.login(user, password)
        return smtp
    except smtplib.SMTPAuthenticationError as exc:
        sys.exit(
            "SMTP authentication failed (535): invalid credentials.\n"
            f"  Server: {host}:{port} ({security})\n"
            f"  User:   {user}\n"
            "Check SMTP_USER and SMTP_PASS in .env:\n"
            "  - Use the full mailbox address as SMTP_USER (e.g. david@pilato.fr)\n"
            "  - Use the mailbox password (Ionos: not necessarily the customer account password)\n"
            "  - ANNOUNCE_FROM should match SMTP_USER for Ionos\n"
            "  - Quote special characters: SMTP_PASS='your#password'\n"
            "If port 465 fails, try SMTP_PORT=587 and SMTP_SECURITY=starttls\n"
            f"Original error: {exc}"
        )


def send_announcement(notes_path: Path, subject: str) -> None:
    host = require_env("SMTP_HOST")
    port = int(require_env("SMTP_PORT"))
    user = require_env("SMTP_USER")
    password = require_env("SMTP_PASS")
    mail_from = os.environ.get("ANNOUNCE_FROM", user).strip() or user
    mail_to = require_env("ANNOUNCE_TO")

    if mail_from != user:
        print(
            f"Warning: ANNOUNCE_FROM ({mail_from}) differs from SMTP_USER ({user}). "
            "Ionos may reject the message.",
            file=sys.stderr,
        )

    if not notes_path.is_file():
        sys.exit(f"Release notes file not found: {notes_path}")

    body = notes_path.read_text(encoding="utf-8")
    message = EmailMessage()
    message["Subject"] = subject
    message["From"] = mail_from
    message["To"] = mail_to
    message.set_content(body)

    with smtp_login(host, port, user, password) as smtp:
        smtp.send_message(message)

    print(f"Announcement sent to {mail_to}")


def check_smtp() -> None:
    host = require_env("SMTP_HOST")
    port = int(require_env("SMTP_PORT"))
    user = require_env("SMTP_USER")
    password = require_env("SMTP_PASS")
    security = smtp_security_mode(port)

    with smtp_login(host, port, user, password):
        pass

    print(f"SMTP login OK ({host}:{port}, {security}, user={user})")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Send FSCrawler release announcement.")
    parser.add_argument(
        "notes_file",
        nargs="?",
        type=Path,
        help="Path to release notes Markdown file",
    )
    parser.add_argument(
        "--subject",
        help='Email subject (e.g. "FSCrawler 3.0 released")',
    )
    parser.add_argument(
        "--check-smtp",
        action="store_true",
        help="Validate SMTP credentials without sending email",
    )
    return parser.parse_args()


def main() -> None:
    load_dotenv(repo_root() / ".env")
    args = parse_args()

    if args.check_smtp:
        check_smtp()
        return

    if args.notes_file is None or not args.subject:
        sys.exit("notes_file and --subject are required unless --check-smtp is used.")

    notes_path = args.notes_file
    if not notes_path.is_absolute():
        notes_path = repo_root() / notes_path
    send_announcement(notes_path, args.subject)


if __name__ == "__main__":
    main()
