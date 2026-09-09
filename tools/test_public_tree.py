import unittest
from check_public_tree import violations


class PublicTreeTest(unittest.TestCase):
    def test_private_identity_in_history_is_detected(self):
        self.assertTrue(violations(b'committer Private <private@example.test>', [b'private@example.test']))
        self.assertFalse(violations(b'MOAMI contributors <contributors@moami.invalid>', [b'private@example.test']))
