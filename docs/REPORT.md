# Izveštaj: Migracija monolita na mikroservise

Radna beleška koja prati odluke tokom projekta (kurs *Razvoj poslovnih sistema*, prof. Gordana Rakić). Sadržaj se kasnije prenosi u README.

## 1. Polazno stanje

Monolitna Spring Boot aplikacija (`backend/demo`) za prodaju pozorišnih ulaznica, sa 4 domenska paketa:

- `plays` — repertoar, predstave, izvođenja, sale, režiseri, scenografi
- `spectators` — gledaoci (identifikovani JMBG-om)
- `ticket_agents` — radnici/blagajnici
- `tickets` — prodaja ulaznica, slanje mejla potvrde

## 2. Analiza alatom (Spring Modulith)

Korišćen [Spring Modulith](https://spring.io/projects/spring-modulith) (`spring-modulith-starter-core` 1.2.13) da se automatski izvedu granice modula direktno iz paketne strukture i Java import-a — bez ručno pisane arhitekture. Rezultat je u repou `ticketing-app-v2` (`backend/demo/src/test/java/pozoriste1/demo/ModularityTests.java`, izlaz u `target/spring-modulith-docs/*.puml`).

**Alat je prepoznao 4 modula/kandidata za servis:**

| Modul | Klase | LOC | Beans | Ca (ulazna sprega) | Ce (izlazna sprega) | Instabilnost I=Ce/(Ca+Ce) |
|---|---|---|---|---|---|---|
| Plays | 17 | 731 | 9 | 1 | 0 | 0.00 |
| Ticket_agents | 4 | 125 | 3 | 2 | 0 | 0.00 |
| Spectators | 4 | 224 | 3 | 1 | 1 | 0.50 |
| Tickets | 5 | 256 | 4 | 0 | 3 | 1.00 |

**Graf zavisnosti (kako ga je alat izveo iz koda):**

```
Tickets --uses--------> Plays
Tickets --depends on--> Spectators
Tickets --depends on--> Ticket_agents
Spectators --uses-----> Ticket_agents
```

**Usput pronađeno i ispravljeno:** `modules.verify()` je prijavio 2 kršenja granica — `TicketService` i `SpectatorService` su primali zavisnost iz drugog modula preko `@Autowired` na polju (field injection) umesto kroz konstruktor. Ispravljeno na constructor injection u oba slučaja.

## 3. Odluka: 2 servisa umesto 4

Alat je predložio 4 fino-granularna modula. Za ovaj projekat odlučeno je da se fizički razdvoji na **tačno 2 mikroservisa**:

- **`users-service`** = Ticket_agents + Spectators
- **`ticketing-service`** = Plays + Tickets

### Obrazloženje

1. **Metrike sprege pokazuju da ova podela minimizuje mrežne pozive.** Spectators već zavisi samo od Ticket_agents (Ce=1) — ta veza ostaje interni (in-process) poziv unutar `users-service`. Tickets već zavisi od Plays (deo veze Ce=3) — i to ostaje interno unutar `ticketing-service`. Od originalne 4 veze na grafu, preko mreže ostaju tačno **2** (`ticketing-service → users-service`, za validaciju radnika i gledaoca).
2. **Modulith otkriva logičke granice unutar koda, ne diktira topologiju deploy-a.** Njegov posao je da pokaže spregu (coupling); koliko servisa se od toga fizički pravi je arhitekturalna/poslovna odluka, ne automatska posledica analize.
3. **"MonolithFirst" princip** (Martin Fowler) — namerno kretanje sa manje, krupnijih servisa; dalja podela ima smisla tek kad stvarna potreba (skaliranje, odvojeni timovi) to zahteva. Prerana fina podela vodi u "distributed monolith" anti-pattern — više mrežnih poziva i operativnog troška bez stvarne dobiti.
4. **Obim kursnog projekta.** Svi traženi koncepti (nezavisan deploy, sinhrona REST komunikacija, otpornost — timeout/retry/circuit breaker, API Gateway, CI/CD po servisu, merenje performansi pre/posle, fuzz testiranje) potpuno se demonstriraju sa 2 servisa. Dodatna 2 servisa ne bi unela novu vrstu znanja u projekat, samo više instanci istog obrasca — dodatna operativna složenost bez dodatne edukativne vrednosti.

### Da li postoji alat koji fizičku podelu radi automatski?

Ne. Ni Spring Modulith, ni komercijalni alati istog tipa (vFunction, IBM Mono2Micro) ne rade punu transformaciju koda — svi rade samo analizu/predlog granica. Fizičko razdvajanje (uklanjanje JPA relacija preko granica servisa, pisanje REST klijenata, odlučivanje o ponašanju pri otkazu zavisnosti) zahteva inženjerski sud i rađeno je ručno.

## 4. Servisna arhitektura (u toku)

- `users-service` — `pozoriste1.users.*`, sopstvena baza, port 8081
- `ticketing-service` — `pozoriste1.ticketing.*`, sopstvena baza, port 8082
- `Ticket.ticketAgentId` / `Ticket.spectatorId` — u `ticketing-service` više nisu JPA `@ManyToOne` relacije (cross-database FK ne postoji), nego obične kolone; postojanje se proverava REST pozivom.
- `UsersServiceClient` (`ticketing-service`) — jedina mrežna zavisnost između servisa, sinhroni REST (`RestTemplate`), sa:
  - **timeout** — `RestClientConfig` (connect 2s, read 3s)
  - **retry** — `@Retry(name="usersService")`
  - **circuit breaker** — `@CircuitBreaker(name="usersService", fallbackMethod=...)`, fallback baca `UsersServiceUnavailableException` → HTTP 503

**Status: Faza 1 (fizičko razdvajanje) završena.** Oba servisa nezavisno kompajliraju (`./mvnw compile`), svaki sa sopstvenim `pom.xml`, `application.properties` (env-var placeholderi) i `application-local.properties` (van git-a). `backend/demo` (monolit) ostaje netaknut kao "pre" baseline za merenje performansi.

## 5. API Gateway

`services/api-gateway` (port 8090) — Spring Cloud Gateway **Server MVC** (`spring-cloud-starter-gateway-mvc`, Spring Cloud 2023.0.3), ne reaktivna WebFlux varijanta, da bi ostao dosledan servlet-baziranom stacku ostatka projekta.

Rutiranje je deklarativno, u `application.yml` (`spring.cloud.gateway.mvc.routes`):

| Ruta (predicate) | Cilj |
|---|---|
| `/spectators/**`, `/ticket-agents/**` | `users-service` (8081) |
| `/tickets/**`, `/performances/**`, `/plays/**`, `/repertory/**` | `ticketing-service` (8082) |

**Testirano ručno** (`./mvnw spring-boot:run`, pa `curl`): poznata ruta ka isključenom servisu vraća 500 sa `ResourceAccessException` na tačan target host/port (npr. `http://localhost:8082/tickets`) — potvrđuje da je rutiranje ispravno konfigurisano nezavisno od toga da li su servisi upaljeni. Nepoznata putanja vraća 404 direktno od gatewaya (nijedna ruta se ne poklapa).

**Status: urađeno.** Dodat `frontend/pozoriste-app/.env` (`VUE_APP_API_BASE_URL=http://localhost:8090`) i `src/config.js` koji ga čita (sa fallback-om), umesto 11 razbacanih hardkodovanih URL-ova. Sva 4 fajla (`LoginView`, `RepertoryView`, `SpectatorsView`, `TicketPurchaseView`) sada uvoze `API_BASE_URL` iz `@/config`.

Usput otkriven i ispravljen realan bug: `TicketPurchaseView.handlePurchase()` je slao ugnježdene objekte (`spectator: { jmbg }`, `ticketAgent: { id }`) u telu zahteva — to je odgovaralo staroj JPA relaciji u monolitu, ali `Ticket` u `ticketing-service`-u sada ima ravna polja `spectatorId`/`ticketAgentId` (nisu više JPA relacije, vidi sekciju 4). Bez ove izmene bi Jackson te vrednosti tiho ignorisao i kupovina karte bi padala na validaciji kod `UsersServiceClient`. Ispravljeno na `spectatorId: this.selectedSpectator` / `ticketAgentId: "r1"`. (`SpectatorsView`-ov `ticketAgent: { id: "r1" }` u POST/PATCH ka `/spectators` ostaje nepromenjen — to je i dalje unutar `users-service`-a, gde `Spectator.ticketAgent` ostaje prava JPA relacija.)

**Napomena:** frontend nije build-ovan/testiran u browseru (nema instaliranih `node_modules` u ovom okruženju) — izmene su proverene ručnim pregledom (proste zamene stringova), ali vredi pokrenuti `npm install && npm run serve` i ručno isprobati kupovinu karte pre nego što se ovo smatra potpuno gotovim.

### Kako pokrenuti lokalno

Postojeći monolit koristi ručno napravljenu `pozoriste` bazu. Za nova dva servisa napravljene su odvojene baze (`users_db`, `ticketing_db`); pošto ne postoji `schema.sql`, Hibernate ih sam kreira preko `spring.jpa.hibernate.ddl-auto=update`. Tabele su verifikovane pokretanjem oba servisa: `users_db` dobija `teatar_gledalac` + `teatar_radnik`, `ticketing_db` dobija svih 9 tabela vezanih za predstave/ulaznice.

```bash
# jednom, u psql-u
CREATE DATABASE users_db;
CREATE DATABASE ticketing_db;

# svaki servis, sa lokalnim profilom (koristi application-local.properties)
cd services/users-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd services/ticketing-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd services/api-gateway && ./mvnw spring-boot:run
```

Testirano uživo: `curl http://localhost:8081/ticket-agents` i `curl http://localhost:8082/plays` vraćaju `[]` (prazne tabele, ali endpoint radi).

## 6. Komunikacija i konzistentnost podataka

**Trenutno stanje:** jednosmerna sinhrona REST komunikacija, `ticketing-service → users-service`, na 2 mesta (`GET /ticket-agents/{id}`, `GET /spectators/{jmbg}`), uvek obavijeno u `UsersServiceClient` (timeout + retry + circuit breaker, vidi sekciju 4). `users-service` nikad ne zove `ticketing-service` — pravac prati graf zavisnosti.

**Zašto nam ne trebaju distribuirane transakcije (za sada):** `TicketService.create()` radi samo **čitanja** ka `users-service` (provera postojanja); jedini **upis** je `repository.save(t)` u sopstvenoj bazi `ticketing-service`-a — obična lokalna ACID transakcija, ništa distribuirano. Postoji jedna poznata slabost: TOCTOU race (radnik/gledalac teorijski mogu biti obrisani između provere i snimanja karte) — prihvaćeno kao nizak rizik za obim projekta.

**Šta bismo koristili da nam ustreba pisanje u oba servisa u istoj operaciji:** ne 2PC/XA (previše zaključava servise, poništava svrhu nezavisnosti), nego **Saga pattern** — niz lokalnih transakcija sa kompenzacionom akcijom ako korak ne uspe (orkestracijom ili koreografijom preko događaja) — uz **Outbox pattern** za pouzdano objavljivanje događaja (npr. budući `TicketCreated` za asinhroni mejl) iz iste lokalne transakcije koja menja podatke, umesto nepovezanog dvostrukog pisanja.

## 7. CI/CD po servisu

Tri odvojena GitHub Actions workflow-a u `.github/workflows/`, svaki okinut samo kad se promeni njemu odgovarajući servis (`paths` filter):

- `users-service-ci.yml` → `services/users-service/**`
- `ticketing-service-ci.yml` → `services/ticketing-service/**`
- `api-gateway-ci.yml` → `services/api-gateway/**`

Svaki: `actions/checkout` → `actions/setup-java` (Temurin 17, sa Maven cache-om) → `./mvnw -B verify`, sa `working-directory` podešenim na taj servis. Pošto servisi trenutno nemaju test klase, `verify` samo kompajlira i pakuje — **testirano lokalno bez pokrenute baze** (`./mvnw verify`, exit 0 za sva tri), što potvrđuje da će proći i na GitHub Actions runneru koji nema pristup lokalnom Postgres-u. Kad se dodaju testovi (stavka "service-tests" na checklisti) koji stvarno pogađaju bazu, workflow-u treba dodati Postgres kao `services:` kontejner.

### CD: Docker image na GitHub Container Registry

Sam `mvn verify` je samo **CI** — kompajlira i pakuje, ali ništa ne isporučuje. Da bi pipeline bio i **CD (Continuous Delivery)**, svaki workflow ima drugi job, `docker`, koji:

1. se pokreće **samo na push u `main`** (ne na pull request) i **samo ako je `build` job prošao** (`needs: build`) — neispravan kod nikad ne dobije image;
2. pravi Docker image servisa iz njegovog `Dockerfile`-a;
3. objavljuje ga na **ghcr.io** kao `ghcr.io/dajanajandric/<servis>`, sa dva taga: `sha-<commit>` (tačno koja verzija koda je u image-u) i `latest`.

Autentifikacija ide preko ugrađenog `GITHUB_TOKEN`-a (`permissions: packages: write`), bez ručno pravljenih tajni. ghcr.io je izabran jer je besplatan, vezan za isti repo i ne zahteva sopstveni server.

**Dockerfile (isti obrazac za sva tri servisa)** — multi-stage build:
- *build faza* (`maven:3.9-eclipse-temurin-17`) — kompajlira JAR unutar kontejnera, pa image ne zavisi od Jave instalirane na mašini koja ga pravi; `pom.xml` se kopira pre `src/` da bi Docker keširao sloj sa zavisnostima;
- *runtime faza* (`eclipse-temurin:17-jre`) — samo JRE i JAR, bez Maven-a i izvornog koda, pa je finalni image znatno manji.

Image ne sadrži nikakvu konfiguraciju okruženja: baza (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), adresa users-service-a (`USERS_SERVICE_URL`) i rute gateway-a (`USERS_SERVICE_URL`, `TICKETING_SERVICE_URL`) čitaju se iz env varijabli, pa isti image radi lokalno, u docker-compose-u ili na serveru. Pravi *deploy* (pokretanje image-a na serveru — Continuous **Deployment**) je van obima projekta; isporuka spremnog, verzionisanog image-a u registry je granica koju pipeline pokriva.

*(sekcije za merenje performansi i fuzz testiranje dodaju se kako se implementiraju)*

## 8. Testni podaci

Za mjerenje performansi sve tri baze (`pozoriste`, `users_db`, `ticketing_db`) su napunjene **istim** podacima, generisanim skriptom `scripts/seed/generate_seed.py` (fiksan random seed, pa je izlaz uvijek isti). Repertoar je preuzet sa sajta Srpskog narodnog pozorišta (prodaja.snp.org.rs, septembar–decembar 2026): 28 predstava, 81 izvođenje na 3 scene (Jovan Đorđević 600 mjesta, Pera Dobrinović 400, Kamerna scena 120), 4 mjesečna repertoara. Režiseri, scenografi, 5 blagajnika i 300 gledalaca su izmišljeni (e-mail adrese na `example.com`). Po izvođenju je prodato 5–35% mjesta, ukupno 4733 ulaznice.

Skripte: `seed_users.sql` → `users_db`, `seed_ticketing.sql` → `ticketing_db`, `seed_monolith.sql` → `pozoriste`. Sve koriste `ON CONFLICT DO NOTHING`, pa se mogu pokrenuti više puta. Stari demo podaci monolita su ostali u bazi (nekoliko redova više u odnosu na servise, zanemarljivo za mjerenje).

**Uočena razlika u šemi:** monolit ima ograničenja dužine kolona (npr. `sala_naziv varchar(20)`, `radnik_password varchar(20)`), a servisi, čije tabele pravi Hibernate (`ddl-auto=update`), imaju `varchar(255)`. Ovo je relevantno za fuzz testiranje: isti predugački unos monolit odbija, a servisi prihvataju.

## 9. Mjerenje performansi (prije / poslije podjele)

**Postavka.** Alat k6 (`perf/benchmark.js`, pokretanje `perf/run.sh monolith|services`). Sve radi lokalno na istom računaru: monolit (8084) i servisi + gateway (8081/8082/8090) kao obični `java -jar` procesi sa `-Xmx512m`, ista Postgres instanca, isti testni podaci (sekcija 8). Mail za potvrdu kupovine ide na lokalni Mailpit (Docker) umjesto na Gmail, da mjerenje ne zavisi od interneta. Prije svakog mjerenja brišu se karte iz prethodnih pokretanja i radi se zagrijavanje JVM-a od 15 s koje se ne broji. Klijent je za monolit direktno na 8084, a za servise preko API Gateway-a (8090), kao što bi išao frontend.

Dva scenarija po 60 s:
- **pregled** – 20 istovremenih korisnika nasumično čitaju: predstave, izvođenja, izvođenja jedne predstave, mjesečni repertoar, gledaoca po JMBG-u, slobodna mjesta.
- **kupovina** – 5 blagajnika: slobodna mjesta za nasumično izvođenje → kupovina karte (`POST /tickets`). Kod servisa kupovina uključuje 2 sinhrona REST poziva ticketing → users (provjera blagajnika i gledaoca).

Izvedena su 3 kruga naizmjenično (M→S, S→M, M→S); prikazani su prosjeci ta 3 kruga.

**Ispravka prije mjerenja.** Prvo (preliminarno) mjerenje otkrilo je beskonačnu rekurziju u JSON-u: `Performance.repertoari` ↔ `Repertory.performances`. Jackson je išao u krug do dubine 1000 i pucao (klijent dobije 200 i odsječen odgovor; >10.000 puta u logu i monolita i servisa). Jedna karta (`GET /tickets/{id}`) imala je 113 KB. Greška je postojala u originalnom monolitu. Ispravljena je sa `@JsonIgnore` na `Performance.getRepertoari()` **u oba projekta**, pa je poređenje i dalje fer. Poslije ispravke karta ima < 1 KB, a u logovima nema grešaka.

**Rezultati** (vrijeme odziva u ms, monolit → servisi):

| Zahtjev | Prosjek | Medijana | p95 |
|---|---|---|---|
| Pregled – ukupno | 36.2 → 39.9 | 26.7 → 37.0 | 81.6 → 75.0 |
| `GET /plays` | 26.0 → 37.5 | 21.4 → 33.8 | 54.6 → 73.3 |
| `GET /performances/by-play/{id}` | 20.3 → 37.0 | 16.6 → 33.6 | 47.8 → 73.2 |
| `GET /repertory/{id}` | 21.2 → 36.9 | 16.3 → 33.7 | 51.4 → 72.1 |
| `GET /spectators/{jmbg}` | 18.5 → 37.1 | 14.5 → 33.6 | 46.3 → 71.6 |
| `GET /performances` | 69.0 → 47.2 | 62.4 → 41.9 | 106.3 → 78.5 |
| `POST /tickets/performance/available-seats` | 40.2 → 25.6 | 35.1 → 19.1 | 75.8 → 65.8 |
| `POST /tickets` (kupovina) | 60.7 → 50.9 | 57.4 → 44.1 | 89.4 → 85.1 |

Propusnost: pregled ~510 → ~493 zahtjeva/s (−3%), kupovina ~54 → ~84 karte/s. Greške: 0 u mjerenjima (2 greške gateway-a desile su se tokom zagrijavanja, vidi dolje).

**Tumačenje.**
- **Jednostavna čitanja su kod servisa sporija za ~17 ms (medijana)** – to je cijena dodatnog mrežnog skoka kroz API Gateway (klijent → gateway → servis). Za male odgovore ta cijena dominira.
- **Kupovina i slobodna mjesta su kod servisa brži**, iako kupovina ima 2 dodatna REST poziva. Najvjerovatniji razlog: u monolitu `Ticket` ima JPA relacije (`@ManyToOne`) na `TicketAgent` i `Spectator`, pa svako učitavanje karata jednog izvođenja (provjera zauzetog mjesta) povlači i blagajnike i gledaoce. U ticketing-service su to obične kolone sa ID-em, pa je upit lakši. Dakle, razdvajanje modela podataka (posljedica podjele baza) ovdje je dobilo više nego što su koštali mrežni pozivi.
- **`GET /performances` je u monolitu sporiji** i zato što monolit ima 13 izvođenja više (stari demo podaci, sekcija 8) i veći JSON (11,2 KB naspram 9,8 KB) – ovaj red nije potpuno uporediv.
- Rep raspodjele (p95) je kod servisa jednak ili bolji jer se opterećenje dijeli na dva procesa (dva JVM-a, dva pool-a konekcija).

**Uočen problem na gateway-u.** Pod opterećenjem gateway povremeno baci `ResourceAccessException` / `NullPointerException` iz JDK `HttpClient`-a (1 put u preliminarnom mjerenju, 2 puta tokom zagrijavanja, od ukupno > 100.000 zahtjeva). Klijent tada dobije 500. Moguće rješenje: prebaciti Spring Cloud Gateway MVC na drugi HTTP klijent (npr. Apache HttpClient 5) ili dodati retry filter na gateway-u za idempotentne GET zahtjeve.

**Ograničenja.** Sve radi na jednom računaru (servisi se ne takmiče za mrežu, ali se takmiče za CPU), mjerenje traje kratko (3 × 60 s po scenariju), a opterećenje je umjereno (20 + 5 korisnika). Rezultati pokazuju trend, ne apsolutne brojke za produkciju.

## 10. Fuzz testiranje

**Alat.** [Schemathesis](https://schemathesis.readthedocs.io) 4.28: iz OpenAPI opisa API-ja (`/v3/api-docs`, dobijen dodavanjem `springdoc-openapi-starter-webmvc-api` u monolit i oba servisa) automatski generiše hiljade zahtjeva: pogrešne tipove, prazna i `null` polja, ogromne brojeve, kontrolne i Unicode znakove, neispravan JSON, nepostojeće ID-eve, kao i sekvence poziva (stateful: napravi → pročitaj → obriši). Za svaki odgovor provjerava da li je server pukao (5xx), da li je prihvatio neispravan ulaz i da li odgovor odgovara opisu API-ja. Pokretanje: `fuzz/run.sh monolith|users|ticketing`. Skripta prije svakog pokretanja vraća baze iz backupa, jer fuzzer šalje i POST/PATCH/DELETE zahtjeve. Isti seed (42) i isti broj primjera (100 po operaciji) za sve mete.

**Rezultati (prvi krug, prije ispravki):**

| Meta | Operacija | Generisano zahtjeva | Jedinstvenih grešaka | od toga 5xx | Trajanje |
|---|---|---|---|---|---|
| monolit | 25 | 2881 | 54 | 10 | 6,8 min |
| users-service | 8 | 828 | 28 | 2 | 22 s |
| ticketing-service | 17 | 4203 | 84 | 9 | 28 min |

Najveći dio „grešaka“ (`Undocumented HTTP status code`, `Response violates schema`) nisu kvarovi. springdoc ne zna da endpoint vraća 404/400, niti da polje može biti `null`. Stvarni problemi robusnosti su odgovori 5xx na neispravan ulaz.

**Uzroci 5xx odgovora** (isti u monolitu i servisima, jer je kod prenesen):
- **Nema validacije ulaza.** POST bez `id` → `JpaSystemException: Identifier ... must be manually assigned` (500). Karta bez izvođenja → `NullPointerException` (500). Referenca na nepostojeći entitet → `TransientPropertyValueException` (500). `null` u obaveznoj koloni ili predugačak string (monolit, `varchar(20)`) → greška baze (500).
- **NUL znak (`\u0000`) u stringu** → Postgres odbija upis (`invalid byte sequence for encoding "UTF8": 0x00`) → 500.
- **Brisanje entiteta koji je u upotrebi** (predstava sa izvođenjima, gledalac sa kartama u monolitu) → povreda stranog ključa → 500 umjesto 409 Conflict.

**Nalazi specifični za mikroservise:**
1. **Neispravan ulaz otvara circuit breaker i blokira prodaju (potvrđeno ručno).** `UsersServiceClient` sastavlja URL spajanjem stringova (`baseUrl + "/ticket-agents/" + id`), a `RestTemplate` taj string tumači kao URI šablon. ID koji sadrži `{` baca `IllegalArgumentException` prije nego što se ikakav poziv pošalje. Circuit breaker to broji kao kvar users-service-a. Poslije 5 takvih zahtjeva kolo se otvara i **10 sekundi svaka kupovina karte, i ispravna, dobija 503** „users-service nedostupan“. Svako ko može poslati zahtjev može ovako stalno blokirati prodaju. U monolitu ovaj problem ne postoji, jer nema mrežnog poziva.
2. **Brisanje gledaoca ne provjerava karte.** U monolitu `DELETE /spectators/{jmbg}` za gledaoca sa kartama puca na stranom ključu (500, ali podaci ostaju ispravni). U users-service brisanje uspijeva (204), a njegove karte u `ticketing_db` ostaju „siročad“ sa JMBG-om koji više ne postoji. Posljedica podjele baza: referencijalni integritet više ne čuva baza.
3. **Sporo pod lošim ulazom.** Ticketing-service je trajao 28 min naspram 7 min monolita. Svaki zahtjev sa neispravnim ID-em gledaoca/blagajnika prolazi kroz retry (3 pokušaja × 300 ms) kad users-service vrati 5xx.

**Sigurnosni nalaz (i u monolitu):** `GET /ticket-agents` vraća lozinke blagajnika u čistom tekstu.

## 11. Ispravke poslije fuzz testiranja i saga brisanja gledaoca

Ispravljeni su **samo servisi**. Monolit ostaje nepromijenjen kao polazna tačka, pa se u drugom krugu fuzzinga vidi razlika.

### 11.1 Ispravke robusnosti (oba servisa)
- **Provjera ulaza (Bean Validation).** Entiteti koji stižu u tijelu zahtjeva imaju pravila: obavezna polja pri kreiranju (validaciona grupa `OnCreate`, pa PATCH i dalje dozvoljava djelimične izmjene), maksimalne dužine, JMBG od tačno 13 cifara, ID-evi samo od slova, cifara i `. _ -`, i zabrana kontrolnih znakova (npr. NUL, koji Postgres odbija). Loš ulaz dobija **400 sa spiskom grešaka** prije nego što stigne do baze.
- **`ControlCharacterFilter`** odbija putanje sa kontrolnim znakovima (npr. `/spectators/%00`) sa 400.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`): povreda stranog ključa (brisanje predstave koja ima izvođenja) → **409 Conflict**, a referenca na nepostojeći objekat → **400**. Ranije je oboje bilo 500.
- **Kreiranje više ne prepisuje postojeće objekte.** `save()` je za postojeći ID tiho radio izmjenu, a sada POST sa zauzetim ID-em vraća 409. Kupovina provjerava i da izvođenje postoji i da je broj mjesta u opsegu sale.
- `GET /plays/{id}` i `GET /performances/{id}` za nepostojeći ID vraćaju 404, a ne prazan odgovor 200.
- **Lozinke blagajnika** se više ne vraćaju u odgovorima (`@JsonProperty(access = WRITE_ONLY)`).

### 11.2 Circuit breaker više ne reaguje na loš ulaz
`UsersServiceClient`: (1) ID koji ne odgovara formatu se odbija bez mrežnog poziva, (2) ID se prosljeđuje kao parametar URI šablona (`/ticket-agents/{id}`), pa se ispravno kodira, (3) circuit breaker broji samo `ResourceAccessException` (timeout, odbijena konekcija) i `HttpServerErrorException` (5xx) (`record-exceptions`), (4) fallback metode hvataju samo te izuzetke i `CallNotPermittedException`. Provjereno ručno: 7 zahtjeva sa `{x}` kao ID-em blagajnika dobija 400, a ispravna kupovina odmah poslije prolazi (200). Prije ispravke kolo bi se otvorilo i kupovina bi dobila 503.

### 11.3 Saga brisanja gledaoca (koreografija, RabbitMQ)
**Problem** (fuzz nalaz 2): users-service je brisao gledaoca, a njegove karte su u `ticketing_db` ostajale bez vlasnika. Pošto su baze odvojene, strani ključ više ne može da spriječi brisanje.

**Rješenje: saga sa koreografijom.** Nijedan servis ne upravlja drugim. Svaki objavljuje događaje na zajednički RabbitMQ topic exchange `pozoriste.events` i reaguje na tuđe:

```
klijent --DELETE /spectators/{jmbg}--> users-service
    users-service:      gledalac -> DELETION_PENDING, objavi spectator.deletion.requested   (odgovor 202 Accepted)
    ticketing-service:  ima li gledalac karte za izvođenja koja još nisu održana?
        da -> objavi spectator.deletion.rejected
                users-service: kompenzacija, gledalac -> ACTIVE
        ne -> karte za prošla izvođenja anonimizuj (spectatorId = NULL), objavi spectator.deletion.approved
                users-service: obriši gledaoca
```

- **Semantičko zaključavanje:** dok traje saga (`DELETION_PENDING`), ticketing-service gledaocu ne prodaje nove karte (409). Inače bi karta kupljena usred sage ostala bez vlasnika.
- **Pravilo za karte** (odluka): brisanje se odbija ako postoji karta za buduće izvođenje. Karte za prošla izvođenja se anonimizuju, pa istorija prodaje ostaje.
- **Trajni redovi** (`durable`): ako ticketing-service ne radi, zahtjev čeka u redu i saga se završi kad se servis vrati. Provjereno ručno: gledalac je ostao u `DELETION_PENDING` dok ticketing nije radio, a obrisan je odmah po njegovom pokretanju.
- **Ako RabbitMQ ne radi**, objava baci izuzetak i transakcija se poništi (gledalac ostaje ACTIVE), a klijent dobija 503.
- **Klase događaja** (`Requested`, `Approved`, `Rejected`) postoje kao kopije u oba servisa, bez zajedničke biblioteke, da servisi ostanu nezavisni. JSON konverter tip određuje iz parametra listener-a, a ne iz imena Java klase pošiljaoca.
- **Frontend** poslije 202 kratko čeka ishod (gledalac nestane → obrisan, vrati se u ACTIVE → odbijeno) i to javlja korisniku.

Ručno testirano: gledalac bez karata → obrisan; gledalac sa kartom samo za prošlo izvođenje → karta anonimizovana, gledalac obrisan; gledalac sa 22 buduće karte → odbijeno, vraćen u ACTIVE.

**Poznata ograničenja.** Objava događaja i upis u bazu nisu atomični: ako commit padne poslije uspješne objave, događaj je već poslat. Pravo rješenje je *transactional outbox* (događaj se upisuje u tabelu u istoj transakciji, a zaseban proces ga objavljuje). Za obim ovog projekta to nije urađeno. Listener-i su idempotentni u smislu da ponovljeni `approved`/`rejected` za gledaoca koji više nije u `DELETION_PENDING` ne radi ništa.

**Pokretanje:** RabbitMQ je dodat u `perf/start-all.sh` (Docker, `-u rabbitmq` jer na Docker Desktopu za Windows inače pada sa `erlang.cookie: eacces`). Web konzola: http://localhost:15672 (guest/guest). Servisi čitaju `RABBITMQ_HOST` i ostale varijable, pa rade i u Dockeru.

### 11.4 Drugi krug fuzz testiranja

Isti alat, seed i broj primjera kao u prvom krugu (sekcija 10). Monolit nije mijenjan, pa za njega važe rezultati prvog kruga.

| Meta | 5xx (1. krug → 2. krug) | Jedinstvenih nalaza | Trajanje |
|---|---|---|---|
| monolit (nepromijenjen) | 10 | 54 | 6,8 min |
| users-service | 2 → **0** | 28 → 17 | 22 s → 1,5 min |
| ticketing-service | 9 → **0** | 84 → 43 | 28 min → 6 min |

Drugi krug je našao još jedan pravi bug, koji je ispravljen prije završnog pokretanja: izvođenje se moglo napraviti bez predstave, sale i termina. Poslije takvog upisa `GET /performances` je pucao (500) **za sve korisnike**, jer lista čita naziv predstave. Sada su ta tri polja obavezna pri kreiranju, a lista preskače nepotpune redove.

Preostali nalazi nisu kvarovi robusnosti:
- `Undocumented HTTP status code` i `Response violates schema`: springdoc u OpenAPI opisu ne navodi 400/404/409 odgovore niti `null` polja.
- `API rejected schema-compliant request`: zahtjev je formalno ispravan, ali referencira nešto što ne postoji (npr. izvođenje `pr-01`), pa je 400 ispravan odgovor. OpenAPI šema ne može da izrazi „ID mora postojati u bazi“.
- `POST /repertory/{id}/performances/{id}` vraća prazan 200 bez `Content-Type`. Kozmetički nalaz, ponašanje preuzeto iz monolita.

Users-service je u drugom krugu sporiji (22 s → 1,5 min) zato što fuzzer sada dolazi dublje: zahtjevi prolaze validaciju, a `DELETE /spectators` pokreće sagu. Ticketing je brži (28 → 6 min) jer loši ID-evi više ne prolaze kroz retry prema users-service-u.

## 12. Testovi servisa

Svaki servis ima svoje testove, koje njegov CI/CD pipeline pokreće (`./mvnw -B verify`) nezavisno od drugog servisa. Testovi ne traže bazu ni RabbitMQ: logika se testira unit testovima sa Mockito mockovima, a HTTP sloj sa `@WebMvcTest` (samo kontroler, validacija, handler grešaka i filter). Zato rade i u GitHub Actions bez dodatnih servisa.

| Servis | Test klasa | Šta provjerava | Testova |
|---|---|---|---|
| users-service | `SpectatorDeletionSagaTest` | saga: pokretanje → DELETION_PENDING + događaj; dvostruko brisanje → odbijeno; broker ne radi → izuzetak; approved → brisanje; ponovljen approved → ignorisan; rejected → kompenzacija (ACTIVE) | 7 |
| users-service | `SpectatorControllerTest` | validacija (JMBG, obavezna polja, e-mail, NUL znak) → 400; duplikat → 409; DELETE → 202 / 404 / 409 / 503; putanja sa `%00` → 400 | 9 |
| users-service | `TicketAgentControllerTest` | lozinke se ne vraćaju; nepostojeći blagajnik → 404; prijava radi | 3 |
| ticketing-service | `SpectatorDeletionListenerTest` | pravilo sage: bez karata → approved; buduća karta → rejected; samo prošle → anonimizacija + approved; karta bez termina → rejected | 4 |
| ticketing-service | `TicketServiceTest` | kupovina: ispravna; zauzeto mjesto („007“ = „7“); mjesto van sale; nepostojeće izvođenje; duplikat ID-a; nepostojeći blagajnik; gledalac u brisanju; slobodna mjesta; nepostojeće izvođenje → 404 | 9 |
| ticketing-service | `UsersServiceClientTest` | neispravan ID (`{x}`, `../`, razmak…) → bez mrežnog poziva; ID kao parametar URI šablona; 404 = „ne postoji“ | 8 |
| ticketing-service | `TicketControllerTest` | ispravna kupovina → 200 + mail; neispravno tijelo → 400 sa detaljima; users-service ne radi → 503; gledalac u brisanju → 409 | 4 |

Ukupno **44 testa** (users 19, ticketing 25). Većina testova direktno pokriva nalaze fuzz testiranja (sekcija 10), pa bi se povratak greške odmah vidio u CI-ju.

Usput ispravljeno: `ControlCharacterFilter` je čitao `servletPath`, koji u MockMvc testovima nije postavljen. Sada provjerava dekodirani `requestURI`, a neispravno kodiranu putanju (npr. `%zz`) odbija sa 400.
