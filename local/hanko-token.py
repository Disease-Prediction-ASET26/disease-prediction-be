#!/usr/bin/env python3
"""Get a session token from the local Hanko, to call the backend without the frontend.

Signs in with an email passcode and reads the passcode from Mailpit. An email Hanko does not
know is registered first. With --onboard, a user without a profile also gets a patient profile
with consent, so the token works for assessments straight away.

Local stack only (docker compose up -d): it refuses any host other than localhost, because it
reads passcodes from Mailpit.

    TOKEN=$(./local/hanko-token.py ana@test.com --onboard)
    curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/assessments

The token is printed on stdout; progress goes to stderr. Tokens last 1 hour (local Hanko).
Needs Python 3.9+ and no extra packages.
"""

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

CAPABILITIES = {
    "webauthn_available": False,
    "webauthn_conditional_mediation_available": False,
    "webauthn_platform_authenticator_available": False,
}
UNKNOWN_EMAIL_SUBJECT = "not recognized"
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}


class FlowError(Exception):
    pass


def log(message):
    print(message, file=sys.stderr)


def request(method, url, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for name, value in (headers or {}).items():
        req.add_header(name, value)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            raw = response.read()
            return response.status, (json.loads(raw) if raw else None), response.headers
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            payload = json.loads(raw) if raw else None
        except ValueError:
            payload = raw.decode(errors="replace")
        return error.code, payload, error.headers
    except urllib.error.URLError as error:
        raise FlowError(f"Cannot reach {url}: {error.reason}. Is the local stack running?")


class HankoFlow:
    """One Hanko flow (login or registration): each step posts an action with the CSRF token."""

    def __init__(self, hanko_url, flow):
        self.hanko_url = hanko_url
        self.token = None
        self.state = self._post(f"{hanko_url}/{flow}", {})

    def _post(self, url, body):
        status, state, headers = request("POST", url, body)
        if not isinstance(state, dict) or "name" not in state:
            raise FlowError(f"Unexpected Hanko response ({status}): {state}")
        if headers.get("X-Auth-Token"):
            self.token = headers["X-Auth-Token"]
        return state

    @property
    def name(self):
        return self.state["name"]

    def actions(self):
        return self.state.get("actions") or {}

    def act(self, action, inputs=None):
        if action not in self.actions():
            raise FlowError(
                f"Hanko step '{self.name}' has no action '{action}'"
                f" (has: {', '.join(self.actions()) or 'none'})"
            )
        href = self.actions()[action]["href"]
        self.state = self._post(
            self.hanko_url + href,
            {"csrf_token": self.state.get("csrf_token", ""), "input_data": inputs or {}},
        )
        if self.state.get("status", 200) >= 400:
            error = self.state.get("error") or {}
            raise FlowError(f"Hanko refused '{action}': {error.get('message', self.state)}")
        return self

    def skip_optional_steps(self):
        """After a successful passcode: skip MFA and passkey offers until the flow succeeds."""
        for _ in range(5):
            if self.token or self.name == "success":
                return
            skip = next((a for a in self.actions() if a.startswith("skip")), None)
            if skip is None:
                raise FlowError(f"Hanko stopped at '{self.name}' with no step to skip")
            self.act(skip)


def wait_for_mail(mailpit_url, email, since, timeout=20):
    """The newest message to `email` received after `since`."""
    query = urllib.parse.quote(f'to:"{email}"')
    deadline = time.time() + timeout
    while time.time() < deadline:
        status, payload, _ = request(
            "GET", f"{mailpit_url}/api/v1/search?query={query}&limit=5"
        )
        if status != 200:
            raise FlowError(f"Mailpit search failed ({status}): {payload}")
        for message in payload.get("messages", []):
            created = datetime.fromisoformat(message["Created"].replace("Z", "+00:00"))
            if created >= since:
                return message
        time.sleep(1)
    raise FlowError(f"No email to {email} arrived in Mailpit within {timeout}s")


def passcode_from(message):
    found = re.search(r"\b(\d{6})\b", message.get("Subject", ""))
    if not found:
        raise FlowError(f"No passcode in the email '{message.get('Subject')}'")
    return found.group(1)


def now():
    # One second of slack for clock differences between the host and the containers.
    return datetime.fromtimestamp(time.time() - 1, tz=timezone.utc)


def login(args):
    flow = HankoFlow(args.hanko_url, "login")
    flow.act("register_client_capabilities", CAPABILITIES)
    flow.act("continue_with_login_identifier", {"email": args.email})
    sent = now()
    flow.act("continue_to_passcode_confirmation")
    message = wait_for_mail(args.mailpit_url, args.email, sent)
    if UNKNOWN_EMAIL_SUBJECT in message.get("Subject", "").lower():
        return None
    flow.act("verify_passcode", {"code": passcode_from(message)})
    flow.skip_optional_steps()
    return flow.token


def register(args):
    flow = HankoFlow(args.hanko_url, "registration")
    flow.act("register_client_capabilities", CAPABILITIES)
    sent = now()
    flow.act("register_login_identifier", {"email": args.email})
    flow.act("verify_passcode", {"code": passcode_from(wait_for_mail(args.mailpit_url, args.email, sent))})
    if "register_password" in flow.actions():
        flow.act("register_password", {"new_password": args.password})
    flow.skip_optional_steps()
    return flow.token


def onboard(args, token):
    """Creates a patient profile with consent when the account has none yet."""
    auth = {"Authorization": f"Bearer {token}"}
    status, body, _ = request("GET", f"{args.api_url}/api/me", headers=auth)
    if status == 200:
        log(f"Profile exists: {body.get('role')}, consent {body.get('consentGranted')}")
        return
    if status != 404:
        raise FlowError(f"GET /api/me answered {status}: {body}")
    profile = {"fullName": args.name, "consentGranted": True}
    if args.date_of_birth:
        profile["dateOfBirth"] = args.date_of_birth
    status, body, _ = request("POST", f"{args.api_url}/api/me", profile, headers=auth)
    if status != 201:
        raise FlowError(f"POST /api/me answered {status}: {body}")
    log(f"Created patient profile '{args.name}' with consent")


def require_local(url, option):
    host = urllib.parse.urlparse(url).hostname
    if host not in LOCAL_HOSTS:
        raise FlowError(f"{option} must point at localhost, not {host}")


def main():
    parser = argparse.ArgumentParser(
        description="Get a session token from the local Hanko (passcodes read from Mailpit)."
    )
    parser.add_argument("email", help="account email; unknown emails are registered first")
    parser.add_argument("--onboard", action="store_true",
                        help="create a patient profile with consent if the account has none")
    parser.add_argument("--name", default="Test Patient", help="full name for --onboard")
    parser.add_argument("--date-of-birth", help="YYYY-MM-DD for --onboard (optional)")
    parser.add_argument("--password", default="local-dev-Passw0rd!",
                        help="password set when registering a new account")
    parser.add_argument("--hanko-url", default="http://localhost:8100")
    parser.add_argument("--mailpit-url", default="http://localhost:8025")
    parser.add_argument("--api-url", default="http://localhost:8080")
    args = parser.parse_args()

    try:
        for url, option in ((args.hanko_url, "--hanko-url"),
                            (args.mailpit_url, "--mailpit-url"),
                            (args.api_url, "--api-url")):
            require_local(url, option)
        token = login(args)
        if token:
            log(f"Signed in as {args.email}")
        else:
            log(f"{args.email} is not registered in the local Hanko; registering it")
            token = register(args)
        if not token:
            raise FlowError("Hanko finished without returning a session token")
        if args.onboard:
            onboard(args, token)
    except FlowError as error:
        log(f"error: {error}")
        return 1
    print(token)
    return 0


if __name__ == "__main__":
    sys.exit(main())
