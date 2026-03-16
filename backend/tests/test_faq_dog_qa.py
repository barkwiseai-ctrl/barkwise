import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.services.faq_dog_qa import match_faq_answer


def test_matches_puppy_biting_faq():
    result = match_faq_answer("My puppy keeps biting our hands. How do I stop this nipping?")
    assert result is not None
    assert result.faq_id == "puppy_biting_mouthing"
    assert result.citations


def test_matches_heartworm_prevention_faq():
    result = match_faq_answer("Should my dog get heartworm prevention all year?")
    assert result is not None
    assert result.faq_id == "heartworm_prevention"
    sources = {citation.source for citation in result.citations}
    assert "FDA CVM" in sources


def test_matches_new_rescue_adjustment_faq():
    result = match_faq_answer("We just adopted a rescue dog and he won't eat. Is this normal?")
    assert result is not None
    assert result.faq_id == "new_rescue_adjustment"


def test_matches_heat_stress_faq_with_australian_sources():
    result = match_faq_answer("It is a very hot day. My dog is panting hard after a walk, what should I do?")
    assert result is not None
    assert result.faq_id == "dog_heat_stress_prevention"
    sources = {citation.source for citation in result.citations}
    assert "Agriculture Victoria" in sources
    assert "NSW Government" in sources


def test_matches_puppy_feeding_australian_faq():
    result = match_faq_answer("How much should I feed my puppy each day?")
    assert result is not None
    assert result.faq_id == "puppy_feeding_basics_au"


def test_matches_parvo_high_risk_faq():
    result = match_faq_answer("My puppy has parvovirus symptoms. What should I do right now?")
    assert result is not None
    assert result.faq_id == "parvo_suspected"
    sources = {citation.source for citation in result.citations}
    assert "RSPCA Australia" in sources
