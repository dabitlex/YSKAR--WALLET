# Projektspezifische ProGuard-/R8-Regeln.
# Fuer eine Finanz-App: Modellklassen, die per Reflection serialisiert werden,
# hier explizit behalten, z.B.:
# -keep class com.yskar.wallet.model.** { *; }

# DER WAECHTER BRAUCHT SEINE DATENBANK - UND DEREN KONSTRUKTOR.
#
# WorkManager fuehrt Buch in einer Room-Datenbank. Room sucht die erzeugte
# Klasse (WorkDatabase_Impl) beim Start per NAMEN und ruft ihren Konstruktor
# ohne Argumente. Die Regeln, die die Bibliothek mitbringt, behalten die
# Klasse - aber R8 im vollen Modus behaelt mit einer Klasse nicht mehr von
# selbst ihren Konstruktor. Der war weg, und die Release-Fassung stuerzte
# bei JEDEM Start ab, noch bevor ein Bildschirm erschien:
#
#   NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
#
# Der Debug-Bau wird nicht verkleinert und hat den Fehler nie gezeigt;
# gefunden hat ihn erst die signierte Datei auf dem Geraet (14.11.2026).
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
