#!/usr/bin/env python3
"""Builds a small, made-up TouchChat vocabulary (.c4v + Images.c4s) for the core tests.

Same tables and columns as real TouchChat files, with a few pages and buttons that cover what
the importer handles: page links, a word that also opens a page (WordPower "I"), a control-only
button, a sound, the person's own picture, a hidden button, a long message stored on overflow
pages, colours, and enough rows that SQLite needs interior b-tree pages.

Writes core/src/commonTest/kotlin/com/robbyolivam/aac/core/TouchChatFixture.kt.
Run from the repository root: python3 scripts/make-touchchat-fixture.py
"""
import base64
import os
import sqlite3
import struct
import tempfile
import zlib

OUT = "core/src/commonTest/kotlin/com/robbyolivam/aac/core/TouchChatFixture.kt"


def tiny_png():
    """A 2×2 red PNG."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + b"\xff\x00\x00" * 2 for _ in range(2))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 2, 2, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def vocab(path):
    db = sqlite3.connect(path)
    db.execute("PRAGMA page_size = 512")
    db.executescript("""
    CREATE TABLE "resources" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "rid" TEXT NOT NULL UNIQUE, "name" TEXT NOT NULL, "type" INTEGER NOT NULL) STRICT;
    CREATE TABLE "actions" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "resource_id" INTEGER NOT NULL, "rank" INTEGER NOT NULL, "code" INTEGER NOT NULL, UNIQUE ("resource_id", "rank")) STRICT;
    CREATE TABLE "action_data" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "action_id" INTEGER NOT NULL, "key" INTEGER NOT NULL, "value" TEXT NOT NULL, UNIQUE ("action_id", "key")) STRICT;
    CREATE TABLE "button_boxes" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "resource_id" INTEGER NOT NULL UNIQUE, "layout_x" INTEGER, "layout_y" INTEGER, "init_size_x" INTEGER, "init_size_y" INTEGER, "scan_pattern_id" INTEGER NOT NULL DEFAULT 0) STRICT;
    CREATE TABLE "button_box_cells" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "button_box_id" INTEGER NOT NULL, "resource_id" INTEGER NOT NULL, "location" INTEGER NOT NULL, "span_x" INTEGER NOT NULL, "span_y" INTEGER NOT NULL, UNIQUE ("button_box_id", "location")) STRICT;
    CREATE TABLE "symbol_links" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "rid" TEXT NOT NULL, "feature" Integer NOT NULL) STRICT;
    CREATE TABLE "button_styles" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "label_on_top" INTEGER, "force_label_on_top" INTEGER, "transparent" INTEGER, "force_transparent" INTEGER, "font_color" INTEGER, "force_font_color" INTEGER, "body_color" INTEGER, "force_body_color" INTEGER, "border_color" INTEGER, "force_border_color" INTEGER, "border_width" INTEGER, "force_border_width" INTEGER, "font_name" TEXT, "font_bold" INTEGER, "font_underline" INTEGER, "font_italic" INTEGER, "font_height" INTEGER, "force_font" INTEGER) STRICT;
    CREATE TABLE "pages" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "resource_id" INTEGER NOT NULL UNIQUE, "symbol_link_id" INTEGER, "page_style_id" INTEGER NOT NULL, "button_style_id" INTEGER NOT NULL, "feature" INTEGER) STRICT;
    CREATE TABLE "button_box_instances" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "page_id" INTEGER NOT NULL, "button_box_id" INTEGER NOT NULL, "position_x" INTEGER, "position_y" INTEGER, "size_x" INTEGER, "size_y" INTEGER) STRICT;
    CREATE TABLE "button_sets" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "resource_id" INTEGER NOT NULL UNIQUE) STRICT;
    CREATE TABLE "buttons" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "resource_id" INTEGER NOT NULL UNIQUE, "label" TEXT, "message" TEXT, "symbol_link_id" INTEGER, "visible" INTEGER, "button_style_id" INTEGER NOT NULL, "pronunciation" TEXT, "skin_tone_override" INTEGER, "scale" REAL) STRICT;
    CREATE TABLE "button_set_modifiers" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "button_set_id" INTEGER NOT NULL, "button_id" INTEGER NOT NULL, "modifier" INTEGER NOT NULL, UNIQUE ("button_set_id", "modifier")) STRICT;
    CREATE TABLE "sounds" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "resource_id" INTEGER NOT NULL UNIQUE, "data" BLOB) STRICT;
    CREATE TABLE "special_pages" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "name" TEXT NOT NULL UNIQUE, "page_id" INTEGER NOT NULL) STRICT;
    """)
    n = [0]

    def resource(name, kind):
        n[0] += 1
        rid = "{R-%04d}" % n[0]
        return db.execute("INSERT INTO resources (rid, name, type) VALUES (?, ?, ?)", (rid, name, kind)).lastrowid, rid

    def style(bgr):
        return db.execute("INSERT INTO button_styles (body_color, font_name) VALUES (?, 'Ubuntu')", (bgr,)).lastrowid

    def actions(resource_id, *items):
        for rank, (code, data) in enumerate(items):
            aid = db.execute("INSERT INTO actions (resource_id, rank, code) VALUES (?, ?, ?)", (resource_id, rank, code)).lastrowid
            for key, value in data.items():
                db.execute("INSERT INTO action_data (action_id, key, value) VALUES (?, ?, ?)", (aid, key, value))

    def button(label, message, style_id, visible=1, symbol=None):
        res, _ = resource(label, 4)
        bid = db.execute("INSERT INTO buttons (resource_id, label, message, symbol_link_id, visible, button_style_id) VALUES (?, ?, ?, ?, ?, ?)",
                         (res, label, message, symbol, visible, style_id)).lastrowid
        return res, bid

    def page(name, cols, rows, page_style):
        res, rid = resource(name, 7)
        pid = db.execute("INSERT INTO pages (resource_id, page_style_id, button_style_id) VALUES (?, 1, ?)", (res, page_style)).lastrowid
        box_res, _ = resource(name + " BBox", 6)
        box = db.execute("INSERT INTO button_boxes (resource_id, layout_x, layout_y) VALUES (?, ?, ?)", (box_res, cols, rows)).lastrowid
        db.execute("INSERT INTO button_box_instances (page_id, button_box_id) VALUES (?, ?)", (pid, box))
        return pid, rid, box

    def place(box, location, resource_id):
        db.execute("INSERT INTO button_box_cells (button_box_id, resource_id, location, span_x, span_y) VALUES (?, ?, ?, 1, 1)",
                   (box, resource_id, location))

    white = style(0xFFFFFF)
    yellow = style(0x6FFFFF)  # 0xBBGGRR: red FF, green FF, blue 6F
    red = style(0x0000FF)
    home, _, home_box = page(".Basic60 Home Page", 3, 2, white)
    food, food_rid, food_box = page(".Food", 2, 1, white)
    db.execute("INSERT INTO special_pages (name, page_id) VALUES ('HOME', ?)", (home,))
    unused, _, _ = page("Unused", 1, 1, white)  # not linked from home: left out

    res, _ = button("FOOD", "", white)
    actions(res, (60, {}), (9, {0: food_rid, 1: "3"}))
    place(home_box, 0, res)

    set_res, _ = resource("I", 5)
    set_id = db.execute("INSERT INTO button_sets (resource_id) VALUES (?)", (set_res,)).lastrowid
    i_res, i_id = button("I", "I ", yellow)
    actions(i_res, (60, {}), (10, {}), (71, {0: "30"}), (9, {0: food_rid, 1: "0"}))
    am_res, am_id = button("am", "am ", yellow)
    db.execute("INSERT INTO button_set_modifiers (button_set_id, button_id, modifier) VALUES (?, ?, 21)", (set_id, am_id))
    db.execute("INSERT INTO button_set_modifiers (button_set_id, button_id, modifier) VALUES (?, ?, 0)", (set_id, i_id))
    place(home_box, 1, set_res)

    res, _ = button("clear", "", white)
    actions(res, (60, {}), (28, {}))
    place(home_box, 2, res)

    sound_res, sound_rid = resource("moo.wav", 3)
    db.execute("INSERT INTO sounds (resource_id, data) VALUES (?, ?)", (sound_res, b"RIFF....WAVEfmt fake"))
    res, _ = button("cow", "moo", white)
    actions(res, (4, {0: sound_rid}), (42, {}))
    place(home_box, 3, res)

    link = db.execute("INSERT INTO symbol_links (rid, feature) VALUES ('{PHOTO-1}', 145)").lastrowid
    res, _ = button("Grandma//Jo", "Grandma Jo", white, symbol=link)
    actions(res, (60, {}), (10, {}))
    place(home_box, 4, res)

    res, _ = button("secret", "secret", white, visible=0)
    actions(res, (10, {}))
    place(home_box, 5, res)

    res, _ = button("apple", "apple", red)
    actions(res, (10, {}))
    place(food_box, 0, res)
    story = "Once upon a time " * 60  # longer than one 512-byte page: stored on overflow pages
    res, _ = button("story", story.strip(), red)
    actions(res, (42, {}))
    place(food_box, 1, res)

    for i in range(150):  # enough rows for interior b-tree pages
        resource("filler %d" % i, 1)
    db.commit()
    db.execute("VACUUM")
    db.close()


def images(path):
    db = sqlite3.connect(path)
    db.execute("PRAGMA page_size = 512")
    db.execute("CREATE TABLE [symbols] ([id] INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, [rid] TEXT UNIQUE NOT NULL, "
               "[compressed] INTEGER NOT NULL, [type] INTEGER NOT NULL, [width] INTEGER NOT NULL, [height] INTEGER NOT NULL, [data] BLOB NOT NULL)")
    db.execute("INSERT INTO symbols (rid, compressed, type, width, height, data) VALUES ('{PHOTO-1}', 0, 2, 2, 2, ?)", (tiny_png(),))
    db.commit()
    db.close()


def kotlin_string(data):
    text = base64.b64encode(data).decode()
    lines = [text[i:i + 110] for i in range(0, len(text), 110)]
    return "\n".join('        "%s" +' % line for line in lines).rstrip(" +")


with tempfile.TemporaryDirectory() as tmp:
    v, im = os.path.join(tmp, "Test.c4v"), os.path.join(tmp, "Images.c4s")
    vocab(v)
    images(im)
    vocab_bytes, image_bytes = open(v, "rb").read(), open(im, "rb").read()

with open(OUT, "w") as f:
    f.write("""package com.robbyolivam.aac.core

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A made-up TouchChat vocabulary. Generated by scripts/make-touchchat-fixture.py: don't edit. */
@OptIn(ExperimentalEncodingApi::class)
object TouchChatFixture {
    val files: Map<String, ByteArray> get() = mapOf(
        "Test.c4v" to Base64.decode(VOCAB),
        "Images.c4s" to Base64.decode(IMAGES),
        "version.txt" to "5".encodeToByteArray(),
    )

    private const val VOCAB =
%s

    private const val IMAGES =
%s
}
""" % (kotlin_string(vocab_bytes), kotlin_string(image_bytes)))
print("wrote", OUT, len(vocab_bytes), "+", len(image_bytes), "bytes")
