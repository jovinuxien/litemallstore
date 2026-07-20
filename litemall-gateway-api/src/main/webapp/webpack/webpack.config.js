// Customer SPA — dev. Same-origin BFF: relative /auth, /srv, /wx are proxied
// to litemall-gateway-api (:8090) so there is no CORS and the SPA ships from
// its own edge in prod.
const path = require('path');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  mode: 'development',
  entry: path.resolve(__dirname, '../app/index.tsx'),
  output: {
    path: path.resolve(__dirname, '../../../../target/classes/static'),
    filename: 'app/[name].bundle.js',
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
      favicon: path.resolve(__dirname, '../public/favicon.ico'),
    }),
    // Trovemo icon set + webmanifest, referenced by absolute path from
    // index.html (index.html is the template, favicon.ico is emitted by
    // HtmlWebpackPlugin above — both excluded to avoid double emission).
    new CopyWebpackPlugin({
      patterns: [
        {
          from: path.resolve(__dirname, '../public'),
          to: '.',
          globOptions: { ignore: ['**/index.html', '**/favicon.ico'] },
        },
      ],
    }),
  ],
  devServer: {
    port: 9000,
    historyApiFallback: true,
    proxy: [
      {
        context: ['/auth', '/srv'],
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      // Dev mirror of the prod Caddy /_cdn image proxy (docker-compose/
      // Caddyfile handle_path blocks): the gateway rewrites CJ image URLs to
      // /_cdn/… since c96c73d6f, so without these routes every product image
      // 404s on :9000.
      {
        context: ['/_cdn/cf'],
        target: 'https://cf.cjdropshipping.com',
        changeOrigin: true,
        pathRewrite: { '^/_cdn/cf': '' },
      },
      {
        context: ['/_cdn/oss'],
        target: 'https://oss-cf.cjdropshipping.com',
        changeOrigin: true,
        pathRewrite: { '^/_cdn/oss': '' },
      },
    ],
  },
};