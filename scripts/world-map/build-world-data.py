#!/usr/bin/env python3
"""Converts Natural Earth admin-0 countries (1:50m, GeoJSON) into shared/data/world-50m.txt.

Source (public domain): https://github.com/nvkelso/natural-earth-vector, file
geojson/ne_50m_admin_0_countries.geojson (https://www.naturalearthdata.com/).

    python3 scripts/world-map/build-world-data.py ne_50m_admin_0_countries.geojson shared/data/world-50m.txt

Output: a header line, then one line per country: "minX,minY,maxX,maxY rings", the rings separated by spaces.
Coordinates are Web Mercator in hundredths of a degree: x = lng * 100, y = degrees(ln(tan(pi/4 + lat/2))) * 100.
A ring is a polyline string: the first vertex absolute, every other vertex as the difference to the one before
(x then y), each number zigzag coded and written in groups of 5 bits (see CHARS). Rings are closed implicitly.
The map is cropped to 60 degrees south (Antarctica is left out) and 84 degrees north.
"""
import json
import math
import sys

UNIT = 100
MIN_LAT = -60.0
MAX_LAT = 84.0
EPSILON = 1.2
FINAL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef"
MORE = "ghijklmnopqrstuvwxyz0123456789-_"
HEADER = "jackdaw-world 1 unit=0.01 mercator"


def mercator_y(lat):
    lat = max(MIN_LAT, min(MAX_LAT, lat))
    return math.degrees(math.log(math.tan(math.pi / 4 + math.radians(lat) / 2)))


def project(lng, lat):
    return round(lng * UNIT), round(mercator_y(lat) * UNIT)


def encode_number(n):
    z = (n << 1) if n >= 0 else ((-n << 1) - 1)
    out = ""
    while z >= 32:
        out += MORE[z & 31]
        z >>= 5
    return out + FINAL[z]


def distance_to_segment(p, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    if length == 0:
        return math.hypot(p[0] - ax, p[1] - ay)
    t = max(0, min(1, ((p[0] - ax) * dx + (p[1] - ay) * dy) / length))
    return math.hypot(p[0] - (ax + t * dx), p[1] - (ay + t * dy))


def simplify(points):
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        lo, hi = stack.pop()
        far, at = -1.0, -1
        for i in range(lo + 1, hi):
            d = distance_to_segment(points[i], points[lo], points[hi])
            if d > far:
                far, at = d, i
        if far > EPSILON:
            keep[at] = True
            stack.append((lo, at))
            stack.append((at, hi))
    return [p for p, k in zip(points, keep) if k]


def ring_points(ring):
    points = []
    for lng, lat in ring:
        p = project(lng, lat)
        if not points or points[-1] != p:
            points.append(p)
    if len(points) > 1 and points[0] == points[-1]:
        points.pop()
    if len(points) < 3:
        return None
    middle = len(points) // 2
    points = simplify(points[: middle + 1])[:-1] + simplify(points[middle:] + [points[0]])[:-1]
    if len(points) < 3:
        return None
    area = sum(points[i][0] * points[(i + 1) % len(points)][1] - points[(i + 1) % len(points)][0] * points[i][1] for i in range(len(points)))
    return points if area != 0 else None


def encode_ring(points):
    out = encode_number(points[0][0]) + encode_number(points[0][1])
    for (px, py), (x, y) in zip(points, points[1:]):
        out += encode_number(x - px) + encode_number(y - py)
    return out


def main(source, target):
    with open(source, encoding="utf-8") as f:
        data = json.load(f)
    lines = []
    for feature in data["features"]:
        props = feature["properties"]
        if props.get("ADM0_A3") == "ATA" or props.get("NAME") == "Antarctica":
            continue
        geometry = feature["geometry"]
        polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
        rings = [r for polygon in polygons for ring in polygon if (r := ring_points(ring))]
        if not rings:
            continue
        xs = [x for r in rings for x, _ in r]
        ys = [y for r in rings for _, y in r]
        lines.append((props.get("ADM0_A3", ""), "%d,%d,%d,%d %s" % (min(xs), min(ys), max(xs), max(ys), " ".join(encode_ring(r) for r in rings))))
    lines.sort(key=lambda entry: entry[0])
    with open(target, "w", encoding="ascii", newline="\n") as f:
        f.write(HEADER + "\n")
        for _, line in lines:
            f.write(line + "\n")
    print("%d countries, %d bytes" % (len(lines), sum(len(l) + 1 for _, l in lines)))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
