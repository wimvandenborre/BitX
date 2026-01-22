#!/usr/bin/env python3
import os
import re

ROOT = os.path.dirname(os.path.dirname(__file__))
SRC = os.path.join(ROOT, 'bitwigapi', 'BitwigAPI25.txt')
OUT = os.path.join(ROOT, 'src', 'generated-sources', 'bitwigapi')

os.makedirs(OUT, exist_ok=True)

with open(SRC, 'r', encoding='utf-8') as f:
    data = f.read()

# The file uses "-e " as separator between entries
entries = [e.strip() for e in data.split('\n-e ') if e.strip()]

pkg_re = re.compile(r'^package\s+([\w\.]+);', re.MULTILINE)
type_re = re.compile(r'public\s+(?:abstract\s+)?(class|interface|enum)\s+(\w+)', re.MULTILINE)

for e in entries:
    pkg_m = pkg_re.search(e)
    type_m = type_re.search(e)
    if not pkg_m or not type_m:
        continue
    pkg = pkg_m.group(1)
    kind = type_m.group(1)
    name = type_m.group(2)
    rel_dir = os.path.join(OUT, *pkg.split('.'))
    os.makedirs(rel_dir, exist_ok=True)
    path = os.path.join(rel_dir, f"{name}.java")
    if os.path.exists(path):
        continue
    with open(path, 'w', encoding='utf-8') as out:
        out.write(f"package {pkg};\n\n")
        if kind == 'class':
            out.write(f"public class {name} {{\n    // generated stub\n}}\n")
        elif kind == 'interface':
            out.write(f"public interface {name} {{\n    // generated stub\n}}\n")
        else:
            out.write(f"public enum {name} {{}}\n")

print('Generated stubs under', OUT)
