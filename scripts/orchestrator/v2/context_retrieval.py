from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import json
from pathlib import Path
import re
import subprocess
from typing import Any, Callable, Mapping, Sequence

from .context_package import (
    ContextPackage,
    ContextPackageDisposition,
    ContextPackageStore,
    repository_identity,
)
from .identity import canonical_digest
from .objective_ingress import ObjectiveProposalStore
from .state_store import JsonStateStoreAdapter


CONTEXT_RETRIEVAL_RELATIVE_PATH = Path('.skyforge-platform-v2/context-retrieval.json')
CONTEXT_RETRIEVAL_BACKUP_RELATIVE_PATH = Path('.skyforge-platform-v2/context-retrieval.json.bak')
ISSUE_FIELDS = 'number,title,body,comments,updatedAt,state'
MAX_TRACKED_TEXT_BYTES = 1_000_000
MAX_SLICES = 24
MAX_SCOPE_PATHS = 12
MAX_TERMS = 24
_TEXT_SUFFIXES = {
    '.java', '.kt', '.kts', '.py', '.json', '.json5', '.md', '.txt', '.yml', '.yaml',
    '.toml', '.gradle', '.properties', '.xml', '.sh', '.ps1', '.cfg', '.ini', '.csv',
}
_STOPWORDS = {
    'about','accepted','after','against','already','also','and','are','because','before','being',
    'bounded','build','cannot','change','current','deterministic','does','existing','from','have',
    'into','issue','must','only','project','required','requires','same','should','skyforge','task',
    'that','their','then','this','through','under','using','when','where','while','with','without',
    'work','worker','would','exact','main','repository','automated','validation','authority',
}
_EXCLUDED_SCOPE_PREFIXES = (
    '.github/', '.skyforge-', 'deploy/orchestrator/', 'scripts/orchestrator/',
)
_EXCLUDED_SCOPE_EXACT = {
    'docs/agent-state/ORCHESTRATOR_ROADMAP.json',
    'docs/agent-state/PROGRAM_PROGRESSION.json',
    'docs/agent-state/PLATFORM_V2_RELEASE5_GATE.json',
    'docs/architecture/SKYFORGE_DEVELOPMENT_PLATFORM_OPTIMIZATION_ROADMAP.md',
}


def _required(value: Any, label: str) -> str:
    text = str(value or '').strip()
    if not text:
        raise ValueError(f'{label} is required')
    return text


def _positive(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f'{label} must be a positive integer')
    return value


def _repo(value: str) -> str:
    text = _required(value, 'repo')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', text):
        raise ValueError('repo must be owner/name')
    return text


@dataclass(frozen=True)
class IssueObservation:
    issue_number: int
    title: str
    body: str
    comments: tuple[str, ...]
    updated_at: str
    state: str

    def __post_init__(self) -> None:
        object.__setattr__(self, 'issue_number', _positive(self.issue_number, 'issue_number'))
        object.__setattr__(self, 'title', _required(self.title, 'issue title'))
        object.__setattr__(self, 'updated_at', _required(self.updated_at, 'issue updated_at'))
        state = str(self.state or '').upper()
        if state not in {'OPEN', 'CLOSED'}:
            raise ValueError('issue state must be OPEN or CLOSED')
        object.__setattr__(self, 'state', state)

    def as_dict(self) -> dict[str, object]:
        return {
            'issue_number': self.issue_number,
            'title': self.title,
            'body': self.body,
            'comments': list(self.comments),
            'updated_at': self.updated_at,
            'state': self.state,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class RetrievedContextSlice:
    path: str
    sha256: str
    byte_count: int
    kind: str
    score: int
    matched_terms: tuple[str, ...]
    line_spans: tuple[tuple[int, int], ...]
    explicit_issue_path: bool = False

    def as_dict(self) -> dict[str, object]:
        return {
            'path': self.path,
            'sha256': self.sha256,
            'byte_count': self.byte_count,
            'kind': self.kind,
            'score': self.score,
            'matched_terms': list(self.matched_terms),
            'line_spans': [list(span) for span in self.line_spans],
            'explicit_issue_path': self.explicit_issue_path,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> 'RetrievedContextSlice':
        if not isinstance(raw, Mapping):
            raise ValueError('retrieved slice must be an object')
        spans = raw.get('line_spans') or []
        if not isinstance(spans, list):
            raise ValueError('line_spans must be a list')
        parsed_spans: list[tuple[int,int]] = []
        for span in spans:
            if not isinstance(span, list) or len(span) != 2:
                raise ValueError('line span must be [start,end]')
            start, end = span
            if any(isinstance(v,bool) or not isinstance(v,int) or v <= 0 for v in (start,end)) or end < start:
                raise ValueError('invalid line span')
            parsed_spans.append((start,end))
        return cls(
            path=_required(raw.get('path'),'slice path'),
            sha256=_required(raw.get('sha256'),'slice sha256'),
            byte_count=int(raw.get('byte_count') or 0),
            kind=_required(raw.get('kind'),'slice kind'),
            score=int(raw.get('score') or 0),
            matched_terms=tuple(str(v) for v in (raw.get('matched_terms') or [])),
            line_spans=tuple(parsed_spans),
            explicit_issue_path=bool(raw.get('explicit_issue_path',False)),
        )


@dataclass(frozen=True)
class ExactPathScopeProposal:
    paths: tuple[str, ...]
    reason: str

    def __post_init__(self) -> None:
        normalized = tuple(sorted({str(path).strip() for path in self.paths if str(path).strip()}))
        object.__setattr__(self, 'paths', normalized)
        object.__setattr__(self, 'reason', _required(self.reason, 'scope proposal reason'))

    def as_dict(self) -> dict[str, object]:
        return {
            'paths': list(self.paths),
            'reason': self.reason,
            'authoritative': False,
            'executable_task_authority': False,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> 'ExactPathScopeProposal':
        if not isinstance(raw, Mapping):
            raise ValueError('scope proposal must be an object')
        if raw.get('authoritative') is not False or raw.get('executable_task_authority') is not False:
            raise ValueError('retrieval scope proposal must remain non-authoritative')
        paths = raw.get('paths')
        if not isinstance(paths,list):
            raise ValueError('scope proposal paths must be a list')
        return cls(tuple(str(v) for v in paths), str(raw.get('reason') or ''))


class ContextRetrievalDisposition(str, Enum):
    INSPECTION_ONLY = 'INSPECTION_ONLY'
    SCOPE_PROPOSED = 'SCOPE_PROPOSED'
    BLOCKED = 'BLOCKED'


@dataclass(frozen=True)
class ContextRetrievalRecord:
    package_id: str
    proposal_id: str
    accepted_main_sha: str
    issue_observation: IssueObservation | None
    query_terms: tuple[str, ...]
    slices: tuple[RetrievedContextSlice, ...]
    scope_proposal: ExactPathScopeProposal
    disposition: ContextRetrievalDisposition
    reason: str

    @property
    def retrieval_id(self) -> str:
        return canonical_digest(self.as_dict(include_id=False))

    def as_dict(self, *, include_id: bool = True) -> dict[str, object]:
        value = {
            'schema_version': 1,
            'package_id': self.package_id,
            'proposal_id': self.proposal_id,
            'accepted_main_sha': self.accepted_main_sha,
            'issue_observation': self.issue_observation.as_dict() if self.issue_observation else None,
            'issue_observation_digest': self.issue_observation.digest if self.issue_observation else '',
            'query_terms': list(self.query_terms),
            'slices': [item.as_dict() for item in self.slices],
            'scope_proposal': self.scope_proposal.as_dict(),
            'disposition': self.disposition.value,
            'reason': self.reason,
            'executable_task_authority': False,
        }
        if include_id:
            value['retrieval_id'] = self.retrieval_id
        return value

    @classmethod
    def from_mapping(cls, raw: Any) -> 'ContextRetrievalRecord':
        if not isinstance(raw, Mapping) or raw.get('schema_version') != 1:
            raise ValueError('invalid context retrieval record')
        if raw.get('executable_task_authority') is not False:
            raise ValueError('context retrieval must remain non-authoritative')
        issue_raw = raw.get('issue_observation')
        issue = None
        if issue_raw is not None:
            if not isinstance(issue_raw, Mapping):
                raise ValueError('issue observation must be object or null')
            issue = IssueObservation(
                issue_number=issue_raw.get('issue_number'), title=issue_raw.get('title'),
                body=str(issue_raw.get('body') or ''), comments=tuple(str(v) for v in (issue_raw.get('comments') or [])),
                updated_at=issue_raw.get('updated_at'), state=issue_raw.get('state'),
            )
        record = cls(
            package_id=_required(raw.get('package_id'),'package_id'),
            proposal_id=_required(raw.get('proposal_id'),'proposal_id'),
            accepted_main_sha=_required(raw.get('accepted_main_sha'),'accepted_main_sha'),
            issue_observation=issue,
            query_terms=tuple(str(v) for v in (raw.get('query_terms') or [])),
            slices=tuple(RetrievedContextSlice.from_mapping(v) for v in (raw.get('slices') or [])),
            scope_proposal=ExactPathScopeProposal.from_mapping(raw.get('scope_proposal')),
            disposition=ContextRetrievalDisposition(str(raw.get('disposition') or '')),
            reason=_required(raw.get('reason'),'retrieval reason'),
        )
        if issue is not None and str(raw.get('issue_observation_digest') or '') != issue.digest:
            raise ValueError('issue observation digest mismatch')
        if str(raw.get('retrieval_id') or '') != record.retrieval_id:
            raise ValueError('retrieval id mismatch')
        return record


@dataclass(frozen=True)
class ContextRetrievalLedger:
    records: tuple[ContextRetrievalRecord, ...] = ()

    def get(self, package_id: str) -> ContextRetrievalRecord | None:
        found = [record for record in self.records if record.package_id == package_id]
        if len(found) > 1:
            raise ValueError('duplicate retrieval package identity')
        return found[0] if found else None

    def put(self, record: ContextRetrievalRecord) -> 'ContextRetrievalLedger':
        existing = self.get(record.package_id)
        if existing is not None:
            if existing != record:
                raise ValueError('context retrieval changed for frozen package')
            return self
        return ContextRetrievalLedger(self.records + (record,))

    def as_dict(self) -> dict[str, object]:
        return {'schema_version':1,'records':[record.as_dict() for record in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> 'ContextRetrievalLedger':
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw,Mapping) or raw.get('schema_version') != 1:
            raise ValueError('invalid context retrieval ledger')
        values = raw.get('records')
        if not isinstance(values,list):
            raise ValueError('context retrieval records must be a list')
        ledger=cls()
        for value in values:
            ledger=ledger.put(ContextRetrievalRecord.from_mapping(value))
        return ledger


class ContextRetrievalStore:
    def __init__(self, adapter: JsonStateStoreAdapter) -> None:
        self.adapter=adapter

    @classmethod
    def for_root(cls, root: Path) -> 'ContextRetrievalStore':
        root=Path(root)
        return cls(JsonStateStoreAdapter(
            path=root/CONTEXT_RETRIEVAL_RELATIVE_PATH,
            backup_path=root/CONTEXT_RETRIEVAL_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> ContextRetrievalLedger:
        return ContextRetrievalLedger.from_mapping(self.adapter.load().as_dict())

    def capture(self, record: ContextRetrievalRecord) -> bool:
        ledger=self.load(); existing=ledger.get(record.package_id)
        if existing is not None:
            if existing != record:
                raise ValueError('context retrieval conflict for frozen package')
            return False
        self.adapter.save(ledger.put(record).as_dict())
        return True


def validate_issue_read_command(args: Sequence[str], *, repo: str, issue_number: int) -> tuple[str,...]:
    repo=_repo(repo); issue=_positive(issue_number,'issue_number')
    expected=('gh','issue','view',str(issue),'--repo',repo,'--json',ISSUE_FIELDS,'--jq=.')
    command=tuple(str(v) for v in args)
    if command != expected:
        raise ValueError('context retrieval permits only the exact candidate issue read command')
    return command


def observe_issue(*, root: Path, repo: str, issue_number: int,
                  runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run) -> IssueObservation:
    command=validate_issue_read_command(
        ['gh','issue','view',str(issue_number),'--repo',repo,'--json',ISSUE_FIELDS,'--jq=.'],
        repo=repo, issue_number=issue_number,
    )
    completed=runner(list(command),cwd=root,check=True,text=True,capture_output=True,timeout=60)
    try:
        raw=json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise ValueError('candidate issue returned malformed JSON') from exc
    if not isinstance(raw,Mapping):
        raise ValueError('candidate issue observation must be an object')
    comments_raw=raw.get('comments') or []
    if not isinstance(comments_raw,list):
        raise ValueError('candidate issue comments must be a list')
    comments: list[str]=[]
    for item in comments_raw:
        if isinstance(item,Mapping):
            body=str(item.get('body') or '').strip()
            if body:
                comments.append(body)
    return IssueObservation(
        issue_number=_positive(raw.get('number'),'observed issue number'),
        title=raw.get('title'), body=str(raw.get('body') or ''), comments=tuple(comments),
        updated_at=raw.get('updatedAt'), state=raw.get('state'),
    )


def _terms(text: str) -> dict[str,int]:
    words=re.findall(r'[A-Za-z][A-Za-z0-9_-]{2,}', str(text or '').lower())
    result: dict[str,int]={}
    for word in words:
        token=word.strip('_-')
        if len(token) < 4 or token in _STOPWORDS or token.isdigit():
            continue
        result[token]=result.get(token,0)+1
    return result


def _query_terms(package: ContextPackage, issue: IssueObservation | None) -> tuple[str,...]:
    weights: dict[str,int]={}
    def add(text: str|None, multiplier: int) -> None:
        for token,count in _terms(text or '').items():
            weights[token]=weights.get(token,0)+count*multiplier
    add(package.objective,5); add(package.stop_boundary,3); add(package.human_gate_message,4)
    if issue:
        add(issue.title,6); add(issue.body,2)
        for comment in issue.comments[-12:]:
            add(comment,1)
    ranked=sorted(weights, key=lambda token:(-weights[token],token))
    return tuple(ranked[:MAX_TERMS])


def _git_ls_files(root: Path) -> tuple[str,...]:
    completed=subprocess.run(
        ['git','-C',str(root),'-c',f'safe.directory={root}','ls-files','-z'],
        check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,
    )
    return tuple(part.decode('utf-8') for part in completed.stdout.split(b'\0') if part)


def _kind(path: str) -> str:
    lower=path.lower()
    name=Path(path).name.lower()
    if '/test/' in lower or '/tests/' in lower or name.startswith('test_') or 'test.' in name or name.endswith('tests.java'):
        return 'test'
    if path.startswith('docs/'):
        return 'doc'
    if '/src/' in path or path.startswith('scripts/') or Path(path).suffix in {'.py','.java','.kt','.kts'}:
        return 'source'
    return 'config'


def _explicit_paths(issue: IssueObservation | None, tracked: set[str]) -> set[str]:
    if issue is None:
        return set()
    text='\n'.join((issue.title,issue.body,*issue.comments))
    candidates=set(re.findall(r'`([^`\n]+)`',text))
    result=set()
    for raw in candidates:
        value=raw.strip().replace('\\','/')
        if value in tracked:
            result.add(value)
    return result


def _line_spans(text: str, terms: tuple[str,...], *, explicit: bool) -> tuple[tuple[int,int],...]:
    lines=text.splitlines()
    hits=[]
    for idx,line in enumerate(lines,1):
        low=line.lower()
        if any(term in low for term in terms):
            hits.append(idx)
            if len(hits)>=8:
                break
    if explicit and not hits and lines:
        hits=[1]
    spans=[]
    for line in hits:
        start=max(1,line-2); end=min(len(lines),line+2)
        if spans and start <= spans[-1][1]+1:
            spans[-1]=(spans[-1][0],max(spans[-1][1],end))
        else:
            spans.append((start,end))
    return tuple(spans[:8])


def retrieve_slices(*, root: Path, package: ContextPackage, issue: IssueObservation | None) -> tuple[tuple[str,...],tuple[RetrievedContextSlice,...]]:
    tracked=_git_ls_files(root); tracked_set=set(tracked)
    terms=_query_terms(package,issue); explicit=_explicit_paths(issue,tracked_set)
    scored=[]
    for path in tracked:
        suffix=Path(path).suffix.lower()
        if suffix not in _TEXT_SUFFIXES and Path(path).name not in {'gradlew','gradlew.bat'}:
            continue
        full=root/path
        try:
            data=full.read_bytes()
        except OSError:
            continue
        if len(data)>MAX_TRACKED_TEXT_BYTES:
            continue
        try:
            text=data.decode('utf-8')
        except UnicodeDecodeError:
            continue
        low_path=path.lower(); low_text=text.lower()
        matched=[]; score=0
        for term in terms:
            path_hits=low_path.count(term)
            content_hits=low_text.count(term)
            if path_hits or content_hits:
                matched.append(term)
                score += min(path_hits,3)*12 + min(content_hits,8)*2
                if term in Path(path).name.lower():
                    score += 10
        is_explicit=path in explicit
        if is_explicit:
            score += 1000
        kind=_kind(path)
        if kind in {'source','test'}:
            score += 3
        if score <= 0:
            continue
        scored.append(RetrievedContextSlice(
            path=path,sha256=hashlib.sha256(data).hexdigest(),byte_count=len(data),kind=kind,
            score=score,matched_terms=tuple(sorted(set(matched))),
            line_spans=_line_spans(text,tuple(matched),explicit=is_explicit),
            explicit_issue_path=is_explicit,
        ))
    scored.sort(key=lambda item:(-item.score,item.path))
    return terms,tuple(scored[:MAX_SLICES])


def _scope_safe(path: str) -> bool:
    if path in _EXCLUDED_SCOPE_EXACT:
        return False
    return not any(path.startswith(prefix) for prefix in _EXCLUDED_SCOPE_PREFIXES)


def build_retrieval_record(*, root: Path, repo: str, package: ContextPackage,
                           gh_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run) -> ContextRetrievalRecord:
    root=Path(root).resolve(); repo=_repo(repo)
    head,clean=repository_identity(root)
    if not clean or head != package.accepted_main_sha:
        return ContextRetrievalRecord(
            package_id=package.package_id,proposal_id=package.proposal_id,accepted_main_sha=package.accepted_main_sha,
            issue_observation=None,query_terms=(),slices=(),
            scope_proposal=ExactPathScopeProposal((), 'repository identity changed since package freeze'),
            disposition=ContextRetrievalDisposition.BLOCKED,
            reason='repository must remain clean and at the package accepted main SHA',
        )

    issue=None
    if package.disposition is ContextPackageDisposition.SCOPE_UNRESOLVED and package.issue_number:
        issue=observe_issue(root=root,repo=repo,issue_number=package.issue_number,runner=gh_runner)
        if issue.issue_number != package.issue_number:
            raise ValueError('candidate issue identity mismatch')

    terms,slices=retrieve_slices(root=root,package=package,issue=issue)

    if package.disposition in {ContextPackageDisposition.HUMAN_GATE,ContextPackageDisposition.READY_READ_ONLY}:
        return ContextRetrievalRecord(
            package_id=package.package_id,proposal_id=package.proposal_id,accepted_main_sha=package.accepted_main_sha,
            issue_observation=issue,query_terms=terms,slices=slices,
            scope_proposal=ExactPathScopeProposal((), 'inspection-only objective does not propose mutation paths'),
            disposition=ContextRetrievalDisposition.INSPECTION_ONLY,
            reason='bounded inspection context retrieved without mutation scope',
        )

    if package.disposition is not ContextPackageDisposition.SCOPE_UNRESOLVED:
        return ContextRetrievalRecord(
            package_id=package.package_id,proposal_id=package.proposal_id,accepted_main_sha=package.accepted_main_sha,
            issue_observation=issue,query_terms=terms,slices=slices,
            scope_proposal=ExactPathScopeProposal((), 'package is not eligible for scope proposal'),
            disposition=ContextRetrievalDisposition.BLOCKED,
            reason='context package disposition is not eligible for mutation scoping',
        )

    candidates=[]
    for item in slices:
        if not _scope_safe(item.path):
            continue
        if item.kind not in {'source','test','doc','config'}:
            continue
        candidates.append(item.path)
        if len(candidates)>=MAX_SCOPE_PATHS:
            break
    if not candidates:
        return ContextRetrievalRecord(
            package_id=package.package_id,proposal_id=package.proposal_id,accepted_main_sha=package.accepted_main_sha,
            issue_observation=issue,query_terms=terms,slices=slices,
            scope_proposal=ExactPathScopeProposal((), 'retrieval found no exact safe tracked paths'),
            disposition=ContextRetrievalDisposition.BLOCKED,
            reason='no bounded exact-path proposal could be derived from issue/repository evidence',
        )
    return ContextRetrievalRecord(
        package_id=package.package_id,proposal_id=package.proposal_id,accepted_main_sha=package.accepted_main_sha,
        issue_observation=issue,query_terms=terms,slices=slices,
        scope_proposal=ExactPathScopeProposal(tuple(candidates), 'exact tracked paths ranked from frozen objective plus exact candidate issue evidence; proposal is not authority'),
        disposition=ContextRetrievalDisposition.SCOPE_PROPOSED,
        reason='bounded exact-path scope proposal derived for later policy validation',
    )


def retrieve_context(*, root: Path, repo: str, package_id: str|None=None,
                     gh_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run) -> tuple[ContextRetrievalRecord,bool]:
    packages=ContextPackageStore.for_root(root).load()
    if not packages.records:
        raise ValueError('no context packages exist')
    if package_id:
        matches=[record for record in packages.records if record.package_id==package_id]
        if len(matches)!=1:
            raise ValueError('context package id not found exactly once')
        package=matches[0]
    else:
        package=packages.records[-1]
    record=build_retrieval_record(root=root,repo=repo,package=package,gh_runner=gh_runner)
    created=ContextRetrievalStore.for_root(root).capture(record)
    return record,created
