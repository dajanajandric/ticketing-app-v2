<template>
  <div class="repertory-page-wrapper">
    <div class="repertory-page container py-5">
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

      <h2 class="text-center mb-5 text-white">Repertoar JUL 2021</h2>

      <div v-if="performances.length > 0" class="table-responsive">
        <table class="table table-dark table-hover align-middle shadow-lg">
          <thead>
            <tr>
              <th>Naziv predstave</th>
              <th>Datum</th>
              <th>Vrijeme</th>
              <th>Sala</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="performance in performances" :key="performance.id">
              <td>{{ performance.playId }}</td>
              <td>{{ formatDate(performance.date) }}</td>
              <td>{{ formatTime(performance.time) }}</td>
              <td>{{ performance.auditoriumName }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="alert-info p-3 mt-4 text-center">
        Nema zakazanih izvođenja.
      </div>
    </div>
  </div>
</template>

<script>
import { API_BASE_URL } from "@/config";

export default {
  name: "RepertoryView",
  data() {
    return {
      performances: [],
    };
  },
  methods: {
    async fetchPerformances() {
      try {
        const response = await fetch(`${API_BASE_URL}/performances`);
        if (!response.ok)
          throw new Error("Greška prilikom učitavanja repertoara.");
        this.performances = await response.json();
      } catch (error) {
        console.error(error);
      }
    },
    formatDate(dateStr) {
      if (!dateStr) return "";
      const d = new Date(dateStr);
      return d.toLocaleDateString("sr-RS", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      });
    },
    formatTime(timeStr) {
      if (!timeStr) return "";
      const [hour, minute] = timeStr.split(":");
      return `${hour}:${minute}`;
    },
    isActive(path) {
      return this.$route.path === path;
    },
  },
  mounted() {
    this.fetchPerformances();
  },
};
</script>

<style scoped>
/* Wrapper sa pozadinom */
.repertory-page-wrapper {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  background-image: url("https://media.istockphoto.com/id/1295114854/photo/empty-red-armchairs-of-a-theater-ready-for-a-show.jpg?s=612x612&w=0&k=20&c=0rDtwzMmLbqe_8GuGw2dpjkD0MsXGywJmdmg0jDbMxQ=");
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  padding: 2rem;
}

/* Glavni sadržaj */
.repertory-page {
  background: rgba(20, 20, 35, 0.92);
  padding: 2rem;
  border-radius: 1rem;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.7);
  color: #fff;
}

/* Tabovi / meni */
.nav-tabs {
  justify-content: center;
  margin-bottom: 2rem;
}

.nav-tabs .nav-link {
  color: #f8f9fa;
  font-weight: bold;
  background-color: rgba(40, 40, 60, 0.6);
  margin: 0 0.5rem;
  border-radius: 0.5rem;
  border: 1px solid rgba(212, 175, 55, 0.3);
  transition: background 0.3s;
}

.nav-tabs .nav-link:hover {
  background-color: rgba(139, 0, 0, 0.5);
}

.nav-tabs .nav-link.active {
  background-color: rgba(212, 175, 55, 0.8);
  color: #000;
}

/* Tabela */
.table-responsive {
  border-radius: 0.5rem;
  overflow: hidden;
}

.table-dark {
  background-color: rgba(30, 30, 50, 0.8);
  margin-bottom: 0;
}

.table-dark thead th {
  border-bottom: 2px solid #d4af37;
  font-weight: bold;
  background-color: rgba(139, 0, 0, 0.6);
  color: #f8f9fa;
}

.table-dark tbody tr:hover {
  background-color: rgba(139, 0, 0, 0.3);
  cursor: pointer;
}

.alert-info {
  background: rgba(255, 255, 255, 0.1);
  color: #d4af37;
  border: 1px solid rgba(212, 175, 55, 0.5);
  border-radius: 0.5rem;
  margin-top: 1rem;
  text-align: center;
}
</style>
