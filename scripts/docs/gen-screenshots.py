#!/usr/bin/env python3
"""Regenerate the app screenshots in docs/assets/.

Prereqs:
  - a running Areté instance (see BASE below or set ARETE_URL)
  - pip install playwright && playwright install chromium

Usage:
  ARETE_URL=http://localhost:6809 python3 scripts/docs/gen-screenshots.py

It pastes scripts/docs/bookstore-demo.yaml, runs the bundled Areté Policy
Engine, and captures: screenshot.png (Explore), screenshot-scoring.png,
screenshot-model.png, screenshot-general.png, screenshot-settings.png.
Best run against a throwaway instance (isolated $HOME) so it doesn't touch a
real spec collection.
"""
import os
import pathlib
from playwright.sync_api import sync_playwright

REPO = pathlib.Path(__file__).resolve().parents[2]
BASE = os.environ.get("ARETE_URL", "http://localhost:6809").rstrip("/")
OUT = REPO / "docs/assets"
SPEC = (REPO / "scripts/docs/bookstore-demo.yaml").read_text()

# Let the page grow so full_page captures everything, keeping the flex row layout.
UNCLIP = """
  body { height: auto !important; min-height: 100vh; }
  .main > .main-content { overflow-y: visible !important; height: auto !important; min-height: 0 !important; }
"""


def active_top(pg):
    return pg.locator(".explore-score-tabset > .tabset-panels > .tab-panel.active")


def shot(pg, name):
    pg.add_style_tag(content=UNCLIP)
    pg.wait_for_timeout(150)
    pg.evaluate("window.scrollTo(0, 0)")
    pg.screenshot(path=str(OUT / name), full_page=True)
    print(name)


def click_tab(pg, name):
    pg.locator(".explore-score-tabset > .tabset-nav .tab-btn", has_text=name).first.click()
    pg.wait_for_timeout(300)


def click_subtab(pg, name):
    active_top(pg).locator(".tabset-nav .tab-btn", has_text=name).first.click()
    pg.wait_for_timeout(300)


def expand_books_get(pg):
    ep = active_top(pg).locator(".endpoint", has_text="/books").first
    if "collapsed" in (ep.get_attribute("class") or ""):
        ep.locator(".endpoint-toggle").click()
        pg.wait_for_timeout(300)


def main():
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1280, "height": 900}, device_scale_factor=2)

        pg.goto(BASE + "/", wait_until="networkidle")
        pg.fill("textarea[name=specText]", SPEC)
        pg.get_by_role("button", name="Submit", exact=True).click()
        pg.wait_for_load_state("networkidle")
        pg.wait_for_timeout(500)

        click_tab(pg, "Explore")
        click_subtab(pg, "Interface")
        expand_books_get(pg)
        shot(pg, "screenshot.png")

        click_tab(pg, "Score")
        pg.locator("#scoring-picker-form").get_by_role("button", name="Score", exact=True).click()
        pg.wait_for_load_state("networkidle")
        pg.wait_for_timeout(800)
        click_tab(pg, "Score")

        click_subtab(pg, "Interface")
        expand_books_get(pg)
        shot(pg, "screenshot-scoring.png")

        click_subtab(pg, "Model")
        shot(pg, "screenshot-model.png")

        click_subtab(pg, "General")
        shot(pg, "screenshot-general.png")

        pg.goto(BASE + "/settings", wait_until="networkidle")
        pg.wait_for_timeout(400)
        shot(pg, "screenshot-settings.png")

        b.close()


if __name__ == "__main__":
    main()
