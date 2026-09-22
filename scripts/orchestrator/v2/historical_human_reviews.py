"""One-time migration of canonical pre-Platform-v2 human review history."""

from __future__ import annotations

from pathlib import Path

from .human_review import (
    HumanReviewSource,
    HumanReviewStore,
    HumanReviewSubmission,
    HumanReviewVerdict,
)
from .review_artifacts import ReviewArtifactCatalog


_CANONICAL_REPO = "ni-da-ba/skyforge"
_DR60_ARTIFACT_ID = "dr60:p2-dressed-region-a"
_DR60_SOURCE_SHA = "4f6526d26408974b3a33d1af62565f225516567d"
_DR70_ARTIFACT_ID = "dr70:key-2885"
_DR70_SOURCE_SHA = "a318f1b1ffb22805087eac52b67035d041a03fb6"


def _dr60_p2_review() -> HumanReviewSubmission:
    return HumanReviewSubmission(
        source=HumanReviewSource(
            repo=_CANONICAL_REPO,
            issue_number=535,
            comment_id=5707566397,
            actor="ni-da-ba",
            created_at="2026-09-17T02:32:15Z",
            updated_at="2026-09-17T02:32:15Z",
        ),
        gate_id="dr-human-exploration-review",
        artifact_id=_DR60_ARTIFACT_ID,
        source_sha=_DR60_SOURCE_SHA,
        verdict=HumanReviewVerdict.CHANGES_REQUIRED,
        findings=(
            "Tall jungle vegetation can clip against the upper production/world boundary; canopy placement must respect remaining vertical headroom.",
            "The canonical specimen's authored hydrology was effectively token water spilling over the island side rather than a coherent geographic feature.",
            "Incidental water and especially lava edge discharge is too common; arbitrary runoff should be strongly suppressed while deliberate justified waterfalls remain valid.",
        ),
        positive_findings=(
            "Core island surface composition was coherent and interesting.",
            "Cave/interior composition was coherent and interesting and should be preserved through the repair.",
        ),
        material_delta=(
            "Project-owner flight review of the canonical DR-60 P2_DRESSED_REGION_A "
            "specimen established the accepted island/cave foundation and the exact "
            "canopy/headroom and hydrology/fluid defects that require bounded repair."
        ),
        next_boundary=(
            "Resume autonomous work only against the explicit DR-60 findings: canopy "
            "headroom safety, physically legible hydrology, and suppression of "
            "unjustified incidental edge discharge. Do not broadly retune terrain or interiors."
        ),
        deferred_product_work=False,
    )


def _dr70_key_2885_review() -> HumanReviewSubmission:
    return HumanReviewSubmission(
        source=HumanReviewSource(
            repo=_CANONICAL_REPO,
            issue_number=754,
            comment_id=5752994015,
            actor="ni-da-ba",
            created_at="2026-09-20T22:02:53Z",
            updated_at="2026-09-20T22:02:53Z",
        ),
        gate_id="dr-human-exploration-rereview",
        artifact_id=_DR70_ARTIFACT_ID,
        source_sha=_DR70_SOURCE_SHA,
        verdict=HumanReviewVerdict.CHANGES_REQUIRED,
        findings=(
            "The review area is classified/presented as an ocean biome; that presentation is not acceptable for the intended island hydrology context.",
            "The water realization itself and surrounding block/material placements are visually poor and require another bounded hydrology/presentation pass.",
            "The selected review island is very small. The channel proves the system, but the specimen is not yet a convincing final integrated showcase.",
        ),
        positive_findings=(
            "The repaired world contains an actual, visibly legible water channel.",
            "The channel is a substantial improvement over the prior water-painted-on-a-hillside failure and confirms real channel geomorphology through the authored hydrology -> fluvial terrain -> Minecraft realization path.",
        ),
        material_delta=(
            "Dedicated key-2885 review after the hydrology-geomorphology repair: "
            "an actual visibly legible water channel is present, materially improving "
            "the prior token hillside-water result."
        ),
        next_boundary=(
            "After the development-platform optimization tranche, resume bounded DR-70 "
            "hydrology/presentation and specimen-quality repair from these persisted "
            "findings; return to human review only with a new qualified artifact and "
            "material delta."
        ),
        deferred_product_work=True,
    )


def migrate_canonical_historical_human_reviews(
    *,
    root: Path,
    repo: str,
    store: HumanReviewStore | None = None,
) -> int:
    """Backfill canonical historical reviews exactly once without inventing new judgment."""

    root = Path(root).resolve()
    if str(repo).strip() != _CANONICAL_REPO:
        return 0

    manifest = root / "docs/agent-state/REVIEW_ARTIFACTS.json"
    if not manifest.is_file():
        # Test/minimal roots and non-product checkouts legitimately omit the registry.
        return 0

    catalog = ReviewArtifactCatalog.for_root(root)
    expected = (
        (_DR60_ARTIFACT_ID, _DR60_SOURCE_SHA, "DR-60"),
        (_DR70_ARTIFACT_ID, _DR70_SOURCE_SHA, "DR-70"),
    )
    for artifact_id, source_sha, label in expected:
        artifact = catalog.get(artifact_id)
        if artifact is None:
            return 0
        if artifact.source_sha != source_sha:
            raise RuntimeError(
                f"canonical {label} historical review artifact source SHA drifted"
            )
        if "CHANGES_REQUIRED" not in artifact.description:
            raise RuntimeError(
                f"canonical {label} artifact no longer records the historical failed review"
            )

    review_store = store or HumanReviewStore.for_root(root)
    created = 0
    for review in (_dr60_p2_review(), _dr70_key_2885_review()):
        result = review_store.capture(review)
        created += 1 if result.created else 0
    return created
