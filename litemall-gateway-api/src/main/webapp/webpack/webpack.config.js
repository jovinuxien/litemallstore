// Customer SPA — dev. Same-origin BFF: relative /auth, /srv, /wx are proxied
// to litemall-gateway-api (:8090) so there is no CORS and the SPA ships from
// its own edge in prod.
const path = require('path');
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
    rules: [{ test: /\.tsx?$/, use: 'ts-loader', exclude: /node_modules/ }],
  },
  plugins: [new HtmlWebpackPlugin({ template: path.resolve(__dirname, '../public/index.html') })],
  devServer: {
    port: 9000,
    historyApiFallback: true,
    proxy: [
      {
        context: ['/auth', '/srv', '/wx'],
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    ],
  },
};