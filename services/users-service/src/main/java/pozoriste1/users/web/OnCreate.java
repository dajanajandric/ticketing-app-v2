package pozoriste1.users.web;

import jakarta.validation.groups.Default;

/**
 * Validaciona grupa za kreiranje (POST): pored obicnih pravila (Default) provjerava
 * i obavezna polja. PATCH koristi samo Default, pa polja tamo mogu izostati.
 */
public interface OnCreate extends Default {
}
