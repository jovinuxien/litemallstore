declare const VERSION: string;
declare const SERVER_API_URL: string;
declare const DEVELOPMENT: string;
declare const I18N_HASH: string;
// Wave 6: Mautic UI base URL baked in at build time (webpack/environment.js);
// empty string when the deployment has no Mautic.
declare const MAUTIC_BASE_URL: string;

declare module '*.json' {
  const value: unknown;
  export default value;
}
