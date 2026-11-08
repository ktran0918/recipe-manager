import inflect

_engine = inflect.engine()


def normalize_ingredient_name(name: str | None) -> str | None:
    if not name or not name.strip():
        return None
    lowered = name.strip().lower()
    # singular_noun() returns the singular form when it recognizes a plural,
    # or False when the word is already singular (or not a noun it recognizes).
    singular = _engine.singular_noun(lowered)
    return singular if singular else lowered
