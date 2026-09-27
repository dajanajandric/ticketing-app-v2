// Sav saobracaj ka backendu ide preko API gateway-a (services/api-gateway),
// koji dalje rutira ka users-service i ticketing-service.
export const API_BASE_URL =
  process.env.VUE_APP_API_BASE_URL || "http://localhost:8090";
