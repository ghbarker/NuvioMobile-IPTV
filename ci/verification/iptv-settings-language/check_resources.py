#!/usr/bin/env python3
"""Validate the mobile Live TV Spanish pack against Compose's base resources."""

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


RESOURCE_ROOT = Path(__file__).resolve().parents[3] / "composeApp/src/commonMain/composeResources"
FORMAT_ARGUMENT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])")
SETTINGS_ENTRY_KEYS = {
    "compose_settings_page_livetv",
    "compose_settings_root_livetv_description",
}


def resources(directory):
    result = {}
    for file in sorted(directory.glob("*.xml")):
        for element in ET.parse(file).getroot():
            name = element.get("name", "")
            if not name.startswith("livetv_") and name not in SETTINGS_ENTRY_KEYS:
                continue
            if name in result:
                raise AssertionError(f"Duplicate {name} in {file}")
            result[name] = element
    return result


def format_arguments(text):
    # %% is a literal percent sign, not a formatting argument.
    return Counter(FORMAT_ARGUMENT.findall(text.replace("%%", "")))


def check_text(name, source, translated):
    assert translated.strip(), f"Empty translation: {name}"
    assert "\ufffd" not in translated, f"Invalid Unicode replacement character: {name}"
    assert format_arguments(source) == format_arguments(translated), f"Format arguments differ: {name}"
    assert source.count(r"\n") == translated.count(r"\n"), f"Escaped newline count differs: {name}"


def main():
    base = resources(RESOURCE_ROOT / "values")
    spanish = resources(RESOURCE_ROOT / "values-es")
    assert base.keys() == spanish.keys(), (
        f"Missing Spanish keys: {sorted(base.keys() - spanish.keys())}; "
        f"Unknown Spanish keys: {sorted(spanish.keys() - base.keys())}"
    )
    for name, source in base.items():
        translated = spanish[name]
        assert source.tag == translated.tag, f"Resource type differs: {name}"
        if source.tag == "string":
            check_text(name, "".join(source.itertext()), "".join(translated.itertext()))
        elif source.tag == "plurals":
            source_items = {item.get("quantity"): item.text or "" for item in source}
            translated_items = {item.get("quantity"): item.text or "" for item in translated}
            assert "other" in translated_items, f"Missing other plural: {name}"
            assert len(translated_items) == len(translated), f"Duplicate plural quantity: {name}"
            assert source_items.keys() == translated_items.keys(), f"Plural quantities differ: {name}"
            for quantity, text in source_items.items():
                check_text(f"{name}/{quantity}", text, translated_items[quantity])
        else:
            raise AssertionError(f"Unhandled Live TV resource type: {source.tag} ({name})")
    counts = Counter(element.tag for element in base.values())
    print(
        f"PASS: {counts['string']} strings and {counts['plurals']} plurals have complete Spanish "
        "coverage, matching resource types, format arguments and escaped newlines."
    )


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, ET.ParseError) as error:
        sys.exit(f"FAIL: {error}")
