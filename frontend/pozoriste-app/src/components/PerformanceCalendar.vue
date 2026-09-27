<template>
  <div class="calendar" :class="{ disabled }">
    <div class="calendar-header">
      <button
        type="button"
        class="nav-btn"
        :disabled="disabled"
        aria-label="Prethodni mesec"
        @click="changeMonth(-1)"
      >
        ‹
      </button>
      <span class="month-title">{{ monthTitle }}</span>
      <button
        type="button"
        class="nav-btn"
        :disabled="disabled"
        aria-label="Sledeći mesec"
        @click="changeMonth(1)"
      >
        ›
      </button>
    </div>

    <div class="grid">
      <span v-for="d in weekDays" :key="d" class="weekday">{{ d }}</span>
      <span v-for="n in leadingBlanks" :key="'blank-' + n" />
      <button
        v-for="day in days"
        :key="day.iso"
        type="button"
        class="day"
        :class="{
          available: day.available,
          unavailable: !day.available,
          selected: day.iso === modelValue,
        }"
        :disabled="disabled || !day.available"
        :title="day.available ? 'Ima izvođenja' : 'Nema izvođenja'"
        @click="$emit('update:modelValue', day.iso)"
      >
        {{ day.number }}
      </button>
    </div>

    <div class="legend">
      <span><i class="dot available" /> ima izvođenja</span>
      <span><i class="dot unavailable" /> nema izvođenja</span>
    </div>
  </div>
</template>

<script>
const MONTHS = [
  "Januar",
  "Februar",
  "Mart",
  "April",
  "Maj",
  "Jun",
  "Jul",
  "Avgust",
  "Septembar",
  "Oktobar",
  "Novembar",
  "Decembar",
];

// Datum kao "YYYY-MM-DD" u lokalnoj vremenskoj zoni (isti format koji vraća backend)
function toIso(year, month, day) {
  const mm = String(month + 1).padStart(2, "0");
  const dd = String(day).padStart(2, "0");
  return `${year}-${mm}-${dd}`;
}

export default {
  name: "PerformanceCalendar",
  props: {
    modelValue: { type: String, default: "" },
    // Datumi ("YYYY-MM-DD") kada predstava ima izvođenje
    availableDates: { type: Array, default: () => [] },
    disabled: { type: Boolean, default: false },
  },
  emits: ["update:modelValue"],
  data() {
    const today = new Date();
    return {
      weekDays: ["Pon", "Uto", "Sre", "Čet", "Pet", "Sub", "Ned"],
      year: today.getFullYear(),
      month: today.getMonth(),
    };
  },
  computed: {
    monthTitle() {
      return `${MONTHS[this.month]} ${this.year}`;
    },
    // Ponedeljak je prvi dan u nedelji
    leadingBlanks() {
      const firstDay = new Date(this.year, this.month, 1).getDay();
      return (firstDay + 6) % 7;
    },
    days() {
      const count = new Date(this.year, this.month + 1, 0).getDate();
      const available = new Set(this.availableDates);
      return Array.from({ length: count }, (_, i) => {
        const iso = toIso(this.year, this.month, i + 1);
        return { iso, number: i + 1, available: available.has(iso) };
      });
    },
  },
  watch: {
    // Kad se izabere druga predstava, kalendar skoči na mesec prvog izvođenja
    availableDates: {
      immediate: true,
      handler(dates) {
        if (!dates.length) return;
        const first = [...dates].sort()[0];
        const [y, m] = first.split("-").map(Number);
        this.year = y;
        this.month = m - 1;
      },
    },
  },
  methods: {
    changeMonth(delta) {
      const d = new Date(this.year, this.month + delta, 1);
      this.year = d.getFullYear();
      this.month = d.getMonth();
    },
  },
};
</script>

<style scoped>
.calendar {
  background: rgba(255, 255, 255, 0.95);
  color: #000;
  border-radius: 0.5rem;
  border: 1px solid #ccc;
  padding: 0.75rem;
}

.calendar.disabled {
  opacity: 0.6;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.month-title {
  font-weight: 600;
}

.nav-btn {
  border: none;
  background: transparent;
  font-size: 1.4rem;
  line-height: 1;
  padding: 0 0.6rem;
  cursor: pointer;
}

.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  text-align: center;
}

.weekday {
  font-size: 0.8rem;
  color: #666;
  padding-bottom: 2px;
}

.day {
  border: 1px solid transparent;
  border-radius: 0.35rem;
  padding: 0.35rem 0;
  background: transparent;
  font-variant-numeric: tabular-nums;
}

.day.available {
  background: #d1f0dc;
  color: #146c43;
  font-weight: 600;
  cursor: pointer;
}

.day.available:hover:not(:disabled) {
  border-color: #146c43;
}

.day.unavailable {
  color: #dc3545;
  cursor: not-allowed;
}

.day.selected {
  background: #007bff;
  color: #fff;
}

.day:focus-visible {
  outline: 2px solid #007bff;
  outline-offset: 1px;
}

.legend {
  display: flex;
  gap: 1rem;
  margin-top: 0.5rem;
  font-size: 0.8rem;
  color: #444;
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}

.dot.available {
  background: #146c43;
}

.dot.unavailable {
  background: #dc3545;
}
</style>
