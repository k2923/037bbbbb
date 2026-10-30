"""Exercise the real WebView selection script against delayed episode transitions."""
import json
from pathlib import Path
import shutil
import subprocess
import unittest


class GoSeriesSelectionTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("node"), "Node.js is required to execute the WebView script")
    def test_episode_selection_waits_for_correct_frame(self):
        root = Path(__file__).resolve().parent.parent
        source = (root / "GoSeries4K/src/main/kotlin/com/goseries4k/GoSeriesPlayback.kt").read_text()
        script = source.split("fun script(id: String): String {", 1)[1].split('return """', 1)[1].split('""".trimIndent()', 1)[0]
        script = script.replace("'$id'", "'134525'").replace("${'$'}", "$")
        test = r'''
const vm = require('node:vm'), assert = require('node:assert/strict');
const script = SCRIPT;
function scenario(initial, postId = '134522') {
  let active = initial, frame = 'https://torbo007.com/embed/01aa', signals = [], clicks = 0, tick;
  const root = {querySelector: () => ({src: frame}), getAttribute: () => postId};
  const button = {click: () => { clicks++; active = '134525'; }};
  const document = {
    readyState: 'complete',
    querySelector: s => s === '#mpPlayer' ? root : button,
    querySelectorAll: () => active === 'ambiguous'
      ? [{getAttribute: () => '134522'}, {getAttribute: () => '134525'}]
      : [{getAttribute: () => active}]
  };
  const context = {document, window: {}, setInterval: fn => {tick = fn; return 1;},
    clearInterval: () => {}, setTimeout: () => {},
    fetch: url => {signals.push(JSON.parse(decodeURIComponent(url.split('=')[1]))); return Promise.resolve();}};
  vm.runInNewContext(script, context);
  return {active: value => active = value, frame: value => frame = value,
    tick: () => tick(), signals, clicks: () => clicks};
}
let x = scenario('134522');
assert.equal(x.clicks(), 1);
assert.equal(x.signals.length, 0); // Active button changed, but iframe is still the old episode.
x.frame('https://torbo007.com/embed/02bb'); x.tick();
assert.equal(x.signals.length, 1);
assert.equal(x.signals[0].currentId, '134525');
assert.equal(x.signals[0].initialId, '134522');
x = scenario('134525', '134525'); assert.equal(x.clicks(), 0); assert.equal(x.signals.length, 1);
x = scenario('134525'); assert.equal(x.signals.length, 0); // Already active, but frame may still be stale.
x.frame('https://torbo007.com/embed/02bb'); x.tick(); assert.equal(x.signals.length, 1);
x = scenario('134522'); x.active('ambiguous'); x.frame('https://torbo007.com/embed/02bb'); x.tick();
assert.equal(x.signals.length, 0);
x = scenario('134522'); x.active('134522'); x.frame('https://torbo007.com/embed/02bb'); x.tick();
assert.equal(x.signals.length, 0);
'''.replace("SCRIPT", json.dumps(script), 1)
        subprocess.run(["node", "-e", test], check=True, cwd=root, timeout=10)


if __name__ == "__main__":
    unittest.main()
