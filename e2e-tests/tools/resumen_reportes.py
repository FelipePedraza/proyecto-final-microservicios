"""Junta los resultados de todos los servicios en una sola pagina HTML.

Lee lo que ya generaron las pruebas (no corre nada):
  - JUnit XML de surefire: vacaciones, auth, gateway y e2e
  - .trx y cobertura Cobertura de empleado-service
  - jacoco.xml de vacaciones y auth
Escribe e2e-tests/reports/resumen.html

Uso (desde la raiz del repo):  python e2e-tests/tools/resumen_reportes.py
"""
import glob
import html
import os
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict
from datetime import datetime

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SALIDA = os.path.join(RAIZ, "e2e-tests", "reports", "resumen.html")


def ruta(*partes):
    return os.path.join(RAIZ, *partes)


def esc(x):
    return html.escape(str(x))


def segundos(txt):
    """'00:00:01.2345' -> 1.23"""
    try:
        h, m, s = txt.split(":")
        return int(h) * 3600 + int(m) * 60 + float(s)
    except (ValueError, AttributeError):
        return 0.0


def leer_surefire(carpeta):
    """Devuelve {clase: [(nombre, estado, segundos, mensaje)]}"""
    clases = OrderedDict()
    for f in sorted(glob.glob(os.path.join(carpeta, "TEST-*.xml"))):
        for tc in ET.parse(f).getroot().iter("testcase"):
            estado, msg = "ok", ""
            for hijo in tc:
                if hijo.tag in ("failure", "error"):
                    estado, msg = "fail", (hijo.get("message") or "")[:300]
                elif hijo.tag == "skipped":
                    estado = "skip"
            clase = tc.get("classname", "?").split(".")[-1]
            clases.setdefault(clase, []).append(
                (tc.get("name", "?"), estado, float(tc.get("time") or 0), msg))
    return clases


def leer_trx(carpeta):
    archivos = sorted(glob.glob(os.path.join(carpeta, "*.trx")), key=os.path.getmtime)
    if not archivos:
        return OrderedDict()
    raiz = ET.parse(archivos[-1]).getroot()
    ns = {"t": raiz.tag.split("}")[0].strip("{")}
    clases = OrderedDict()
    for r in raiz.iterfind(".//t:UnitTestResult", ns):
        nombre = r.get("testName", "?")
        clase, _, metodo = nombre.rpartition(".")
        outcome = r.get("outcome")
        estado = "ok" if outcome == "Passed" else "skip" if outcome == "NotExecuted" else "fail"
        msg = ""
        m = r.find(".//t:Message", ns)
        if m is not None and m.text:
            msg = m.text[:300]
        clases.setdefault(clase.split(".")[-1], []).append(
            (metodo, estado, segundos(r.get("duration")), msg))
    return clases


def cobertura_jacoco(archivo):
    if not os.path.exists(archivo):
        return None
    raiz = ET.parse(archivo).getroot()
    out = {}
    for c in raiz.findall("counter"):
        out[c.get("type")] = (int(c.get("missed")), int(c.get("covered")))
    return out


def cobertura_cobertura(carpeta):
    archivos = glob.glob(os.path.join(carpeta, "**", "coverage.cobertura.xml"), recursive=True)
    if not archivos:
        return None
    raiz = ET.parse(max(archivos, key=os.path.getmtime)).getroot()
    out = {}
    lv, lc = int(raiz.get("lines-valid", 0)), int(raiz.get("lines-covered", 0))
    bv, bc = int(raiz.get("branches-valid", 0)), int(raiz.get("branches-covered", 0))
    out["LINE"] = (lv - lc, lc)
    out["BRANCH"] = (bv - bc, bc)
    return out


def pct(par):
    if not par:
        return None
    falladas, cubiertas = par
    total = falladas + cubiertas
    return None if total == 0 else 100.0 * cubiertas / total


def barra(valor):
    if valor is None:
        return '<span class="muted">sin datos</span>'
    tono = "good" if valor >= 70 else "mid" if valor >= 50 else "bad"
    return (f'<div class="bar"><span class="{tono}" style="width:{valor:.1f}%"></span></div>'
            f'<b>{valor:.1f} %</b>')


def tarjeta(titulo, sub, clases, cob, enlaces):
    total = sum(len(v) for v in clases.values())
    ok = sum(1 for v in clases.values() for t in v if t[1] == "ok")
    fail = sum(1 for v in clases.values() for t in v if t[1] == "fail")
    skip = total - ok - fail
    tiempo = sum(t[2] for v in clases.values() for t in v)
    estado = "fail" if fail else "ok" if total else "skip"
    p = []
    p.append(f'<section class="card {estado}"><header><div><h2>{esc(titulo)}</h2>'
             f'<p class="muted">{esc(sub)}</p></div>'
             f'<span class="pill {estado}">{"Con fallos" if fail else "Todo bien" if total else "Sin resultados"}</span></header>')
    if total:
        p.append(f'<div class="stats"><div><b>{total}</b><span>pruebas</span></div>'
                 f'<div class="g"><b>{ok}</b><span>pasaron</span></div>'
                 f'<div class="r"><b>{fail}</b><span>fallaron</span></div>'
                 f'<div class="y"><b>{skip}</b><span>omitidas</span></div>'
                 f'<div><b>{tiempo:.1f}s</b><span>tiempo</span></div></div>')
    if cob:
        l, r = pct(cob.get("LINE")), pct(cob.get("BRANCH"))
        p.append(f'<div class="cov"><div><span>Líneas</span>{barra(l)}</div>'
                 f'<div><span>Ramas</span>{barra(r)}</div></div>')
    if enlaces:
        p.append('<p class="links">' + " · ".join(
            f'<a href="{esc(h)}">{esc(t)}</a>' for t, h in enlaces) + "</p>")
    for clase, tests in clases.items():
        malas = sum(1 for t in tests if t[1] == "fail")
        abierto = " open" if malas else ""
        p.append(f'<details{abierto}><summary><span>{esc(clase)}</span>'
                 f'<em>{len(tests)} pruebas{f" · {malas} con fallo" if malas else ""}</em></summary><ul>')
        for nombre, st, seg, msg in tests:
            icono = {"ok": "✓", "fail": "✕", "skip": "–"}[st]
            extra = f'<small>{esc(msg)}</small>' if msg else ""
            p.append(f'<li class="{st}"><i>{icono}</i><span>{esc(nombre)}{extra}</span>'
                     f'<time>{seg:.2f}s</time></li>')
        p.append("</ul></details>")
    if not total:
        p.append('<p class="muted">No encontré resultados. Corre las pruebas de este servicio primero.</p>')
    p.append("</section>")
    return "".join(p)


CSS = """
:root{--bg:#f5f6f8;--card:#fff;--tx:#1b1f24;--mu:#6b7280;--ln:#e5e7eb;--ok:#15803d;--okb:#dcfce7;--ko:#b91c1c;--kob:#fee2e2;--wa:#a16207;--wab:#fef3c7;--ac:#2563eb}
@media(prefers-color-scheme:dark){:root{--bg:#0f1216;--card:#171b21;--tx:#e6e8eb;--mu:#98a1ad;--ln:#262c35;--ok:#4ade80;--okb:#14301f;--ko:#f87171;--kob:#3a1818;--wa:#fbbf24;--wab:#3a2e10;--ac:#6ea8ff}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--tx);font:15px/1.5 system-ui,Segoe UI,sans-serif}
main{max-width:1100px;margin:0 auto;padding:32px 16px 64px}
h1{margin:0 0 4px;font-size:26px}h2{margin:0;font-size:18px}.muted{color:var(--mu);margin:0}
.top{display:flex;flex-wrap:wrap;justify-content:space-between;gap:12px;margin-bottom:24px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(480px,1fr));gap:16px}
@media(max-width:560px){.grid{grid-template-columns:1fr}}
.card{background:var(--card);border:1px solid var(--ln);border-radius:12px;padding:18px;border-top:4px solid var(--ok)}
.card.fail{border-top-color:var(--ko)}.card.skip{border-top-color:var(--wa)}
.card header{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:12px}
.pill{font-size:12px;padding:3px 10px;border-radius:99px;white-space:nowrap;background:var(--okb);color:var(--ok)}
.pill.fail{background:var(--kob);color:var(--ko)}.pill.skip{background:var(--wab);color:var(--wa)}
.stats{display:grid;grid-template-columns:repeat(5,1fr);gap:8px;margin-bottom:12px}
.stats div{background:var(--bg);border-radius:8px;padding:8px;text-align:center}
.stats b{display:block;font-size:20px}.stats span{font-size:12px;color:var(--mu)}
.stats .g b{color:var(--ok)}.stats .r b{color:var(--ko)}.stats .y b{color:var(--wa)}
.cov{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:10px}
.cov span{font-size:12px;color:var(--mu);display:block}
.bar{height:8px;background:var(--ln);border-radius:99px;overflow:hidden;margin:4px 0}
.bar span{display:block;height:100%;font-size:0}.bar .good{background:var(--ok)}.bar .mid{background:var(--wa)}.bar .bad{background:var(--ko)}
.links{margin:6px 0 10px;font-size:13px}a{color:var(--ac)}
details{border-top:1px solid var(--ln);padding:6px 0}
summary{cursor:pointer;display:flex;justify-content:space-between;gap:8px;padding:4px 0;font-weight:600;font-size:14px}
summary em{font-style:normal;font-weight:400;color:var(--mu);font-size:12px}
ul{list-style:none;margin:4px 0 8px;padding:0}
li{display:grid;grid-template-columns:20px 1fr auto;gap:8px;padding:3px 0;font-size:13px;word-break:break-word}
li i{font-style:normal;font-weight:700;color:var(--ok)}li.fail i{color:var(--ko)}li.skip i{color:var(--wa)}
li small{display:block;color:var(--ko)}li time{color:var(--mu);font-size:12px}
"""


def main():
    tarjetas = [
        tarjeta("vacaciones-service", "JUnit 5 + Testcontainers",
                leer_surefire(ruta("vacaciones-service", "target", "surefire-reports")),
                cobertura_jacoco(ruta("vacaciones-service", "target", "site", "jacoco", "jacoco.xml")),
                [("Cobertura JaCoCo", "../../vacaciones-service/target/site/jacoco/index.html")]),
        tarjeta("auth-service", "JUnit 5 + Spring Boot",
                leer_surefire(ruta("auth-service", "target", "surefire-reports")),
                cobertura_jacoco(ruta("auth-service", "target", "site", "jacoco", "jacoco.xml")),
                [("Cobertura JaCoCo", "../../auth-service/target/site/jacoco/index.html")]),
        tarjeta("empleado-service", "xUnit + coverlet",
                leer_trx(ruta("empleado-service", "empleadoService.Tests", "TestResults")),
                cobertura_cobertura(ruta("empleado-service", "empleadoService.Tests", "TestResults")), []),
        tarjeta("gateway-service", "JUnit 5 + Spring",
                leer_surefire(ruta("gateway-service", "target", "surefire-reports")), None, []),
        tarjeta("e2e-tests", "Cucumber + Rest Assured contra el stack real",
                leer_surefire(ruta("e2e-tests", "target", "surefire-reports")), None,
                [("Informe Cucumber", "cucumber.html")]),
    ]
    ahora = datetime.now().strftime("%d/%m/%Y %H:%M")
    pagina = (f'<!doctype html><html lang="es"><head><meta charset="utf-8">'
              f'<meta name="viewport" content="width=device-width,initial-scale=1">'
              f'<title>Resumen de pruebas</title><style>{CSS}</style></head><body><main>'
              f'<div class="top"><div><h1>Resumen de pruebas</h1>'
              f'<p class="muted">Proyecto final de microservicios</p></div>'
              f'<p class="muted">Generado el {ahora}</p></div>'
              f'<div class="grid">{"".join(tarjetas)}</div></main></body></html>')
    os.makedirs(os.path.dirname(SALIDA), exist_ok=True)
    with open(SALIDA, "w", encoding="utf-8") as f:
        f.write(pagina)
    print("Listo:", SALIDA)


if __name__ == "__main__":
    sys.exit(main())
