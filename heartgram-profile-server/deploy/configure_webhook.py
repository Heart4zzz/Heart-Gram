"""Register a dedicated HeartGram bot from an interactive terminal."""

import argparse
import getpass
import json
import re
import secrets
import subprocess
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


INSTALL_ENVIRONMENT = r"""
set -eu
umask 077
target=/etc/heartgram-profiles.env
staged="$(sudo -n mktemp /etc/.heartgram-profiles-env.XXXXXXXX)"
trap 'sudo -n rm -f -- "$staged"' EXIT
sudo -n tee "$staged" >/dev/null
incoming="$(sudo -n sed -n 's/^HEARTGRAM_BOT_USERNAME=//p' "$staged")"
if sudo -n test -e "$target"; then
    previous="$(sudo -n sed -n 's/^HEARTGRAM_BOT_USERNAME=//p' "$target")"
    if [ -n "$previous" ] && [ "$previous" != "$incoming" ]; then
        printf '%s\n' 'Сервис уже подключён к другому боту.' >&2
        exit 1
    fi
fi
sudo -n chmod 0600 "$staged"
sudo -n mv -T -- "$staged" "$target"
sudo -n systemctl restart heartgram-profiles.service
sudo -n systemctl is-active --quiet heartgram-profiles.service
"""


def post_json(url, body, headers=None):
    request = Request(url, json.dumps(body).encode("utf-8"),
                      headers={"Content-Type": "application/json", **(headers or {})})
    with urlopen(request, timeout=15) as response:
        return json.load(response)


def bot_api(token, method, body):
    try:
        response = post_json(f"https://api.telegram.org/bot{token}/{method}", body)
    except HTTPError as error:
        detail = json.load(error).get("description", "")
        raise RuntimeError(f"Telegram {method}: HTTP {error.code}. {detail.replace(token, '[скрыто]')}") from None
    except (URLError, TimeoutError):
        raise RuntimeError(f"Не удалось подключиться к Telegram для {method}.") from None
    if not response.get("ok"):
        raise RuntimeError(f"Telegram не выполнил {method}.")
    return response["result"]


def main():
    parser = argparse.ArgumentParser(description="Подключить отдельного бота HeartGram через Telegram webhook.")
    parser.add_argument("public_address", help="публичный HTTPS-адрес сервиса HeartGram")
    parser.add_argument("ssh_target", help="пользователь@IPv4")
    parser.add_argument("--key", required=True, type=Path, help="локальный путь к SSH-ключу")
    args = parser.parse_args()
    base = args.public_address.rstrip("/")
    address = urlsplit(base)
    if address.scheme != "https" or not address.hostname or address.username or address.password or address.query or address.fragment:
        parser.error("нужен HTTPS-адрес сервиса")
    if not args.key.is_file() or not re.fullmatch(r"[a-zA-Z0-9_.-]+@[a-zA-Z0-9.-]+", args.ssh_target):
        parser.error("проверьте SSH-адрес и путь к ключу")
    if not sys.stdin.isatty():
        parser.error("запустите скрипт в обычном терминале для скрытого ввода токена")

    with urlopen(f"{base}/health", timeout=10) as response:
        if json.load(response).get("ok") is not True:
            raise RuntimeError("Сервис HeartGram не прошёл проверку /health.")
    token = getpass.getpass("Токен отдельного бота от BotFather (ввод скрыт): ").strip()
    if not re.fullmatch(r"[0-9]+:[A-Za-z0-9_-]{20,}", token):
        raise RuntimeError("Формат токена не похож на токен BotFather.")

    bot = bot_api(token, "getMe", {})
    username = bot.get("username", "")
    if not bot.get("is_bot") or not re.fullmatch(r"[A-Za-z0-9_]{5,32}", username):
        raise RuntimeError("Telegram не подтвердил имя бота.")

    webhook_url = f"{base}/telegram/webhook"
    previous = bot_api(token, "getWebhookInfo", {}).get("url", "")
    if previous and previous != webhook_url:
        raise RuntimeError("У бота уже есть webhook другого сервиса. Используйте отдельного бота HeartGram.")

    secret = secrets.token_urlsafe(32)
    if bot_api(token, "setWebhook", {"url": webhook_url, "secret_token": secret,
                                    "max_connections": 5, "allowed_updates": ["message"]}) is not True:
        raise RuntimeError("Telegram не подтвердил регистрацию webhook.")
    config = f"HEARTGRAM_BOT_USERNAME={username}\nHEARTGRAM_BOT_WEBHOOK_SECRET={secret}\n"
    subprocess.run(["ssh", "-i", str(args.key), "-o", "IdentitiesOnly=yes", "-o", "BatchMode=yes",
                    "-o", "StrictHostKeyChecking=yes", "-o", "ConnectTimeout=8",
                    "--", args.ssh_target, INSTALL_ENVIRONMENT],
                   input=config.encode("ascii"), check=True, timeout=25)

    for _ in range(5):
        try:
            reply = post_json(webhook_url, {}, {"X-Telegram-Bot-Api-Secret-Token": secret})
            break
        except HTTPError as error:
            if error.code not in (403, 502, 503):
                raise
            time.sleep(1)
    else:
        raise RuntimeError("Сервис на VPS ещё не принимает webhook.")
    if reply.get("ok") is not True or bot_api(token, "getWebhookInfo", {}).get("url") != webhook_url:
        raise RuntimeError("Финальная проверка webhook не прошла.")
    print(f"Бот @{username} подключён. Подтвердите аккаунт из HeartGram, чтобы проверить вход.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError, ValueError) as error:
        if isinstance(error, RuntimeError):
            print(error, file=sys.stderr)
        else:
            print("Не удалось завершить настройку. Проверьте доступ по SSH и работу /health.", file=sys.stderr)
        sys.exit(1)
