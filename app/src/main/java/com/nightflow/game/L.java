package com.nightflow.game;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Explicit language choice, including every menu, garage, race result and HUD label. */
public final class L {
    public static final String[] LANGUAGES={"Bosanski","English","Deutsch","Hrvatski"};
    static final Map<String,String[]> TEXT=new LinkedHashMap<>();
    private static void a(String key,String bs,String en,String de){TEXT.put(key,new String[]{bs,en,de,bs});}
    static {
        a("tag","OTVORENA CESTA / TVOJ RITAM","OPEN ROAD / YOUR RHYTHM","OFFENE STRASSE / DEIN RHYTHMUS");
        a("tagline","Tvoj auto. Tvoja sljedeća vožnja.","Your car. Your next drive.","Dein Auto. Deine nächste Fahrt.");
        a("drive","VOZI   ›","DRIVE   ›","FAHREN   ›");
        a("settings","POSTAVKE","SETTINGS","EINSTELLUNGEN");
        a("garage","GARAŽA / TUNING","GARAGE / TUNING","GARAGE / TUNING");
        a("modes","ODABERI VOŽNJU","CHOOSE YOUR DRIVE","FAHRT AUSWÄHLEN");
        a("mode0","ZEN","ZEN","ZEN");a("mode1","KLASIK","CLASSIC","KLASSISCH");
        a("mode2","HARDCORE","HARDCORE","HARDCORE");a("mode3","UTRKA PROTIV AI","AI RACE","KI-RENNEN");
        a("mode4","VOŽNJA NA VRIJEME","TIME ATTACK","ZEITFAHREN");
        a("desc0","Lagana vožnja. Sudar te uspori, vožnja se nastavlja.","Relaxed cruising. Recover after every collision.","Entspannt fahren. Nach Kollisionen geht es weiter.");
        a("desc1","Preticanja, bodovi i jedan pokušaj.","Overtakes, points and one chance.","Überholen, Punkte und ein Versuch.");
        a("desc2","Gušći saobraćaj. Jedan sudar završava vožnju.","Dense traffic. One collision ends the run.","Dichter Verkehr. Eine Kollision beendet die Fahrt.");
        a("desc3","Tri AI vozača. Stigni prvi do cilja; sudar +2 s.","Three AI drivers. Finish first; collisions add 2 s.","Drei KI-Fahrer. Werde Erster; Kollisionen kosten 2 s.");
        a("desc4","Prođi kroz kapije; promašaj +3 s. Utrkuj se sa svojim rekordom.","Pass the gates; a miss adds 3 s. Race your best ghost.","Passiere die Tore; verpasst: +3 s. Fahre gegen deinen Rekord.");
        a("record","REKORD","BEST","REKORD");a("soundtrack","ORIGINALNA MUZIKA / 78 BPM","ORIGINAL SOUNDTRACK / 78 BPM","ORIGINAL-SOUNDTRACK / 78 BPM");
        a("pause","PAUZA","PAUSED","PAUSE");a("waiting","Vožnja te čeka.","Your drive is waiting.","Deine Fahrt wartet.");
        a("resume","NASTAVI","RESUME","WEITER");a("calibrate","PORAVNAJ VOLAN","CALIBRATE STEERING","LENKUNG KALIBRIEREN");
        a("calibrated","Volan je poravnat.","Steering calibrated.","Lenkung kalibriert.");
        a("menu","GLAVNI MENI","MAIN MENU","HAUPTMENÜ");a("again","PONOVO VOZI","DRIVE AGAIN","NOCHMAL FAHREN");
        a("ended","VOŽNJA ZAVRŠENA","RUN COMPLETE","FAHRT BEENDET");a("finished","CILJ!","FINISH!","ZIEL!");
        a("result","%.2f km · %d preticanja","%.2f km · %d overtakes","%.2f km · %d Überholmanöver");
        a("place","MJESTO %d / 4","POSITION %d / 4","PLATZ %d / 4");a("time","VRIJEME","TIME","ZEIT");
        a("penalty","KAZNA +%.0f s","PENALTY +%.0f s","STRAFE +%.0f s");
        a("newbest","NOVI REKORD / GHOST SPREMLJEN","NEW BEST / GHOST SAVED","NEUER REKORD / GHOST GESPEICHERT");
        a("ghostHint","Rekord se čuva zasebno za svaki auto, tuning i dužinu.","Records are separate for each car, tune and distance.","Rekorde gelten pro Auto, Tuning und Streckenlänge.");
        a("language","Jezik / Language","Language","Sprache");a("image","SLIKA","VISUALS","GRAFIK");
        a("graphics","Grafika","Graphics","Grafikqualität");a("quality0","Štednja","Battery saver","Sparmodus");
        a("quality1","Uravnoteženo","Balanced","Ausgewogen");a("quality2","Visoko","High","Hoch");a("quality3","Ultra","Ultra","Ultra");
        a("fps","Ograničenje FPS-a","Frame rate limit","Bildratenlimit");
        a("fpsHint","Stvarni FPS zavisi od uređaja i odabranih postavki.","Actual FPS depends on the device and settings.","Die tatsächliche Bildrate hängt von Gerät und Einstellungen ab.");
        a("bloom","Sjaj svjetala","Light bloom","Lichtschein");a("shadows","Sjene","Shadows","Schatten");
        a("night","Noć","Night","Nacht");a("wet","Mokar asfalt i odsjaji","Wet road reflections","Nasse Fahrbahn und Reflexionen");
        a("showFps","Prikaži FPS","Show FPS","FPS anzeigen");a("camera","Kamera","Camera","Kamera");
        a("camera0","Blisko praćenje","Close chase","Nahe Verfolgerkamera");a("camera1","Daleko praćenje","Far chase","Ferne Verfolgerkamera");a("camera2","Pogled s haube","Hood view","Motorhaube");
        a("fov","Vidno polje","Field of view","Sichtfeld");a("controlHeading","UPRAVLJANJE","CONTROLS","STEUERUNG");
        a("controls","Kontrole","Controls","Steuerung");a("control0","Naginjanje telefona","Tilt steering","Neigungssteuerung");
        a("control1","Strelice na ekranu","Touch arrows","Pfeiltasten");a("sensorMissing","Senzor nije dostupan; koristi strelice.","No tilt sensor; use touch arrows.","Kein Neigungssensor; verwende Pfeiltasten.");
        a("sensitivity","Osjetljivost","Sensitivity","Empfindlichkeit");a("invert","Obrni naginjanje","Invert tilt","Neigung umkehren");
        a("autoGas","Automatski gas / opuštena vožnja","Auto throttle / relaxed driving","Automatisch Gas geben");
        a("controlsHint","Drži gas ili kočnicu. Bez automatskog gasa auto usporava kad pustiš pedalu.","Hold either pedal. With auto throttle off, releasing the accelerator slows the car.","Pedal gedrückt halten. Ohne Automatik rollt das Auto beim Loslassen aus.");
        a("audio","ZVUK","AUDIO","AUDIO");a("music","Muzika","Music","Musik");a("effects","Motor i efekti","Engine and effects","Motor und Effekte");
        a("vibration","Vibracija pri sudaru","Collision vibration","Vibration bei Kollision");a("save","SPREMI","SAVE","SPEICHERN");a("cancel","ODUSTANI","CANCEL","ABBRECHEN");
        a("traffic","Saobraćaj / jačina AI-ja","Traffic / AI strength","Verkehr / KI-Stärke");
        a("density0","Manje / lagano","Light / easy","Wenig / leicht");a("density1","Srednje","Medium","Mittel");a("density2","Više / teško","Heavy / hard","Viel / schwer");
        a("length","Dužina utrke","Race distance","Renndistanz");
        a("workshop","TUNING RADIONICA","TUNING WORKSHOP","TUNING-WERKSTATT");
        a("rotate","Povuci da okreneš auto","Drag to rotate the car","Ziehen, um das Auto zu drehen");
        a("car","Auto","Car","Auto");a("paint","Boja","Paint","Lack");
        a("paint0","Menta","Mint","Mint");a("paint1","Koraljna","Coral","Koralle");a("paint2","Biserna","Pearl","Perlmutt");
        a("paint3","Ljubičasta","Violet","Violett");a("paint4","Zlatna","Gold","Gold");a("paint5","Ponoćna","Midnight","Mitternacht");
        a("engine","Motor","Engine","Motor");a("stage0","Serijski","Stock","Serie");a("stage1","Stage 1 / ECU","Stage 1 / ECU","Stage 1 / ECU");
        a("stage2","Stage 2 / turbo","Stage 2 / turbo","Stage 2 / Turbo");a("stage3","Stage 3 / race","Stage 3 / race","Stage 3 / Race");
        a("gearbox","Mjenjač","Transmission","Getriebe");a("gearbox0","6 brzina / serijski","6-speed / stock","6-Gang / Serie");
        a("gearbox1","7 brzina / sport","7-speed / sport","7-Gang / Sport");a("gearbox2","8 brzina / race","8-speed / race","8-Gang / Race");
        a("tyres","Gume","Tyres","Reifen");a("tyres0","Cestovne","Road","Straße");a("tyres1","Sportske","Sport","Sport");a("tyres2","Poluslik","Semi-slick","Semi-Slick");
        a("kit","Karoserija","Body kit","Karosserie-Kit");a("kit0","Serijska","Stock","Serie");a("kit1","Sport / spojler","Sport / spoiler","Sport / Spoiler");
        a("kit2","Widebody / race","Widebody / race","Widebody / Race");a("rims","Felge","Wheels","Felgen");
        a("rims0","Brušeni aluminij","Brushed alloy","Aluminium");a("rims1","Bronzane","Bronze","Bronze");a("rims2","Crne","Black","Schwarz");
        a("neon","Podno osvjetljenje","Underglow","Unterbodenlicht");a("applyTune","SPREMI TUNING","SAVE TUNE","TUNING SPEICHERN");
        a("tuneHint","Svi dijelovi su otključani. Motor, mjenjač i gume mijenjaju vožnju.","All parts are unlocked. Engine, gearbox and tyres change the handling and performance.","Alle Teile sind frei. Motor, Getriebe und Reifen ändern das Fahrverhalten.");
        a("power","KS","HP","PS");a("shift","promjena brzine","shift time","Schaltzeit");a("grip","prianjanje","grip","Grip");
        a("score","BODOVI","SCORE","PUNKTE");a("tilt","NAGINJANJE","TILT","NEIGUNG");
        a("brake","KOČNICA","BRAKE","BREMSE");a("gas","GAS","GAS","GAS");
        a("readyTilt","DRŽI TELEFON UDOBNO · NAGINJI LIJEVO / DESNO","HOLD COMFORTABLY · TILT LEFT / RIGHT","HANDY BEQUEM HALTEN · LINKS / RECHTS NEIGEN");
        a("readyTouch","STRELICAMA UPRAVLJAŠ · DRŽI PEDALE","STEER WITH ARROWS · HOLD THE PEDALS","MIT PFEILEN LENKEN · PEDALE HALTEN");
        a("recover","NASTAVI VOZITI","KEEP DRIVING","WEITERFAHREN");a("near","TIJESNO!","CLOSE CALL!","KNAPP!");
        a("gate","KAPIJA","GATE","TOR");a("gateMiss","PROMAŠENA KAPIJA +3 s","MISSED GATE +3 s","TOR VERPASST +3 s");
        a("firstGhost","POSTAVI PRVI REKORD","SET YOUR FIRST GHOST","SETZE DEINEN ERSTEN REKORD");
        a("graphicsError","Grafika se nije pokrenula","Graphics could not start","Grafik konnte nicht starten");
        a("graphicsNeed","Potreban je OpenGL ES 3.0. Detalj: ","OpenGL ES 3.0 is required. Detail: ","OpenGL ES 3.0 erforderlich. Detail: ");
        a("close","Zatvori","Close","Schließen");
        TEXT.get("traffic")[3]="Promet / jačina AI-ja";TEXT.get("desc2")[3]="Gušći promet. Jedan sudar završava vožnju.";
        TEXT.get("music")[3]="Glazba";TEXT.get("soundtrack")[3]="ORIGINALNA GLAZBA / 78 BPM";
        TEXT.get("result")[3]="%.2f km · %d pretjecanja";TEXT.get("desc1")[3]="Pretjecanja, bodovi i jedan pokušaj.";
        TEXT.get("length")[3]="Duljina utrke";TEXT.get("tyres2")[3]="Poluslik";
    }
    public static String t(Settings s,String key,Object... args){
        String[] row=TEXT.get(key);if(row==null)throw new IllegalArgumentException("Missing translation: "+key);
        String str=row[s.language];return args.length==0?str:String.format(Locale.ROOT,str,args);
    }
    public static String[] options(Settings s,String prefix,int count){String[] a=new String[count];for(int i=0;i<count;i++)a[i]=t(s,prefix+i);return a;}
    public static String clock(float seconds){int ms=Math.round(Math.max(0,seconds)*1000);return String.format(Locale.ROOT,"%02d:%02d.%03d",ms/60000,(ms/1000)%60,ms%1000);}
}
