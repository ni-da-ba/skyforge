#!/usr/bin/env python3
from __future__ import annotations
import argparse
import json
from pathlib import Path
from v2.objective_promotion_effect import (
    ObjectivePromotionPostDisposition,
    execute_frozen_promotion_post,
)

def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path.cwd())
    parser.add_argument('--repo', default='ni-da-ba/skyforge')
    args = parser.parse_args(argv)
    result = execute_frozen_promotion_post(root=args.root, repo=args.repo)
    print(json.dumps(result.as_dict(), sort_keys=True, indent=2))
    return 0 if result.disposition in {
        ObjectivePromotionPostDisposition.EXECUTED,
        ObjectivePromotionPostDisposition.RECONCILED,
        ObjectivePromotionPostDisposition.ALREADY_COMPLETE,
    } else 2

if __name__ == '__main__':
    raise SystemExit(main())
