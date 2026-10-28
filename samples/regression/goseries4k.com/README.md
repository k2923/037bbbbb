# GoSeries4K parser fixtures

Decoded and reduced from the five user-uploaded MHTML files in `samples/goseries4k.com/`
(commit b4d4982096c35aff2bb485ee885aba1275a13240). The originals have no file extensions;
their MIME headers identify the format. Original files remain unchanged.

Each fixture retains its original Content-Location as a canonical URL. `series.html`
restores the Torbo iframe URL from the captured child's Content-Location instead of
using MHTML's `cid:` reference. Scripts, CSS, comments, form data, SVG and advertisement
overlays were removed; content cards inside `.list-ads` remain because that wrapper
also contains the site's real recommendations. No signed media URLs are bundled.

- home: 20 cards / 16 distinct titles across home sections
- search-empty: `?s=` (empty query); verifies search layout, not successful keyword search
- category / category-page2: 16 titles each, actual next-page URL
- series: Tigers Sniff the Rose, 14 real episode buttons, subtitle group, 8 recommendations

The title advertises 40 episodes; the parser returns only the 14 existing buttons.
No sample catalog mode or automatic fallback is published for this site. Live catalog
requests still require access to the real website. Only Torbo's observed embed/HLS
routes are supported initially; other hosts fail in the app without opening a web window.
