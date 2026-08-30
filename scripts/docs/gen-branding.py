#!/usr/bin/env python3
"""Regenerate the Areté logo and favicons from the brand spec.

Visual identity: an "A" monogram stylised as a twin-peak mountain — a white
silhouette with a triangular counter, centred on a solid blue (#2563eb)
rounded-square badge.

Outputs:
  docs/assets/favicon.svg
  docs/assets/favicon.ico                     (16, 32, 48)
  docs/assets/logo.png                        (512)
  docs/assets/apple-touch-icon.png            (180)
  docs/assets/icon-512.png                    (512, PWA/app icon)
  arete-app/src/main/resources/static/favicon.svg
  arete-app/src/main/resources/static/favicon.ico
  arete-app/src/main/resources/static/apple-touch-icon.png
  arete-app/src/main/resources/static/icon-512.png

Requires: Pillow.
"""

from pathlib import Path

from PIL import Image, ImageDraw

SS = 8  # supersampling factor for anti-aliasing

REPO = Path(__file__).resolve().parents[2]
BLUE = "#2563eb"

# Twin-peak mountain that also reads as an "A": tall left peak = the apex,
# a notch, a lower right peak; the triangular counter keeps the letterform.
MOUNTAIN = [(12, 50), (27, 14), (33, 24), (39, 18), (52, 50)]
COUNTER = [(28.5, 26), (22.5, 40), (35, 40)]

_poly = lambda pts: "M" + " L".join(f"{x} {y}" for x, y in pts) + " Z"

SVG = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <rect x="2" y="2" width="60" height="60" rx="14" fill="{BLUE}"/>
  <path fill="#ffffff" fill-rule="evenodd"
        d="{_poly(MOUNTAIN)} {_poly(COUNTER)}"/>
</svg>
"""

SVG_TARGETS = [
    REPO / "docs/assets/favicon.svg",
    REPO / "arete-app/src/main/resources/static/favicon.svg",
]

# (relative path, size)
PNG_TARGETS = [
    ("docs/assets/logo.png", 512),
    ("docs/assets/apple-touch-icon.png", 180),
    ("docs/assets/icon-512.png", 512),
    ("arete-app/src/main/resources/static/apple-touch-icon.png", 180),
    ("arete-app/src/main/resources/static/icon-512.png", 512),
]

ICO_TARGETS = [
    "docs/assets/favicon.ico",
    "arete-app/src/main/resources/static/favicon.ico",
]
ICO_SIZES = [16, 32, 48]




def render(size: int) -> Image.Image:
    """Draw the badge on a 64-unit grid, supersampled, then downscale."""
    n = size * SS
    u = n / 64  # one grid unit in pixels
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    d.rounded_rectangle([2 * u, 2 * u, 62 * u, 62 * u], radius=14 * u, fill=BLUE)
    d.polygon([(x * u, y * u) for x, y in MOUNTAIN], fill="#ffffff")
    d.polygon([(x * u, y * u) for x, y in COUNTER], fill=BLUE)

    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    for t in SVG_TARGETS:
        t.write_text(SVG)
        print("wrote", t.relative_to(REPO))

    for rel, size in PNG_TARGETS:
        render(size).save(REPO / rel)
        print("wrote", rel)

    base = render(512)
    for rel in ICO_TARGETS:
        base.save(REPO / rel, sizes=[(s, s) for s in ICO_SIZES])
        print("wrote", rel)


if __name__ == "__main__":
    main()
