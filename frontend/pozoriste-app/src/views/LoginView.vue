<template>
  <div
    class="login-page-wrapper d-flex align-items-center justify-content-center min-vh-100"
  >
    <div class="login-card shadow-lg">
      <div class="card-body p-5">
        <div class="text-center mb-4">
          <i class="bi bi-ticket-perforated login-icon"></i>
          <h2 class="login-title mt-3 mb-1 fw-bold">Pozorište</h2>
          <p class="text-muted">Sistem za upravljanje kartama</p>
        </div>

        <form @submit.prevent="handleLogin">
          <div class="mb-3">
            <label for="username" class="form-label text-white"
              >Korisničko ime</label
            >
            <input
              type="text"
              class="form-control form-control-lg"
              id="username"
              v-model="username"
              placeholder="Unesite korisničko ime"
              required
            />
          </div>

          <div class="mb-4">
            <label for="password" class="form-label text-white">Lozinka</label>
            <input
              type="password"
              class="form-control form-control-lg"
              id="password"
              v-model="password"
              placeholder="Unesite lozinku"
              required
            />
          </div>

          <div
            v-if="error"
            class="alert alert-danger d-flex align-items-center"
            role="alert"
          >
            <i class="bi bi-exclamation-triangle-fill me-2"></i>
            <span>{{ error }}</span>
          </div>

          <button
            type="submit"
            class="btn btn-primary btn-lg w-100 fw-semibold"
          >
            <i class="bi bi-box-arrow-in-right me-2"></i>Prijavi se
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE_URL } from "@/config";

export default {
  name: "LoginView",
  data() {
    return {
      username: "",
      password: "",
      error: null,
    };
  },
  methods: {
    async handleLogin() {
      if (!this.username || !this.password) {
        this.error = "Molimo popunite sva polja";
        return;
      }

      try {
        const params = new URLSearchParams();
        params.append("username", this.username);
        params.append("password", this.password);

        const response = await axios.post(
          `${API_BASE_URL}/ticket-agents/login`,
          params,
          { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
        );

        if (response.status === 200) {
          this.error = null;
          localStorage.setItem("username", this.username);
          // ID prijavljenog blagajnika - šalje se pri kupovini karte i dodavanju gledaoca
          const agents = await axios.get(`${API_BASE_URL}/ticket-agents`);
          const agent = agents.data.find((a) => a.username === this.username);
          if (agent) localStorage.setItem("ticketAgentId", agent.id);
          this.$router.push("/purchase");
        }
      } catch (err) {
        if (err.response && err.response.status === 401) {
          this.error = "Neispravno korisničko ime ili lozinka";
        } else {
          this.error = "Došlo je do greške pri prijavi";
        }
        console.error(err);
      }
    },
  },
};
</script>

<style scoped>
.login-page-wrapper {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-image: url("https://media.istockphoto.com/id/1295114854/photo/empty-red-armchairs-of-a-theater-ready-for-a-show.jpg?s=612x612&w=0&k=20&c=0rDtwzMmLbqe_8GuGw2dpjkD0MsXGywJmdmg0jDbMxQ=");
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  padding: 2rem;
}

.login-card {
  background: rgba(0, 0, 0, 0.6); /* providan crni okvir */
  padding: 2rem;
  border-radius: 1rem;
  max-width: 450px;
  width: 100%;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.5);
}

.login-icon {
  font-size: 4rem;
  color: #ffd700; /* svetla ikona za kontrast */
}

.login-title {
  color: #fff;
}

.form-label {
  color: #fff;
}

.form-control {
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

.alert {
  font-size: 0.95rem;
}
</style>
