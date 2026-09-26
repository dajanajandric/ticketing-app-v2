<template>
  <div class="spectators-page-wrapper">
    <div class="spectators-page container py-5">
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

      <h2 class="mb-4 text-white">Gledaoci</h2>

      <!-- Dugme za dodavanje novog gledaoca -->
      <button class="btn btn-success mb-4" @click="showAddForm = !showAddForm">
        {{ showAddForm ? "Otkaži" : "+ Dodaj novog gledaoca" }}
      </button>

      <!-- Forma za dodavanje novog gledaoca -->
      <div v-if="showAddForm" class="add-form mb-4">
        <h4 class="text-white mb-3">Novi gledalac</h4>
        <form @submit.prevent="handleAddSpectator">
          <div class="row">
            <div class="col-md-6 mb-3">
              <label for="jmbg" class="form-label text-white"
                >JMBG (13 cifara):</label
              >
              <input
                type="text"
                id="jmbg"
                class="form-control"
                v-model="newSpectator.jmbg"
                maxlength="13"
                pattern="[0-9]{13}"
                required
                placeholder="1234567890123"
              />
              <small class="text-white-50"
                >{{ newSpectator.jmbg.length }}/13</small
              >
            </div>
            <div class="col-md-6 mb-3">
              <label for="firstName" class="form-label text-white">Ime:</label>
              <input
                type="text"
                id="firstName"
                class="form-control"
                v-model="newSpectator.firstName"
                required
                placeholder="Petar"
              />
            </div>
          </div>

          <div class="row">
            <div class="col-md-6 mb-3">
              <label for="lastName" class="form-label text-white"
                >Prezime:</label
              >
              <input
                type="text"
                id="lastName"
                class="form-control"
                v-model="newSpectator.lastName"
                required
                placeholder="Petrović"
              />
            </div>
            <div class="col-md-6 mb-3">
              <label for="phoneNumber" class="form-label text-white"
                >Broj telefona:</label
              >
              <input
                type="tel"
                id="phoneNumber"
                class="form-control"
                v-model="newSpectator.phoneNumber"
                placeholder="065123456"
              />
            </div>
          </div>

          <div class="mb-3">
            <label for="emailAddress" class="form-label text-white"
              >Email adresa:</label
            >
            <input
              type="email"
              id="emailAddress"
              class="form-control"
              v-model="newSpectator.emailAddress"
              placeholder="petar@example.com"
            />
          </div>

          <div class="d-flex gap-2">
            <button
              type="submit"
              class="btn btn-primary"
              :disabled="!isFormValid"
            >
              Sačuvaj
            </button>
            <button type="button" class="btn btn-secondary" @click="cancelAdd">
              Otkaži
            </button>
          </div>
        </form>
      </div>

      <!-- Forma za izmenu gledaoca -->
      <div v-if="showEditForm" class="add-form mb-4">
        <h4 class="text-white mb-3">Izmena gledaoca</h4>
        <form @submit.prevent="handleUpdateSpectator">
          <div class="row">
            <div class="col-md-6 mb-3">
              <label class="form-label text-white">JMBG:</label>
              <input
                type="text"
                class="form-control"
                :value="editingSpectator.jmbg"
                disabled
              />
              <small class="text-white-50">JMBG se ne može menjati</small>
            </div>
            <div class="col-md-6 mb-3">
              <label for="editFirstName" class="form-label text-white"
                >Ime:</label
              >
              <input
                type="text"
                id="editFirstName"
                class="form-control"
                v-model="editingSpectator.firstName"
                required
              />
            </div>
          </div>

          <div class="row">
            <div class="col-md-6 mb-3">
              <label for="editLastName" class="form-label text-white"
                >Prezime:</label
              >
              <input
                type="text"
                id="editLastName"
                class="form-control"
                v-model="editingSpectator.lastName"
                required
              />
            </div>
            <div class="col-md-6 mb-3">
              <label for="editPhoneNumber" class="form-label text-white"
                >Broj telefona:</label
              >
              <input
                type="tel"
                id="editPhoneNumber"
                class="form-control"
                v-model="editingSpectator.phoneNumber"
              />
            </div>
          </div>

          <div class="mb-3">
            <label for="editEmailAddress" class="form-label text-white"
              >Email adresa:</label
            >
            <input
              type="email"
              id="editEmailAddress"
              class="form-control"
              v-model="editingSpectator.emailAddress"
            />
          </div>

          <div class="d-flex gap-2">
            <button type="submit" class="btn btn-primary">
              Sačuvaj izmene
            </button>
            <button type="button" class="btn btn-secondary" @click="cancelEdit">
              Otkaži
            </button>
          </div>
        </form>
      </div>

      <!-- Loading state -->
      <div v-if="loading" class="text-center text-white">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Učitavanje...</span>
        </div>
      </div>

      <!-- Error state -->
      <div v-if="error" class="alert alert-danger">
        {{ error }}
      </div>

      <!-- Tabela gledalaca -->
      <div v-if="!loading && !error" class="table-responsive">
        <table class="table table-dark table-striped table-hover">
          <thead>
            <tr>
              <th scope="col">Ime i prezime</th>
              <th scope="col">Broj telefona</th>
              <th scope="col">Email adresa</th>
              <th scope="col"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="spectator in spectators" :key="spectator.jmbg">
              <td>{{ spectator.firstName }} {{ spectator.lastName }}</td>
              <td>{{ spectator.phoneNumber || "N/A" }}</td>
              <td>{{ spectator.emailAddress || "N/A" }}</td>
              <td>
                <button
                  class="btn btn-sm me-2"
                  @click="editSpectator(spectator)"
                  title="Izmeni"
                >
                  <i class="bi bi-pen" style="color: white"></i>
                </button>
                <button
                  class="btn btn-sm"
                  @click="deleteSpectator(spectator.jmbg)"
                  title="Obriši"
                >
                  <i class="bi bi-trash" style="color: white"></i>
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="spectators.length === 0" class="alert alert-info">
          Nema registrovanih gledalaca.
        </div>
      </div>

      <div v-if="spectators.length > 0" class="mt-3 text-white">
        <strong>Ukupno gledalaca: {{ spectators.length }}</strong>
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE_URL } from "@/config";

export default {
  name: "SpectatorsList",
  data() {
    return {
      spectators: [],
      loading: false,
      error: null,
      showAddForm: false,
      showEditForm: false,
      newSpectator: {
        jmbg: "",
        firstName: "",
        lastName: "",
        phoneNumber: "",
        emailAddress: "",
      },
      editingSpectator: null,
    };
  },
  computed: {
    isFormValid() {
      return (
        this.newSpectator.jmbg.length === 13 &&
        this.newSpectator.firstName.trim() !== "" &&
        this.newSpectator.lastName.trim() !== ""
      );
    },
  },
  async mounted() {
    await this.fetchSpectators();
  },
  methods: {
    isActive(path) {
      return this.$route.path === path;
    },
    async fetchSpectators() {
      this.loading = true;
      this.error = null;
      try {
        const response = await axios.get(`${API_BASE_URL}/spectators`);
        this.spectators = response.data;
      } catch (err) {
        console.error("Greška prilikom učitavanja gledalaca:", err);
        this.error = "Nije moguće učitati listu gledalaca. Pokušajte ponovo.";
      } finally {
        this.loading = false;
      }
    },
    async handleAddSpectator() {
      if (!this.isFormValid) return;
      try {
        const spectatorData = {
          jmbg: this.newSpectator.jmbg,
          firstName: this.newSpectator.firstName,
          lastName: this.newSpectator.lastName,
          phoneNumber: this.newSpectator.phoneNumber || null,
          emailAddress: this.newSpectator.emailAddress || null,
          ticketAgent: { id: "r1" },
        };
        await axios.post(`${API_BASE_URL}/spectators`, spectatorData);
        this.resetForm();
        await this.fetchSpectators();
        alert("Gledalac je uspešno dodat!");
      } catch (err) {
        console.error("Greška prilikom dodavanja gledaoca:", err);
        alert(err.response?.data || "Greška prilikom dodavanja gledaoca.");
      }
    },
    editSpectator(spectator) {
      this.showAddForm = false;
      this.showEditForm = true;
      this.editingSpectator = { ...spectator };
    },
    async handleUpdateSpectator() {
      if (!this.editingSpectator) return;
      try {
        const updateData = {
          firstName: this.editingSpectator.firstName,
          lastName: this.editingSpectator.lastName,
          phoneNumber: this.editingSpectator.phoneNumber || null,
          emailAddress: this.editingSpectator.emailAddress || null,
        };
        await axios.patch(
          `${API_BASE_URL}/spectators/${this.editingSpectator.jmbg}`,
          updateData
        );
        this.cancelEdit();
        await this.fetchSpectators();
        alert("Gledalac je uspešno ažuriran!");
      } catch (err) {
        console.error("Greška prilikom ažuriranja gledaoca:", err);
        alert(err.response?.data || "Greška prilikom ažuriranja gledaoca.");
      }
    },
    async deleteSpectator(jmbg) {
      if (!confirm("Da li ste sigurni da želite da obrišete ovog gledaoca?"))
        return;
      try {
        await axios.delete(`${API_BASE_URL}/spectators/${jmbg}`);
        await this.fetchSpectators();
        alert("Gledalac je uspešno obrisan!");
      } catch (err) {
        console.error("Greška prilikom brisanja gledaoca:", err);
        alert(err.response?.data || "Greška prilikom brisanja gledaoca.");
      }
    },
    resetForm() {
      this.newSpectator = {
        jmbg: "",
        firstName: "",
        lastName: "",
        phoneNumber: "",
        emailAddress: "",
      };
      this.showAddForm = false;
    },
    cancelAdd() {
      this.resetForm();
    },
    cancelEdit() {
      this.editingSpectator = null;
      this.showEditForm = false;
    },
  },
};
</script>

<style scoped>
html,
body {
  height: 100%;
  margin: 0;
}

.spectators-page-wrapper {
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

.spectators-page {
  background: rgba(20, 20, 35, 0.92);
  padding: 2rem;
  border-radius: 1rem;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.7);
  max-width: 1200px;
  width: 100%;
  color: #fff;
}

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

.spinner-border {
  width: 3rem;
  height: 3rem;
  border-width: 0.3rem;
}

.add-form {
  background: rgba(30, 30, 50, 0.6);
  padding: 1.5rem;
  border-radius: 0.5rem;
  border: 1px solid rgba(212, 175, 55, 0.3);
}

.form-control {
  background: rgba(255, 255, 255, 0.9);
  color: #000;
  border-radius: 0.5rem;
  border: 1px solid #ccc;
}

.form-control:focus {
  border-color: #d4af37;
  box-shadow: 0 0 0 0.2rem rgba(212, 175, 55, 0.25);
}

.btn-success {
  background-color: #28a745;
  border-color: #28a745;
}

.btn-success:hover {
  background-color: #218838;
  border-color: #1e7e34;
}

.btn-primary {
  background-color: #8b0000;
  border-color: #8b0000;
}

.btn-primary:hover {
  background-color: #660000;
  border-color: #660000;
}

.btn-secondary {
  background-color: #6c757d;
  border-color: #6c757d;
}

.text-white-50 {
  color: rgba(255, 255, 255, 0.7) !important;
}
</style>
