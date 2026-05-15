from __future__ import annotations

import argparse
from pathlib import Path
import sys

from heap_mapper.codegen import CCodeGenerator
from heap_mapper.mapper import AddressMapper
from heap_mapper.model import InfeasiblePathError
from heap_mapper.parser import load_path


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="heap-mapper",
        description="Generate a C test input structure from a JSON execution path.",
    )
    parser.add_argument("path", help="JSON file with selected execution path")
    parser.add_argument("--out", default="generated/test.c", help="output C file")
    parser.add_argument("--root", default="p", help="input root pointer variable")
    parser.add_argument("--target", default="target_function", help="target C function name")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    try:
        operations = load_path(args.path)
        result = AddressMapper(
            root_variable=args.root,
            target_function=args.target,
        ).process_path(operations)
        code = CCodeGenerator().generate(result)
    except (InfeasiblePathError, ValueError) as error:
        print(f"heap-mapper: {error}", file=sys.stderr)
        return 2

    output_path = Path(args.out)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(code, encoding="utf-8")
    print(f"generated {output_path}")
    return 0
