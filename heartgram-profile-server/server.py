"""HeartGram profile service. Run behind an HTTPS reverse proxy."""

import base64
import binascii
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs
from urllib.request import Request, urlopen

DEVELOPER, TESTER, BANNED, CREATOR, DONATE = 1, 2, 4, 8, 16
ASSIGNABLE = TESTER | CREATOR | DONATE
MAX_FRAME = 9
THEME_BUBBLE, RAINBOW_BUBBLE = -1, -2
MAX_BANNER = 768 * 1024
AUTH_LIFETIME = 30 * 86400


class ApiError(Exception):
    def __init__(self, status, message):
        self.status = status
        self.message = message


class Profiles:
    def __init__(self, path, bot, webhook_secret="", owner_id=0):
        self.path = path
        self.bot = bot
        self.owner_id = owner_id
        self.webhook_secret = webhook_secret
        with self.db() as db:
            db.executescript("""
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS profile (
                    uid INTEGER PRIMARY KEY, frame INTEGER NOT NULL DEFAULT 0,
                    active INTEGER, granted INTEGER NOT NULL DEFAULT 0,
                    banned_until INTEGER NOT NULL DEFAULT 0, banner BLOB,
                    revision INTEGER NOT NULL DEFAULT 0, bubble INTEGER NOT NULL DEFAULT -1
                );
                CREATE TABLE IF NOT EXISTS challenge (
                    code TEXT PRIMARY KEY, ticket TEXT NOT NULL UNIQUE,
                    uid INTEGER NOT NULL, expires INTEGER NOT NULL,
                    confirmed INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE IF NOT EXISTS session (
                    digest TEXT PRIMARY KEY, uid INTEGER NOT NULL, expires INTEGER NOT NULL
                );
            """)
            db.execute("BEGIN IMMEDIATE")
            if "bubble" not in {column[1] for column in db.execute("PRAGMA table_info(profile)")}:
                db.execute("ALTER TABLE profile ADD COLUMN bubble INTEGER NOT NULL DEFAULT -1")

    def db(self):
        return Connection(self.path)

    def connect(self, uid):
        if not self.bot:
            raise ApiError(503, "bot_unavailable")
        now = int(time.time())
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("DELETE FROM challenge WHERE expires <= ?", (now,))
            db.execute("DELETE FROM session WHERE expires <= ?", (now,))
            count = db.execute("SELECT COUNT(*) FROM challenge WHERE uid = ?", (uid,)).fetchone()[0]
            total = db.execute("SELECT COUNT(*) FROM challenge").fetchone()[0]
            if count >= 6 or total >= 1500:
                raise ApiError(429, "retry_later")
            code, ticket = secrets.token_urlsafe(24), secrets.token_urlsafe(32)
            db.execute("INSERT INTO challenge VALUES (?, ?, ?, ?, 0)", (code, digest(ticket), uid, now + 300))
        return {"ticket": ticket, "bot": f"https://t.me/{self.bot}?start=hg_{code}"}

    def confirm(self, uid, code):
        with self.db() as db:
            cursor = db.execute("UPDATE challenge SET confirmed = 1 WHERE code = ? AND uid = ? AND expires > ?",
                                (code, uid, int(time.time())))
            return cursor.rowcount == 1

    def redeem(self, ticket):
        now = int(time.time())
        key = digest(ticket)
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT uid, confirmed FROM challenge WHERE ticket = ? AND expires > ?", (key, now)).fetchone()
            if row is None:
                raise ApiError(404, "expired")
            if not row[1]:
                return {"pending": True}
            db.execute("DELETE FROM challenge WHERE ticket = ?", (key,))
            token = secrets.token_urlsafe(32)
            db.execute("INSERT INTO session VALUES (?, ?, ?)", (digest(token), row[0], now + AUTH_LIFETIME))
            return {"uid": str(row[0]), "token": token}

    def user_for(self, authorization):
        if not authorization or not authorization.startswith("Bearer "):
            raise ApiError(401, "link_account")
        token = authorization[7:]
        if not re.fullmatch(r"[A-Za-z0-9_-]{43}", token):
            raise ApiError(401, "link_account")
        with self.db() as db:
            row = db.execute("SELECT uid FROM session WHERE digest = ? AND expires > ?",
                             (digest(token), int(time.time()))).fetchone()
            if row is None:
                raise ApiError(401, "link_account")
            return row[0]

    def profile(self, db, uid, include_grants=False):
        row = db.execute("SELECT frame, active, granted, banned_until, banner IS NOT NULL, revision, bubble FROM profile WHERE uid = ?",
                         (uid,)).fetchone()
        frame, active, granted, until, banner, revision, bubble = row or (0, None, 0, 0, False, 0, THEME_BUBBLE)
        if self.owner_id and uid == self.owner_id:
            granted |= DEVELOPER
        banned = until > int(time.time())
        active = granted if active is None else (active & granted)
        if bubble == RAINBOW_BUBBLE and (not granted or banned):
            bubble = THEME_BUBBLE
        result = {"uid": str(uid), "frame": frame, "badges": active | (BANNED if banned else 0),
                  "banner": bool(banner), "revision": revision, "banned_until": until if banned else 0, "bubble": bubble}
        if include_grants:
            result["granted"] = granted
        return result

    def get(self, uid, grants=False):
        with self.db() as db:
            return self.profile(db, uid, grants)

    def decorations(self, ids):
        with self.db() as db:
            result = []
            for uid in ids:
                data = self.profile(db, uid)
                if data["badges"] or data["frame"] or data["bubble"] != THEME_BUBBLE:
                    result.append({key: data[key] for key in ("uid", "frame", "badges", "bubble")})
            return {"profiles": result}

    def banner(self, uid):
        with self.db() as db:
            row = db.execute("SELECT banner FROM profile WHERE uid = ?", (uid,)).fetchone()
            if row is None or row[0] is None:
                raise ApiError(404, "no_banner")
            return row[0]

    def edit(self, uid, body):
        frame = number(body, "frame", 0, MAX_FRAME) if "frame" in body else None
        requested = number(body, "badges", 0, 31) if "badges" in body else None
        bubble = number(body, "bubble", RAINBOW_BUBBLE, 0xFFFFFF) if "bubble" in body else None
        if frame is None and requested is None and bubble is None and "banner" not in body:
            raise ApiError(400, "empty_edit")
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            data = self.profile(db, uid, True)
            if data["banned_until"]:
                raise ApiError(403, "banned")
            if requested is not None and requested & ~data["granted"]:
                raise ApiError(403, "badge_unavailable")
            if bubble == RAINBOW_BUBBLE and not data["granted"]:
                raise ApiError(403, "badge_required")
            db.execute("INSERT OR IGNORE INTO profile(uid) VALUES (?)", (uid,))
            db.execute("""UPDATE profile SET frame = COALESCE(?, frame),
                       active = COALESCE(?, active), bubble = COALESCE(?, bubble), revision = revision + 1 WHERE uid = ?""",
                       (frame, requested, bubble, uid))
            if "banner" in body:
                banner = body["banner"]
                if banner is not None:
                    try:
                        banner = base64.b64decode(banner, validate=True)
                    except (ValueError, TypeError, binascii.Error) as exc:
                        raise ApiError(400, "invalid_banner") from exc
                    if len(banner) > MAX_BANNER or not valid_banner(banner):
                        raise ApiError(400, "invalid_banner")
                db.execute("UPDATE profile SET banner = ? WHERE uid = ?", (banner, uid))
            return self.profile(db, uid, True)

    def moderate(self, caller, uid, body):
        if not self.owner_id or caller != self.owner_id or uid == self.owner_id:
            raise ApiError(403, "owner_only")
        granted = number(body, "granted", 0, 31)
        seconds = number(body, "seconds", 0, 366 * 86400) if "seconds" in body else None
        if granted & ~ASSIGNABLE:
            raise ApiError(400, "invalid_badges")
        with self.db() as db:
            db.execute("INSERT OR IGNORE INTO profile(uid) VALUES (?)", (uid,))
            db.execute("""UPDATE profile SET granted = ?, banned_until = COALESCE(?, banned_until),
                       active = (active & ?) | (? & ~granted), revision = revision + 1 WHERE uid = ?""",
                       (granted, None if seconds is None else int(time.time()) + seconds if seconds else 0, granted, granted, uid))
            return self.profile(db, uid, True)


class Connection(sqlite3.Connection):
    def __init__(self, path):
        super().__init__(path, timeout=5)

    def __exit__(self, exc_type, exc_value, trace):
        try:
            return super().__exit__(exc_type, exc_value, trace)
        finally:
            self.close()


def number(body, key, minimum, maximum):
    value = body.get(key)
    if type(value) is not int or not minimum <= value <= maximum:
        raise ApiError(400, "invalid_" + key)
    return value


def valid_banner(data):
    if len(data) < 12 or not data.startswith(b"\xff\xd8") or not data.endswith(b"\xff\xd9"):
        return False
    pos = 2
    while pos + 4 < len(data) and data[pos] == 255:
        marker = data[pos + 1]
        size = int.from_bytes(data[pos + 2:pos + 4], "big")
        if size < 2 or pos + 2 + size > len(data):
            return False
        if marker in (0xc0, 0xc2) and size >= 8:
            height = int.from_bytes(data[pos + 5:pos + 7], "big")
            width = int.from_bytes(data[pos + 7:pos + 9], "big")
            return 0 < width <= 1200 and 0 < height <= 600
        pos += 2 + size
    return False


def user_id(value):
    if not isinstance(value, str) or not re.fullmatch(r"[1-9][0-9]{0,15}", value):
        raise ApiError(400, "invalid_uid")
    return int(value)


def digest(token):
    if not isinstance(token, str) or len(token) > 128:
        raise ApiError(400, "invalid_ticket")
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


class Handler(BaseHTTPRequestHandler):
    server_version = "HeartGramProfiles"

    def setup(self):
        self.request.settimeout(10)
        super().setup()

    def do_GET(self):
        self.respond(False)

    def do_POST(self):
        self.respond(True)

    def respond(self, post):
        try:
            payload, kind = self.route(post)
            self.send_response(200)
        except ApiError as error:
            self.send_response(error.status)
            payload, kind = {"error": error.message}, "application/json"
        if kind == "application/json":
            payload = json.dumps(payload, separators=(",", ":")).encode()
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(payload)

    def body(self):
        size = self.headers.get("Content-Length", "")
        if not size.isdecimal() or len(size) > 8 or int(size) > MAX_BANNER * 4 // 3 + 8192:
            raise ApiError(413, "payload_too_large")
        try:
            body = json.loads(self.rfile.read(int(size)))
        except (ValueError, UnicodeError) as exc:
            raise ApiError(400, "invalid_json") from exc
        if not isinstance(body, dict):
            raise ApiError(400, "invalid_json")
        return body

    def route(self, post):
        profiles = self.server.profiles
        path, _, query = self.path.partition("?")
        json_type = "application/json"
        auth = self.headers.get("Authorization")
        if post and path == "/connect":
            return profiles.connect(user_id(self.body().get("uid"))), json_type
        if post and path == "/connect/redeem":
            return profiles.redeem(self.body().get("ticket")), json_type
        if post and path == "/telegram/webhook":
            secret = self.headers.get("X-Telegram-Bot-Api-Secret-Token", "")
            if not profiles.webhook_secret or not secret.isascii() or not hmac.compare_digest(secret, profiles.webhook_secret):
                raise ApiError(403, "webhook_forbidden")
            reply = confirm_start(profiles, self.body())
            return ({"method": "sendMessage", **reply} if reply else {"ok": True}), json_type
        if path == "/me":
            uid = profiles.user_for(auth)
            return (profiles.edit(uid, self.body()) if post else profiles.get(uid, True)), json_type
        if not post and path == "/decorations":
            fields = parse_qs(query).get("ids", [])
            ids = fields[0].split(",", 40) if len(fields) == 1 else []
            if not 1 <= len(ids) <= 40:
                raise ApiError(400, "invalid_ids")
            return profiles.decorations(dict.fromkeys(map(user_id, ids))), json_type
        admin = re.fullmatch(r"/admin/([0-9]+)", path)
        if admin:
            caller = profiles.user_for(auth)
            if not profiles.owner_id or caller != profiles.owner_id:
                raise ApiError(403, "owner_only")
            uid = user_id(admin[1])
            return (profiles.moderate(caller, uid, self.body()) if post else profiles.get(uid, True)), json_type
        view = re.fullmatch(r"/profiles/([0-9]+)(/banner)?", path)
        if view and not post:
            uid = user_id(view[1])
            return (profiles.banner(uid), "image/jpeg") if view[2] else (profiles.get(uid), json_type)
        if path == "/health" and not post:
            return {"ok": True}, json_type
        raise ApiError(404, "not_found")

    def log_message(self, format, *args):
        # Challenge and session values go through request bodies and are never logged.
        return


def confirm_start(profiles, update):
    message = update.get("message")
    if not isinstance(message, dict):
        return None
    sender = message.get("from") or {}
    chat = message.get("chat") or {}
    if not isinstance(sender, dict) or not isinstance(chat, dict):
        return None
    uid = sender.get("id")
    text = message.get("text")
    if type(uid) is not int or uid <= 0 or uid != chat.get("id") or chat.get("type") != "private" or not isinstance(text, str):
        return None
    match = re.fullmatch(r"/start hg_([A-Za-z0-9_-]{32})", text)
    if not match:
        return None
    ok = profiles.confirm(uid, match[1])
    return {"chat_id": uid, "text":
            "HeartGram: вернитесь в приложение и нажмите «Проверить»." if ok
            else "HeartGram: ссылка истекла или открыта из другого аккаунта."}


def bot_loop(profiles, token):
    offset = 0
    while True:
        try:
            result = bot_call(token, "getUpdates", {"offset": offset, "timeout": 25, "allowed_updates": ["message"]})
            for update in result:
                reply = confirm_start(profiles, update)
                if reply:
                    bot_call(token, "sendMessage", reply)
                offset = update["update_id"] + 1
        except Exception:
            # Avoid logging URLs that contain the bot token.
            time.sleep(4)


def bot_call(token, method, body):
    request = Request(f"https://api.telegram.org/bot{token}/{method}", data=json.dumps(body).encode(),
                      headers={"Content-Type": "application/json"})
    with urlopen(request, timeout=35) as response:
        result = json.load(response)
        if not result.get("ok"):
            raise RuntimeError("Telegram bot API error")
        return result["result"]


def main():
    token = os.environ.get("HEARTGRAM_BOT_TOKEN", "")
    webhook_secret = os.environ.get("HEARTGRAM_BOT_WEBHOOK_SECRET", "")
    if webhook_secret and not re.fullmatch(r"[A-Za-z0-9_-]{32,256}", webhook_secret):
        raise SystemExit("Set a valid HEARTGRAM_BOT_WEBHOOK_SECRET")
    bot = os.environ.get("HEARTGRAM_BOT_USERNAME", "").lstrip("@") if token or webhook_secret else ""
    if (token or webhook_secret) and not re.fullmatch(r"[A-Za-z0-9_]{5,32}", bot):
        raise SystemExit("Set a valid HEARTGRAM_BOT_USERNAME")
    owner = os.environ.get("HEARTGRAM_OWNER_ID", "")
    if owner and not re.fullmatch(r"[1-9][0-9]{0,15}", owner):
        raise SystemExit("Set HEARTGRAM_OWNER_ID to a valid Telegram user ID")
    profiles = Profiles(os.environ.get("HEARTGRAM_PROFILE_DB", "heartgram-profiles.sqlite3"),
                        bot, webhook_secret, int(owner) if owner else 0)
    if token and not webhook_secret:
        threading.Thread(target=bot_loop, args=(profiles, token), daemon=True).start()
    with ThreadingHTTPServer((os.environ.get("HEARTGRAM_BIND", "127.0.0.1"), int(os.environ.get("PORT", "8315"))), Handler) as server:
        server.profiles = profiles
        server.serve_forever()


if __name__ == "__main__":
    main()
