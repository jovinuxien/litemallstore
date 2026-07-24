// Customer SPA — production bundle into the gateway-api jar static assets.
const path = require('path');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  mode: 'production',
  entry: path.resolve(__dirname, '../app/index.tsx'),
  output: {
    path: path.resolve(__dirname, '../../../../target/classes/static'),
    filename: 'app/[name].[contenthash].js',
    publicPath: '/',
    clean: true,
  },
  resolve: {
    extensions: ['.tsx', '.ts', '.js'],
    alias: {
      app: path.resolve(__dirname, '../app'),
      '@litemall/shared': path.resolve(__dirname, '../../../../../litemall-shared/src/index.ts'),
    },
  },
  module: {
    rules: [
      // onlyCompileBundledFiles: type-check just the routed import graph, not
      // every file under tsconfig `include`. Peripheral views not yet migrated
      // in Phase 6 (blog/account/support/…) stay out of the build until routed.
      { test: /\.tsx?$/, use: { loader: 'ts-loader', options: { onlyCompileBundledFiles: true } }, exclude: /node_modules/ },
      { test: /\.scss$/, use: ['style-loader', 'css-loader', 'sass-loader'] },
      { test: /\.css$/, use: ['style-loader', 'css-loader'] },
      { test: /\.(woff2?|ttf|eot|svg|png|jpe?g|gif|webp)$/, type: 'asset/resource' },
    ],
  },
  plugins: [
    new HtmlWebpackPlugin({
      template: path.resolve(__dirname, '../public/index.html'),
      // No `favicon:` option — the template carries the full favicon link set
      // (with cache-busting queries); the plugin's auto-injected bare
      // <link rel="icon"> would duplicate it.
    }),
    // Trovemo icon set + webmanifest, referenced by absolute path from
    // index.html (index.html is the template and excluded; favicon.ico is
    // copied like the rest of the pack).
    new CopyWebpackPlugin({
      patterns: [
        {
          from: path.resolve(__dirname, '../public'),
          to: '.',
          globOptions: { ignore: ['**/index.html'] },
        },
      ],
    }),
  ],
};