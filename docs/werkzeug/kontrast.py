def lin(c):
    c = c/255
    return c/12.92 if c <= 0.03928 else ((c+0.055)/1.055)**2.4
def L(h):
    h = h.lstrip('#')
    r,g,b = (int(h[i:i+2],16) for i in (0,2,4))
    return 0.2126*lin(r)+0.7152*lin(g)+0.0722*lin(b)
def ratio(a,b):
    la,lb = L(a),L(b)
    hi,lo = max(la,lb),min(la,lb)
    return (hi+0.05)/(lo+0.05)

GRUND='#080D18'; KARTE='#111827'; FLAECHE='#172235'
TEXT='#F4F7FC'; LEISE='#8D9BB0'; BLAU='#2F7BFF'; GRUEN='#32C98A'; ROT='#F05D68'

pairs = [
 ('Text        auf Grund',   TEXT, GRUND), ('Text        auf Karte',   TEXT, KARTE),
 ('Text        auf Flaeche', TEXT, FLAECHE),
 ('Sekundaer   auf Grund',   LEISE, GRUND), ('Sekundaer   auf Karte',   LEISE, KARTE),
 ('Sekundaer   auf Flaeche', LEISE, FLAECHE),
 ('Blau        auf Grund',   BLAU, GRUND), ('Blau        auf Karte',   BLAU, KARTE),
 ('Blau        auf Flaeche', BLAU, FLAECHE),
 ('Text        auf Blau',    TEXT, BLAU),
 ('Gruen       auf Grund',   GRUEN, GRUND), ('Gruen       auf Karte',   GRUEN, KARTE),
 ('Gruen       auf Flaeche', GRUEN, FLAECHE),
 ('Rot         auf Grund',   ROT, GRUND), ('Rot         auf Karte',   ROT, KARTE),
 ('Rot         auf Flaeche', ROT, FLAECHE),
]
print(f"{'Paar':26} {'Ratio':>6}  AA-Text  AA-Gross/UI")
print('-'*58)
for name,fg,bg in pairs:
    r = ratio(fg,bg)
    aa   = 'OK ' if r>=4.5 else 'NEIN'
    aa18 = 'OK ' if r>=3.0 else 'NEIN'
    print(f"{name:26} {r:6.2f}  {aa:7}  {aa18}")
