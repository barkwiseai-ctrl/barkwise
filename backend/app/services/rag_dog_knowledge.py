from typing import Any, Dict, List


TRUSTED_DOG_KNOWLEDGE: List[Dict[str, Any]] = [
    {
        "id": "kb_aaha_vax_2022",
        "title": "Canine Vaccination Principles",
        "source": "AAHA",
        "url": "https://www.aaha.org/resources/2022-aaha-canine-vaccination-guidelines/",
        "topics": ["vaccination", "preventive care", "puppy", "adult dog", "risk-based care"],
        "content": (
            "Core vaccines are recommended broadly for dogs, while non-core vaccines should be chosen based on "
            "lifestyle and exposure risk. Vaccination planning should be individualized, documented, and reviewed "
            "during routine preventive care visits."
        ),
    },
    {
        "id": "kb_wsava_vax",
        "title": "Global Vaccination Framework",
        "source": "WSAVA",
        "url": "https://wsava.org/global-guidelines/vaccination-guidelines/",
        "topics": ["vaccination", "global guidelines", "core vaccines", "booster timing"],
        "content": (
            "Core vaccine use and booster intervals should balance protection and medical need. Protocols should "
            "reflect local disease pressure, age, and individual risk factors rather than one-size-fits-all schedules."
        ),
    },
    {
        "id": "kb_wsava_nutrition",
        "title": "Nutrition Assessment in Clinical Care",
        "source": "WSAVA",
        "url": "https://wsava.org/global-guidelines/global-nutrition-guidelines/",
        "topics": ["nutrition", "body condition", "weight", "feeding plan", "diet"],
        "content": (
            "Nutrition should be assessed routinely using body condition, muscle condition, life stage, and health "
            "status. Feeding plans should be adjusted over time to avoid obesity and nutrient imbalance."
        ),
    },
    {
        "id": "kb_merck_dermatology",
        "title": "Canine Skin and Coat Basics",
        "source": "Merck Veterinary Manual",
        "url": "https://www.merckvetmanual.com/",
        "topics": ["skin", "coat", "matting", "itch", "grooming", "dermatology"],
        "content": (
            "Chronic coat and skin issues should be assessed for underlying causes such as allergy, parasites, "
            "infection, or grooming barriers. Early intervention and routine coat care reduce progression and discomfort."
        ),
    },
    {
        "id": "kb_avma_preventive",
        "title": "Routine Veterinary Preventive Care",
        "source": "AVMA",
        "url": "https://www.avma.org/resources-tools/pet-owners",
        "topics": ["preventive care", "wellness exam", "screening", "owner communication"],
        "content": (
            "Regular veterinary exams support early detection, preventive planning, and shared decision-making. "
            "Changes in appetite, behavior, mobility, and elimination are important to report early."
        ),
    },
    {
        "id": "kb_avma_behavior_visit_prep",
        "title": "Preparing for Vet Visits and Behavior Discussions",
        "source": "AVMA",
        "url": "https://www.avma.org/resources-tools/pet-owners",
        "topics": ["behavior", "anxiety", "vet visit", "history taking", "owner notes"],
        "content": (
            "Clear behavior history helps clinicians identify triggers and safer handling plans. "
            "Owners should share timing, environment, and progression of fear or stress behaviors before appointments."
        ),
    },
    {
        "id": "kb_aspca_toxicants",
        "title": "Common Household Toxicants for Dogs",
        "source": "ASPCA Animal Poison Control",
        "url": "https://www.aspca.org/pet-care/animal-poison-control",
        "topics": ["toxin", "poison", "emergency", "home safety"],
        "content": (
            "Many human foods, medications, and household products are toxic to dogs. If toxin exposure is suspected, "
            "owners should seek immediate veterinary or poison-control advice and avoid wait-and-see delays."
        ),
    },
    {
        "id": "kb_aspca_poison_response",
        "title": "Immediate Response to Suspected Poisoning",
        "source": "ASPCA Animal Poison Control",
        "url": "https://www.aspca.org/pet-care/animal-poison-control",
        "topics": ["poison", "emergency response", "triage", "toxin evidence"],
        "content": (
            "Rapid triage improves outcomes after toxin exposure. Gather product details, amount, and time of exposure "
            "and contact a veterinarian or poison hotline promptly rather than inducing home treatment without guidance."
        ),
    },
    {
        "id": "kb_merck_weight",
        "title": "Canine Obesity and Weight Risk",
        "source": "Merck Veterinary Manual",
        "url": "https://www.merckvetmanual.com/",
        "topics": ["obesity", "weight", "body condition", "exercise", "calories"],
        "content": (
            "Excess body weight increases orthopedic, metabolic, and quality-of-life risks. "
            "Weight plans should combine measured food portions, reassessment of treats, and gradual activity changes."
        ),
    },
    {
        "id": "kb_wsava_body_condition",
        "title": "Body and Muscle Condition Monitoring",
        "source": "WSAVA",
        "url": "https://wsava.org/global-guidelines/global-nutrition-guidelines/",
        "topics": ["body condition score", "muscle condition", "follow-up", "monitoring"],
        "content": (
            "Regular body and muscle condition tracking is essential during growth, adulthood, senior care, and illness. "
            "Small adjustments over time are usually safer and more sustainable than abrupt feeding changes."
        ),
    },
]
