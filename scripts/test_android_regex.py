"""Compile 24HD's literal Regex patterns with ICU, Android's regex backend.

Requires the host ICU runtime (Ubuntu: libicu-dev). Python's and the JDK's
regex engines do not enforce the same syntax as Android's ICU engine.
"""
import ctypes
import ctypes.util
import json
import os
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
PARSER = ROOT / 'TwentyFourHD/src/main/kotlin/com/twentyfourhd/TwentyFourHDParser.kt'


class ParseError(ctypes.Structure):
    _fields_ = [('line', ctypes.c_int32), ('offset', ctypes.c_int32),
                ('pre', ctypes.c_uint16 * 16), ('post', ctypes.c_uint16 * 16)]


class AndroidRegexTest(unittest.TestCase):
    def test_24hd_patterns_compile_with_android_regex_backend(self):
        library = os.environ.get('ANDROID_REGEX_ICU_LIBRARY') or ctypes.util.find_library('icui18n')
        self.assertIsNotNone(library, 'Install libicu-dev to run Android regex verification')
        icu = ctypes.CDLL(library)
        # Linux ICU symbols are versioned, for example uregex_open_74.
        version = re.search(r'\.so\.(\d+)', str(library))
        suffix = '_' + version.group(1) if version else ''
        compile_regex = getattr(icu, 'uregex_open' + suffix)
        compile_regex.argtypes = [ctypes.POINTER(ctypes.c_uint16), ctypes.c_int32,
                                  ctypes.c_uint32, ctypes.POINTER(ParseError),
                                  ctypes.POINTER(ctypes.c_int32)]
        compile_regex.restype = ctypes.c_void_p
        close_regex = getattr(icu, 'uregex_close' + suffix)
        close_regex.argtypes = [ctypes.c_void_p]
        close_regex.restype = None
        literals = re.findall(r'\bRegex\(\s*(""".*?"""|"(?:\\.|[^"\\])*")',
                              PARSER.read_text(), re.DOTALL)
        self.assertTrue(literals, 'No 24HD Regex literals found')
        for literal in literals:
            pattern = literal[3:-3] if literal.startswith('"""') else json.loads(literal)
            with self.subTest(pattern=pattern):
                # This parser currently has only constant patterns. Fail if an
                # interpolated pattern is introduced instead of pretending to test it.
                self.assertNotIn('${', pattern)
                encoded = pattern.encode('utf-16-le')
                units = (ctypes.c_uint16 * (len(encoded) // 2)).from_buffer_copy(encoded)
                status, location = ctypes.c_int32(0), ParseError()
                handle = compile_regex(units, len(units), 0, ctypes.byref(location), ctypes.byref(status))
                try:
                    self.assertLessEqual(status.value, 0,
                                         f'ICU rejected pattern at {location.offset}, status {status.value}')
                finally:
                    if handle:
                        close_regex(handle)


if __name__ == '__main__':
    unittest.main()
