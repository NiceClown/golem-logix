"""Screenshot analysis for the DevShots rig: OCR text + pixel statistics.

The assistant cannot look at images directly, so every shot is described by
what it contains: the text on it (OCR) and its colours (pixel stats).
Usage: python tools/shotcheck.py <file.png> [<file.png> ...]
"""
import subprocess
import sys
from collections import Counter
from pathlib import Path

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

TESS = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

# palette of the mod (approximate), used to tell "the mod is drawn" from "the world"
NAMED = {
    (111, 244, 255): "cyan (nixie/load)",
    (255, 179, 71): "amber (nixie/load)",
    (255, 80, 56): "red (nixie/load)",
    (255, 138, 42): "orange (nixie/capacity)",
    (90, 58, 38): "dim ember (nixie/dark)",
    (38, 208, 214): "teal (source/glow)",
    (180, 208, 255): "sky",
}


def ocr(path: Path) -> str:
    """OCR the shot: twice the size, sparse text mode, utf-8 safe."""
    try:
        big = path.with_name(path.stem + "_ocr.png")
        img = Image.open(path).convert("RGB")
        img = img.resize((img.width * 2, img.height * 2), Image.LANCZOS)
        img.save(big)
        out = subprocess.run([TESS, str(big), "stdout", "--psm", "11", "-l", "eng"],
                             capture_output=True, timeout=90)
        big.unlink(missing_ok=True)
        text = out.stdout.decode("utf-8", errors="replace")
        return " ".join(text.split())
    except Exception as exc:  # noqa: BLE001
        return f"<ocr failed: {exc}>"


def stats(path: Path) -> dict:
    img = Image.open(path).convert("RGB")
    w, h = img.size
    px = list(img.getdata())
    named = Counter()
    for p in px:
        for c, name in NAMED.items():
            if abs(p[0] - c[0]) < 26 and abs(p[1] - c[1]) < 26 and abs(p[2] - c[2]) < 26:
                named[name] += 1
                break
    dark = sum(1 for p in px if sum(p) < 120)
    bright = sum(1 for p in px if sum(p) > 600)
    top = Counter(px).most_common(4)
    return {
        "size": (w, h),
        "named": named,
        "dark": dark,
        "bright": bright,
        "top": top,
        "total": w * h,
    }


def main() -> None:
    for arg in sys.argv[1:]:
        path = Path(arg)
        print("=" * 70)
        print(path.name)
        st = stats(path)
        print(f"  size {st['size'][0]}x{st['size'][1]}")
        print(f"  dark {st['dark']*100//st['total']}%  bright {st['bright']*100//st['total']}%")
        print("  palette hits:", dict(st["named"].most_common()) or "none")
        print("  top colours:", ", ".join(f"{c}x{n}" for c, n in st["top"]))
        print("  text:", ocr(path)[:600])


if __name__ == "__main__":
    main()
