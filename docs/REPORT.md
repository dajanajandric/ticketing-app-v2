# Izveštaj: migracija monolita na mikroservise

Detaljan izveštaj uz projekat za kurs *Razvoj poslovnih sistema* (prof. Gordana Rakić). Kratak pregled, arhitektura i uputstvo za pokretanje su u [README-u](../README.md); ovde su odluke, merenja i nalazi.

## Sadržaj

1. [Polazno stanje](#1-polazno-stanje)
2. [Analiza alatom (Spring Modulith)](#2-analiza-alatom-spring-modulith)
3. [Odluka: 2 servisa umesto 4](#3-odluka-2-servisa-umesto-4)
4. [Servisna arhitektura](#4-servisna-arhitektura)
5. [API Gateway i frontend](#5-api-gateway-i-frontend)
6. [Komunikacija i konzistentnost podataka](#6-komunikacija-i-konzistentnost-podataka)
7. [CI/CD po servisu](#7-cicd-po-servisu)
8. [Testni podaci](#8-testni-podaci)
9. [Merenje performansi (pre i posle podele)](#9-merenje-performansi-pre-i-posle-podele)
10. [Fuzz testiranje](#10-fuzz-testiranje)
11. [Ispravke posle fuzz testiranja i saga brisanja gledaoca](#11-ispravke-posle-fuzz-testiranja-i-saga-brisanja-gledaoca)
12. [Testovi servisa](#12-testovi-servisa)

## 1. Polazno stanje

Monolitna Spring Boot aplikacija (`backend/demo`) za prodaju pozorišnih ulaznica, sa 4 domenska paketa:

- `plays` — repertoar, predstave, izvođenja, sale, režiseri, scenografi
- `spectators` — gledaoci (identifikovani JMBG-om)
- `ticket_agents` — radnici/blagajnici
- `tickets` — prodaja ulaznica, slanje mejla potvrde

Monolit je namerno ostavljen kao polazna tačka („pre“) za merenje performansi i fuzz testiranje. U njemu su izmenjene samo dve stvari: ispravka rekurzije u JSON-u (sekcija 9), da poređenje bude fer, i dodat OpenAPI opis (springdoc) za fuzz testiranje (sekcija 10).

## 2. Analiza alatom (Spring Modulith)

Korišćen je [Spring Modulith](https://spring.io/projects/spring-modulith) (`spring-modulith-starter-core` 1.2.13) da se granice modula automatski izvedu iz paketne strukture i Java import-a, bez ručno pisane arhitekture. Test je u `backend/demo/src/test/java/pozoriste1/demo/ModularityTests.java`, a izlaz u `target/spring-modulith-docs/*.puml`.

**Alat je prepoznao 4 modula, kandidata za servis:**

| Modul | Klase | LOC | Beans | Ca (ulazna sprega) | Ce (izlazna sprega) | Nestabilnost I = Ce/(Ca+Ce) |
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

**Usput pronađeno i ispravljeno:** `modules.verify()` je prijavio 2 kršenja granica: `TicketService` i `SpectatorService` su zavisnost iz drugog modula primali preko `@Autowired` na polju (field injection), a ne kroz konstruktor. Ispravljeno na constructor injection u oba slučaja.

## 3. Odluka: 2 servisa umesto 4

Alat je predložio 4 fino granulisana modula. Za ovaj projekat odlučeno je da se aplikacija fizički podeli na **tačno 2 mikroservisa**:

- **`users-service`** = Ticket_agents + Spectators
- **`ticketing-service`** = Plays + Tickets

### Obrazloženje

1. **Metrike sprege pokazuju da ova podela minimizuje mrežne pozive.** Spectators zavisi samo od Ticket_agents (Ce = 1), pa ta veza ostaje interni poziv unutar `users-service`. Tickets zavisi od Plays, i to ostaje interno unutar `ticketing-service`. Od 4 veze na grafu preko mreže ostaju tačno **2** (`ticketing-service → users-service`, za proveru blagajnika i gledaoca).
2. **Modulith otkriva logičke granice u kodu, ali ne diktira topologiju deploy-a.** Njegov posao je da pokaže spregu; koliko servisa se od toga fizički pravi je arhitekturna i poslovna odluka, a ne automatska posledica analize.
3. **Princip „MonolithFirst“** (Martin Fowler): namerno se kreće sa manje, krupnijih servisa, a dalja podela ima smisla tek kad stvarna potreba (skaliranje, odvojeni timovi) to zahteva. Prerana fina podela vodi u „distribuirani monolit“: više mrežnih poziva i operativnog troška bez stvarne dobiti.
4. **Obim projekta.** Svi traženi koncepti (nezavisan deploy, sinhrona REST komunikacija, otpornost kroz timeout/retry/circuit breaker, API Gateway, CI/CD po servisu, merenje performansi, fuzz testiranje) potpuno se demonstriraju sa 2 servisa. Dodatna 2 servisa bi donela samo više instanci istog obrasca.

### Da li postoji alat koji fizičku podelu radi automatski?

Ne. Ni Spring Modulith, ni komercijalni alati istog tipa (vFunction, IBM Mono2Micro) ne rade punu transformaciju koda; svi rade samo analizu i predlog granica. Fizičko razdvajanje (uklanjanje JPA relacija preko granica servisa, pisanje REST klijenata, odluka o ponašanju pri otkazu zavisnosti) zahteva inženjersku procenu i rađeno je ručno.

## 4. Servisna arhitektura

- **`users-service`** — paket `pozoriste1.users.*`, baza `users_db`, port 8081.
- **`ticketing-service`** — paket `pozoriste1.ticketing.*`, baza `ticketing_db`, port 8082.
- `Ticket.ticketAgentId` i `Ticket.spectatorId` u `ticketing-service` više nisu JPA `@ManyToOne` relacije (strani ključ između dve baze ne postoji), već obične kolone. Postojanje blagajnika i gledaoca proverava se REST pozivom.
- **`UsersServiceClient`** (`ticketing-service`) je jedina sinhrona mrežna zavisnost između servisa (`RestTemplate`), sa:
  - **timeout-om** — `RestClientConfig` (connect 2 s, read 3 s);
  - **retry-jem** — `@Retry(name = "usersService")`, 3 pokušaja na 300 ms, samo za mrežne greške i 5xx;
  - **circuit breaker-om** — `@CircuitBreaker(name = "usersService")`; kolo se otvara kad 50% od poslednjih 10 poziva ne uspe i ostaje otvoreno 10 s. Fallback baca `UsersServiceUnavailableException`, a kupovina tada dobija HTTP 503.

Svaki servis ima sopstveni `pom.xml`, `Dockerfile` i `application.properties` sa env-var placeholderima (`DB_URL`, `USERS_SERVICE_URL`, `RABBITMQ_HOST`…). Lokalni `application-local.properties` nije u git-u. Tabele pravi Hibernate (`spring.jpa.hibernate.ddl-auto=update`).

## 5. API Gateway i frontend

`services/api-gateway` (port 8090) je Spring Cloud Gateway **Server MVC** (`spring-cloud-starter-gateway-mvc`, Spring Cloud 2023.0.3), a ne reaktivna WebFlux varijanta, da bi ostao dosledan servlet stack-u ostatka projekta. Rutiranje je deklarativno, u `application.yml`:

| Ruta | Cilj |
|---|---|
| `/spectators/**`, `/ticket-agents/**` | `users-service` (8081) |
| `/tickets/**`, `/performances/**`, `/plays/**`, `/repertory/**` | `ticketing-service` (8082) |

Adrese servisa se čitaju iz `USERS_SERVICE_URL` i `TICKETING_SERVICE_URL`. Nepoznata putanja dobija 404 direktno od gateway-a.

**CORS je podešen samo na gateway-u** (`CorsConfig`, dozvoljeni origin iz `frontend.url`, podrazumevano `http://localhost:8080`), jer je gateway jedina ulazna tačka za browser. Servisi su ranije imali `@CrossOrigin` na kontrolerima, ali gateway je preflight (`OPTIONS`) zahteve odbijao sa `403 Invalid CORS request`, pa iz browsera nije radio nijedan POST/PATCH/DELETE sa JSON telom (kupovina, slobodna mesta, gledaoci); GET zahtevi nemaju preflight, pa se greška nije videla dok frontend nije isproban. `@CrossOrigin` je uklonjen iz servisa, jer bi se inače u odgovoru pojavila dva `Access-Control-Allow-Origin` zaglavlja, koja browser takođe odbija.

**Frontend** (`frontend/pozoriste-app`, Vue) sada sve pozive šalje gateway-u: adresa je u `src/config.js` (`VUE_APP_API_BASE_URL`, podrazumevano `http://localhost:8090`), umesto 11 hardkodovanih URL-ova u 4 fajla. Usput su ispravljene tri stvari:

- **Telo zahteva za kupovinu.** `TicketPurchaseView` je slao ugnežđene objekte (`spectator: { jmbg }`, `ticketAgent: { id }`), što je odgovaralo JPA relacijama u monolitu. `Ticket` u `ticketing-service` ima ravna polja `spectatorId`/`ticketAgentId`, pa bi Jackson te vrednosti tiho ignorisao.
- **Hardkodovani blagajnik `"r1"`.** Kupovina i dodavanje gledaoca uvek su slali blagajnika `r1`, koji postoji samo u starim demo podacima monolita. Sada se posle prijave pamti ID prijavljenog blagajnika i šalje se on.
- **Brisanje gledaoca je saga** (sekcija 11.3): posle odgovora 202 frontend kratko čeka ishod i javlja „obrisan“ ili „odbijeno“. Greške validacije se prikazuju sa spiskom polja koja nisu ispravna.

## 6. Komunikacija i konzistentnost podataka

**Sinhrono (REST):** `ticketing-service → users-service`, na 2 mesta (`GET /ticket-agents/{id}`, `GET /spectators/{jmbg}`), uvek kroz `UsersServiceClient`. `users-service` nikad ne zove `ticketing-service` sinhrono; pravac prati graf zavisnosti.

**Kupovina karte ne traži distribuiranu transakciju.** `TicketService.create()` ka `users-service` radi samo **čitanja** (provera postojanja), a jedini **upis** je u sopstvenu bazu `ticketing-service` — obična lokalna ACID transakcija.

**Brisanje gledaoca menja podatke u oba servisa**, pa je rešeno **sagom sa koreografijom** preko RabbitMQ-a (sekcija 11.3), a ne 2PC/XA transakcijom, koja bi zaključavala oba servisa i poništila svrhu njihove nezavisnosti.

## 7. CI/CD po servisu

Tri odvojena GitHub Actions workflow-a u `.github/workflows/`. Svaki se pokreće samo kad se promeni njegov servis (`paths` filter):

- `users-service-ci.yml` → `services/users-service/**`
- `ticketing-service-ci.yml` → `services/ticketing-service/**`
- `api-gateway-ci.yml` → `services/api-gateway/**`

**CI (job `build`):** `actions/checkout` → `actions/setup-java` (Temurin 17, Maven keš) → `./mvnw -B verify`, u folderu tog servisa. `verify` pokreće testove servisa (sekcija 12: 19 u users-service, 25 u ticketing-service) i pakuje JAR. Testovi ne traže bazu ni RabbitMQ, pa GitHub runner-u ne trebaju dodatni servisi.

### CD: Docker image na GitHub Container Registry

Svaki workflow ima i drugi job, `docker`, koji:

1. se pokreće **samo na push u `main`** (ne na pull request) i **samo ako je `build` prošao** (`needs: build`), pa neispravan kod nikad ne dobije image;
2. pravi Docker image iz `Dockerfile`-a servisa;
3. objavljuje ga na **ghcr.io** kao `ghcr.io/dajanajandric/<servis>`, sa tagovima `sha-<commit>` (tačna verzija koda) i `latest`.

Autentifikacija ide preko ugrađenog `GITHUB_TOKEN`-a (`permissions: packages: write`), bez ručno pravljenih tajni. ghcr.io je izabran jer je besplatan, vezan za isti repozitorijum i ne traži sopstveni server.

**Dockerfile (isti obrazac za sva tri servisa)** je multi-stage:
- *build faza* (`maven:3.9-eclipse-temurin-17`) kompajlira JAR u kontejneru, pa image ne zavisi od Jave na mašini koja ga pravi; `pom.xml` se kopira pre `src/`, da Docker kešira sloj sa zavisnostima;
- *runtime faza* (`eclipse-temurin:17-jre`) sadrži samo JRE i JAR, bez Maven-a i izvornog koda.

Image ne sadrži konfiguraciju okruženja: baza, adresa users-service-a, RabbitMQ i rute gateway-a čitaju se iz env varijabli, pa isti image radi lokalno, u docker-compose-u ili na serveru. Pravi *deploy* na server (Continuous **Deployment**) je van obima projekta; pipeline se završava isporukom verzionisanog image-a u registry.

## 8. Testni podaci

Za merenje performansi sve tri baze (`pozoriste`, `users_db`, `ticketing_db`) napunjene su **istim** podacima, generisanim lokalnom Python skriptom sa fiksnim random seed-om (skripta nije deo repozitorijuma). Repertoar je preuzet sa sajta Srpskog narodnog pozorišta (prodaja.snp.org.rs, septembar–decembar 2026): 28 predstava, 81 izvođenje na 3 scene (Jovan Đorđević 600 mesta, Pera Dobrinović 400, Kamerna scena 120) i 4 mesečna repertoara. Režiseri, scenografi, 5 blagajnika (`blag-01` … `blag-05`) i 300 gledalaca su izmišljeni, sa e-mail adresama na `example.com`. Po izvođenju je prodato 5–35% mesta, ukupno 4733 ulaznice.

Stari demo podaci monolita ostali su u bazi (nekoliko redova više u odnosu na servise, zanemarljivo za merenje).

**Uočena razlika u šemi:** monolit ima ograničenja dužine kolona (npr. `sala_naziv varchar(20)`, `radnik_password varchar(20)`), a servisi, čije tabele pravi Hibernate, imaju `varchar(255)`. To je bilo relevantno za fuzz testiranje: isti predugačak unos monolit odbija na nivou baze, a servisi su ga prihvatali dok nije dodata validacija (sekcija 11).

## 9. Merenje performansi (pre i posle podele)

**Postavka.** Alat k6 (`perf/benchmark.js`, pokretanje `perf/run.sh monolith|services`). Sve radi lokalno na istom računaru: monolit (8084) i servisi sa gateway-om (8081/8082/8090) kao obični `java -jar` procesi sa `-Xmx512m`, ista Postgres instanca, isti testni podaci (sekcija 8). Mejl za potvrdu kupovine ide na lokalni Mailpit (Docker), a ne na Gmail, da merenje ne zavisi od interneta. Pre svakog merenja brišu se karte iz prethodnih pokretanja i radi se zagrevanje JVM-a od 15 s, koje se ne računa. Klijent za monolit ide direktno na 8084, a za servise preko API Gateway-a (8090), kao i frontend.

Dva scenarija po 60 s:
- **pregled** — 20 istovremenih korisnika nasumično čita predstave, izvođenja, izvođenja jedne predstave, mesečni repertoar, gledaoca po JMBG-u i slobodna mesta;
- **kupovina** — 5 blagajnika: slobodna mesta za nasumično izvođenje → kupovina karte (`POST /tickets`). Kod servisa kupovina uključuje 2 sinhrona REST poziva ticketing → users.

Izvedena su 3 kruga naizmenično (M→S, S→M, M→S); prikazani su proseci ta 3 kruga.

**Ispravka pre merenja.** Prvo (preliminarno) merenje otkrilo je beskonačnu rekurziju u JSON-u: `Performance.repertoari` ↔ `Repertory.performances`. Jackson je išao u krug do dubine 1000 i pucao: klijent dobije 200 i odsečen odgovor, a u logu i monolita i servisa bilo je više od 10.000 grešaka. Jedna karta (`GET /tickets/{id}`) imala je 113 KB. Greška je postojala u originalnom monolitu. Ispravljena je sa `@JsonIgnore` na `Performance.getRepertoari()` **u oba projekta**, pa je poređenje i dalje fer. Posle ispravke karta ima manje od 1 KB.

**Rezultati** (vreme odziva u ms, monolit → servisi):

| Zahtev | Prosek | Medijana | p95 |
|---|---|---|---|
| Pregled — ukupno | 36.2 → 39.9 | 26.7 → 37.0 | 81.6 → 75.0 |
| `GET /plays` | 26.0 → 37.5 | 21.4 → 33.8 | 54.6 → 73.3 |
| `GET /performances/by-play/{id}` | 20.3 → 37.0 | 16.6 → 33.6 | 47.8 → 73.2 |
| `GET /repertory/{id}` | 21.2 → 36.9 | 16.3 → 33.7 | 51.4 → 72.1 |
| `GET /spectators/{jmbg}` | 18.5 → 37.1 | 14.5 → 33.6 | 46.3 → 71.6 |
| `GET /performances` | 69.0 → 47.2 | 62.4 → 41.9 | 106.3 → 78.5 |
| `POST /tickets/performance/available-seats` | 40.2 → 25.6 | 35.1 → 19.1 | 75.8 → 65.8 |
| `POST /tickets` (kupovina) | 60.7 → 50.9 | 57.4 → 44.1 | 89.4 → 85.1 |

Propusnost: pregled ~510 → ~493 zahteva/s (−3%), kupovina ~54 → ~84 karte/s. U merenjima nije bilo grešaka (2 greške gateway-a desile su se tokom zagrevanja, videti niže).

**Tumačenje.**
- **Jednostavna čitanja su kod servisa sporija za ~17 ms (medijana).** To je cena dodatnog mrežnog skoka kroz API Gateway (klijent → gateway → servis), koja kod malih odgovora dominira.
- **Kupovina i slobodna mesta su kod servisa brži**, iako kupovina ima 2 dodatna REST poziva. Najverovatniji razlog: u monolitu `Ticket` ima JPA relacije na `TicketAgent` i `Spectator`, pa učitavanje karata jednog izvođenja (provera zauzetog mesta) povlači i blagajnike i gledaoce. U ticketing-service to su obične kolone sa ID-em, pa je upit lakši. Razdvajanje modela podataka je ovde donelo više nego što su koštali mrežni pozivi. (Ovo objašnjenje nije potvrđeno SQL logovima.)
- **`GET /performances` je u monolitu sporiji** i zato što monolit ima 13 izvođenja više (stari demo podaci) i veći JSON (11,2 KB naspram 9,8 KB), pa ovaj red nije potpuno uporediv.
- **p95 je kod servisa jednak ili bolji**, jer se opterećenje deli na dva procesa (dva JVM-a, dva pool-a konekcija).

**Uočen problem na gateway-u.** Pod opterećenjem gateway povremeno baci `ResourceAccessException` / `NullPointerException` iz JDK `HttpClient`-a (1 put u preliminarnom merenju i 2 puta tokom zagrevanja, od ukupno preko 100.000 zahteva). Klijent tada dobije 500. Moguće rešenje: prebaciti Spring Cloud Gateway MVC na drugi HTTP klijent (npr. Apache HttpClient 5) ili dodati retry filter za idempotentne GET zahteve.

**Ograničenja.** Sve radi na jednom računaru (servisi se ne takmiče za mrežu, ali se takmiče za procesor), merenje je kratko (3 × 60 s po scenariju), a opterećenje umereno (20 + 5 korisnika). Rezultati pokazuju trend, a ne apsolutne brojke za produkciju.

## 10. Fuzz testiranje

**Alat.** [Schemathesis](https://schemathesis.readthedocs.io) 4.28 iz OpenAPI opisa API-ja (`/v3/api-docs`, dobijen dodavanjem `springdoc-openapi-starter-webmvc-api` u monolit i oba servisa) automatski generiše hiljade zahteva: pogrešne tipove, prazna i `null` polja, ogromne brojeve, kontrolne i Unicode znakove, neispravan JSON, nepostojeće ID-eve, kao i nizove poziva (stateful: napravi → pročitaj → obriši). Za svaki odgovor proverava da li je server pukao (5xx), da li je prihvatio neispravan ulaz i da li odgovor odgovara opisu API-ja.

Pokretanje: `fuzz/run.sh monolith|users|ticketing`. Skripta pre svakog pokretanja vraća baze iz backup-a, jer fuzzer šalje i POST/PATCH/DELETE zahteve. Sve mete imaju isti seed (42) i isti broj primera (100 po operaciji). Izveštaji su u `fuzz/results/krug1/` i `fuzz/results/krug2/`.

**Rezultati prvog kruga (pre ispravki):**

| Meta | Operacija | Generisano zahteva | Jedinstvenih nalaza | Od toga 5xx | Trajanje |
|---|---|---|---|---|---|
| monolit | 25 | 2881 | 54 | 10 | 6,8 min |
| users-service | 8 | 828 | 28 | 2 | 22 s |
| ticketing-service | 17 | 4203 | 84 | 9 | 28 min |

Najveći deo nalaza (`Undocumented HTTP status code`, `Response violates schema`) nisu kvarovi: springdoc ne zna da endpoint vraća 404/400, niti da polje može biti `null`. Stvarni problemi robusnosti su odgovori 5xx na neispravan ulaz.

**Uzroci 5xx odgovora** (isti u monolitu i servisima, jer je kod prenesen):
- **Nema validacije ulaza.** POST bez `id` → `JpaSystemException: Identifier ... must be manually assigned` (500). Karta bez izvođenja → `NullPointerException` (500). Referenca na nepostojeći entitet → `TransientPropertyValueException` (500). `null` u obaveznoj koloni ili predugačak string (monolit, `varchar(20)`) → greška baze (500).
- **NUL znak (`\u0000`) u stringu** → Postgres odbija upis (`invalid byte sequence for encoding "UTF8": 0x00`) → 500.
- **Brisanje entiteta koji je u upotrebi** (predstava sa izvođenjima, gledalac sa kartama u monolitu) → povreda stranog ključa → 500 umesto 409 Conflict.

**Nalazi specifični za mikroservise:**
1. **Neispravan ulaz otvara circuit breaker i blokira prodaju (potvrđeno ručno).** `UsersServiceClient` je sastavljao URL spajanjem stringova (`baseUrl + "/ticket-agents/" + id`), a `RestTemplate` taj string tumači kao URI šablon. ID koji sadrži `{` baca `IllegalArgumentException` pre nego što se ijedan poziv pošalje. Circuit breaker je to brojao kao kvar users-service-a: posle 5 takvih zahteva kolo se otvaralo i **10 sekundi je svaka kupovina karte, i ispravna, dobijala 503**. Svako ko može da pošalje zahtev mogao je tako stalno da blokira prodaju. U monolitu ovaj problem ne postoji, jer nema mrežnog poziva.
2. **Brisanje gledaoca ne proverava karte.** U monolitu `DELETE /spectators/{jmbg}` za gledaoca sa kartama puca na stranom ključu (500, ali podaci ostaju ispravni). U users-service brisanje je uspevalo (204), a karte u `ticketing_db` ostajale su bez vlasnika. Posledica podele baza: referencijalni integritet više ne čuva baza.
3. **Sporost pod lošim ulazom.** Fuzzing ticketing-service-a je trajao 28 min, a monolita 7 min. Svaki zahtev sa neispravnim ID-em gledaoca ili blagajnika prolazio je kroz retry (3 pokušaja × 300 ms) kad users-service vrati 5xx.

**Bezbednosni nalaz (i u monolitu):** `GET /ticket-agents` vraća lozinke blagajnika u čistom tekstu.

## 11. Ispravke posle fuzz testiranja i saga brisanja gledaoca

Ispravljeni su **samo servisi**. Monolit ostaje nepromenjen kao polazna tačka, pa se u drugom krugu fuzz testiranja vidi razlika.

### 11.1 Ispravke robusnosti (oba servisa)
- **Provera ulaza (Bean Validation).** Entiteti koji stižu u telu zahteva imaju pravila: obavezna polja pri kreiranju (validaciona grupa `OnCreate`, pa PATCH i dalje dozvoljava delimične izmene), maksimalne dužine, JMBG od tačno 13 cifara, ID-evi samo od slova, cifara i `. _ -`, i zabrana kontrolnih znakova (npr. NUL, koji Postgres odbija). Loš ulaz dobija **400 sa spiskom grešaka** pre nego što stigne do baze.
- **`ControlCharacterFilter`** odbija putanje sa kontrolnim znakovima (npr. `/spectators/%00`) i neispravno kodirane putanje sa 400.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`): povreda stranog ključa (brisanje predstave koja ima izvođenja) → **409 Conflict**, a referenca na nepostojeći objekat → **400**. Ranije je oboje bilo 500.
- **Kreiranje više ne prepisuje postojeće objekte.** `save()` je za postojeći ID tiho radio izmenu, a sada POST sa zauzetim ID-em vraća 409. Kupovina proverava i da izvođenje postoji i da je broj mesta u opsegu sale.
- `GET /plays/{id}` i `GET /performances/{id}` za nepostojeći ID vraćaju 404, a ne prazan odgovor 200.
- **Lozinke blagajnika** se više ne vraćaju u odgovorima (`@JsonProperty(access = WRITE_ONLY)`); prijava i dalje radi.

### 11.2 Circuit breaker više ne reaguje na loš ulaz
`UsersServiceClient`: (1) ID koji ne odgovara formatu odbija se bez mrežnog poziva; (2) ID se prosleđuje kao parametar URI šablona (`/ticket-agents/{id}`), pa se ispravno kodira; (3) circuit breaker broji samo `ResourceAccessException` (timeout, odbijena konekcija) i `HttpServerErrorException` (5xx), preko `record-exceptions`; (4) fallback metode hvataju samo te izuzetke i `CallNotPermittedException`. Provereno ručno: 7 zahteva sa `{x}` kao ID-em blagajnika dobija 400, a ispravna kupovina odmah posle toga prolazi (200). Pre ispravke kolo bi se otvorilo i kupovina bi dobila 503.

### 11.3 Saga brisanja gledaoca (koreografija, RabbitMQ)
**Problem** (fuzz nalaz 2): users-service je brisao gledaoca, a njegove karte su u `ticketing_db` ostajale bez vlasnika. Pošto su baze odvojene, strani ključ više ne može da spreči brisanje.

**Rešenje: saga sa koreografijom.** Nijedan servis ne upravlja drugim. Svaki objavljuje događaje na zajednički RabbitMQ topic exchange `pozoriste.events` i reaguje na tuđe:

```
klijent --DELETE /spectators/{jmbg}--> users-service
    users-service:      gledalac -> DELETION_PENDING, objavi spectator.deletion.requested   (odgovor 202 Accepted)
    ticketing-service:  ima li gledalac karte za izvođenja koja još nisu održana?
        da -> objavi spectator.deletion.rejected
                users-service: kompenzacija, gledalac -> ACTIVE
        ne -> karte za prošla izvođenja anonimizuj (spectatorId = NULL), objavi spectator.deletion.approved
                users-service: obriši gledaoca
```

- **Semantičko zaključavanje:** dok traje saga (`DELETION_PENDING`), ticketing-service tom gledaocu ne prodaje nove karte (409). Inače bi karta kupljena usred sage ostala bez vlasnika.
- **Pravilo za karte:** brisanje se odbija ako postoji karta za buduće izvođenje. Karte za prošla izvođenja se anonimizuju, pa istorija prodaje ostaje.
- **Trajni redovi** (`durable`): ako ticketing-service ne radi, zahtev čeka u redu i saga se završava kad se servis vrati.
- **Ako RabbitMQ ne radi**, objava baca izuzetak i transakcija se poništava (gledalac ostaje ACTIVE), a klijent dobija 503.
- **Klase događaja** (`Requested`, `Approved`, `Rejected`) postoje kao kopije u oba servisa, bez zajedničke biblioteke, da servisi ostanu nezavisni. JSON konverter tip određuje iz parametra listener-a, a ne iz imena Java klase pošiljaoca.

**Ručno testirano:**

| Slučaj | Ishod |
|---|---|
| Gledalac bez karata | obrisan |
| Gledalac sa kartom samo za prošlo izvođenje | karta anonimizovana, gledalac obrisan |
| Gledalac sa 22 buduće karte | odbijeno, vraćen u ACTIVE |
| ticketing-service ugašen tokom brisanja | gledalac čeka u `DELETION_PENDING`, obrisan čim se ticketing vratio |

**Poznata ograničenja.** Objava događaja i upis u bazu nisu atomični: ako commit padne posle uspešne objave, događaj je već poslat. Pravo rešenje je *transactional outbox* (događaj se upisuje u tabelu u istoj transakciji, a zaseban proces ga objavljuje); za obim ovog projekta to nije urađeno. Listener-i su idempotentni u smislu da ponovljeni `approved`/`rejected` za gledaoca koji više nije u `DELETION_PENDING` ne radi ništa.

**Pokretanje:** RabbitMQ se pokreće u Docker-u (videti README). Na Docker Desktop-u za Windows kontejner mora imati `-u rabbitmq`, inače pada sa `erlang.cookie: eacces`. Web konzola: http://localhost:15672 (guest/guest).

### 11.4 Drugi krug fuzz testiranja

Isti alat, seed i broj primera kao u prvom krugu. Monolit nije menjan, pa za njega važe rezultati prvog kruga.

| Meta | 5xx (1. krug → 2. krug) | Jedinstvenih nalaza | Trajanje |
|---|---|---|---|
| monolit (nepromenjen) | 10 | 54 | 6,8 min |
| users-service | 2 → **0** | 28 → 17 | 22 s → 1,5 min |
| ticketing-service | 9 → **0** | 84 → 43 | 28 min → 6 min |

Drugi krug je našao još jedan pravi bug, ispravljen pre završnog pokretanja: izvođenje je moglo da se napravi bez predstave, sale i termina. Posle takvog upisa `GET /performances` je pucao (500) **za sve korisnike**, jer lista čita naziv predstave. Sada su ta tri polja obavezna pri kreiranju, a lista preskače nepotpune redove.

Preostali nalazi nisu kvarovi robusnosti:
- `Undocumented HTTP status code` i `Response violates schema`: springdoc u OpenAPI opisu ne navodi 400/404/409 odgovore niti `null` polja.
- `API rejected schema-compliant request`: zahtev je formalno ispravan, ali referencira nešto što ne postoji (npr. izvođenje `pr-01`), pa je 400 ispravan odgovor. OpenAPI šema ne može da izrazi „ID mora postojati u bazi“.
- `POST /repertory/{id}/performances/{id}` vraća prazan 200 bez `Content-Type`; kozmetički nalaz, ponašanje preuzeto iz monolita.

Users-service je u drugom krugu sporiji (22 s → 1,5 min) jer fuzzer sada ide dublje: zahtevi prolaze validaciju, a `DELETE /spectators` pokreće sagu. Ticketing je brži (28 → 6 min) jer loši ID-evi više ne prolaze kroz retry prema users-service-u.

## 12. Testovi servisa

Svaki servis ima svoje testove, koje njegov CI/CD pipeline pokreće (`./mvnw -B verify`) nezavisno od drugog servisa. Testovi ne traže bazu ni RabbitMQ: logika se testira unit testovima sa Mockito mock-ovima, a HTTP sloj sa `@WebMvcTest` (samo kontroler, validacija, handler grešaka i filter). Zato rade i u GitHub Actions bez dodatnih servisa.

| Servis | Test klasa | Šta proverava | Testova |
|---|---|---|---|
| users-service | `SpectatorDeletionSagaTest` | saga: pokretanje → DELETION_PENDING + događaj; dvostruko brisanje → odbijeno; broker ne radi → izuzetak; approved → brisanje; ponovljen approved → ignorisan; rejected → kompenzacija (ACTIVE) | 7 |
| users-service | `SpectatorControllerTest` | validacija (JMBG, obavezna polja, e-mail, NUL znak) → 400; duplikat → 409; DELETE → 202 / 404 / 409 / 503; putanja sa `%00` → 400 | 9 |
| users-service | `TicketAgentControllerTest` | lozinke se ne vraćaju; nepostojeći blagajnik → 404; prijava radi | 3 |
| ticketing-service | `SpectatorDeletionListenerTest` | pravilo sage: bez karata → approved; buduća karta → rejected; samo prošle → anonimizacija + approved; karta bez termina → rejected | 4 |
| ticketing-service | `TicketServiceTest` | kupovina: ispravna; zauzeto mesto („007“ = „7“); mesto van sale; nepostojeće izvođenje; duplikat ID-a; nepostojeći blagajnik; gledalac u brisanju; slobodna mesta; nepostojeće izvođenje → 404 | 9 |
| ticketing-service | `UsersServiceClientTest` | neispravan ID (`{x}`, `../`, razmak…) → bez mrežnog poziva; ID kao parametar URI šablona; 404 = „ne postoji“ | 8 |
| ticketing-service | `TicketControllerTest` | ispravna kupovina → 200 + mejl; neispravno telo → 400 sa detaljima; users-service ne radi → 503; gledalac u brisanju → 409 | 4 |

Ukupno **44 testa** (users 19, ticketing 25). Većina testova direktno pokriva nalaze fuzz testiranja (sekcija 10), pa bi se povratak greške odmah video u CI-ju.
