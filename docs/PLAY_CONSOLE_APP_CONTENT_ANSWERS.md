# Play Console App Content Answers (BarkWise)

Last updated: 2026-03-02

This file gives recommended answers for common Play Console policy forms for this codebase.

## 1) OAuth Question (Closed Testing)

Short answer: **No, OAuth is not required for closed testing**.

- You only need OAuth if your app integrates OAuth-based identity/providers (for example Google Sign-In).
- This app currently uses invite + OTP/password + bearer token auth and does not implement OAuth providers.

## 2) Privacy Policy URL

Use this page (after deployment):

- `/web/privacy/`

For the **Account deletion URL** field in Play Console, use:

- `/web/privacy/delete-account/`

Example full URL:

- `https://<your-backend-domain>/web/privacy/`
- `https://<your-backend-domain>/web/privacy/delete-account/`

The policy page is in:

- `/Users/yingxu/public-repos/pet-social-app/backend/app/web/privacy/index.html`

## 2b) Child Safety Standards URL

Use this public standards page in child safety declarations:

- `https://<your-backend-domain>/web/child-safety/`

Page source:

- `/Users/yingxu/public-repos/pet-social-app/backend/app/web/child-safety/index.html`

## 3) App Access (Play Console)

Recommended answer: **Yes, some app functionality is restricted behind login/invite**.

Provide reviewers:

- test account method: invite + OTP login
- any test credentials needed for demo mode
- clear steps for full feature access (community, services, messaging, chat)

Why:

- API routes enforce actor authorization for user data actions.
- core flows use login/session state.

## 4) Account Deletion (Play User Data)

Recommended answer: **Yes, users can request account deletion in-app**.

Evidence:

- Android app calls `DELETE /auth/me`.
- Backend `delete_me` removes user-linked records across auth, messages, notifications, services, community, and memory stores.

## 5) Data Safety (Suggested Selections)

Important: confirm these against your deployment toggles (`OPENAI_API_KEY`, Firebase setup, OTP email provider).

### Data collected

- Personal info:
  - Email address (invite/OTP flow)
  - User ID/account identifiers
- Location:
  - Approximate location (coarse permission)
  - Optional lat/lng in certain community/onboarding flows
- App activity:
  - User-generated content (posts/comments/messages/chat)
  - In-app analytics/diagnostic events (community analytics endpoints)
- App info and performance:
  - Diagnostic/perf/crash events (including optional Firebase/Crashlytics when configured)
- Device or other IDs:
  - Push notification device token

### Data sharing (third parties/processors)

Potential sharing in configured deployments:

- OpenAI (chat processing)
- Resend (OTP email delivery)
- Firebase Cloud Messaging (push notifications)
- Google services for maps/location features

### Data handling flags

Recommended when true in your deployment:

- Data is encrypted in transit: **Yes** (production HTTPS/TLS expected)
- Users can request deletion: **Yes** (in-app + backend endpoint)

## 6) Ads

Recommended answer: **No** (if you do not serve ads).

If this changes, update Ads declaration before rollout.

## 7) Content Rating

Complete questionnaire according to current app behavior (social/community + user-generated content + messaging).

You should declare moderation/report/block functionality where prompted.

## 8) Financial Features / Other Declarations

If asked about financial features:

- Current app scope: not a banking, lending, trading, or wallet app.
- Answer accordingly unless product scope changes.

## 9) Evidence Pointers In Code

- Auth + delete account:
  - `/Users/yingxu/public-repos/pet-social-app/backend/app/routers/auth.py`
- Notification tokens:
  - `/Users/yingxu/public-repos/pet-social-app/backend/app/routers/notifications.py`
  - `/Users/yingxu/public-repos/pet-social-app/backend/app/services/notification_store.py`
- Chat and AI provider wiring:
  - `/Users/yingxu/public-repos/pet-social-app/backend/app/routers/chat.py`
  - `/Users/yingxu/public-repos/pet-social-app/backend/app/services/ai_orchestrator.py`
- Community uploads + analytics:
  - `/Users/yingxu/public-repos/pet-social-app/backend/app/routers/community.py`
- Android permissions:
  - `/Users/yingxu/public-repos/pet-social-app/android/app/src/main/AndroidManifest.xml`

## 10) Official Google Policy References

- User Data policy overview:
  - https://support.google.com/googleplay/android-developer/answer/9888076
- Data safety form guide:
  - https://support.google.com/googleplay/android-developer/answer/10787469
- Account deletion requirement:
  - https://support.google.com/googleplay/android-developer/answer/13327111
- App access declarations:
  - https://support.google.com/googleplay/android-developer/answer/9859455
- Financial features declaration:
  - https://support.google.com/googleplay/android-developer/answer/12671314
