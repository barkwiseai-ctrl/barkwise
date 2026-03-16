#!/usr/bin/env python3
import argparse
import json
import random
import sqlite3
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from app.services.service_store import service_store  # noqa: E402


PHOTO_LIBRARY = [
    "https://images.unsplash.com/photo-1450778869180-41d0601e046e",
    "https://images.unsplash.com/photo-1517849845537-4d257902454a",
    "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
    "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
    "https://images.unsplash.com/photo-1530281700549-e82e7bf110d6",
    "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
    "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
    "https://images.unsplash.com/photo-1517423440428-a5a00ad493e8",
]

SUBURB_WEIGHTS = [
    ("Sunshine West", 0.55),
    ("Surry Hills", 0.18),
    ("Newtown", 0.17),
    ("Redfern", 0.10),
]

GROOMING_ADJECTIVES = [
    "Velvet",
    "Pawlish",
    "Fresh Coat",
    "Cedar",
    "Honey",
    "Cloud",
    "Calm",
    "Urban",
    "Neighbourhood",
    "Willow",
    "Soft Touch",
    "Pure Paw",
]

GROOMING_NOUNS = [
    "Groom Bar",
    "Coat Studio",
    "Wash House",
    "Trim Atelier",
    "Detail Lounge",
    "Pup Spa",
    "Groom Lab",
    "Bath Collective",
]

WALKING_ADJECTIVES = [
    "Parkloop",
    "Stride & Sniff",
    "TailTrail",
    "Morning Zoomies",
    "Leash Lane",
    "Rover Route",
    "Happy Harness",
    "Pack Pace",
    "Sunrise Snouts",
    "Pocket Walkers",
    "Neighbourhood Stroll",
]

WALKING_NOUNS = [
    "Crew",
    "Collective",
    "Club",
    "Co",
    "Routes",
    "Squad",
    "Walk Team",
]

GROOMING_DESCRIPTIONS = [
    "Gentle bath, coat care, and trim plans tailored to temperament and coat type.",
    "Stress-aware grooming sessions with clear handover notes and photo updates.",
    "Calm one-dog-at-a-time grooming with mat prevention and skin-safe products.",
    "Breed-aware full grooms with tidy finishes and post-session care summaries.",
]

WALKING_DESCRIPTIONS = [
    "Structured walks with sniff breaks, hydration checks, and clear pickup windows.",
    "Reliable solo and paired walks focused on calm leash behavior and confidence.",
    "Energy-matched dog walking with route variety and post-walk behavior notes.",
    "Flexible local walks with heat-aware timing, safe pacing, and photo updates.",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed diverse service listings for BarkWise.")
    parser.add_argument("--count", type=int, default=48, help="Total listings to add.")
    parser.add_argument(
        "--test-owner-user-id",
        default="user_2",
        help="Primary test account to keep provider-side pipeline testable.",
    )
    parser.add_argument(
        "--test-owner-count",
        type=int,
        default=12,
        help="How many of the seeded listings should belong to the test owner.",
    )
    parser.add_argument(
        "--grooming-ratio",
        type=float,
        default=0.65,
        help="Share of new listings that should be grooming (0.0-1.0).",
    )
    parser.add_argument("--seed", type=int, default=20260225, help="Random seed.")
    parser.add_argument(
        "--normalize-snowy-duplicates",
        type=int,
        default=1,
        help="Set to 1 to rename repeated 'Snowy Test Walkers' entries to unique names.",
    )
    parser.add_argument(
        "--keep-snowy-count",
        type=int,
        default=1,
        help="How many 'Snowy Test Walkers' entries to keep unchanged during normalization.",
    )
    parser.add_argument("--json-out", default="", help="Optional summary output path.")
    return parser.parse_args()


def load_existing_names() -> set[str]:
    names: set[str] = set()
    with sqlite3.connect(service_store.db_path) as conn:
        for (name,) in conn.execute("SELECT name FROM providers"):
            if isinstance(name, str):
                names.add(name.strip().lower())
    return names


def pick_suburb(rand: random.Random) -> str:
    suburbs = [item[0] for item in SUBURB_WEIGHTS]
    weights = [item[1] for item in SUBURB_WEIGHTS]
    return rand.choices(suburbs, weights=weights, k=1)[0]


def build_name(
    *,
    rand: random.Random,
    category: str,
    suburb: str,
    existing_names: set[str],
    serial: int,
) -> str:
    if category == "grooming":
        adjectives = GROOMING_ADJECTIVES
        nouns = GROOMING_NOUNS
    else:
        adjectives = WALKING_ADJECTIVES
        nouns = WALKING_NOUNS

    suburb_token = suburb.split()[0]
    for _ in range(80):
        base = f"{rand.choice(adjectives)} {rand.choice(nouns)}"
        candidate = f"{base} {suburb_token}"
        normalized = candidate.strip().lower()
        if "snowy test walkers" in normalized:
            continue
        if normalized not in existing_names:
            existing_names.add(normalized)
            return candidate

    fallback = f"{suburb_token} {'Grooming' if category == 'grooming' else 'Walking'} Collective {serial:03d}"
    existing_names.add(fallback.lower())
    return fallback


def build_description(rand: random.Random, category: str, suburb: str) -> tuple[str, str, int]:
    if category == "grooming":
        description = rand.choice(GROOMING_DESCRIPTIONS)
        add_on = rand.choice(
            [
                "Includes coat-condition check and gentle de-shed options.",
                "Supports anxious dogs with slower transition handling.",
                "Offers quick tidy-up add-ons between full sessions.",
            ]
        )
        price = rand.randint(48, 155)
    else:
        description = rand.choice(WALKING_DESCRIPTIONS)
        add_on = rand.choice(
            [
                "Supports reactivity-aware spacing and trigger notes.",
                "Offers midday, after-work, and weekend rounds.",
                "Includes reinforcement of sit, wait, and recall basics.",
            ]
        )
        price = rand.randint(20, 62)

    full_description = (
        f"{description} {add_on} "
        f"Service area centered on {suburb}, with clear communication before and after each booking."
    )
    return f"{description} {add_on}", full_description, price


def owner_for_index(index: int, test_owner_user_id: str, test_owner_count: int) -> str:
    if index < test_owner_count:
        return test_owner_user_id
    return f"seed_owner_{index - test_owner_count + 1:03d}"


def normalize_snowy_duplicates(
    *,
    rand: random.Random,
    existing_names: set[str],
    keep_count: int,
) -> list[dict[str, str]]:
    keep = max(0, keep_count)
    renamed: list[dict[str, str]] = []
    with sqlite3.connect(service_store.db_path) as conn:
        rows = conn.execute(
            """
            SELECT id, suburb, category
            FROM providers
            WHERE lower(name) = 'snowy test walkers'
            ORDER BY rowid ASC
            """
        ).fetchall()
        for idx, (provider_id, suburb, category) in enumerate(rows):
            if idx < keep:
                continue
            safe_suburb = str(suburb or "Sunshine West")
            safe_category = str(category or "dog_walking")
            replacement = build_name(
                rand=rand,
                category=safe_category,
                suburb=safe_suburb,
                existing_names=existing_names,
                serial=idx + 1,
            )
            conn.execute(
                "UPDATE providers SET name = ? WHERE id = ?",
                (replacement, str(provider_id)),
            )
            renamed.append(
                {
                    "id": str(provider_id),
                    "old_name": "Snowy Test Walkers",
                    "new_name": replacement,
                    "suburb": safe_suburb,
                }
            )
        conn.commit()
    return renamed


def main() -> None:
    args = parse_args()
    if args.count < 0:
        raise SystemExit("--count must be >= 0")
    if args.test_owner_count < 0:
        raise SystemExit("--test-owner-count must be >= 0")
    if not 0 <= args.grooming_ratio <= 1:
        raise SystemExit("--grooming-ratio must be between 0.0 and 1.0")
    if args.keep_snowy_count < 0:
        raise SystemExit("--keep-snowy-count must be >= 0")

    rand = random.Random(args.seed)
    existing_names = load_existing_names()
    created: list[dict[str, str]] = []

    for idx in range(args.count):
        category = "grooming" if rand.random() < args.grooming_ratio else "dog_walking"
        suburb = pick_suburb(rand)
        owner = owner_for_index(
            index=idx,
            test_owner_user_id=args.test_owner_user_id,
            test_owner_count=args.test_owner_count,
        )
        name = build_name(
            rand=rand,
            category=category,
            suburb=suburb,
            existing_names=existing_names,
            serial=idx + 1,
        )
        description, full_description, price = build_description(rand, category, suburb)
        images = rand.sample(PHOTO_LIBRARY, k=3)
        provider = service_store.add_provider(
            owner_user_id=owner,
            name=name,
            category=category,
            suburb=suburb,
            description=description,
            price_from=price,
            full_description=full_description,
            image_urls=images,
        )
        created.append(
            {
                "id": provider.id,
                "name": provider.name,
                "category": provider.category,
                "suburb": provider.suburb,
                "owner_user_id": owner,
            }
        )

    renamed_snowy = []
    if args.normalize_snowy_duplicates == 1:
        renamed_snowy = normalize_snowy_duplicates(
            rand=rand,
            existing_names=existing_names,
            keep_count=args.keep_snowy_count,
        )

    by_category: dict[str, int] = {}
    by_owner: dict[str, int] = {}
    by_suburb: dict[str, int] = {}
    for row in created:
        by_category[row["category"]] = by_category.get(row["category"], 0) + 1
        by_owner[row["owner_user_id"]] = by_owner.get(row["owner_user_id"], 0) + 1
        by_suburb[row["suburb"]] = by_suburb.get(row["suburb"], 0) + 1

    summary = {
        "created_count": len(created),
        "requested_count": args.count,
        "test_owner_user_id": args.test_owner_user_id,
        "test_owner_count_created": by_owner.get(args.test_owner_user_id, 0),
        "created_by_category": by_category,
        "created_by_suburb": by_suburb,
        "created_by_owner_top10": sorted(by_owner.items(), key=lambda item: item[1], reverse=True)[:10],
        "snowy_duplicates_renamed": len(renamed_snowy),
        "snowy_renamed_sample": renamed_snowy[:12],
        "sample": created[:12],
    }
    print(json.dumps(summary, indent=2))

    if args.json_out:
        out_path = Path(args.json_out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
