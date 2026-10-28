import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import generate_sample_catalog as generator


class RestoreFrameTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.page = self.root / 'samples/site/episode.html'
        self.child = self.page.parent / 'episode_files/saved.html'
        self.child.parent.mkdir(parents=True)
        self.child.write_text('<!-- saved from url=(0038)https://player.example/embed/123 -->')
        self.root_patch = patch.object(generator, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def test_src_is_restored_even_when_data_src_precedes_it(self):
        result = generator.clean('<iframe data-src="/lazy" src = "./episode_files/saved.html"></iframe>', self.page)
        self.assertIn('data-src="/lazy" src="https://player.example/embed/123"', result)
        self.assertNotIn('_files/', result)

    def test_replacement_only_changes_the_matched_src_span(self):
        result = generator.clean('<iframe data-src="./episode_files/saved.html" src="./episode_files/saved.html"></iframe>', self.page)
        self.assertIn('data-src="./episode_files/saved.html" src="https://player.example/embed/123"', result)

    def test_data_src_alone_is_not_mistaken_for_src(self):
        tag = '<iframe data-src="./episode_files/saved.html"></iframe>'
        self.assertEqual(tag, generator.clean(tag, self.page))

    def test_expiring_child_url_is_not_restored_and_lazy_attribute_is_untouched(self):
        self.child.write_text('<!-- saved from url=(0050)https://player.example/embed/123?token=expired -->')
        result = generator.clean('<iframe data-src="/lazy" src="./episode_files/saved.html"></iframe>', self.page)
        self.assertIn('data-src="/lazy"', result)
        self.assertNotIn('token=', result)
        self.assertNotIn('_files/', result)


if __name__ == '__main__':
    unittest.main()
