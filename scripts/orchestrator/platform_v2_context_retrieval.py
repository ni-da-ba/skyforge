#!/usr/bin/env python3
from __future__ import annotations
import argparse,json
from pathlib import Path
from v2.context_retrieval import retrieve_context

def main(argv=None)->int:
    p=argparse.ArgumentParser()
    p.add_argument('--root',type=Path,default=Path.cwd())
    p.add_argument('--repo',default='ni-da-ba/skyforge')
    p.add_argument('--package-id',default=None)
    a=p.parse_args(argv)
    record,created=retrieve_context(root=a.root,repo=a.repo,package_id=a.package_id)
    out=record.as_dict(); out['created']=created
    print(json.dumps(out,sort_keys=True,indent=2))
    return 0

if __name__=='__main__': raise SystemExit(main())
