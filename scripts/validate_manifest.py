#!/usr/bin/env python3
"""Read-only CSV/media preflight; no database connection or third-party dependencies."""
import argparse
import csv
from pathlib import Path
import re
import sys

HEADER = ['term', 'normalized_term', 'category', 'media_path', 'media_type', 'source',
          'license', 'attribution', 'verification_status', 'notes']
EXTENSIONS = {'IMAGE': {'.png', '.jpg', '.jpeg', '.webp'}, 'VIDEO': {'.mp4', '.webm'}, 'GIF': {'.gif'}}
LIMITS = {'term': 120, 'normalized_term': 120, 'media_path': 255, 'source': 255,
          'license': 255, 'attribution': 500, 'notes': 500}


def normalize(value):
    value = value.lower().replace('’', "'")
    return re.sub(r'\s+', ' ', ''.join(c if c.isalnum() or c in "' " else ' ' for c in value)).strip()


def validate(manifest, media_root, public=False):
    errors, rights = [], []
    seen, registered = set(), set()
    root = Path(media_root).resolve()
    count = 0
    with Path(manifest).open(encoding='utf-8-sig', newline='') as file:
        reader = csv.DictReader(file)
        if reader.fieldnames != HEADER:
            return ['Manifest header must match data/vocabulary-template.csv'], [], 0, 0
        for line, row in enumerate(reader, 2):
            count += 1
            if None in row or any(v is None for v in row.values()):
                errors.append(f'line {line}: incorrect column count')
                continue
            row = {k: v.strip() for k, v in row.items()}
            key = normalize(row['normalized_term'] or row['term'])
            category = row['category'].upper()
            identity = (category, key)
            if not row['term'] or not key:
                errors.append(f'line {line}: term is required')
            if identity in seen:
                errors.append(f'line {line}: duplicate category/normalized term {identity}')
            seen.add(identity)
            if category not in {'WORD', 'PHRASE', 'LETTER', 'DIGIT'}:
                errors.append(f'line {line}: invalid category')
            words = len(key.split())
            if (category == 'LETTER' and not re.fullmatch('[a-z]', key)
                    or category == 'DIGIT' and not re.fullmatch('[0-9]', key)
                    or category == 'WORD' and words != 1
                    or category == 'PHRASE' and not 2 <= words <= 4):
                errors.append(f'line {line}: invalid term for {category}')
            for field, limit in LIMITS.items():
                if len(row[field]) > limit or field == 'normalized_term' and len(key) > limit:
                    errors.append(f'line {line}: {field} exceeds {limit} characters')
            status = row['verification_status'].upper() or 'UNVERIFIED'
            if status not in {'VERIFIED', 'UNVERIFIED'}:
                errors.append(f'line {line}: invalid verification status')
            relative, kind = row['media_path'], row['media_type'].upper()
            if bool(relative) != bool(kind):
                errors.append(f'line {line}: media_path and media_type must be provided together')
            if status == 'VERIFIED' and not relative:
                errors.append(f'line {line}: VERIFIED requires media')
            if not relative:
                continue
            if '\\' in relative or '\0' in relative or Path(relative).is_absolute():
                errors.append(f'line {line}: media path must be relative with forward slashes')
                continue
            path = root / relative
            if not path.resolve().is_relative_to(root) or path.is_symlink():
                errors.append(f'line {line}: media path escapes root or is a symlink')
                continue
            if kind not in EXTENSIONS or path.suffix.lower() not in EXTENSIONS.get(kind, set()):
                errors.append(f'line {line}: extension does not match media type')
            if not path.is_file() or not path.stat().st_size:
                errors.append(f'line {line}: registered media is missing or empty: {relative}')
            else:
                try:
                    with path.open('rb') as asset:
                        asset.read(1)
                except OSError:
                    errors.append(f'line {line}: media is unreadable: {relative}')
                registered.add(path.resolve())
            incomplete = [k for k in ('source', 'license', 'attribution') if not row[k]]
            unresolved = any(s in row['license'].lower() for s in ('see hf card', 'unknown', 'unconfirmed', 'check data.gov', 'other'))
            if incomplete or unresolved:
                rights.append(f'line {line} ({row["term"]}): rights need review; missing {incomplete}, unresolved licence={unresolved}')
                if public or status == 'VERIFIED':
                    errors.append(f'line {line}: documented source, resolved licence and attribution required')
    if not count:
        errors.append('Manifest contains no vocabulary entries')
    return errors, rights, count, sum(p.stat().st_size for p in registered)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', default='data/vocabulary.csv')
    parser.add_argument('--media-root', default='media')
    parser.add_argument('--public', action='store_true', help='Fail on incomplete/unresolved redistribution metadata')
    args = parser.parse_args()
    try:
        errors, rights, count, size = validate(args.manifest, args.media_root, args.public)
    except (OSError, UnicodeError, csv.Error) as error:
        print(f'Manifest preflight failed: {error}', file=sys.stderr)
        return 1
    for issue in errors:
        print('ERROR:', issue)
    for issue in rights:
        print('REVIEW:', issue)
    print(f'{count} vocabulary rows; {size / 1024 / 1024:.2f} MiB registered media; {len(errors)} errors; {len(rights)} rights reviews.')
    print('Licence text is not proof of permission or authentic ISL verification; review the original rights evidence.')
    return int(bool(errors))


if __name__ == '__main__':
    sys.exit(main())
