import csv
from pathlib import Path
import tempfile
import unittest

from validate_manifest import HEADER, validate


class ManifestValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.media = self.root / 'media'
        self.media.mkdir()
        (self.media / 'A.jpg').write_bytes(b'path-validation fixture, not a sign image')
        self.csv = self.root / 'vocabulary.csv'

    def check(self, rows, public=False):
        with self.csv.open('w', newline='') as file:
            writer = csv.DictWriter(file, fieldnames=HEADER)
            writer.writeheader()
            writer.writerows(rows)
        return validate(self.csv, self.media, public)

    def row(self, **changes):
        return dict(term='A', category='LETTER', media_path='A.jpg', media_type='IMAGE',
                    verification_status='UNVERIFIED', **changes)

    def test_unverified_rights_are_reported_and_block_public_preflight(self):
        errors, rights, count, size = self.check([self.row()])
        self.assertFalse(errors)
        self.assertEqual(len(rights), 1)
        self.assertEqual(count, 1)
        self.assertGreater(size, 0)
        self.assertTrue(self.check([self.row()], public=True)[0])

    def test_complete_credit_does_not_implicitly_change_verification(self):
        row = self.row(source='Fixture source', license='CC-BY-4.0', attribution='Fixture credit')
        self.assertFalse(self.check([row], public=True)[0])
        self.assertEqual(row['verification_status'], 'UNVERIFIED')

    def test_verified_requires_media_and_resolved_credit(self):
        row = self.row()
        row['verification_status'] = 'VERIFIED'
        self.assertTrue(self.check([row])[0])
        row.update(source='Fixture', license='research (see HF card)', attribution='Fixture')
        self.assertTrue(self.check([row])[0])
        row.update(media_path='', media_type='')
        self.assertTrue(self.check([row])[0])

    def test_duplicate_identity_is_case_insensitive(self):
        duplicate = self.row()
        duplicate['term'] = 'a'
        self.assertTrue(any('duplicate' in error for error in self.check([self.row(), duplicate])[0]))

    def test_traversal_absolute_and_symlink_paths_are_rejected(self):
        (self.media / 'linked.jpg').symlink_to(self.media / 'A.jpg')
        for path in ('../outside.jpg', '/etc/passwd', 'linked.jpg', 'letters\\A.jpg'):
            row = self.row()
            row['media_path'] = path
            with self.subTest(path=path):
                self.assertTrue(self.check([row])[0])

    def test_missing_empty_and_mismatched_media_are_rejected(self):
        (self.media / 'empty.jpg').touch()
        for path, kind in [('missing.jpg', 'IMAGE'), ('empty.jpg', 'IMAGE'), ('A.jpg', 'VIDEO')]:
            row = self.row()
            row.update(media_path=path, media_type=kind)
            with self.subTest(path=path, kind=kind):
                self.assertTrue(self.check([row])[0])

    def test_invalid_status_category_and_oversized_metadata_are_rejected(self):
        for field, value in [('verification_status', 'TRUSTED'), ('category', 'NOUN'), ('notes', 'x' * 501)]:
            row = self.row()
            row[field] = value
            with self.subTest(field=field):
                self.assertTrue(self.check([row])[0])

    def test_empty_manifest_and_incorrect_columns_are_rejected(self):
        self.assertTrue(self.check([])[0])
        self.csv.write_text('term,category\nA,LETTER\n')
        self.assertTrue(validate(self.csv, self.media)[0])
        self.csv.write_text(','.join(HEADER) + '\nA,LETTER\n')
        self.assertTrue(validate(self.csv, self.media)[0])


if __name__ == '__main__':
    unittest.main()
