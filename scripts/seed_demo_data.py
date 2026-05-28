#!/usr/bin/env python3
"""Insert demo devices and SMS messages for local UI testing."""

from __future__ import annotations

import hashlib
import secrets
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parents[1] / "backend"
sys.path.insert(0, str(BACKEND_DIR))

from database import get_connection, init_db, utc_now  # noqa: E402
from services.code_extractor import extract_verification_code  # noqa: E402

TZ = timezone(timedelta(hours=8))

DEMO_DEVICES = (
    ("iqoo 主力机", "17316543631"),
    ("红米备用", "13800138000"),
    ("测试卡2", None),
)

DEMO_MESSAGES = (
    ("10690000", "【阿里巴巴】验证码2751，您正在进行短信登录，切勿将验证码泄露于他人，验证码15分钟内有效。"),
    ("10086", "【中国移动】您的验证码是839201，5分钟内有效。"),
    ("95588", "【工商银行】登录验证码：662840，请勿告知他人。"),
    ("106907003531", "【腾讯科技】微信登录验证码：483920，10分钟内有效。"),
    ("1065502", "【京东】您的校验码为119283，请在10分钟内完成验证。"),
    ("95188", "【支付宝】验证码582910，您正在登录支付宝，请勿泄露。"),
    ("1069095599", "【美团】验证码 736251，用于手机号登录。"),
    ("10010", "【中国联通】您的动态密码是904817，有效期5分钟。"),
    ("10690000", "【阿里巴巴】验证码881023，您正在修改绑定手机。"),
    ("95533", "【建设银行】验证码：330918，任何索要验证码的都是骗子。"),
    ("106907003531", "【腾讯科技】QQ安全登录验证码448201。"),
    ("10086", "【中国移动】尊敬的客户，验证码628471，用于业务办理。"),
    ("1065502", "【京东】验证码556102，用于账号登录验证。"),
    ("95188", "【支付宝】您的验证码是771204，请勿转发。"),
    ("1069095599", "【美团】验证码902134，15分钟内有效。"),
    ("10010", "【中国联通】验证码445678，用于身份验证。"),
    ("95588", "【工商银行】动态验证码332901，请勿泄露。"),
    ("10690000", "【阿里巴巴】验证码102938，您正在进行支付验证。"),
    ("10086", "【中国移动】验证码556677，用于套餐变更确认。"),
    ("106907003531", "【腾讯科技】验证码889900，用于游戏账号绑定。"),
    ("95188", "【支付宝】验证码223344，用于找回密码。"),
    ("1065502", "【京东】验证码778899，用于收货确认。"),
    ("95533", "【建设银行】验证码665544，用于转账确认。"),
    ("10010", "【中国联通】验证码112233，用于停机复机。"),
    ("1069095599", "【美团】验证码998877，用于外卖支付。"),
)


def build_message_hash(device_id: str, sender: str, body: str) -> str:
    raw = f"{device_id}|{sender.strip()}|{body.strip()}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def seed() -> None:
    init_db()
    now = datetime.now(TZ)
    created = utc_now()

    with get_connection() as conn:
        device_ids: list[str] = []
        for name, phone in DEMO_DEVICES:
            row = conn.execute(
                "SELECT device_id FROM devices WHERE device_name = ?",
                (name,),
            ).fetchone()
            if row:
                device_ids.append(row["device_id"])
                continue

            device_id = str(uuid.uuid4())
            api_key = secrets.token_urlsafe(24)
            conn.execute(
                """
                INSERT INTO devices (
                    device_id, device_name, phone_number, api_key, last_seen_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                (device_id, name, phone, api_key, created, created),
            )
            device_ids.append(device_id)

        inserted = 0
        for index, (sender, body) in enumerate(DEMO_MESSAGES):
            device_id = device_ids[index % len(device_ids)]
            received_at = (now - timedelta(hours=index * 2, minutes=index * 3)).isoformat()
            message_hash = build_message_hash(device_id, sender, body)
            exists = conn.execute(
                "SELECT id FROM sms_messages WHERE message_hash = ?",
                (message_hash,),
            ).fetchone()
            if exists:
                continue

            verification_code = extract_verification_code(body)
            conn.execute(
                """
                INSERT INTO sms_messages (
                    device_id, sender, body, received_at, created_at,
                    verification_code, message_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    device_id,
                    sender,
                    body,
                    received_at,
                    created,
                    verification_code,
                    message_hash,
                ),
            )
            inserted += 1

            conn.execute(
                """
                UPDATE devices SET last_seen_at = ?
                WHERE device_id = ?
                """,
                (received_at, device_id),
            )

    print(f"demo devices: {len(device_ids)}")
    print(f"new sms inserted: {inserted}")
    print("open http://127.0.0.1:8080/sms/ (password: admin123)")


if __name__ == "__main__":
    seed()
