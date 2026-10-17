import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from publish import write_manifest

class PublishContractTests(unittest.TestCase):
    def test_complete_sample_manifest(self):
        sample = Path(__file__).resolve().parents[1] / 'android/app/src/main/assets/sample.pdf'
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            pdf = root / 'issues/2026-10-16.pdf'
            pdf.parent.mkdir()
            pdf.write_bytes(sample.read_bytes())
            result = write_manifest(pdf, root, '2026-10-16')
            self.assertEqual(result['pages'], 3)
            self.assertEqual(result['sha256'], hashlib.sha256(sample.read_bytes()).hexdigest())
            self.assertEqual(result['pdf'], 'issues/2026-10-16.pdf')
            self.assertEqual(json.loads((root/'latest.json').read_text()), result)

    def test_corrupt_input_does_not_publish_manifest(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            pdf=root/'broken.pdf'
            pdf.write_bytes(b'not a PDF')
            with self.assertRaises(Exception):
                write_manifest(pdf, root, '2026-10-16')
            self.assertFalse((root/'latest.json').exists())

if __name__ == '__main__':
    unittest.main()
