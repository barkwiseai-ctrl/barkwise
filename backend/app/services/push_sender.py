import logging
import os
from threading import Lock
from typing import List

logger = logging.getLogger(__name__)


class PushSender:
    def __init__(self):
        self._lock = Lock()
        self._initialized = False
        self._enabled = False
        self._messaging = None

    def _ensure_initialized(self) -> None:
        if self._initialized:
            return
        with self._lock:
            if self._initialized:
                return
            credentials_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "").strip()
            if not credentials_path:
                self._initialized = True
                self._enabled = False
                logger.info("Push sender disabled: FIREBASE_CREDENTIALS_PATH not set")
                return
            try:
                import firebase_admin
                from firebase_admin import credentials, messaging
            except Exception:
                self._initialized = True
                self._enabled = False
                logger.exception("Push sender disabled: firebase-admin import failed")
                return

            try:
                cred = credentials.Certificate(credentials_path)
                if not firebase_admin._apps:  # pylint: disable=protected-access
                    firebase_admin.initialize_app(cred)
                self._messaging = messaging
                self._enabled = True
                logger.info("Push sender initialized")
            except Exception:
                self._enabled = False
                logger.exception("Push sender disabled: Firebase init failed")
            finally:
                self._initialized = True

    def send_notification(
        self,
        tokens: List[str],
        title: str,
        body: str,
        data: dict[str, str],
    ) -> List[str]:
        self._ensure_initialized()
        if not self._enabled or not tokens:
            return []
        assert self._messaging is not None
        try:
            message = self._messaging.MulticastMessage(
                notification=self._messaging.Notification(title=title, body=body),
                tokens=tokens,
                data=data,
            )
            batch = self._messaging.send_each_for_multicast(message)
            invalid: List[str] = []
            for idx, response in enumerate(batch.responses):
                if response.success:
                    continue
                error_text = str(response.exception).lower() if response.exception else ""
                if "registration token" in error_text or "invalid argument" in error_text:
                    invalid.append(tokens[idx])
            return invalid
        except Exception:
            logger.exception("Push send failed")
            return []


push_sender = PushSender()
