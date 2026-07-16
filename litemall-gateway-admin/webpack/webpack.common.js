const path = require('path');
const fs = require('fs');
const webpack = require('webpack');
const { merge } = require('webpack-merge');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const ESLintPlugin = require('eslint-webpack-plugin');
const { hashElement } = require('folder-hash');
const MergeJsonWebpackPlugin = require('merge-jsons-webpack-plugin');
const utils = require('./utils.js');
const environment = require('./environment');

const getTsLoaderRule = env => {
  const rules = [
    {
      loader: 'thread-loader',
      options: {
        // There should be 1 cpu for the fork-ts-checker-webpack-plugin.
        // The value may need to be adjusted (e.g. to 1) in some CI environments,
        // as cpus() may report more cores than what are available to the build.
        workers: require('os').cpus().length - 1,
      },
    },
    {
      loader: 'ts-loader',
      options: {
        transpileOnly: true,
        happyPackMode: true,
      },
    },
  ];
  return rules;
};

module.exports = async options => {
  const development = options.env === 'development';
  // The admin SPA carries no i18n bundles (the JHipster i18n copy is disabled
  // below). Guard the hash so a missing i18n folder doesn't break the build;
  // I18N_HASH is only a cache-busting define.
  const i18nDir = path.resolve(__dirname, '../src/main/webapp/i18n');
  const languagesHash = fs.existsSync(i18nDir)
    ? await hashElement(i18nDir, { algo: 'md5', encoding: 'hex', files: { include: ['*.json'] } })
    : { hash: 'noi18n' };

  return merge(
    {
      cache: {
        // 1. Set cache type to filesystem
        type: 'filesystem',
        cacheDirectory: path.resolve(__dirname, '../target/webpack'),
        buildDependencies: {
          // 2. Add your config as buildDependency to get cache invalidation on config change
          config: [
            __filename,
            path.resolve(__dirname, `webpack.${development ? 'dev' : 'prod'}.js`),
            path.resolve(__dirname, 'environment.js'),
            path.resolve(__dirname, 'utils.js'),
            path.resolve(__dirname, '../postcss.config.js'),
            path.resolve(__dirname, './../tsconfig.json'),
          ],
        },
      },
      resolve: {
        extensions: ['.js', '.jsx', '.ts', '.tsx', '.json'],
        modules: ['node_modules'],
        alias: utils.mapTypescriptAliasToWebpackAlias(),
        fallback: {
          path: require.resolve('path-browserify'),
        },
      },
      module: {
        rules: [
          {
            test: /\.tsx?$/,
            use: getTsLoaderRule(options.env),

            include: [utils.root('/src/main/webapp/app')],
            exclude: [utils.root('node_modules')],

            //include: [path.resolve(__dirname, '/src/main/webapp/app')],
            //exclude: [path.resolve(__dirname, 'node_modules')],
          },
          /*
       ,
       Disabled due to https://github.com/jhipster/generator-jhipster/issues/16116
       Can be enabled with @reduxjs/toolkit@>1.6.1
      {
        enforce: 'pre',
        test: /\.jsx?$/,
        loader: 'source-map-loader'
      }
      */
        ],
      },
      stats: {
        children: false,
      },
      plugins: [
        new webpack.EnvironmentPlugin({
          // react-jhipster requires LOG_LEVEL config.
          LOG_LEVEL: development ? 'info' : 'error',
        }),
        new webpack.DefinePlugin({
          I18N_HASH: JSON.stringify(languagesHash.hash),
          DEVELOPMENT: JSON.stringify(development),
          VERSION: JSON.stringify(environment.VERSION),
          SERVER_API_URL: JSON.stringify(environment.SERVER_API_URL),
          MAUTIC_BASE_URL: JSON.stringify(environment.MAUTIC_BASE_URL),
        }),
        new ESLintPlugin({
          baseConfig: {
            parserOptions: {
              project: ['../tsconfig.json'],
            },
          },
        }),
        new ForkTsCheckerWebpackPlugin(),
        new CopyWebpackPlugin({
          patterns: [
            {
              // https://github.com/swagger-api/swagger-ui/blob/v4.6.1/swagger-ui-dist-package/README.md
              context: require('swagger-ui-dist').getAbsoluteFSPath(),
              from: '*.{js,css,html,png}',
              to: 'swagger-ui/',
              globOptions: { ignore: ['**/index.html'] },
            },
            /* {
              from: require.resolve('axios/dist/axios.min.js'),
              to: 'swagger-ui/',
            },*/
            /*  { from: path.resolve(__dirname, '../src/main/webapp/swagger-ui/'), to: 'swagger-ui/' },
            { from: path.resolve(__dirname, '../src/main/webapp/content/'), to: 'content/' },
            { from: path.resolve(__dirname, '../src/main/webapp/favicon.ico'), to: 'favicon.ico' },
            { from: path.resolve(__dirname, '../src/main/webapp/manifest.webapp'), to: 'manifest.webapp' }, */
            // jhipster-needle-add-assets-to-webpack - JHipster will add/remove third-party resources in this array
            { from: path.resolve(__dirname, '../src/main/webapp/robots.txt'), to: 'robots.txt' },
          ],
        }),
        new HtmlWebpackPlugin({
          template: path.resolve(__dirname, '../src/main/webapp/index.html'),
          chunksSortMode: 'auto',
          inject: 'body',
          base: '/',
        }),
        new MergeJsonWebpackPlugin({
          output: {
            groupBy: [
              //{ pattern: utils.root('/src/main/webapp/i18n/en/*.json'), fileName: utils.root('./i18n/en.json') },
              //jhipster-needle-i18n-language-webpack - JHipster will add/remove languages in this array
            ],
          },
        }),
      ],
    }
    // jhipster-needle-add-webpack-config - JHipster will add custom config
  );
};
