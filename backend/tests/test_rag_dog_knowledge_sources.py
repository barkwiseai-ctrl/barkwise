import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.services.rag_dog_knowledge import TRUSTED_DOG_KNOWLEDGE


def test_trusted_knowledge_includes_external_authority_entries():
    ids = {str(item.get("id", "")) for item in TRUSTED_DOG_KNOWLEDGE if isinstance(item, dict)}
    assert "kb_avsab_humane_training_2021" in ids
    assert "kb_fda_heartworm_facts" in ids
    assert "kb_cdc_pet_food_safety" in ids
    assert "kb_rspca_au_parvovirus" in ids
    assert "kb_agvic_heat_and_pets" in ids
    assert "kb_nsw_pets_hot_weather" in ids
