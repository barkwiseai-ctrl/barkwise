import re
from dataclasses import dataclass
from typing import List, Optional, Pattern

from app.models import ChatCitation


@dataclass(frozen=True)
class FaqMatchResult:
    faq_id: str
    answer: str
    badges: List[str]
    citations: List[ChatCitation]


@dataclass(frozen=True)
class _FaqEntry:
    faq_id: str
    answer: str
    badges: List[str]
    citations: List[ChatCitation]
    patterns: List[Pattern[str]]

    def matches(self, message: str) -> bool:
        return all(pattern.search(message) for pattern in self.patterns)


def _pattern(value: str) -> Pattern[str]:
    return re.compile(value, re.IGNORECASE)


FAQ_ENTRIES: List[_FaqEntry] = [
    _FaqEntry(
        faq_id="dog_vaccines",
        answer=(
            "For most dogs, core vaccines are planned by age and risk profile, then boosted on schedule. "
            "Use your vet to finalize timing based on your dog's age, history, and local exposure risk."
        ),
        badges=["FAQ QA", "Vet Guidance"],
        citations=[
            ChatCitation(
                title="Canine Vaccination Guidelines",
                source="AAHA",
                url="https://www.aaha.org/resources/2022-aaha-canine-vaccination-guidelines/",
            ),
            ChatCitation(
                title="Vaccination Guidelines",
                source="WSAVA",
                url="https://wsava.org/global-guidelines/vaccination-guidelines/",
            ),
        ],
        patterns=[
            _pattern(r"\b(vaccine|vaccines|vaccination|vaccinations|booster|boosters|immunization|immunisation)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="dog_grooming_frequency",
        answer=(
            "Most dogs do well with regular brushing and scheduled grooming that matches coat type. "
            "Short coats usually need less frequent full grooms; long, curly, or double coats typically need more frequent maintenance."
        ),
        badges=["FAQ QA", "Care Basics"],
        citations=[
            ChatCitation(
                title="Grooming and Coat Care",
                source="AVMA",
                url="https://www.avma.org/resources-tools/pet-owners",
            ),
        ],
        patterns=[
            _pattern(r"\b(groom|grooming|brush|brushing|coat care)\b"),
            _pattern(r"\b(how often|frequency|schedule)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="dog_walk_duration",
        answer=(
            "A practical baseline is consistent daily walks adjusted for age, breed energy, weather, and fitness. "
            "Start with shorter sessions and increase gradually while monitoring recovery, appetite, and behavior."
        ),
        badges=["FAQ QA", "Exercise"],
        citations=[
            ChatCitation(
                title="Dog Exercise and Wellness Basics",
                source="AVMA",
                url="https://www.avma.org/resources-tools/pet-owners",
            ),
        ],
        patterns=[
            _pattern(r"\b(walk|walking|exercise)\b"),
            _pattern(r"\b(how long|how much|minutes|duration)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="dog_toxic_foods",
        answer=(
            "Common emergency toxins for dogs include chocolate, grapes or raisins, xylitol, onions, and garlic. "
            "If ingestion is possible, contact your vet or poison hotline immediately and do not wait for symptoms."
        ),
        badges=["FAQ QA", "Safety"],
        citations=[
            ChatCitation(
                title="Animal Poison Control Guidance",
                source="ASPCA Animal Poison Control",
                url="https://www.aspca.org/pet-care/animal-poison-control",
            ),
            ChatCitation(
                title="Pet Poisoning Basics",
                source="Merck Veterinary Manual",
                url="https://www.merckvetmanual.com/",
            ),
        ],
        patterns=[
            _pattern(r"\b(chocolate|grape|grapes|raisin|raisins|xylitol|onion|garlic|toxic|poison)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="parvo_suspected",
        answer=(
            "Possible parvovirus signs in an unwell puppy should be treated urgently. "
            "Keep your puppy isolated from other dogs, focus on hydration support while arranging immediate veterinary assessment, "
            "and avoid delays because parvo can deteriorate quickly."
        ),
        badges=["FAQ QA", "AU Trusted Source", "High Risk Safe Mode"],
        citations=[
            ChatCitation(
                title="What is canine parvovirus?",
                source="RSPCA Australia",
                url="https://kb.rspca.org.au/categories/companion-animals/dogs/health-issues/what-is-canine-parvovirus",
            ),
            ChatCitation(
                title="Canine Vaccination Guidelines",
                source="AAHA",
                url="https://www.aaha.org/resources/2022-aaha-canine-vaccination-guidelines/",
            ),
        ],
        patterns=[
            _pattern(r"\b(parvo|parvovirus)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="dog_mild_gi",
        answer=(
            "For mild vomiting or diarrhea, pause rich foods, keep water available, and monitor closely. "
            "Urgent care is needed for repeated vomiting, blood, weakness, dehydration signs, or if symptoms persist beyond 24 hours."
        ),
        badges=["FAQ QA", "Triage"],
        citations=[
            ChatCitation(
                title="Acute GI Signs in Dogs",
                source="Merck Veterinary Manual",
                url="https://www.merckvetmanual.com/",
            ),
        ],
        patterns=[
            _pattern(r"\b(vomit(?:ed|ing|s)?|diarrhea|diarrhoea)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="puppy_biting_mouthing",
        answer=(
            "Puppy mouthing is common and is best handled with reward-based training. "
            "End play briefly when teeth touch skin, redirect to an appropriate chew, and reward calm behavior. "
            "Keep sessions short and consistent, and avoid punishment-based methods that can increase fear or stress."
        ),
        badges=["FAQ QA", "Behavior Basics"],
        citations=[
            ChatCitation(
                title="Humane Dog Training Position Statement",
                source="AVSAB",
                url="https://avsab.org/wp-content/uploads/2021/08/AVSAB-Humane-Dog-Training-Position-Statement-2021.pdf",
            ),
            ChatCitation(
                title="Puppy Socialization Position Statements",
                source="AVSAB",
                url="https://avsab.org/resources/position-statements/",
            ),
        ],
        patterns=[
            _pattern(r"\b(puppy|young dog)\b"),
            _pattern(r"\b(bite|biting|mouthing|nipping)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="crate_training_distress",
        answer=(
            "Crate training should be gradual and positive. Build short successful crate sessions during the day, "
            "pair the crate with food or calm rewards, and increase duration slowly. "
            "If your dog shows panic-level distress rather than mild protest, reduce difficulty and consult your veterinarian "
            "or a qualified behavior professional."
        ),
        badges=["FAQ QA", "Behavior Basics"],
        citations=[
            ChatCitation(
                title="Humane Dog Training Position Statement",
                source="AVSAB",
                url="https://avsab.org/wp-content/uploads/2021/08/AVSAB-Humane-Dog-Training-Position-Statement-2021.pdf",
            ),
            ChatCitation(
                title="Canine Life Stage Care",
                source="AAHA",
                url="https://www.aaha.org/resources/life-stage-canine/",
            ),
        ],
        patterns=[
            _pattern(r"\b(crate|kennel)\b"),
            _pattern(r"\b(cry|crying|whine|whining|anxious|anxiety|panic)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="leash_reactivity_basics",
        answer=(
            "Leash reactivity is often managed best with distance, predictable routines, and reward-based counterconditioning. "
            "Create space before your dog escalates, reward calm check-ins, and avoid punitive corrections that can worsen fear responses."
        ),
        badges=["FAQ QA", "Behavior Safety"],
        citations=[
            ChatCitation(
                title="Humane Dog Training Position Statement",
                source="AVSAB",
                url="https://avsab.org/wp-content/uploads/2021/08/AVSAB-Humane-Dog-Training-Position-Statement-2021.pdf",
            ),
            ChatCitation(
                title="Dog Safety and Handling Basics",
                source="CDC",
                url="https://www.cdc.gov/healthy-pets/about/dogs.html",
            ),
        ],
        patterns=[
            _pattern(r"\b(leash|lead)\b"),
            _pattern(r"\b(reactive|reactivity|lunge|lunges|lunging|bark|barks|barking|growl|growls|aggressive)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="new_rescue_adjustment",
        answer=(
            "Many newly adopted dogs need decompression time before appetite, sleep, and behavior stabilize. "
            "Use a quiet routine, predictable feeding schedule, and low-pressure introductions in the first days. "
            "Contact your vet promptly for persistent vomiting, diarrhea, lethargy, or continued refusal to eat."
        ),
        badges=["FAQ QA", "Adoption Support"],
        citations=[
            ChatCitation(
                title="Canine Life Stage Care",
                source="AAHA",
                url="https://www.aaha.org/resources/life-stage-canine/",
            ),
            ChatCitation(
                title="Routine Veterinary Preventive Care",
                source="AVMA",
                url="https://www.avma.org/resources-tools/pet-owners",
            ),
        ],
        patterns=[
            _pattern(r"\b(adopt|adopted|rescue|shelter|foster)\b"),
            _pattern(r"\b(dog|puppy)\b"),
            _pattern(r"\b(not eating|won't eat|wont eat|anxious|scared|stress)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="heartworm_prevention",
        answer=(
            "Heartworm prevention is safer and easier than treatment. "
            "Use veterinarian-guided year-round prevention and routine testing, since mosquitoes and exposure risk can persist outside peak seasons."
        ),
        badges=["FAQ QA", "Preventive Care"],
        citations=[
            ChatCitation(
                title="Heartworm Disease Facts",
                source="FDA CVM",
                url="https://www.fda.gov/animal-veterinary/animal-health-literacy/keep-worms-out-your-pets-heart-facts-about-heartworm-disease",
            ),
            ChatCitation(
                title="Heartworm Guidelines",
                source="American Heartworm Society",
                url="https://www.heartwormsociety.org/guidelines",
            ),
        ],
        patterns=[
            _pattern(r"\b(heartworm|mosquito)\b"),
            _pattern(r"\b(prevent|prevention|preventive|test|testing)\b"),
            _pattern(r"\b(dog|puppy)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="dog_heat_stress_prevention",
        answer=(
            "Heat stress in dogs can become life-threatening quickly. "
            "Use shade, cool water, airflow, and avoid exercise during hot parts of the day. "
            "If your dog is panting heavily, weak, vomiting, or collapsing, treat this as urgent and contact a vet immediately."
        ),
        badges=["FAQ QA", "AU Trusted Source"],
        citations=[
            ChatCitation(
                title="Heat and Pets",
                source="Agriculture Victoria",
                url="https://agriculture.vic.gov.au/livestock-and-animals/animal-welfare-victoria/dogs/health/heat-and-pets",
            ),
            ChatCitation(
                title="Prepare Pets and Livestock for Hot Weather",
                source="NSW Government",
                url="https://www.nsw.gov.au/emergency/prepare/pets-and-livestock",
            ),
        ],
        patterns=[
            _pattern(r"\b(heat|hot weather|hot day|summer|heatwave|heat wave)\b"),
            _pattern(r"\b(dog|puppy)\b"),
            _pattern(r"\b(pant|panting|overheat|heatstroke|heat stress|collapse|vomit(?:ed|ing|s)?|exercise|walk)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="dog_separation_anxiety",
        answer=(
            "Separation-related anxiety is best managed with gradual training rather than punishment. "
            "Use short departures, predictable routines, enrichment before alone time, and calm returns. "
            "If distress is intense or persistent, ask your vet and a qualified behavior professional for a structured plan."
        ),
        badges=["FAQ QA", "AU Trusted Source"],
        citations=[
            ChatCitation(
                title="What can I do if my dog is anxious when I'm not at home?",
                source="RSPCA Australia",
                url="https://kb.rspca.org.au/knowledge-base/what-can-i-do-if-my-dog-is-anxious-when-im-not-at-home/",
            ),
            ChatCitation(
                title="Humane Dog Training Position Statement",
                source="AVSAB",
                url="https://avsab.org/wp-content/uploads/2021/08/AVSAB-Humane-Dog-Training-Position-Statement-2021.pdf",
            ),
        ],
        patterns=[
            _pattern(r"\b(separation anxiety|separation|anxious when|alone|home alone|left alone)\b"),
            _pattern(r"\b(dog|puppy)\b"),
            _pattern(r"\b(whine|bark|destroy|panic|stress|anxious)\b"),
        ],
    ),
    _FaqEntry(
        faq_id="puppy_feeding_basics_au",
        answer=(
            "For puppies, use a complete growth diet and feed measured portions based on age, size, and body condition. "
            "Transition foods gradually over several days, avoid overfeeding treats, and review the plan with your vet as your puppy grows."
        ),
        badges=["FAQ QA", "AU Trusted Source"],
        citations=[
            ChatCitation(
                title="What should I feed my puppy?",
                source="RSPCA Australia",
                url="https://kb.rspca.org.au/categories/companion-animals/dogs/puppies/what-should-i-feed-my-puppy",
            ),
            ChatCitation(
                title="Global Nutrition Guidelines",
                source="WSAVA",
                url="https://wsava.org/global-guidelines/global-nutrition-guidelines/",
            ),
        ],
        patterns=[
            _pattern(r"\b(puppy)\b"),
            _pattern(r"\b(feed|feeding|food|diet|kibble|how much)\b"),
        ],
    ),
]


def match_faq_answer(message: str) -> Optional[FaqMatchResult]:
    normalized = re.sub(r"\s+", " ", message.strip().lower())
    if not normalized:
        return None
    for entry in FAQ_ENTRIES:
        if entry.matches(normalized):
            return FaqMatchResult(
                faq_id=entry.faq_id,
                answer=entry.answer,
                badges=list(entry.badges),
                citations=list(entry.citations),
            )
    return None
