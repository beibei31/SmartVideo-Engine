from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
RUN_DIR = ROOT / "pet-runs" / "homu"
OUT_DIR = RUN_DIR / "local"
FINAL_DIR = RUN_DIR / "final"
QA_DIR = RUN_DIR / "qa"

CELL_W = 192
CELL_H = 208
COLS = 8
ROWS = 9

FRAME_COUNTS = {
    "idle": 6,
    "running-right": 8,
    "running-left": 8,
    "waving": 4,
    "jumping": 5,
    "failed": 8,
    "waiting": 6,
    "running": 6,
    "review": 6,
}

ROW_ORDER = list(FRAME_COUNTS)

COLORS = {
    "outline": (38, 34, 44, 255),
    "hair": (40, 42, 49, 255),
    "hair_hi": (91, 92, 102, 255),
    "skin": (255, 226, 206, 255),
    "blush": (245, 151, 167, 160),
    "eye": (112, 82, 188, 255),
    "eye_hi": (230, 220, 255, 255),
    "white": (248, 247, 250, 255),
    "gray": (151, 147, 162, 255),
    "dark": (42, 37, 50, 255),
    "purple": (99, 70, 132, 255),
    "shield": (176, 170, 145, 255),
    "shield_dark": (102, 97, 84, 255),
    "blue": (128, 169, 224, 255),
}


def ellipse(draw: ImageDraw.ImageDraw, box, fill, outline=None, width=1):
    draw.ellipse(box, fill=fill, outline=outline, width=width)


def poly(draw: ImageDraw.ImageDraw, points, fill, outline=None):
    draw.polygon(points, fill=fill)
    if outline:
        draw.line(points + [points[0]], fill=outline, width=3, joint="curve")


def line(draw: ImageDraw.ImageDraw, points, fill, width=3):
    draw.line(points, fill=fill, width=width, joint="curve")


def draw_homu(draw: ImageDraw.ImageDraw, cx: int, base_y: int, *, state: str, frame: int, facing: int = 1):
    count = FRAME_COUNTS[state]
    t = frame / max(1, count - 1)
    bob = int(math.sin(t * math.tau) * 3)
    jump = 0
    lean = 0
    arm_wave = 0
    sad = False
    work = False
    review = False

    if state in {"running-right", "running-left"}:
        lean = 5 * facing
        bob += int(math.sin(t * math.tau * 2) * 4)
        cx += int((t - 0.5) * 20 * facing)
    elif state == "waving":
        arm_wave = int(math.sin(t * math.pi) * 20) + 16
    elif state == "jumping":
        jump = int(math.sin(t * math.pi) * 28)
    elif state == "failed":
        sad = True
        bob += min(frame, 3)
    elif state == "waiting":
        bob += int(math.sin(t * math.tau) * 2)
    elif state == "running":
        work = True
        bob += int(math.sin(t * math.tau * 3) * 2)
    elif state == "review":
        review = True
        lean = -3

    y = base_y + bob - jump
    sx = facing

    def x(v: int) -> int:
        return cx + sx * v

    # Hair back mass and long side locks.
    ellipse(draw, (cx - 43, y - 143, cx + 43, y - 59), COLORS["hair"], COLORS["outline"], 3)
    poly(draw, [(x(-31), y - 83), (x(-61), y - 35), (x(-44), y - 14), (x(-20), y - 69)], COLORS["hair"], COLORS["outline"])
    poly(draw, [(x(25), y - 83), (x(61), y - 37), (x(47), y - 9), (x(14), y - 67)], COLORS["hair"], COLORS["outline"])
    line(draw, [(x(-25), y - 133), (x(19), y - 140)], COLORS["hair_hi"], 4)

    # Body, skirt, legs.
    poly(draw, [(x(-28), y - 66), (x(28), y - 66), (x(35), y - 14), (x(-34), y - 14)], COLORS["white"], COLORS["outline"])
    poly(draw, [(x(-37), y - 57), (x(-15), y - 73), (x(0), y - 47), (x(-24), y - 34)], COLORS["gray"], COLORS["outline"])
    poly(draw, [(x(37), y - 57), (x(15), y - 73), (x(0), y - 47), (x(24), y - 34)], COLORS["gray"], COLORS["outline"])
    poly(draw, [(x(-34), y - 15), (x(34), y - 15), (x(42), y + 18), (x(-42), y + 18)], COLORS["gray"], COLORS["outline"])
    for i in range(5):
        xx = x(-31 + i * 16)
        line(draw, [(xx, y - 12), (xx + sx * 5, y + 14)], (116, 111, 128, 255), 2)

    leg_swing = int(math.sin(t * math.tau) * 9) if "running" in state else 0
    line(draw, [(x(-13), y + 13), (x(-17 + leg_swing), y + 54)], COLORS["dark"], 10)
    line(draw, [(x(13), y + 13), (x(17 - leg_swing), y + 54)], COLORS["dark"], 10)
    line(draw, [(x(-22 + leg_swing), y + 57), (x(-6 + leg_swing), y + 57)], COLORS["outline"], 5)
    line(draw, [(x(7 - leg_swing), y + 57), (x(25 - leg_swing), y + 57)], COLORS["outline"], 5)

    # Arms.
    left_arm_up = state == "waving"
    if left_arm_up:
        line(draw, [(x(-30), y - 51), (x(-52), y - 74 - arm_wave)], COLORS["white"], 9)
        ellipse(draw, (x(-58) - 5, y - 82 - arm_wave, x(-58) + 5, y - 72 - arm_wave), COLORS["skin"], COLORS["outline"], 2)
    elif state == "waiting":
        line(draw, [(x(-27), y - 48), (x(-42), y - 28)], COLORS["white"], 9)
        line(draw, [(x(27), y - 48), (x(42), y - 28)], COLORS["white"], 9)
    elif work:
        line(draw, [(x(-27), y - 48), (x(-44), y - 34)], COLORS["white"], 9)
        line(draw, [(x(27), y - 48), (x(44), y - 34)], COLORS["white"], 9)
    else:
        line(draw, [(x(-27), y - 48), (x(-43), y - 20)], COLORS["white"], 9)
        line(draw, [(x(27), y - 48), (x(43), y - 20)], COLORS["white"], 9)

    # Tiny time-shield charm, attached near the right hand.
    shield_cx = x(47 if not review else 39)
    shield_cy = y - 24 if not work else y - 31
    ellipse(draw, (shield_cx - 13, shield_cy - 13, shield_cx + 13, shield_cy + 13), COLORS["shield"], COLORS["outline"], 3)
    ellipse(draw, (shield_cx - 8, shield_cy - 8, shield_cx + 8, shield_cy + 8), None, COLORS["shield_dark"], 2)
    line(draw, [(shield_cx, shield_cy - 10), (shield_cx + sx * 4, shield_cy + 8)], COLORS["shield_dark"], 2)

    # Head and face.
    ellipse(draw, (cx - 34 + lean, y - 128, cx + 34 + lean, y - 68), COLORS["skin"], COLORS["outline"], 3)
    poly(draw, [(x(-35) + lean, y - 111), (x(-12) + lean, y - 133), (x(-2) + lean, y - 103)], COLORS["hair"], COLORS["outline"])
    poly(draw, [(x(-8) + lean, y - 132), (x(18) + lean, y - 123), (x(3) + lean, y - 98)], COLORS["hair"], COLORS["outline"])
    poly(draw, [(x(17) + lean, y - 122), (x(34) + lean, y - 109), (x(17) + lean, y - 92)], COLORS["hair"], COLORS["outline"])
    line(draw, [(cx - 26 + lean, y - 132), (cx + 27 + lean, y - 130)], COLORS["dark"], 5)

    eye_y = y - 99
    if sad:
        line(draw, [(cx - 20 + lean, eye_y), (cx - 10 + lean, eye_y + 3)], COLORS["outline"], 3)
        line(draw, [(cx + 10 + lean, eye_y + 3), (cx + 20 + lean, eye_y)], COLORS["outline"], 3)
        line(draw, [(cx - 14 + lean, eye_y + 7), (cx - 14 + lean, eye_y + 15)], COLORS["blue"], 3)
    else:
        ellipse(draw, (cx - 22 + lean, eye_y - 7, cx - 9 + lean, eye_y + 7), COLORS["eye"], COLORS["outline"], 2)
        ellipse(draw, (cx + 9 + lean, eye_y - 7, cx + 22 + lean, eye_y + 7), COLORS["eye"], COLORS["outline"], 2)
        ellipse(draw, (cx - 18 + lean, eye_y - 5, cx - 14 + lean, eye_y - 1), COLORS["eye_hi"])
        ellipse(draw, (cx + 13 + lean, eye_y - 5, cx + 17 + lean, eye_y - 1), COLORS["eye_hi"])
    ellipse(draw, (cx - 26 + lean, y - 87, cx - 19 + lean, y - 82), COLORS["blush"])
    ellipse(draw, (cx + 19 + lean, y - 87, cx + 26 + lean, y - 82), COLORS["blush"])
    if sad:
        line(draw, [(cx - 5 + lean, y - 78), (cx + 5 + lean, y - 81)], COLORS["outline"], 2)
    else:
        line(draw, [(cx - 3 + lean, y - 80), (cx + 4 + lean, y - 80)], COLORS["outline"], 2)

    # Bow at collar.
    poly(draw, [(x(-7), y - 62), (x(-26), y - 70), (x(-20), y - 53)], COLORS["purple"], COLORS["outline"])
    poly(draw, [(x(7), y - 62), (x(26), y - 70), (x(20), y - 53)], COLORS["purple"], COLORS["outline"])
    ellipse(draw, (cx - 6, y - 67, cx + 6, y - 55), COLORS["purple"], COLORS["outline"], 2)

    if work:
        # Attached small blue "processing gem" held between hands.
        ellipse(draw, (cx - 12, y - 39, cx + 12, y - 17), COLORS["blue"], COLORS["outline"], 2)
        line(draw, [(cx - 5, y - 35), (cx + 6, y - 22)], (226, 240, 255, 255), 2)
    if review:
        line(draw, [(cx - 16, y - 108), (cx - 9, y - 111)], COLORS["outline"], 2)
        line(draw, [(cx + 9, y - 111), (cx + 16, y - 108)], COLORS["outline"], 2)


def render_atlas() -> Image.Image:
    atlas = Image.new("RGBA", (CELL_W * COLS, CELL_H * ROWS), (0, 0, 0, 0))
    for row, state in enumerate(ROW_ORDER):
        count = FRAME_COUNTS[state]
        for frame in range(count):
            cell = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
            draw = ImageDraw.Draw(cell)
            facing = -1 if state == "running-left" else 1
            draw_homu(draw, CELL_W // 2, 146, state=state, frame=frame, facing=facing)
            atlas.alpha_composite(cell, (frame * CELL_W, row * CELL_H))
    return atlas


def make_contact_sheet(atlas: Image.Image) -> Image.Image:
    sheet = Image.new("RGBA", (CELL_W * COLS, CELL_H * ROWS), (236, 238, 242, 255))
    tile = 24
    bg = Image.new("RGBA", sheet.size, (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)
    for y in range(0, sheet.height, tile):
        for x in range(0, sheet.width, tile):
            color = (224, 226, 232, 255) if (x // tile + y // tile) % 2 else (248, 249, 252, 255)
            bg_draw.rectangle((x, y, x + tile - 1, y + tile - 1), fill=color)
    sheet.alpha_composite(bg)
    sheet.alpha_composite(atlas)
    d = ImageDraw.Draw(sheet)
    for x in range(0, sheet.width + 1, CELL_W):
        line(d, [(x, 0), (x, sheet.height)], (120, 126, 140, 255), 1)
    for y in range(0, sheet.height + 1, CELL_H):
        line(d, [(0, y), (sheet.width, y)], (120, 126, 140, 255), 1)
    return sheet


def validate(atlas: Image.Image) -> dict:
    errors = []
    if atlas.size != (1536, 1872):
        errors.append(f"Unexpected atlas size: {atlas.size}")
    alpha = atlas.getchannel("A")
    for row, state in enumerate(ROW_ORDER):
        for col in range(COLS):
            crop = alpha.crop((col * CELL_W, row * CELL_H, (col + 1) * CELL_W, (row + 1) * CELL_H))
            nonempty = crop.getbbox() is not None
            should = col < FRAME_COUNTS[state]
            if should and not nonempty:
                errors.append(f"{state} frame {col} is empty")
            if not should and nonempty:
                errors.append(f"{state} unused frame {col} is not transparent")
    return {
        "ok": not errors,
        "atlas": {"width": atlas.width, "height": atlas.height, "cell_width": CELL_W, "cell_height": CELL_H},
        "errors": errors,
    }


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    FINAL_DIR.mkdir(parents=True, exist_ok=True)
    QA_DIR.mkdir(parents=True, exist_ok=True)

    atlas = render_atlas()
    png_path = FINAL_DIR / "spritesheet.png"
    webp_path = FINAL_DIR / "spritesheet.webp"
    contact_path = QA_DIR / "contact-sheet.png"
    validation_path = FINAL_DIR / "validation.json"
    summary_path = QA_DIR / "run-summary.json"

    atlas.save(png_path)
    atlas.save(webp_path, lossless=True, method=6)
    make_contact_sheet(atlas).save(contact_path)

    validation = validate(atlas)
    validation_path.write_text(json.dumps(validation, indent=2), encoding="utf-8")

    pet_json = {
        "id": "homu",
        "displayName": "Homu",
        "description": "A tiny calm chibi time-guardian pet inspired by Homura Akemi: dark hair, violet eyes, white-gray magical uniform cues, shy loyalty, and a small shield-like time charm.",
        "spritesheetPath": "spritesheet.webp",
    }
    (FINAL_DIR / "pet.json").write_text(json.dumps(pet_json, indent=2), encoding="utf-8")

    summary = {
        "ok": validation["ok"],
        "run_dir": str(RUN_DIR),
        "spritesheet": str(webp_path),
        "pet_json": str(FINAL_DIR / "pet.json"),
        "validation": str(validation_path),
        "contact_sheet": str(contact_path),
        "package": None,
        "method": "local deterministic PIL drawing",
    }
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    if not validation["ok"]:
        raise SystemExit(json.dumps(validation, indent=2))


if __name__ == "__main__":
    main()
