const path = require('path');
const webpackMerge = require('webpack-merge').merge;
// BrowserSyncPlugin removed (Option A): it was configured with port: 9000,
// the same port as webpack-dev-server, so the two raced for the socket and
// the `/srv` proxy below silently never fired. WDS's `hot: true` already
// provides live reload, so BrowserSync was redundant in this setup.
const SimpleProgressWebpackPlugin = require('simple-progress-webpack-plugin');
const WebpackNotifierPlugin = require('webpack-notifier');
const sass = require('sass');

const utils = require('./utils');
const commonConfig = require('./webpack.common');

const ENV = 'development';

module.exports = async options =>
  webpackMerge(await commonConfig({ env: ENV }), {
    devtool: 'cheap-module-source-map',
    mode: ENV,
    entry: utils.root('src/main/webapp/app', 'index'),
    output: {
      path: utils.root('gateway/target/classes/static/'),
      filename: '[name].[contenthash:8].js',
      chunkFilename: '[name].[chunkhash:8].chunk.js',
    },
    optimization: {
      moduleIds: 'named',
    },
    module: {
      rules: [
        {
          test: /\.(sa|sc|c)ss$/,
          use: [
            'style-loader',
            'css-loader',
            {
              loader: 'postcss-loader',
            },
            {
              loader: 'sass-loader',
              options: { implementation: sass },
            },
          ],
        },
        {
          test: /\.svg$/,
          use: ['@svgr/webpack'],
        },
      ],
    },
    devServer: {
      hot: true,
      static: {
        directory: './target/classes/static/',
      },
      port: 9000,
      proxy:{
        '/srv': {
          target: 'http://localhost:8080',
                  secure: false,
                  changeOrigin: true,
                  logLevel: 'debug'
       },
        '/auth': {
                target: 'http://localhost:8080',
                secure: false,
                changeOrigin: true
        },
        '/management': {
                 target: 'http://localhost:8080',
                 secure: false,
                 changeOrigin: true
        },

        '/api': {
                target: 'http://localhost:8080',
                secure: false,
                changeOrigin: true
              },
        '/auth': {
                target: 'http://localhost:8080',
                secure: false,
                changeOrigin: true
              }
      },
      
      https: options.tls,
      // Option C: never rewrite backend API paths to index.html. If the `/srv`
      // proxy above ever fails (target down, eureka deregistration, timeout),
      // historyApiFallback would otherwise hand back the SPA shell with 200,
      // hiding the failure behind a sign-in loop. Explicit pass-through here
      // makes the failure visible as a real 404 from the upstream layer.
      historyApiFallback: {
        rewrites: [
          { from: /^\/srv\//, to: context => context.parsedUrl.pathname },
        ],
      },
    },
    stats: process.env.JHI_DISABLE_WEBPACK_LOGS ? 'none' : options.stats,
    plugins: [
      process.env.JHI_DISABLE_WEBPACK_LOGS
        ? null
        : new SimpleProgressWebpackPlugin({
            format: options.stats === 'minimal' ? 'compact' : 'expanded',
          }),
      new WebpackNotifierPlugin({
        title: 'Web Store',
        contentImage: path.join(__dirname, 'logo-jhipster.png'),
      }),
    ].filter(Boolean),
  });
