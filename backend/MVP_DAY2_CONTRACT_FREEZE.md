# MVP Day 2 Contract Freeze

Date: 2026-02-22

This document freezes the backend response contracts for the two MVP journeys:
- Journey A: Meet up at a park to socialize dogs.
- Journey B: Book a groomer.

Any additive/removal/rename changes to these response payload keys require explicit contract review and updates to tests.

## Journey A Contracts

### `GET /community/groups`
Response item keys:
- `id`
- `name`
- `suburb`
- `member_count`
- `official`
- `owner_user_id`
- `membership_status`
- `is_admin`
- `pending_request_count`
- `group_badges`
- `cooperative_score`
- `my_pack_builder_points`
- `my_clean_park_points`

### `GET /community/events`
### `POST /community/events`
### `POST /community/events/{event_id}/rsvp`
Response keys:
- `id`
- `title`
- `description`
- `suburb`
- `date`
- `group_id`
- `attendee_count`
- `created_by`
- `rsvp_status`
- `status`

### `POST /community/groups/{group_id}/challenges/participate` (check-in)
Response keys:
- `challenge`
- `my_contribution_count`
- `contribution_count`
- `reward_unlocked`
- `unlocked_badges`

Nested `challenge` keys:
- `id`
- `group_id`
- `type`
- `title`
- `description`
- `target_count`
- `progress_count`
- `status`
- `reward_label`
- `start_at`
- `end_at`

## Journey B Contracts

### `GET /services/providers?category=grooming`
Response item keys:
- `id`
- `name`
- `category`
- `suburb`
- `rating`
- `review_count`
- `price_from`
- `description`
- `full_description`
- `image_urls`
- `latitude`
- `longitude`
- `distance_km`
- `owner_user_id`
- `owner_label`
- `status`
- `response_time_minutes`
- `local_bookers_this_month`
- `shared_group_bookers`
- `social_proof`
- `quote_sprint_tier`
- `quote_response_rate_pct`
- `quote_response_streak`
- `vet_checked`
- `vet_checked_until`
- `vet_checked_by`
- `highlighted_vet`
- `highlighted_vet_until`

### `GET /services/providers/{provider_id}/availability`
Response item keys:
- `date`
- `time_slot`
- `available`
- `reason`

### `POST /services/bookings`
Response keys:
- `id`
- `owner_user_id`
- `provider_id`
- `pet_name`
- `date`
- `time_slot`
- `note`
- `status`

## Notifications Contracts (both journeys)

### `POST /notifications/register-device`
Response:
- `{ "status": "ok" }`

### `GET /notifications`
### `POST /notifications/{notification_id}/read`
Response keys:
- `id`
- `user_id`
- `title`
- `body`
- `category`
- `read`
- `created_at`
- `deep_link`

## Enforcement

Contract enforcement test:
- `/Users/yingxu/public-repos/pet-social-app/backend/tests/test_api.py`
- `test_mvp_day2_contract_freeze_parks_and_grooming`
