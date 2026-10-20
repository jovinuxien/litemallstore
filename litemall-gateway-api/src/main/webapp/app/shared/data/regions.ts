/**
 * Static region/state/province suggestions per shipping country — no external
 * service, same philosophy as dialCodes. Keyed by ISO-3166 alpha-2 and limited
 * to the checkout's supported destination countries; the region field remains
 * FREE TEXT (these only power the type-ahead), so a country missing here or a
 * region outside its list degrades to plain typing.
 */
import { SHIPPING_COUNTRIES } from './countries';

export const REGIONS_BY_COUNTRY: Record<string, string[]> = {
  US: [
    'Alabama', 'Alaska', 'Arizona', 'Arkansas', 'California', 'Colorado', 'Connecticut', 'Delaware',
    'District of Columbia', 'Florida', 'Georgia', 'Hawaii', 'Idaho', 'Illinois', 'Indiana', 'Iowa',
    'Kansas', 'Kentucky', 'Louisiana', 'Maine', 'Maryland', 'Massachusetts', 'Michigan', 'Minnesota',
    'Mississippi', 'Missouri', 'Montana', 'Nebraska', 'Nevada', 'New Hampshire', 'New Jersey',
    'New Mexico', 'New York', 'North Carolina', 'North Dakota', 'Ohio', 'Oklahoma', 'Oregon',
    'Pennsylvania', 'Rhode Island', 'South Carolina', 'South Dakota', 'Tennessee', 'Texas', 'Utah',
    'Vermont', 'Virginia', 'Washington', 'West Virginia', 'Wisconsin', 'Wyoming',
  ],
  CA: [
    'Alberta', 'British Columbia', 'Manitoba', 'New Brunswick', 'Newfoundland and Labrador',
    'Northwest Territories', 'Nova Scotia', 'Nunavut', 'Ontario', 'Prince Edward Island', 'Quebec',
    'Saskatchewan', 'Yukon',
  ],
  GB: ['England', 'Scotland', 'Wales', 'Northern Ireland'],
  SE: [
    'Blekinge', 'Dalarna', 'Gotland', 'Gävleborg', 'Halland', 'Jämtland', 'Jönköping', 'Kalmar',
    'Kronoberg', 'Norrbotten', 'Skåne', 'Stockholm', 'Södermanland', 'Uppsala', 'Värmland',
    'Västerbotten', 'Västernorrland', 'Västmanland', 'Västra Götaland', 'Örebro', 'Östergötland',
  ],
  NO: [
    'Agder', 'Akershus', 'Buskerud', 'Finnmark', 'Innlandet', 'Møre og Romsdal', 'Nordland', 'Oslo',
    'Rogaland', 'Telemark', 'Troms', 'Trøndelag', 'Vestfold', 'Vestland', 'Østfold',
  ],
  DK: ['Hovedstaden', 'Midtjylland', 'Nordjylland', 'Sjælland', 'Syddanmark'],
  DE: [
    'Baden-Württemberg', 'Bayern', 'Berlin', 'Brandenburg', 'Bremen', 'Hamburg', 'Hessen',
    'Mecklenburg-Vorpommern', 'Niedersachsen', 'Nordrhein-Westfalen', 'Rheinland-Pfalz', 'Saarland',
    'Sachsen', 'Sachsen-Anhalt', 'Schleswig-Holstein', 'Thüringen',
  ],
  FR: [
    'Auvergne-Rhône-Alpes', 'Bourgogne-Franche-Comté', 'Bretagne', 'Centre-Val de Loire', 'Corse',
    'Grand Est', 'Guadeloupe', 'Guyane', 'Hauts-de-France', 'La Réunion', 'Martinique', 'Mayotte',
    'Normandie', 'Nouvelle-Aquitaine', 'Occitanie', 'Pays de la Loire', 'Provence-Alpes-Côte d\'Azur',
    'Île-de-France',
  ],
  AU: [
    'Australian Capital Territory', 'New South Wales', 'Northern Territory', 'Queensland',
    'South Australia', 'Tasmania', 'Victoria', 'Western Australia',
  ],
};

export const regionsFor = (iso2?: string): string[] =>
  iso2 ? REGIONS_BY_COUNTRY[iso2.toUpperCase()] ?? [] : [];

/** A region of one delivery country, labelled with it ("Skåne · Sweden") for lists that span countries. */
export interface LabelledRegion {
  region: string;
  /** ISO2 of the delivery country the region belongs to. */
  country: string;
  label: string;
}

let labelled: LabelledRegion[] | null = null;

/**
 * Every region of every delivery country, in the delivery list's order — the
 * suggestions to offer while the form's Country is still EMPTY, so a typed or picked
 * region can name the country instead of being ignored. Computed once.
 */
export const deliveryRegions = (): LabelledRegion[] => {
  if (labelled) return labelled;
  labelled = SHIPPING_COUNTRIES.flatMap(c => regionsFor(c.code).map(region => ({ region, country: c.code, label: `${region} · ${c.name}` })));
  return labelled;
};
