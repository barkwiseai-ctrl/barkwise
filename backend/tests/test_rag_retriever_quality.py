import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.services.rag_retriever import RagRetriever


def _sources(docs):
    return [str(doc.get("source", "")) for doc in docs if isinstance(doc, dict)]


def test_health_query_prefers_knowledge_base_docs():
    retriever = RagRetriever()
    context = retriever.build_context(
        message="What vaccine schedule should I discuss for my puppy?",
        suburb="Surry Hills",
        profile_memory={},
        intent="general_pet_question",
        tool_results={},
    )
    docs = context["documents"]
    assert len(docs) > 0
    assert _sources(docs).count("knowledge_base") >= 1


def test_services_query_includes_provider_docs():
    retriever = RagRetriever()
    context = retriever.build_context(
        message="Need a dog walker near me",
        suburb="Surry Hills",
        profile_memory={},
        intent="find_dog_walker",
        tool_results={},
    )
    docs = context["documents"]
    assert len(docs) > 0
    assert "provider" in _sources(docs)


def test_community_query_includes_event_or_post_docs():
    retriever = RagRetriever()
    context = retriever.build_context(
        message="Any community event this week in Newtown?",
        suburb="Newtown",
        profile_memory={},
        intent="community_discovery",
        tool_results={},
    )
    docs = context["documents"]
    assert len(docs) > 0
    sources = _sources(docs)
    assert any(source in {"community_event", "community_post", "group"} for source in sources)


def test_top_docs_are_deduped_and_capped_to_six():
    retriever = RagRetriever()
    context = retriever.build_context(
        message="Help with local pet groups and events and posts",
        suburb="Surry Hills",
        profile_memory={},
        intent="community_discovery",
        tool_results={},
    )
    docs = [doc for doc in context["documents"] if isinstance(doc, dict)]
    dedupe_keys = {f"{doc.get('source','')}:{doc.get('id','')}" for doc in docs}
    assert len(docs) <= 6
    assert len(docs) == len(dedupe_keys)


def test_poison_query_prioritizes_knowledge_base():
    retriever = RagRetriever()
    context = retriever.build_context(
        message="Possible poison exposure. My dog may have eaten toxins.",
        suburb="Redfern",
        profile_memory={},
        intent="general_pet_question",
        tool_results={},
    )
    docs = context["documents"]
    assert len(docs) > 0
    assert str(docs[0].get("source", "")) == "knowledge_base"


def test_health_query_with_general_assistant_intent_still_uses_knowledge_base():
    retriever = RagRetriever()
    context = retriever.build_context(
        message="I think this might be poisoning symptoms.",
        suburb="Surry Hills",
        profile_memory={},
        intent="general_assistant_query",
        tool_results={},
    )
    docs = context["documents"]
    assert len(docs) > 0
    assert any(str(doc.get("source", "")) == "knowledge_base" for doc in docs)
