<template>
  <div class="ticket-page-wrapper">
    <div class="ticket-page container py-5">
      <!-- Meni / Tabovi -->
      <nav class="mb-4">
        <ul class="nav nav-tabs justify-content-center">
          <li class="nav-item">
            <router-link
              to="/purchase"
              class="nav-link"
              :class="{ active: isActive('/purchase') }"
            >
              ULAZNICE
            </router-link>
          </li>
          <li class="nav-item">
            <router-link
              to="/spectators"
              class="nav-link"
              :class="{ active: isActive('/spectators') }"
            >
              GLEDAOCI
            </router-link>
          </li>
          <li class="nav-item">
            <router-link
              to="/repertory"
              class="nav-link"
              :class="{ active: isActive('/repertory') }"
            >
              REPERTOAR
            </router-link>
          </li>
        </ul>
      </nav>

      <h2 class="mb-4 text-white">Kupovina karata</h2>

      <!-- Gledalac -->
      <div class="mb-3">
        <label for="spectator" class="form-label text-white">Gledalac:</label>
        <select id="spectator" class="form-select" v-model="selectedSpectator">
          <option value="" disabled>Izaberite gledaoca</option>
          <option v-for="s in spectators" :key="s.jmbg" :value="s.jmbg">
            {{ s.firstName }} {{ s.lastName }}
          </option>
        </select>
      </div>

      <!-- Predstava -->
      <div class="mb-3">
        <label for="play" class="form-label text-white">Predstava:</label>
        <select
          id="play"
          class="form-select"
          v-model="selectedPlay"
          @change="onPlayChange"
        >
          <option value="" disabled>Izaberite predstavu</option>
          <option v-for="p in plays" :key="p.id" :value="p.id">
            {{ p.title }}
          </option>
        </select>
      </div>

      <!-- Datum -->
      <div class="mb-3">
        <label class="form-label text-white">Datum:</label>
        <PerformanceCalendar
          v-model="selectedDate"
          :available-dates="availableDates"
          :disabled="!selectedPlay"
          @update:model-value="onDateChange"
        />
        <small
          v-if="selectedPlay && availableDates.length === 0"
          class="text-white-50"
        >
          Ova predstava nema predstojećih izvođenja
        </small>
      </div>

      <!-- Vreme -->
      <div class="mb-3">
        <label for="performance" class="form-label text-white">Vreme:</label>
        <select
          id="performance"
          class="form-select"
          v-model="selectedPerformance"
          @change="onPerformanceChange"
          :disabled="!selectedDate || performances.length === 0"
        >
          <option value="" disabled>Izaberite vreme</option>
          <option v-for="perf in performances" :key="perf.id" :value="perf.id">
            {{ perf.time }}
          </option>
        </select>
        <small
          v-if="selectedDate && performances.length === 0"
          class="text-white-50"
        >
          Nema dostupnih termina za izabrani datum
        </small>
      </div>

      <!-- Sediste -->
      <div class="mb-3">
        <label for="seat" class="form-label text-white">Sediste:</label>
        <select
          id="seat"
          class="form-select"
          v-model.number="selectedSeat"
          :disabled="!selectedPerformance || availableSeats.length === 0"
        >
          <option :value="null" disabled>Izaberite sediste</option>
          <option v-for="seat in availableSeats" :key="seat" :value="seat">
            {{ seat }}
          </option>
        </select>
        <small
          v-if="selectedPerformance && availableSeats.length === 0"
          class="text-white-50"
        >
          Nema dostupnih mesta za ovaj termin
        </small>
      </div>

      <!-- Detalji -->
      <div
        v-if="selectedPerformanceDetails"
        class="alert alert-info bg-opacity-50"
      >
        <strong>Detalji izvodjenja:</strong><br />
        Sala: {{ selectedPerformanceDetails.auditoriumName }}<br />
        Termin:
        {{
          formatDateTime(
            selectedPerformanceDetails.date,
            selectedPerformanceDetails.time
          )
        }}<br />
        <span v-if="selectedSeat">Sediste: {{ selectedSeat }}</span>
      </div>

      <!-- Dugme -->
      <button
        type="button"
        class="btn btn-primary mt-3"
        :disabled="!canPurchase"
        @click="handlePurchase"
      >
        Kupite kartu
      </button>

      <div v-if="purchaseSuccess" class="alert alert-success mt-3">
        Karta je uspešno kupljena!
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE_URL } from "@/config";
import PerformanceCalendar from "@/components/PerformanceCalendar.vue";

export default {
  name: "TicketPurchase",
  components: { PerformanceCalendar },
  data() {
    return {
      spectators: [],
      plays: [],
      playPerformances: [],
      performances: [],
      availableSeats: [],
      selectedSpectator: "",
      selectedPlay: "",
      selectedDate: "",
      selectedPerformance: "",
      selectedSeat: null,
      purchaseSuccess: false,
    };
  },
  computed: {
    canPurchase() {
      return Boolean(
        this.selectedSpectator &&
          this.selectedPlay &&
          this.selectedDate &&
          this.selectedPerformance &&
          this.selectedSeat !== null
      );
    },
    // Datumi predstojećih izvođenja izabrane predstave; ostali su u kalendaru crveni
    availableDates() {
      const now = new Date();
      return [
        ...new Set(
          this.playPerformances
            .filter((p) => new Date(`${p.date}T${p.time}`) > now)
            .map((p) => p.date)
        ),
      ];
    },
    selectedPerformanceDetails() {
      return this.performances.find((p) => p.id === this.selectedPerformance);
    },
  },
  async mounted() {
    await this.fetchSpectators();
    await this.fetchPlays();
  },
  methods: {
    isActive(path) {
      return this.$route.path === path;
    },
    async fetchSpectators() {
      try {
        const res = await axios.get(`${API_BASE_URL}/spectators`);
        this.spectators = res.data;
      } catch (err) {
        console.error("Greška prilikom učitavanja gledalaca:", err);
      }
    },
    async fetchPlays() {
      try {
        const res = await axios.get(`${API_BASE_URL}/plays`);
        this.plays = res.data;
      } catch (err) {
        console.error("Greška prilikom učitavanja predstava:", err);
      }
    },
    async fetchPlayPerformances() {
      if (!this.selectedPlay) return;
      try {
        const res = await axios.get(
          `${API_BASE_URL}/performances/by-play/${this.selectedPlay}`
        );
        // 204 (predstava bez izvođenja) vraća prazno telo
        this.playPerformances = Array.isArray(res.data) ? res.data : [];
      } catch (err) {
        console.error("Greška prilikom učitavanja termina:", err);
        this.playPerformances = [];
      }
    },
    async fetchAvailableSeats(performanceId) {
      if (!performanceId) return;
      try {
        const res = await axios.post(
          `${API_BASE_URL}/tickets/performance/available-seats`,
          { id: performanceId }
        );
        this.availableSeats = res.data;
      } catch (err) {
        console.error("Greška prilikom učitavanja slobodnih mesta:", err);
        this.availableSeats = [];
      }
    },
    async onPlayChange() {
      this.selectedDate = "";
      this.selectedPerformance = "";
      this.selectedSeat = null;
      this.playPerformances = [];
      this.performances = [];
      this.availableSeats = [];
      await this.fetchPlayPerformances();
    },
    onDateChange() {
      this.selectedPerformance = "";
      this.selectedSeat = null;
      this.availableSeats = [];
      this.performances = this.playPerformances.filter(
        (perf) => perf.date === this.selectedDate
      );
    },
    async onPerformanceChange() {
      this.selectedSeat = null;
      await this.fetchAvailableSeats(this.selectedPerformance);
    },
    generateTicketId() {
      const random = Math.random().toString(36).substring(2, 8);
      const timestamp = Date.now().toString(36);
      return `ul_${random}_${timestamp}`.substring(0, 50);
    },
    async handlePurchase() {
      if (!this.canPurchase) return;
      const ticketAgentId = localStorage.getItem("ticketAgentId");
      if (!ticketAgentId) {
        alert(
          "Prijavite se ponovo - nije poznato koji blagajnik prodaje kartu."
        );
        return;
      }
      try {
        const ticketData = {
          id: this.generateTicketId(),
          price: "300 RSD",
          numberOfSeatInAuditorium: this.selectedSeat.toString(),
          spectatorId: this.selectedSpectator,
          performance: { id: this.selectedPerformance },
          ticketAgentId,
        };
        await axios.post(`${API_BASE_URL}/tickets`, ticketData);
        this.purchaseSuccess = true;
        setTimeout(this.resetForm, 1500);
      } catch (err) {
        console.error(err);
        alert(
          [err.response?.data?.message, ...(err.response?.data?.details || [])]
            .filter(Boolean)
            .join("\n") ||
            err.response?.data ||
            "Greška prilikom kupovine karte."
        );
      }
    },
    resetForm() {
      this.selectedSpectator = "";
      this.selectedPlay = "";
      this.selectedDate = "";
      this.selectedPerformance = "";
      this.selectedSeat = null;
      this.playPerformances = [];
      this.performances = [];
      this.availableSeats = [];
      this.purchaseSuccess = false;
    },
    formatDateTime(dateString, timeString) {
      if (!dateString || !timeString) return "";
      const [year, month, day] = dateString.split("-");
      return `${day}.${month}.${year} ${timeString}`;
    },
  },
};
</script>

<style scoped>
.ticket-page-wrapper {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background-image: url("https://media.istockphoto.com/id/1295114854/photo/empty-red-armchairs-of-a-theater-ready-for-a-show.jpg?s=612x612&w=0&k=20&c=0rDtwzMmLbqe_8GuGw2dpjkD0MsXGywJmdmg0jDbMxQ=");
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  padding: 2rem;
  width: 100%;
}

.ticket-page {
  background: rgba(0, 0, 0, 0.6);
  padding: 2rem;
  border-radius: 1rem;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.5);
  max-width: 600px;
  width: 100%;
  color: #fff;
}

.form-control,
.form-select,
.btn {
  background: rgba(255, 255, 255, 0.9);
  color: #000;
  border-radius: 0.5rem;
  border: 1px solid #ccc;
}

.btn-primary {
  background-color: #007bff;
  border-color: #007bff;
}

.btn-primary:hover {
  background-color: #0056b3;
  border-color: #0056b3;
}

.text-white-50 {
  color: rgba(255, 255, 255, 0.7) !important;
}
</style>
