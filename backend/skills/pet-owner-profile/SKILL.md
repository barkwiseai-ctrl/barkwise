---
name: pet-owner-profile
description: Capture and update pet owner profile attributes from conversation context. Use when users share pet name, breed, age, weight, suburb, or ask to update profile.
---

## When to Use
- Use when the message contains pet identity or health-context profile details.
- Use when the user asks to edit or confirm profile information.

## Tools
- `add_pet_owner_profile`

## Workflow
- Parse profile values from free-text message.
- Persist parsed values into profile memory and lock confirmed fields.
- Return profile card payload for A2UI rendering.
