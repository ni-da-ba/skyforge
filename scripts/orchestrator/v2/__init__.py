"""Inert Platform v2 control-plane package.

The production Skyforge controller deliberately does not import this package yet.
Modules here may model state, replay decisions, and persistence contracts, but receive
no repository-mutating authority merely because they exist.
"""

from .core import (
    ControllerState,
    CoreDecision,
    HumanGateRecord,
    ManagedPRObservation,
    ManagedPRState,
    PendingWorkerState,
    reduce_managed_pr,
)
from .decision import (
    CachedDecisionDisposition,
    ClassifierDecision,
    DecisionFreshnessObservation,
    DecisionKind,
    PendingDecisionRecord,
    SourcePRState,
    WorkerTier,
    cached_decision_disposition,
    cached_decision_is_current,
)
from .domain import (
    CIState,
    MechanicalSnapshot,
    PRClass,
    TransitionKind,
    TransitionPlan,
    decide_mechanical,
)
from .effects import EffectKind, EffectStatus, RemoteEffectIdentity, RemoteEffectRecord
from .events import DurableEvent, normalize_legacy_event_key
from .external import (
    ClaimAdmissionDecision,
    ClaimAdmissionDisposition,
    ClaimReleaseDisposition,
    ClaimRetentionDecision,
    ClaimRetentionDisposition,
    ControllerIssueOwner,
    ExternalClaimRequest,
    ExternalDispatchDecision,
    ExternalPREventDecision,
    ExternalPREventDisposition,
    ExternalProducerClaim,
    classify_claim_admission,
    classify_claim_release,
    classify_claim_retention,
    classify_external_dispatch_hold,
    classify_external_pr_event,
)
from .fence import FenceBusyError, WriterFence
from .identity import AcceptanceIdentity, FrozenTaskSpec, TaskAttemptIdentity, canonical_digest
from .inbox import (
    InboxCompactionReport,
    InboxState,
    InboxTransition,
    compact_events,
    enqueue_events,
    select_dispatch_batch,
)
from .ownership import ControllerIdentity, OwnershipToken
from .replay import ReplayRecord
from .state_store import JsonStateSnapshot, JsonStateStoreAdapter, StateStoreError
from .worker import (
    WorkerLifecycleState,
    WorkerRecoveryDecision,
    WorkerRecoveryDisposition,
    WorkerRecoveryObservation,
    classify_worker_recovery,
)

__all__ = [
    "AcceptanceIdentity",
    "CIState",
    "CachedDecisionDisposition",
    "ClassifierDecision",
    "ControllerIdentity",
    "ControllerIssueOwner",
    "ControllerState",
    "DecisionFreshnessObservation",
    "DecisionKind",
    "ClaimAdmissionDecision",
    "ClaimAdmissionDisposition",
    "ClaimReleaseDisposition",
    "ClaimRetentionDecision",
    "ClaimRetentionDisposition",
    "CoreDecision",
    "EffectKind",
    "EffectStatus",
    "ExternalClaimRequest",
    "ExternalDispatchDecision",
    "ExternalPREventDecision",
    "ExternalPREventDisposition",
    "ExternalProducerClaim",
    "DurableEvent",
    "FenceBusyError",
    "FrozenTaskSpec",
    "HumanGateRecord",
    "InboxCompactionReport",
    "InboxState",
    "InboxTransition",
    "JsonStateSnapshot",
    "JsonStateStoreAdapter",
    "ManagedPRObservation",
    "ManagedPRState",
    "MechanicalSnapshot",
    "OwnershipToken",
    "PRClass",
    "PendingDecisionRecord",
    "PendingWorkerState",
    "RemoteEffectIdentity",
    "RemoteEffectRecord",
    "ReplayRecord",
    "SourcePRState",
    "StateStoreError",
    "TaskAttemptIdentity",
    "TransitionKind",
    "TransitionPlan",
    "WorkerLifecycleState",
    "WorkerRecoveryDecision",
    "WorkerRecoveryDisposition",
    "WorkerRecoveryObservation",
    "WorkerTier",
    "WriterFence",
    "cached_decision_disposition",
    "cached_decision_is_current",
    "canonical_digest",
    "classify_claim_admission",
    "classify_claim_release",
    "classify_claim_retention",
    "classify_external_dispatch_hold",
    "classify_external_pr_event",
    "classify_worker_recovery",
    "compact_events",
    "decide_mechanical",
    "enqueue_events",
    "normalize_legacy_event_key",
    "reduce_managed_pr",
    "select_dispatch_batch",
]
