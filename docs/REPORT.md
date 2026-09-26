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

Puna vizuelna analiza (dijagram, kartice modula, before/after ispravki): https://claude.ai/code/artifact/bf619154-bc69-44b8-8a3a-20ab600b85d4

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
