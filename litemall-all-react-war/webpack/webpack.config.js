const path = require('path');
const webpackMerge = require('webpack-merge').merge;
const BrowserSyncPlugin = require('browser-sync-webpack-plugin');
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
      path: utils.root('../target/classes/static/'),
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
              options: { implementation: require('sass'), sourceMap: true },
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
        /*directory: './target/classes/static/', */
        directory: path.resolve(__dirname, '../src/main/webapp/content/public'),
        publicPath: '/',
      },
      port: 9060,

      proxy: [
        {
          //context: [ '/srv', '/wx', '/admin', '/management', '/h2-console', '/auth'],
          context: ['/catalog', '/admin', '/management', '/h2-console', '/auth', '/oauth2', '/login'],
          target: `http${options.tls ? 's' : ''}://localhost:8080/srv`,
          secure: false,
          changeOrigin: true,
        },
      ],
      https: options.tls,
      historyApiFallback: true,
    },
    stats: process.env.JHI_DISABLE_WEBPACK_LOGS ? 'none' : options.stats,

    plugins: [
      process.env.JHI_DISABLE_WEBPACK_LOGS
        ? null
        : new SimpleProgressWebpackPlugin({
            format: options.stats === 'minimal' ? 'compact' : 'expanded',
          }),

      new BrowserSyncPlugin(
        {
          https: options.tls,
          host: 'localhost',
          port: 9000,
          proxy: {
            target: `http${options.tls ? 's' : ''}://localhost:${options.watch ? '8080' : '9060'}`,
            ws: true,
            proxyOptions: {
              changeOrigin: false, //pass the Host header to the backend unchanged  https://github.com/Browsersync/browser-sync/issues/430
            },
          },
          /* socket: {
            clients: {
              heartbeatTimeout: 60000,
            },
          }, */

          serveStatic: [
            {
              route: '/content/public',
              dir: path.resolve(__dirname, '../src/main/webapp/content/public'),
            },
          ],
          /*  ghostMode: {
            // uncomment this part to disable BrowserSync ghostMode; https://github.com/jhipster/generator-jhipster/issues/11116
            clicks: false,
            location: false,
            forms: false,
            scroll: false,
          }, */
        },
        {
          reload: false,
        }
      ),
      new WebpackNotifierPlugin({
        title: 'Web Store',
        contentImage: path.join(__dirname, 'logo-jhipster.png'),
      }),
    ].filter(Boolean),
  });
