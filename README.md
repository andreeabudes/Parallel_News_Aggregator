Acest proiect se ocupă cu procesarea în paralel aunui volum mare de articole extrase din fișiere JSON, apoi le filtrează, le clasifică și generează rapoarte statistice structurate.


##  Funcționalități Principale

* **Ștergere Duplicate:** Identifică și elimină automat articolele duplicate pe baza identificatorului `uuid` sau a titlului (`title`).
* **Clasificare Dinamică:** Grupează articolele în funcție de categorie și limbă, validându-le pe baza unor liste de referință predefinite (`categories.txt`, `languages.txt`).
* **Analiză Textuală (Keywords):** Analizează textul articolelor scrise în limba engleză. Cuvintele sunt convertite la litere mici, se elimină caracterele speciale și se contorizează aparițiile, ignorând o listă specifică de cuvinte de legătură (stop-words din `english_linking_words.txt`).
* **Generare de Statistici:** Creează un fișier `reports.txt` care include metrici precum numărul de duplicate găsite, autorul de top, categoria și limba dominantă, precum și cel mai frecvent cuvânt cheie.

## Paralelizare

Soluția împarte execuția în faze clar delimitate, sincronizate folosind bariere de tip `CyclicBarrier` pentru a asigura corectitudinea datelor înaintea trecerii la următoarea etapă. Munca este divizată echilibrat între thread-uri folosind indecși de start și end.

Cele 4 etape principale de execuție sunt:
1. **Citirea (Paralel):** Thread-urile parsează fișierele JSON utilizând biblioteca Jackson. Articolele sunt agregate în siguranță într-o structură `Collections.synchronizedList` pentru a preveni coliziunile la scriere (race conditions).
2. **Filtrarea (Secvențial):** Thread-ul principal (`id == 0`) elimină duplicatele. Pentru evidența numărului de duplicate s-a folosit clasa `AtomicInteger`, asigurând o numărare sigură.
3. **Procesarea și Indexarea (Paralel):** Articolele unice sunt reîmpărțite și procesate în paralel. Datele sunt agregate lock-free folosind structuri de tip `ConcurrentHashMap` cuplată cu `ConcurrentHashMap.newKeySet()`, permițând inserții concurente rapide.
4. **Scrierea Rezultatelor (Secvențial):** Thread-ul principal sortează colecțiile finale și scrie datele în sistemul de fișiere (`all_articles.txt`, fișiere pe limbi/categorii, `keywords_count.txt`, `reports.txt`).

## Performanță și Scalabilitate

Programul a fost testat pe o arhitectură cu procesor AMD Ryzen 7 7840HS (8 nuclee), 32GB RAM, folosind WSL (Windows 11 + Ubuntu 22.04 LTS).

Calculul accelerației (Speedup) s-a realizat folosind formula `S(p) = T(1) / T(p)`.
* Performanța crește odată cu numărul de thread-uri, obținând un **speedup de 3.02** pe 4 thread-uri.
* Eficiența se menține extrem de ridicată (98%) la trecerea de la 1 la 2 thread-uri.
* Limitele scalabilității sunt date de overhead-ul de sincronizare introdus de bariere și structurile concurente globale, precum și de variațiile de dimensiune ale fișierelor JSON (care fac ca divizarea muncii să nu fie complet uniformă ca volum efectiv de text).
