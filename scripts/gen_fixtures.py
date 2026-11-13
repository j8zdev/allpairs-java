"""Generates src/test/resources/parity_fixtures.txt from the reference Python implementation.

Run from the project root with the venv that has allpairspy installed:

    ../.venv/bin/python scripts/gen_fixtures.py
"""

import random
from pathlib import Path

from allpairspy import AllPairs

OUT = Path(__file__).resolve().parent.parent / "src/test/resources/parity_fixtures.txt"


def suffix(value):
    return value[value.index("v"):]


# Keep in sync with CONSTRAINTS in ParityTest.java
CONSTRAINTS = {
    "none": lambda row: True,
    "first_two": lambda row: not (len(row) > 1 and suffix(row[0]) == "v0" and suffix(row[1]) == "v1"),
    "first_third_differ": lambda row: not (len(row) > 2 and suffix(row[0]) == suffix(row[2])),
    "last_two": lambda row: not (len(row) > 3 and suffix(row[-1]) == suffix(row[-2])),
}


def make_params(sizes):
    return [[f"p{i}v{j}" for j in range(size)] for i, size in enumerate(sizes)]


def main():
    rng = random.Random(20261112)
    cases = []

    # the README example shape, plus a few fixed shapes
    for sizes, n in [
        ([2, 4, 2, 3, 6], 2),
        ([2, 4, 2, 3, 6], 3),
        ([3, 3, 3, 3], 2),
        ([1, 1], 2),
        ([2, 2], 3),
        ([10, 10, 10], 2),
        ([2] * 10, 2),
        ([4, 3, 2, 5], 1),
        ([3, 4, 2, 3], 4),
    ]:
        for name in CONSTRAINTS:
            cases.append((sizes, n, name, []))

    for _ in range(150):
        sizes = [rng.randint(1, 5) for _ in range(rng.randint(2, 7))]
        n = rng.choice([1, 2, 2, 2, 3, 3, 4])
        name = rng.choice(list(CONSTRAINTS))
        tested = []
        if rng.random() < 0.4:
            params = make_params(sizes)
            for _ in range(rng.randint(1, 4)):
                row = [rng.choice(values) for values in params]
                # the Java version drops a repeated row instead of counting it twice
                if CONSTRAINTS[name](row) and row not in tested:
                    tested.append(row)
        cases.append((sizes, n, name, tested))

    lines = []
    for idx, (sizes, n, name, tested) in enumerate(cases):
        params = make_params(sizes)
        rows = list(AllPairs(params, filter_func=CONSTRAINTS[name], previously_tested=tested, n=n))
        lines.append(f"case {idx}")
        lines.append(f"n {n}")
        lines.append(f"constraint {name}")
        lines.extend("param " + "|".join(values) for values in params)
        lines.extend("tested " + "|".join(row) for row in tested)
        lines.extend("row " + "|".join(row) for row in rows)
        lines.append("end")

    OUT.write_text("\n".join(lines) + "\n")
    print(f"wrote {len(cases)} cases to {OUT}")


if __name__ == "__main__":
    main()
