#!/usr/bin/env python3
"""Draws, in img/, how the domain models are layered: the classes of a metamodel, the types that a
model defines as data, and the elements of another model that are instances of those types.
Everything is read from the .ecore files and from the fixtures. Run it from this folder:

    python3 render_levels.py

PNG copies are made by svg_to_png.mjs, as for the class diagrams.
"""
import textwrap
import xml.etree.ElementTree as ET

from render_diagrams import FONT, HERE, LINE, Box, Package, arrow, class_box, text

FIXTURES = HERE.parent / 'fixtures' / 'giulia'
# What the derivation makes of Giulia's models is not a fixture: it is kept with the experiment,
# where her profile and intent are the case C2.
DERIVED = HERE.parent / 'experiment' / 'cases' / 'c2'
XSI = '{http://www.w3.org/2001/XMLSchema-instance}type'
XMI_ID = '{http://www.omg.org/XMI}id'
# The captions of the bands have a column of their own on the left, clear of the connectors.
GAP, LEFT, BAND_PAD = 28, 330, 0


def dashed(points):
    return (f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in points)}" fill="none" stroke="{LINE}" '
            f'stroke-width="1.3" stroke-dasharray="6 4"/>')


def instance_of(points, label=None, label_at=None):
    out = dashed(points) + arrow(points)
    return out + (text(*label_at, label, 12.5, fill=LINE, style='italic') if label else '')


def band(y, height, width, caption, note):
    lines = textwrap.wrap(note, 38)
    return (f'<rect x="20" y="{y}" width="{width - 40:.1f}" height="{height:.1f}" rx="8" fill="#F7F8FA" stroke="#D5DAE3"/>'
            + ''.join(text(36, y + 28 + i * 20, line, 15, weight='bold') for i, line in enumerate(textwrap.wrap(caption, 30)))
            + ''.join(text(36, y + 28 + (len(textwrap.wrap(caption, 30)) + i) * 19 + 4, line, 12.5, fill='#4D566A')
                      for i, line in enumerate(lines)))


def render(name, title, type_class, instance_class, types, instances, captions):
    """`types` are (id, heading, rows); `instances` are (heading, rows, id of their type).
    The class boxes are placed here: only their content is given."""
    top = 70
    type_class.y = instance_class.y = top + BAND_PAD + 20
    class_bottom = max(type_class.bottom, instance_class.bottom)
    band1 = max(class_bottom + 24 - top, 110)

    y2 = top + band1 + 46
    boxes, by_id, x = [], {}, LEFT
    for type_id, heading, rows in types:
        box = Box(x, y2 + BAND_PAD + 20, heading, rows, type_class.title, 'type')
        boxes.append(box)
        by_id[type_id] = box
        x = box.right + GAP
    band2 = max(b.bottom for b in boxes) + 24 - y2

    y3 = y2 + band2 + 46
    placed, taken = [], {}
    for heading, rows, type_id in instances:
        parent = by_id[type_id]
        # an instance stands under its type; a second one of the same type goes below the first
        y = taken.get(type_id, y3 + BAND_PAD + 20)
        box = Box(parent.x, y, heading, rows, instance_class.title, 'instance')
        # one wider than its type must not run into the instance of the next type
        clash = [b.right for b, _ in placed if b.y < box.bottom and box.y < b.bottom and b.right + GAP > box.x]
        if clash:
            box.x = max(clash) + GAP
        taken[type_id] = box.bottom + 18
        placed.append((box, parent))
    band3 = max(b.bottom for b, _ in placed) + 46 - y3

    # the class the types are instances of is centred over them, the other one is on the right
    type_class.x = (boxes[0].x + boxes[-1].right) / 2 - type_class.w / 2
    right = max(boxes[-1].right, max(b.right for b, _ in placed)) + 70
    instance_class.x = right - instance_class.w / 2
    width = instance_class.right + 60
    height = y3 + band3 + 30

    bus = y2 - 23
    lines = [dashed([(boxes[0].cx, bus), (boxes[-1].cx, bus)])]
    lines += [dashed([(b.cx, b.y), (b.cx, bus)]) for b in boxes]
    lines.append(instance_of([(type_class.cx, bus), (type_class.cx, type_class.bottom)], 'istanza di', (type_class.cx + 10, bus - 6)))
    for box, parent in placed:
        if box.y - parent.bottom > 30:
            # straight up, at a point that is over the instance and under its type
            x = min(max(box.cx, parent.x + 20), parent.right - 20, box.right - 20)
            lines.append(instance_of([(x, box.y), (x, parent.bottom)], captions['link'], (x + 10, y3 - 18)))
    low = y3 + band3 - 20
    lines.append(dashed([(min(b.cx for b, _ in placed) - 40, low), (right, low)]))
    lines += [dashed([(b.cx - 40, b.bottom), (b.cx - 40, low)]) for b, _ in placed if b.bottom == max(
        p.bottom for p, q in placed if q is _)]
    lines.append(instance_of([(right, low), (right, instance_class.bottom)], 'istanza di', (right + 10, y2 + 30)))

    body = (f'<rect x="0" y="0" width="{width:.1f}" height="{height:.1f}" fill="#FFFFFF"/>'
            + text(20, 40, title, 20, weight='bold')
            + band(top, band1, width, *captions['classes'])
            + band(y2, band2, width, *captions['types'])
            + band(y3, band3, width, *captions['instances'])
            + ''.join(lines)
            + ''.join(b.svg() for b in [type_class, instance_class, *boxes, *[b for b, _ in placed]]))
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.1f}" height="{height:.1f}" '
           f'viewBox="0 0 {width:.1f} {height:.1f}" font-family="{FONT}">{body}</svg>\n')
    (HERE / 'img' / f'levels-{name}.svg').write_text(svg, encoding='utf-8')
    print(f'img/levels-{name}.svg  {width:.0f}x{height:.0f}')


def target_id(element, reference):
    return element.find(reference).get('href').split('#')[1]


def needs():
    catalog = ET.parse(FIXTURES / 'default.needs.xmi').getroot()
    profile = ET.parse(FIXTURES / 'giulia.profile.xmi').getroot()
    parameters, types = {}, []
    for t in catalog.findall('types'):
        rows = []
        for p in t.findall('parameters'):
            parameters[p.get(XMI_ID)] = p.get('name')
            rows.append(f"{p.get('name')} : {p.get('kind', 'INTEGER')}" + ('' if p.get('required') != 'false' else ' [0..1]'))
        for e in t.findall('effects'):
            rows.append('→ ' + (e.get('constraintClass') if e.get(XSI) == 'nd:ConstraintEffect' else f"ExerciseSpec.{e.get('feature')}"))
        types.append((t.get(XMI_ID), t.get('name'), rows))
    instances = []
    for n in profile.findall('needs'):
        rows = [f"{parameters[target_id(s, 'parameter')]} = {s.get('value')}" for s in n.findall('slots')]
        instances.append((n.get(XMI_ID), rows, target_id(n, 'type')))
    render('needs', 'Bisogni: dal metamodello ai bisogni di un allievo',
           class_box(Package('needs'), 'NeedType', 0, 0), class_box(Package('profile'), 'Need', 0, 0), types, instances, {
               'classes': ('Metamodelli', 'Le classi: fisse, cambiarle richiede di cambiare il codice.'),
               'types': ('Catalogo dei bisogni (default.needs)',
                         'I tipi: dati di un modello. Ognuno dichiara i parametri e, con →, che cosa fa alla specifica.'),
               'instances': (f"Profilo di {profile.get('name')} (giulia.profile)",
                             'I bisogni di un allievo: ognuno indica il suo tipo e ne valorizza i parametri.'),
               'link': 'type',
           })


def quality():
    model = ET.parse(FIXTURES / 'piano.quality.xmi').getroot()
    spec = ET.parse(DERIVED / 'c2.intent.derived.xmi').getroot()
    # Which rule put each goal in the spec says who wanted it: read from the trace of the derivation.
    reasons = {'CopyQualityGoals': 'chiesto dalla docente', 'ApplyImplicitGoals': 'implicito nel modello di qualità'}
    wanted = {}
    for link in ET.parse(DERIVED / 'c2.intent.weaving.xmi').getroot().findall('links'):
        if link.get('rule') in reasons:
            wanted[target_id(link, 'target')] = reasons[link.get('rule')]
    types = []
    for d in model.findall('dimensions'):
        rows = [f"metric = {d.get('metric')}"]
        rows += [f"{b.get('level', 'LOW')} : {b.get('min', '0.0')} – {b.get('max')}" for b in d.findall('bands')]
        if d.get('implicit') == 'true':
            rows.append(f"implicit, {d.get('defaultLevel', 'LOW')}")
        types.append((d.get(XMI_ID), d.get('name'), rows))
    instances = [(g.get(XMI_ID), [f"level = {g.get('level', 'LOW')}", wanted.get(g.get(XMI_ID), '')], target_id(g, 'dimension'))
                 for g in spec.findall('qualityGoals')]
    render('quality', 'Qualità: dal metamodello agli obiettivi di un esercizio',
           class_box(Package('quality'), 'QualityDimension', 0, 0), class_box(Package('exercise'), 'QualityGoal', 0, 0),
           types, instances, {
               'classes': ('Metamodelli', 'Le classi: fisse, cambiarle richiede di cambiare il codice.'),
               'types': ('Modello di qualità (piano.quality)',
                         'Le dimensioni: dati di un modello. Ognuna dichiara la metrica con cui si misura e che cosa vuol dire ogni livello.'),
               'instances': ('Specifica derivata per Giulia',
                             'Gli obiettivi di un esercizio: ognuno indica una dimensione e il livello. '
                             'Uno viene dall\'intento, l\'altro lo aggiunge la derivazione.'),
               'link': 'dimension',
           })


if __name__ == '__main__':
    needs()
    quality()
