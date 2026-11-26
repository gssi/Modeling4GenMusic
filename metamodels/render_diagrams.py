#!/usr/bin/env python3
"""Draws the class diagrams of the metamodels in img/ as SVG, reading classes, attributes and
enumerations from the .ecore files. Only the placement of the boxes and the routes of the
connectors are written here, per diagram. Run it from this folder:

    python3 render_diagrams.py

PNG copies are made from the SVG files by svg_to_png.mjs (see the note at its top).
"""
import pathlib
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

HERE = pathlib.Path(__file__).parent
XSI = '{http://www.w3.org/2001/XMLSchema-instance}type'
FONT = 'Liberation Sans, Arial, Helvetica, sans-serif'
INK, LINE = '#1E2530', '#333'
ROW, HEAD, HEAD_STEREO, PAD = 19, 29, 45, 10


def width_of(text, size, bold=False):
    """Rough text width: enough to size a box around it."""
    wide, narrow = set('mwMW@'), set('iljtfr:.,;[]()! ')
    units = sum(0.9 if c in wide else 0.3 if c in narrow else 0.68 if c.isupper() else 0.56 for c in text)
    return units * size * (1.06 if bold else 1.0)


class Package:
    def __init__(self, name):
        root = ET.parse(HERE / f'{name}.ecore').getroot()
        self.name, self.ns_uri = root.get('name'), root.get('nsURI')
        self.classes, self.enums = {}, {}
        for c in root.findall('eClassifiers'):
            if c.get(XSI) == 'ecore:EEnum':
                self.enums[c.get('name')] = [l.get('name') for l in c.findall('eLiterals')]
            else:
                self.classes[c.get('name')] = c

    def attributes(self, class_name):
        rows = []
        for f in self.classes[class_name].findall('eStructuralFeatures'):
            if f.get(XSI) != 'ecore:EAttribute':
                continue
            row = f"{f.get('name')} : {type_name(f)}"
            if f.get('lowerBound') == '1':
                row += ' [1]'
            if f.get('defaultValueLiteral') is not None:
                row += f" = {f.get('defaultValueLiteral')}"
            rows.append(row)
        return rows

    def is_abstract(self, class_name):
        return self.classes[class_name].get('abstract') == 'true'


def type_name(feature):
    """`EInt`, `Strength`, or `profile::Instrument` for a type of another metamodel."""
    ref = feature.get('eType')
    if ref is None:
        ref = feature.find('eType').get('href')
    file, _, fragment = ref.rpartition('#//')
    name = fragment or ref
    if file.endswith('.ecore') and not file.startswith('http'):
        return f"{file.split('/')[-1][:-len('.ecore')]}::{name}"
    return name


class Box:
    def __init__(self, x, y, title, rows=(), stereotype=None, kind='class'):
        self.x, self.y, self.title, self.rows, self.stereotype, self.kind = x, y, title, list(rows), stereotype, kind
        self.head = HEAD_STEREO if stereotype else HEAD
        texts = [width_of(title, 15, True) + 24] + [width_of(r, 13.5) + 2 * PAD for r in self.rows]
        if stereotype:
            texts.append(width_of(f'«{stereotype}»', 12.5) + 24)
        self.w = max(84, *texts)
        self.h = self.head + (len(self.rows) * ROW + PAD if self.rows else 0)

    cx = property(lambda self: self.x + self.w / 2)
    cy = property(lambda self: self.y + self.h / 2)
    right = property(lambda self: self.x + self.w)
    bottom = property(lambda self: self.y + self.h)

    def svg(self):
        fill, head, stroke, dash = {
            'class': ('#FFFFFF', '#EEF1F7', '#3A4A66', ''),
            'enum': ('#FFFDF4', '#FBF0CF', '#8A6A12', ''),
            'external': ('#F4F4F2', '#E6E6E2', '#8A8A85', ' stroke-dasharray="5 3"'),
        }[self.kind]
        italic = 'italic' if self.stereotype == 'abstract' else 'normal'
        out = [f'<rect x="{self.x}" y="{self.y}" width="{self.w:.1f}" height="{self.h}" fill="{fill}" stroke="{stroke}" stroke-width="1.3"{dash}/>',
               f'<rect x="{self.x}" y="{self.y}" width="{self.w:.1f}" height="{self.head}" fill="{head}" stroke="{stroke}" stroke-width="1.3"{dash}/>']
        name_y = self.y + (36 if self.stereotype else 20)
        if self.stereotype:
            out.append(text(self.cx, self.y + 17, f'«{self.stereotype}»', 12.5, anchor='middle', style='italic'))
        out.append(text(self.cx, name_y, self.title, 15, anchor='middle', weight='bold', style=italic))
        for i, row in enumerate(self.rows):
            out.append(text(self.x + PAD, self.y + self.head + 18 + i * ROW, row, 13.5))
        return ''.join(out)


def text(x, y, content, size, anchor='start', weight='normal', style='normal', fill=INK):
    return (f'<text x="{x:.1f}" y="{y:.1f}" text-anchor="{anchor}" font-family="{FONT}" font-size="{size}" '
            f'font-weight="{weight}" font-style="{style}" fill="{fill}">{escape(content)}</text>')


def line(points):
    return f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in points)}" fill="none" stroke="{LINE}" stroke-width="1.3"/>'


def arrow(points):
    """Open arrow head at the end of a route whose last stretch is horizontal or vertical."""
    (px, py), (x, y) = points[-2], points[-1]
    if abs(x - px) > abs(y - py):
        d = 11 if x > px else -11
        return line([(x - d, y + 5.5), (x, y), (x - d, y - 5.5)])
    d = 11 if y > py else -11
    return line([(x - 5.5, y - d), (x, y), (x + 5.5, y - d)])


def diamond(x, y, direction):
    """Filled diamond whose tip touches (x, y), pointing away along `direction`; returns it and its far end."""
    dx, dy = {'right': (1, 0), 'down': (0, 1), 'left': (-1, 0)}[direction]
    if dx:
        points = [(x, y), (x + 8 * dx, y + 6.5), (x + 16 * dx, y), (x + 8 * dx, y - 6.5)]
    else:
        points = [(x, y), (x - 6.5, y + 8 * dy), (x, y + 16 * dy), (x + 6.5, y + 8 * dy)]
    return f'<polygon points="{" ".join(f"{a:.1f},{b:.1f}" for a, b in points)}" fill="{LINE}"/>', points[2]


def containment(start, direction, route, label, label_at):
    """A containment from the edge point `start` of the container, through `route`, to the contained class."""
    shape, far = diamond(*start, direction)
    points = [far] + route
    return shape + line(points) + arrow(points) + text(*label_at, label, 12.5, fill=LINE)


def reference(route, label, label_at):
    return line(route) + arrow(route) + text(*label_at, label, 12.5, fill=LINE)


def inheritance(parent, children, bus_y):
    """Hollow triangle under the parent and a horizontal bus to the top of every subclass."""
    x, y = parent.cx, parent.bottom
    out = [f'<polygon points="{x:.1f},{y} {x - 9:.1f},{y + 14} {x + 9:.1f},{y + 14}" fill="#FFFFFF" stroke="{LINE}" stroke-width="1.3"/>',
           line([(x, y + 14), (x, bus_y)])]
    xs = [c.cx for c in children] + [x]
    out.append(line([(min(xs), bus_y), (max(xs), bus_y)]))
    out += [line([(c.cx, bus_y), (c.cx, c.y)]) for c in children]
    return ''.join(out)


def enum_column(package, x, y):
    boxes = []
    for name, literals in package.enums.items():
        box = Box(x, y, name, literals, 'enumeration', 'enum')
        boxes.append(box)
        y = box.bottom + 27
    return boxes


def class_box(package, name, x, y):
    return Box(x, y, name, package.attributes(name), 'abstract' if package.is_abstract(name) else None)


def write(package, boxes, connectors, origin):
    ox, oy = origin
    width = max(b.right for b in boxes) + 40 - ox
    height = max(b.bottom for b in boxes) + 37 - oy
    body = (f'<rect x="{ox}" y="{oy}" width="{width:.1f}" height="{height:.1f}" fill="#FFFFFF"/>'
            + text(ox + 70, -14, f'Metamodello {package.name} ({package.ns_uri})', 20, weight='bold')
            + ''.join(connectors) + ''.join(b.svg() for b in boxes))
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.1f}" height="{height:.1f}" '
           f'viewBox="{ox} {oy} {width:.1f} {height:.1f}">{body}</svg>\n')
    (HERE / 'img' / f'{package.name}.svg').write_text(svg, encoding='utf-8')
    print(f'img/{package.name}.svg  {width:.0f}x{height:.0f}')


def profile():
    p = Package('profile')
    learner = class_box(p, 'Learner', 40, 150)
    skill = class_box(p, 'Skill', 420, 20)
    need = class_box(p, 'Need', 420, 185)
    low_vision = class_box(p, 'LowVision', 420, 300)
    hand_span = class_box(p, 'HandSpanLimit', low_vision.right + 36, 300)
    pattern = class_box(p, 'PatternMastery', 420, 430)
    via = 330
    connectors = [
        containment((learner.right, learner.y + 10), 'right', [(via, learner.y + 10), (via, skill.cy), (skill.x, skill.cy)],
                    'skills  0..*', (via + 8, skill.cy - 6)),
        containment((learner.right, need.cy), 'right', [(need.x, need.cy)], 'needs  0..*', (via + 8, need.cy - 6)),
        containment((learner.right, learner.bottom - 16), 'right',
                    [(via, learner.bottom - 16), (via, pattern.cy), (pattern.x, pattern.cy)], 'patterns  0..*', (via + 8, pattern.cy - 6)),
        inheritance(need, [low_vision, hand_span], 268),
    ]
    classes = [learner, skill, need, low_vision, hand_span, pattern]
    write(p, classes + enum_column(p, max(b.right for b in classes) + 70, 20), connectors, (-30, -60))


def exercise():
    p = Package('exercise')
    spec = class_box(p, 'ExerciseSpec', 60, 40)
    constraint = class_box(p, 'Constraint', 430, 70.5)
    goal = class_box(p, 'QualityGoal', 60, 290)
    subclasses, x = [], 300
    for name in ['ContainsPattern', 'ExcludesPattern', 'RangeConstraint', 'RenderConstraint', 'SpanConstraint']:
        box = class_box(p, name, x, 240)
        subclasses.append(box)
        x = box.right + 34
    contains, excludes = subclasses[0], subclasses[1]
    pattern = class_box(p, 'Pattern', 400, 450)
    external = Box(60, 520, 'Learner', (), 'profile', 'external')
    hand = Box(subclasses[-1].x, 400, 'Hand', (), 'profile', 'external')
    span = subclasses[-1]
    connectors = [
        containment((spec.right, constraint.cy), 'right', [(constraint.x, constraint.cy)], 'constraints  0..*',
                    (spec.right + 22, constraint.cy - 6)),
        inheritance(constraint, subclasses, 207.75),
        containment((contains.cx, contains.bottom), 'down', [(contains.cx, 400), (pattern.x + 55, 400), (pattern.x + 55, pattern.y)],
                    'pattern  1', (contains.cx + 8, 370)),
        containment((excludes.cx, excludes.bottom), 'down', [(excludes.cx, 420), (pattern.x + 135, 420), (pattern.x + 135, pattern.y)],
                    'pattern  1', (excludes.cx + 8, 370)),
        containment((spec.x + 103, spec.bottom), 'down', [(spec.x + 103, goal.y)], 'qualityGoals  0..*', (spec.x + 111, 243)),
        reference([(spec.x, spec.y + 40), (30, spec.y + 40), (30, external.cy), (external.x, external.cy)], 'learner  0..1', (36, external.cy - 30)),
        # The type of an attribute of another metamodel, shown as a dependency on that classifier.
        f'<polyline points="{span.x + 40:.1f},{span.bottom} {span.x + 40:.1f},{hand.y}" fill="none" stroke="{LINE}" stroke-width="1.3" stroke-dasharray="5 3"/>',
        arrow([(span.x + 40, span.bottom), (span.x + 40, hand.y)]),
        text(span.x + 48, span.bottom + 30, 'tipo di hand', 12.5, fill=LINE),
    ]
    classes = [spec, constraint, goal, *subclasses, pattern, external, hand]
    write(p, classes + enum_column(p, max(b.right for b in classes) + 70, 20), connectors, (-10, -60))


if __name__ == '__main__':
    profile()
    exercise()
