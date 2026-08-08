/**
 * Country calling codes (Wave 16) — the ONLY static data is the ISO-3166
 * alpha-2 → ITU dial-code map below. Display names come from the browser's
 * own `Intl.DisplayNames` (complete + localizable, no shipped name table) and
 * the flag is computed from the iso2 via regional-indicator codepoints.
 * No external service, no libphonenumber: an E.164 number is composed as
 * `+<dial><national digits>` — enough for storage and CJ's phone field.
 */

const DIAL: Record<string, string> = {
  AD: '376', AE: '971', AF: '93', AG: '1268', AI: '1264', AL: '355', AM: '374', AO: '244', AR: '54', AS: '1684',
  AT: '43', AU: '61', AW: '297', AZ: '994', BA: '387', BB: '1246', BD: '880', BE: '32', BF: '226', BG: '359',
  BH: '973', BI: '257', BJ: '229', BM: '1441', BN: '673', BO: '591', BR: '55', BS: '1242', BT: '975', BW: '267',
  BY: '375', BZ: '501', CA: '1', CD: '243', CF: '236', CG: '242', CH: '41', CI: '225', CK: '682', CL: '56',
  CM: '237', CN: '86', CO: '57', CR: '506', CU: '53', CV: '238', CW: '599', CY: '357', CZ: '420', DE: '49',
  DJ: '253', DK: '45', DM: '1767', DO: '1809', DZ: '213', EC: '593', EE: '372', EG: '20', ER: '291', ES: '34',
  ET: '251', FI: '358', FJ: '679', FK: '500', FM: '691', FO: '298', FR: '33', GA: '241', GB: '44', GD: '1473',
  GE: '995', GF: '594', GH: '233', GI: '350', GL: '299', GM: '220', GN: '224', GP: '590', GQ: '240', GR: '30',
  GT: '502', GU: '1671', GW: '245', GY: '592', HK: '852', HN: '504', HR: '385', HT: '509', HU: '36', ID: '62',
  IE: '353', IL: '972', IN: '91', IQ: '964', IR: '98', IS: '354', IT: '39', JM: '1876', JO: '962', JP: '81',
  KE: '254', KG: '996', KH: '855', KI: '686', KM: '269', KN: '1869', KR: '82', KW: '965', KY: '1345', KZ: '7',
  LA: '856', LB: '961', LC: '1758', LI: '423', LK: '94', LR: '231', LS: '266', LT: '370', LU: '352', LV: '371',
  LY: '218', MA: '212', MC: '377', MD: '373', ME: '382', MG: '261', MH: '692', MK: '389', ML: '223', MM: '95',
  MN: '976', MO: '853', MQ: '596', MR: '222', MS: '1664', MT: '356', MU: '230', MV: '960', MW: '265', MX: '52',
  MY: '60', MZ: '258', NA: '264', NC: '687', NE: '227', NG: '234', NI: '505', NL: '31', NO: '47', NP: '977',
  NR: '674', NU: '683', NZ: '64', OM: '968', PA: '507', PE: '51', PF: '689', PG: '675', PH: '63', PK: '92',
  PL: '48', PM: '508', PR: '1787', PS: '970', PT: '351', PW: '680', PY: '595', QA: '974', RE: '262', RO: '40',
  RS: '381', RU: '7', RW: '250', SA: '966', SB: '677', SC: '248', SD: '249', SE: '46', SG: '65', SI: '386',
  SK: '421', SL: '232', SM: '378', SN: '221', SO: '252', SR: '597', SS: '211', ST: '239', SV: '503', SY: '963',
  SZ: '268', TC: '1649', TD: '235', TG: '228', TH: '66', TJ: '992', TL: '670', TM: '993', TN: '216', TO: '676',
  TR: '90', TT: '1868', TV: '688', TW: '886', TZ: '255', UA: '380', UG: '256', US: '1', UY: '598', UZ: '998',
  VC: '1784', VE: '58', VG: '1284', VI: '1340', VN: '84', VU: '678', WS: '685', XK: '383', YE: '967', ZA: '27',
  ZM: '260', ZW: '263',
};

/**
 * National significant-number length (digits AFTER the dial prefix above, trunk
 * '0' already stripped) per country — ITU numbering-plan ranges, [min, max].
 * Curated for the markets we ship to; anything unlisted falls back to the
 * E.164 envelope so an exotic plan is never hard-blocked on bad data.
 */
const LEN: Record<string, [number, number]> = {
  US: [10, 10], CA: [10, 10], MX: [10, 10], BR: [10, 11], AR: [10, 10], CL: [9, 9], CO: [10, 10], PE: [9, 9],
  VE: [10, 10], EC: [9, 9], BO: [8, 8], PY: [9, 9], UY: [8, 8], CR: [8, 8], PA: [8, 8], GT: [8, 8], HN: [8, 8],
  NI: [8, 8], SV: [8, 8], CU: [8, 8],
  GB: [9, 10], IE: [7, 9], FR: [9, 9], DE: [6, 11], IT: [6, 11], ES: [9, 9], PT: [9, 9], NL: [9, 9], BE: [8, 9],
  LU: [6, 9], CH: [9, 9], AT: [7, 13], SE: [7, 9], NO: [8, 8], DK: [8, 8], FI: [5, 12], IS: [7, 7], EE: [7, 8],
  LV: [8, 8], LT: [8, 8], PL: [9, 9], CZ: [9, 9], SK: [9, 9], HU: [8, 9], RO: [9, 9], BG: [8, 9], GR: [10, 10],
  HR: [8, 9], SI: [8, 8], RS: [8, 9], BA: [8, 8], MK: [8, 8], AL: [8, 9], ME: [8, 8], XK: [8, 8], MT: [8, 8],
  CY: [8, 8],
  RU: [10, 10], KZ: [10, 10], UA: [9, 9], BY: [9, 9], MD: [8, 8], GE: [9, 9], AM: [8, 8], AZ: [9, 9],
  TR: [10, 10], IL: [8, 9], SA: [9, 9], AE: [8, 9], QA: [8, 8], KW: [8, 8], BH: [8, 8], OM: [8, 8], JO: [9, 9],
  LB: [7, 8], IQ: [10, 10], IR: [10, 10], SY: [9, 9], YE: [9, 9], PS: [9, 9],
  EG: [10, 10], MA: [9, 9], DZ: [9, 9], TN: [8, 8], LY: [9, 9], SD: [9, 9], ET: [9, 9], KE: [9, 9], NG: [8, 10],
  GH: [9, 9], ZA: [9, 9], TZ: [9, 9], UG: [9, 9], RW: [9, 9], ZM: [9, 9], ZW: [9, 9], MZ: [9, 9], AO: [9, 9],
  CM: [9, 9], CI: [10, 10], SN: [9, 9], ML: [8, 8], BF: [8, 8], NE: [8, 8], TG: [8, 8], BJ: [8, 10], GA: [7, 8],
  CD: [9, 9], CG: [9, 9], MG: [9, 10], MU: [7, 8], GM: [7, 7], GN: [8, 9], SL: [8, 8], MW: [7, 9], LS: [8, 8],
  BW: [7, 8], NA: [8, 9], SZ: [7, 8],
  IN: [10, 10], PK: [10, 10], BD: [8, 10], LK: [9, 9], NP: [8, 10], AF: [9, 9], MM: [8, 10], TH: [8, 9],
  VN: [9, 10], KH: [8, 9], LA: [8, 10], MY: [9, 10], SG: [8, 8], ID: [8, 12], PH: [10, 10], CN: [10, 12],
  HK: [8, 8], MO: [8, 8], TW: [8, 9], JP: [9, 11], KR: [8, 11], MN: [8, 8], UZ: [9, 9], TJ: [9, 9], KG: [9, 9],
  TM: [8, 8],
  AU: [9, 9], NZ: [8, 10], FJ: [7, 7],
};

/** Envelope for unlisted plans: E.164 caps at 15 digits incl. dial; stay lenient. */
const LEN_FALLBACK: [number, number] = [4, 14];

export interface PhoneLengthCheck {
  ok: boolean;
  kind?: 'short' | 'long';
  min: number;
  max: number;
  /** digit count actually checked (national digits, trunk zeros stripped) */
  count: number;
}

/**
 * Check the national digit count against the selected country's plan. Empty
 * input is OK (required-ness is the caller's business). Uses the SAME digit
 * normalization as {@link toE164} so what we validate is what we store.
 */
export const checkPhoneLength = (iso2: string, national: string): PhoneLengthCheck => {
  const digits = national.replace(/\D/g, '').replace(/^0+/, '');
  const [min, max] = LEN[iso2] ?? LEN_FALLBACK;
  if (!digits.length) return { ok: true, min, max, count: 0 };
  if (digits.length < min) return { ok: false, kind: 'short', min, max, count: digits.length };
  if (digits.length > max) return { ok: false, kind: 'long', min, max, count: digits.length };
  return { ok: true, min, max, count: digits.length };
};

/**
 * Split a full international string (`+…` or `00…`) into the dial-matched
 * country + national remainder — longest dial prefix wins; ties on shared
 * dials (+1, +7) break toward `preferIso2`, then the browser locale.
 * Returns null when the text carries no international prefix or no dial matches.
 */
export const splitInternational = (
  raw: string,
  preferIso2?: string
): { country: DialCountry; national: string } | null => {
  const v = raw.trim();
  const intl = v.startsWith('+') ? v.slice(1) : v.startsWith('00') ? v.slice(2) : null;
  if (intl === null) return null;
  const digits = intl.replace(/\D/g, '');
  if (!digits) return null;
  const matches = dialCountries().filter(c => digits.startsWith(c.dial));
  if (!matches.length) return null;
  const longest = Math.max(...matches.map(c => c.dial.length));
  const candidates = matches.filter(c => c.dial.length === longest);
  const best =
    candidates.find(c => c.iso2 === preferIso2) ??
    candidates.find(c => c.iso2 === localeIso2()) ??
    candidates[0];
  return { country: best, national: digits.slice(best.dial.length) };
};

/** Browser-locale region when it is a country we know, else 'US'. */
export const localeIso2 = (): string => {
  try {
    const region = (navigator.language.split('-')[1] ?? '').toUpperCase();
    return DIAL[region] ? region : 'US';
  } catch {
    return 'US'; // no navigator (tests/SSR)
  }
};

export interface DialCountry {
  iso2: string;
  name: string;
  dial: string;
  flag: string;
}

const flagOf = (iso2: string): string =>
  String.fromCodePoint(...[...iso2].map(c => 0x1f1e6 + c.charCodeAt(0) - 65));

let cached: DialCountry[] | null = null;

/** All countries, English display names, sorted by name. Computed once. */
export const dialCountries = (): DialCountry[] => {
  if (cached) return cached;
  let names: Intl.DisplayNames | null = null;
  try {
    names = new Intl.DisplayNames(['en'], { type: 'region' });
  } catch {
    names = null; // ancient browser: iso2 codes still render
  }
  cached = Object.entries(DIAL)
    .map(([iso2, dial]) => ({ iso2, dial, flag: flagOf(iso2), name: names?.of(iso2) ?? iso2 }))
    .sort((a, b) => a.name.localeCompare(b.name));
  return cached;
};

/** `+<dial><national digits>` — E.164-shaped, or '' when the number is empty. */
export const toE164 = (dial: string, national: string): string => {
  const digits = national.replace(/\D/g, '').replace(/^0+/, '');
  return digits ? `+${dial}${digits}` : '';
};
