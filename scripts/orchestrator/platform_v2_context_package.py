#!/usr/bin/env python3
"""Build one durable, non-authoritative OPT-2 context package."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from v2.context_package import package_proposal

def main(argv=None) -> int:
    parser=argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path.cwd())
    parser.add_argument('--proposal-id', default=None)
    args=parser.parse_args(argv)
    package, created = package_proposal(root=args.root, proposal_id=args.proposal_id)
    out=package.as_dict()
    out['created']=created
    print(json.dumps(out, sort_keys=True, indent=2))
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
