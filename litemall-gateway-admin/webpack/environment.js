module.exports = {
  // APP_VERSION is passed as an environment variable from the Gradle / Maven build tasks.
  // eslint-disable-next-line no-prototype-builtins
  VERSION: process.env.hasOwnProperty('APP_VERSION') ? process.env.APP_VERSION : 'DEV',
  // The root URL for API calls, ending with a '/' - for example: `"https://www.jhipster.tech:8081/myservice/"`.
  // If this URL is left empty (""), then it will be relative to the current context.
  // If you use an API server, in `prod` mode, you will need to enable CORS
  // (see the `jhipster.cors` common JHipster property in the `application-*.yml` configurations)
  SERVER_API_URL: '',
  // Wave 6: Mautic UI base URL (e.g. http://localhost:8085). Set at SPA build
  // time; when empty the campaign page hides its "Segment in Mautic" link-out.
  // eslint-disable-next-line no-prototype-builtins
  MAUTIC_BASE_URL: process.env.hasOwnProperty('MAUTIC_BASE_URL') ? process.env.MAUTIC_BASE_URL : '',
};
