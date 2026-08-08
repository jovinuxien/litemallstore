/**
 * Stackless unit tests for the customer SPA (Wave-19 onward). Runs on the
 * jest/ts-jest toolchain hoisted to the npm-workspace root (declared by the
 * gateway-admin webapp) — `npm test` from this directory.
 *
 * instantsearch.js is ESM-only under `es/`; jest runs CJS, so its runtime
 * imports are mapped onto the package's `cjs/` build.
 */
module.exports = {
  rootDir: '.',
  testEnvironment: 'jsdom',
  transform: {
    '^.+\\.tsx?$': [
      'ts-jest',
      {
        tsconfig: './tsconfig.test.json',
        diagnostics: false,
      },
    ],
  },
  testEnvironmentOptions: {
    url: 'http://localhost/',
  },
  testMatch: ['<rootDir>/app/**/@(*.)@(spec.ts?(x))'],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
  moduleNameMapper: {
    '\\.(css|scss)$': 'identity-obj-proxy',
    '^instantsearch\\.js/es/(.*)$': 'instantsearch.js/cjs/$1',
    '^app/(.*)$': '<rootDir>/app/$1',
    '^@litemall/shared$': '<rootDir>/../../../../litemall-shared/src/index.ts',
  },
  cacheDirectory: '<rootDir>/../../../target/jest-cache',
  testPathIgnorePatterns: ['/node_modules/'],
};
