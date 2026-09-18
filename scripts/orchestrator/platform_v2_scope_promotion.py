#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path
from v2.scope_promotion import PromotionDisposition, validate_latest_promotion

def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path.cwd())
    parser.add_argument('--repo', default='ni-da-ba/skyforge')
    args = parser.parse_args(argv)
    result, created = validate_latest_promotion(root=args.root, repo=args.repo)
    output = result.as_dict(); output['created'] = created
    print(json.dumps(output, sort_keys=True, indent=2))
    return 0 if result.disposition is PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST else 2

if __name__ == '__main__':
    raise SystemExit(main())
